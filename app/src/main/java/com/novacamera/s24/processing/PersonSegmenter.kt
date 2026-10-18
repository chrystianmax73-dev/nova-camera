package com.novacamera.s24.processing

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.novacamera.s24.core.imaging.Plane
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Segmentação do Retrato com o modelo MediaPipe "selfie_multiclass_256x256" (Apache-2.0),
 * embutido em assets e executado offline. Além de pessoa × fundo, separa **cabelo**, pele do rosto,
 * pele do corpo, roupa e acessórios/objetos na mão (óculos, controle): tudo o que não é fundo fica
 * nítido; só o cabelo recebe transição larga.
 *
 * Limites do modelo: entrada 256×256 (bordas finas são refinadas depois na foto em resolução
 * cheia por [Engine.refineMatte]); feito para pessoas próximas; não separa animais ou objetos.
 */
object PersonSegmenter {
    private const val MODEL = "selfie_multiclass_256x256.tflite"
    /**
     * Prévia do Retrato (foto): modelo leve de pessoa × fundo, 250 KB (Apache-2.0). O multiclasse
     * gastava 77 ms de CPU por quadro contra 3,6 ms deste (LiteRT no servidor, 256×256, 1 thread) e,
     * a 3–10 análises/s, era a thread que dominava a CPU da prévia (~75% no S24, diagnóstico 0.26.7).
     * A foto final e o vídeo cinematográfico continuam com o multiclasse.
     */
    private const val LIVE_LIGHT_MODEL = "selfie_segmenter.tflite"
    private const val INPUT = 768

    // Ordem das máscaras de confiança do modelo multiclasse.
    private const val BACKGROUND = 0
    private const val HAIR = 1
    private const val FACE_SKIN = 3

    class Result(
        /** Confiança de "é pessoa" (1 − fundo). */
        val person: Plane,
        /** Cabelo: única região com transição larga (fios finos). Acessórios e objetos já estão em [person]. */
        val hair: Plane,
        /** Fração da imagem ocupada pela pessoa. */
        val coverage: Float,
        /** Fração ocupada por pele do rosto (rosto detectado de fato). */
        val faceCoverage: Float,
    )

    private fun options(model: String = MODEL) = ImageSegmenter.ImageSegmenterOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath(model).setDelegate(Delegate.CPU).build())
        .setRunningMode(RunningMode.IMAGE)
        .setOutputConfidenceMasks(true)
        .setOutputCategoryMask(false)
        .build()

    fun segment(context: Context, photo: Bitmap): Result {
        val segmenter = ImageSegmenter.createFromOptions(context.applicationContext, options())
        try { return run(segmenter, photo, INPUT) } finally { segmenter.close() }
    }

    /** Segmentador mantido aberto para a prévia ao vivo do Retrato (uma chamada por quadro analisado). */
    class LiveResult(val person:Plane,val coverage:Float)
    class Live(context: Context, private val light: Boolean = false) : java.io.Closeable {
        private val loadedAt=android.os.SystemClock.elapsedRealtime()
        private val segmenter = ImageSegmenter.createFromOptions(context.applicationContext,
            options(if (light) LIVE_LIGHT_MODEL else MODEL))
        val loadMs=android.os.SystemClock.elapsedRealtime()-loadedAt
        var inputMs=0L;private set
        var nativeMs=0L;private set
        var extractionMs=0L;private set
        fun segment(frame: Bitmap): LiveResult {
            val before=android.os.SystemClock.elapsedRealtime()
            return withOwnedInput(frame,256) { image ->
                val nativeAt=android.os.SystemClock.elapsedRealtime();inputMs=nativeAt-before
                val masks=segmenter.segment(image).confidenceMasks().get()
                val extractAt=android.os.SystemClock.elapsedRealtime();nativeMs=extractAt-nativeAt
                try {
                    // A prévia só usa pessoa/fundo. Cabelo e rosto completos continuam na foto final.
                    // Modelo leve: uma máscara só, já de "pessoa"; multiclasse: 1 − fundo.
                    val m=masks[if (light) 0 else BACKGROUND]
                    val buf=ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
                    val person=if (light) personFromConfidence(buf,m.width,m.height) else personFromBackground(buf,m.width,m.height)
                    LiveResult(person,person.data.count { it>0.5f }/person.data.size.toFloat())
                } finally { masks.forEach { it.close() };extractionMs=android.os.SystemClock.elapsedRealtime()-extractAt }
            }
        }
        override fun close() = segmenter.close()
    }

    /** Máscara única do modelo leve: já é a confiança de pessoa. */
    internal fun personFromConfidence(conf:java.nio.FloatBuffer,w:Int,h:Int):Plane {
        conf.rewind()
        return Plane(w,h,FloatArray(w*h) { conf.get().coerceIn(0f,1f) })
    }

    /** Mesma aritmética de 1−fundo, diretamente no plano final: sem três planos descartados. */
    internal fun personFromBackground(bg:java.nio.FloatBuffer,w:Int,h:Int):Plane {
        bg.rewind()
        return Plane(w,h,FloatArray(w*h) { 1f-bg.get().coerceIn(0f,1f) })
    }

    /** MPImage toma posse do Bitmap: fechar uma imagem emprestada reciclaria o quadro do chamador. */
    internal fun <T> withOwnedInput(photo:Bitmap,input:Int,block:(com.google.mediapipe.framework.image.MPImage)->T):T {
        val scale=input.toFloat()/max(photo.width,photo.height)
        val scaled=if(scale<1f) Bitmap.createScaledBitmap(photo,max(1,(photo.width*scale).toInt()),max(1,(photo.height*scale).toInt()),true) else photo
        val owned=if(scaled===photo) photo.copy(Bitmap.Config.ARGB_8888,false) else scaled
        val image=try { BitmapImageBuilder(owned).build() } catch(e:Throwable) { owned.recycle();throw e }
        try { return block(image) } finally { image.close() }
    }

    private fun run(segmenter: ImageSegmenter, photo: Bitmap, input: Int): Result = withOwnedInput(photo,input) { image ->
        val masks = segmenter.segment(image).confidenceMasks().get()
        try {
            fun plane(i: Int): Plane {
                val img = masks[i]
                val buf = ByteBufferExtractor.extract(img).order(ByteOrder.nativeOrder()).asFloatBuffer()
                val p = Plane(img.width, img.height)
                buf.rewind()
                for (k in 0 until img.width * img.height) p.data[k] = buf.get().coerceIn(0f, 1f)
                return p
            }
            val bg = plane(BACKGROUND)
            val person = Plane(bg.w, bg.h, FloatArray(bg.data.size) { 1f - bg.data[it] })
            val hair = plane(HAIR)
            val face = plane(FACE_SKIN)
            val n = person.data.size.toFloat()
            Result(person, hair, person.data.count { it > 0.5f } / n, face.data.count { it > 0.5f } / n)
        } finally { masks.forEach { it.close() } }
    }
}
