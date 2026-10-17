package com.novacamera.s24.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.novacamera.s24.core.VideoFinishPolicy
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Job local opcional enquanto o processo existir. Nunca altera nem apaga o URI original. */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoFinishJob {
    data class State(val running:Boolean=false,val progress:Int=0,val message:String="",
        val original:Uri?=null,val result:Uri?=null)
    @Volatile var state=State();private set
    @Volatile private var generation=0L
    @Volatile var recording=false
    private val main=Handler(Looper.getMainLooper())
    private val worker=ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,ArrayBlockingQueue(1),
        { task -> Thread(task,"nova-video-finish").apply { priority=Thread.MIN_PRIORITY } },ThreadPoolExecutor.DiscardOldestPolicy())
    private val listeners=mutableSetOf<(State)->Unit>()
    private var transformer:Transformer?=null
    private var output:File?=null
    fun restore(context:Context) {
        if(state.running || state.result!=null)return
        val prefs=context.getSharedPreferences("video_finish",Context.MODE_PRIVATE)
        val original=prefs.getString("original",null);val result=prefs.getString("result",null)
        if(original!=null && result!=null)state=State(progress=100,message="Último acabamento concluído",original=Uri.parse(original),result=Uri.parse(result))
    }
    fun addListener(listener:(State)->Unit) { listeners+=listener;listener(state) }
    fun removeListener(listener:(State)->Unit) { listeners-=listener }
    private fun update(value:State) { state=value;listeners.toList().forEach { it(value) } }
    private fun alive(token:Long) = token==generation && state.running
    fun cancel(reason:String="Cancelado; original preservado") {
        if(!state.running)return
        generation++;transformer?.cancel();transformer=null
        output?.delete();output=null
        update(state.copy(running=false,message=reason))
    }
    fun start(context:Context,uri:Uri) {
        if(state.running || recording)return
        val app=context.applicationContext
        val power=app.getSystemService(PowerManager::class.java)
        if(power.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE) {
            update(State(message="Aparelho quente; aguarde para aprimorar o vídeo",original=uri));return
        }
        val token=++generation
        update(State(true,0,"Analisando vídeo localmente",uri))
        val started=SystemClock.elapsedRealtime()
        // Térmica independente da Activity da câmera. Sem baixar qualidade para acelerar.
        val poll=object:Runnable {
            override fun run() {
                if(!alive(token))return
                if(recording) { cancel("Acabamento cancelado para priorizar a nova gravação; original preservado");return }
                if(power.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE) {
                    cancel("Acabamento cancelado por temperatura; original preservado");return
                }
                if(SystemClock.elapsedRealtime()-started>30*60*1000L) { cancel("Tempo de processamento excedido; original preservado");return }
                val progress=ProgressHolder()
                if(transformer?.getProgress(progress)==Transformer.PROGRESS_STATE_AVAILABLE)
                    update(state.copy(progress=10+progress.progress*75/100,message="Aplicando acabamento suave"))
                main.postDelayed(this,500)
            }
        }
        main.post(poll)
        worker.execute {
            try {
                val before=inspect(app,uri,token)
                require(minOf(before.width,before.height)<=1080 && maxOf(before.width,before.height)<=1920) { "Nesta etapa, acabamento só em até Full HD; original 4K preservado" }
                val span=(before.video.maxOrNull() ?: 0L)-(before.video.minOrNull() ?: 0L)
                require(span>0 && (before.video.size-1)*1_000_000.0/span<=60.5) { "Nesta etapa, acabamento até 60 fps; original preservado" }
                require(before.durationUs in 1L..120_000_000L) { "Nesta etapa, acabamento em clipes de até 2 minutos" }
                require(before.mime=="video/avc" || before.mime=="video/hevc") { "Codec ainda não disponível para acabamento" }
                require(before.transfer!=6 && before.transfer!=7) { "HDR não é convertido para SDR; original preservado" }
                val lift=analyze(app,uri,before.durationUs,token)
                if(!alive(token))return@execute
                if(lift<=0.0001f) {
                    main.post { if(alive(token))update(state.copy(running=false,message="Sombras já equilibradas; original mantido sem criar cópia desnecessária")) }
                    return@execute
                }
                val file=File(app.cacheDir,"video-finish-$token-${System.nanoTime()}.mp4")
                main.post {
                    if(!alive(token))return@post
                    output=file
                    val matrixValues=VideoFinishPolicy.matrix(lift)
                    val matrix=object:RgbMatrix {
                        override fun getMatrix(presentationTimeUs:Long,useHdr:Boolean):FloatArray {
                            check(!useHdr) { "HDR indisponível neste acabamento" };return matrixValues
                        }
                    }
                    val encoder=DefaultEncoderFactory.Builder(app).setEnableFallback(false)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(before.bitrate.coerceAtLeast(12_000_000)).build()).build()
                    val item=EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(emptyList(),listOf(matrix))).build()
                    val t=Transformer.Builder(app).setVideoMimeType(before.mime).setEncoderFactory(encoder)
                        .addListener(object:Transformer.Listener {
                            override fun onCompleted(composition:Composition,exportResult:ExportResult) {
                                if(!alive(token)) { file.delete();return }
                                transformer=null
                                update(state.copy(progress=90,message="Conferindo MP4, quadros e áudio"))
                                worker.execute { verifyAndSave(app,uri,before,file,token) }
                            }
                            override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException) {
                                if(alive(token))fail(token,exportException.message ?: "Encoder recusou o acabamento")
                            }
                        }).build()
                    transformer=t
                    try { t.start(item,file.absolutePath) }
                    catch(e:RuntimeException) { fail(token,e.message ?: "Falha ao iniciar acabamento") }
                }
            } catch(e:Exception) { main.post { fail(token,e.message ?: "Falha ao analisar vídeo") } }
        }
    }
    private fun fail(token:Long,message:String) {
        if(!alive(token))return
        transformer?.cancel();transformer=null;output?.delete();output=null
        update(state.copy(running=false,message="$message · original preservado"))
    }
    private data class Info(val width:Int,val height:Int,val rotation:Int,val mime:String,val bitrate:Int,
        val transfer:Int,val durationUs:Long,val video:LongArray,val audio:LongArray,val audioMime:String?,val audioRate:Int,val audioChannels:Int,val audioHash:ByteArray)
    private fun inspect(context:Context,uri:Uri,token:Long):Info {
        val ex=MediaExtractor()
        try {
            ex.setDataSource(context,uri,null)
            val video=mutableListOf<Long>();val audio=mutableListOf<Long>()
            var format:MediaFormat?=null;var audioMime:String?=null;var audioRate=0;var audioChannels=0
            val digest=MessageDigest.getInstance("SHA-256")
            val data=ByteBuffer.allocate(1024*1024)
            var videos=0;var audios=0
            for(i in 0 until ex.trackCount) {
                val f=ex.getTrackFormat(i);val mime=f.getString(MediaFormat.KEY_MIME) ?: error("Faixa sem codec")
                if(mime.startsWith("video/")) { format=f;videos++ }
                else if(mime.startsWith("audio/")) { audioMime=mime;audios++;audioRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);audioChannels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                else error("Faixa adicional não suportada; original preservado")
                ex.selectTrack(i);ex.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while(ex.sampleTime>=0) {
                    check(alive(token)) { "Cancelado" }
                    if(mime.startsWith("video/"))video+=ex.sampleTime
                    else {
                        audio+=ex.sampleTime;data.clear()
                        val size=ex.readSampleData(data,0)
                        require(size>=0 && size<=data.capacity()) { "Amostra de áudio inválida" }
                        data.position(0);data.limit(size);digest.update(data)
                    }
                    if(!ex.advance())break
                }
                ex.unselectTrack(i)
            }
            require(videos==1 && audios<=1) { "Múltiplas faixas não suportadas nesta etapa" }
            val f=checkNotNull(format)
            fun int(key:String,default:Int=0)=if(f.containsKey(key))f.getInteger(key) else default
            val duration=if(f.containsKey(MediaFormat.KEY_DURATION))f.getLong(MediaFormat.KEY_DURATION) else (video.maxOrNull() ?: 0L)
            return Info(int(MediaFormat.KEY_WIDTH),int(MediaFormat.KEY_HEIGHT),int(MediaFormat.KEY_ROTATION),
                f.getString(MediaFormat.KEY_MIME)!!,int(MediaFormat.KEY_BIT_RATE,20_000_000),int(MediaFormat.KEY_COLOR_TRANSFER),duration,
                video.toLongArray(),audio.toLongArray(),audioMime,audioRate,audioChannels,digest.digest())
        } finally { ex.release() }
    }
    private fun analyze(context:Context,uri:Uri,duration:Long,token:Long):Float {
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(context,uri)
            var sum=0.0;var count=0L
            val pixels=IntArray(192*192)
            repeat(8) { index ->
                check(alive(token)) { "Cancelado" }
                val bitmap:Bitmap=retriever.getScaledFrameAtTime(duration*index/8,MediaMetadataRetriever.OPTION_CLOSEST,192,192) ?: return@repeat
                try {
                    bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    for(i in 0 until bitmap.width*bitmap.height) {
                        val c=pixels[i];sum+=((c shr 16 and 255)*.2126+(c shr 8 and 255)*.7152+(c and 255)*.0722)/255;count++
                    }
                } finally { bitmap.recycle() }
            }
            require(count>0) { "Não foi possível analisar os quadros" }
            return VideoFinishPolicy.lift(sum/count)
        } finally { retriever.release() }
    }
    private fun discard(context:Context,uri:Uri) {
        runCatching { context.contentResolver.delete(uri,null,null) }.onFailure { android.util.Log.w("NovaVideoFinish","Falha ao limpar cópia pendente",it) }
    }
    private fun verifyAndSave(context:Context,uri:Uri,before:Info,file:File,token:Long) {
        var pending:Uri?=null
        try {
            val after=inspect(context,Uri.fromFile(file),token)
            require(VideoFinishPolicy.displayed(before.width,before.height,before.rotation)==VideoFinishPolicy.displayed(after.width,after.height,after.rotation)) { "Saída mudou resolução/orientação" }
            require(before.mime==after.mime && VideoFinishPolicy.timeline(before.video,after.video)) { "Saída mudou codec, quadros ou cadência" }
            require(before.audioMime==after.audioMime && before.audioRate==after.audioRate && before.audioChannels==after.audioChannels && before.audioHash.contentEquals(after.audioHash) &&
                before.audio.size==after.audio.size && (before.audio.isEmpty() ||
                (VideoFinishPolicy.timeline(before.audio,after.audio) && VideoFinishPolicy.sync(before.video.minOrNull()!!,before.audio.minOrNull()!!,after.video.minOrNull()!!,after.audio.minOrNull()!!)))) { "Saída mudou áudio ou sincronização" }
            check(alive(token)) { "Cancelado" }
            val values=ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME,"Nova-Acabamento-${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Nova Camera/Acabamento")
                put(MediaStore.Video.Media.IS_PENDING,1)
            }
            val saved=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values) ?: error("Não foi possível criar cópia")
            pending=saved
            context.contentResolver.openOutputStream(saved)!!.use { out -> file.inputStream().use { input ->
                val buffer=ByteArray(128*1024)
                while(true) { check(alive(token)) { "Cancelado" };val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n) }
            } }
            pending=null
            main.post {
                if(!alive(token))discard(context,saved)
                else try {
                    check(context.contentResolver.update(saved,ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING,0) },null,null)>0) { "Cópia não pôde ser disponibilizada" }
                    output=null
                    context.getSharedPreferences("video_finish",Context.MODE_PRIVATE).edit().putString("original",uri.toString()).putString("result",saved.toString()).apply()
                    update(state.copy(running=false,progress=100,message="Acabamento salvo; original preservado",result=saved))
                } catch(e:Exception) { discard(context,saved);fail(token,e.message ?: "Falha ao publicar cópia") }
            }
        } catch(e:Exception) {
            pending?.let { discard(context,it) }
            main.post { fail(token,e.message ?: "Falha ao conferir acabamento") }
        } finally { file.delete() }
    }
}
