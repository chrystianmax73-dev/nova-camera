package com.novacamera.s24.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.novacamera.s24.core.PreviewMaskCalibration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * Retrato ao vivo: a pré-visualização recebe o desfoque calculado com a máscara do assunto (pessoa
 * e/ou profundidade, ver [PortraitMaskAnalyzer]) e a intensidade escolhida, antes da foto. É uma
 * prévia: a foto final passa pelo processamento completo em resolução cheia, com o mesmo modelo de
 * profundidade e a mesma intensidade.
 *
 * Processamento em OpenGL ES 2.0 numa thread própria: quadro da câmera (textura OES) → cópia
 * reduzida (1/4) → desfoque separável → composição com a máscara. Qualquer erro de GL desliga o
 * efeito pelo [onError] (a câmera volta à prévia normal; nunca tela preta).
 */
class PortraitPreviewEffect private constructor(
    private val processor: PortraitGlProcessor,
    onError: Consumer<Throwable>,
    targets: Int = PREVIEW,
) : CameraEffect(targets, processor.executor, processor, onError) {

    /** 0..1, a mesma escala do desfoque final. */
    var strength: Float
        get() = processor.strength
        set(v) { processor.strength = v.coerceIn(0f, 1f) }

    /** Retrato (máscara + desfoque). Fora do Retrato só o estilo é aplicado, sem o custo do desfoque. */
    var blurEnabled: Boolean
        get() = processor.blurEnabled
        set(v) { processor.blurEnabled = v }

    /** Tom e temperatura ao vivo: mesma conta da foto ([com.novacamera.s24.core.imaging.PhotoStyle]). */
    var style: com.novacamera.s24.core.imaging.PhotoStyle
        get() = com.novacamera.s24.core.imaging.PhotoStyle(processor.tone, processor.warmth)
        set(v) { processor.tone = v.tone; processor.warmth = v.warmth }

    /** Máscara em coordenadas do buffer da câmera (linha 0 = topo do quadro da análise). */
    fun updateMask(mask: ByteArray, w: Int, h: Int,atMs:Long=android.os.SystemClock.elapsedRealtime()) = processor.postMask(mask, w, h,atMs)

    /** Luminância 32×24 do quadro da análise, para descobrir a orientação da máscara (uma vez). */
    fun offerCalibration(luma: FloatArray) = processor.offerCalibration(luma)
    val needsCalibration get() = processor.needsCalibration

    /** Sem máscara (aquecimento, sem assunto): prévia sem desfoque. */
    fun clearMask() = processor.postMask(null, 0, 0)

    val maskReady get() = processor.maskReady
    val signatureDrawings get() = processor.signatureDrawings
    fun release() = processor.release()
    fun releaseThen(done: () -> Unit) = processor.release(done)

    companion object {
        fun cinematic(style: com.novacamera.s24.processing.SignatureStyle?, onError: (Throwable) -> Unit): PortraitPreviewEffect {
            val main = Handler(android.os.Looper.getMainLooper())
            var reported = false
            val fail: (Throwable) -> Unit = { e -> main.post { if (!reported) { reported=true; onError(e) } } }
            val processor=PortraitGlProcessor(fail).apply { watermark=style; expireMasks=true }
            return PortraitPreviewEffect(processor,Consumer { e -> processor.release(); fail(e) },PREVIEW or VIDEO_CAPTURE)
        }

        /** [onError] chega na thread principal, uma única vez; o efeito já foi liberado. */
        fun create(onError: (Throwable) -> Unit): PortraitPreviewEffect {
            val main = Handler(android.os.Looper.getMainLooper())
            var reported = false
            val fail: (Throwable) -> Unit = { e -> main.post { if (!reported) { reported = true; onError(e) } } }
            val p = PortraitGlProcessor(fail)
            return PortraitPreviewEffect(p, Consumer { e -> p.release(); fail(e) })
        }
    }
}

internal class PortraitGlProcessor(private val onFatal: (Throwable) -> Unit) : SurfaceProcessor {
    private val thread = HandlerThread("nova-portrait-gl").also { it.start() }
    private val handler = Handler(thread.looper)
    val executor = Executor { r -> handler.post(r) }

    @Volatile var signatureDrawings=0;private set
    @Volatile var watermark: com.novacamera.s24.processing.SignatureStyle? = null
    @Volatile var expireMasks = false
    @Volatile private var maskAt = 0L
    val maskReady get() = hasMask && (!expireMasks || com.novacamera.s24.core.CinematicPolicy.fresh(android.os.SystemClock.elapsedRealtime(),maskAt))
    @Volatile var strength = 0.6f
    @Volatile var blurEnabled = false
    @Volatile var tone = 0f
    @Volatile var warmth = 0f
    @Volatile private var released = false

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    private var surfaceTexture: SurfaceTexture? = null
    private var oesTex = 0
    private var inputSize = Size(0, 0)
    private var signatureInput: com.novacamera.s24.processing.CinematicSignature.Input? = null
    private var signatureRevision = 0L
    private var maskBlend=0f
    private var blendAt=0L
    private class Out(val output: SurfaceOutput, val egl: EGLSurface, val size: Size, var logo: Int = 0, var logoRevision: Long = -1)
    private val outputs = ArrayList<Out>()

    private val metrics=com.novacamera.s24.core.GlPerformance()
    private val uniformLocations=com.novacamera.s24.core.GlLocationCache { p,n -> GLES20.glGetUniformLocation(p,n) }
    private val attributeLocations=com.novacamera.s24.core.GlLocationCache { p,n -> GLES20.glGetAttribLocation(p,n) }
    private val maskStorage=com.novacamera.s24.core.MaskTextureStorage()
    private fun uniform(p:Int,n:String)=uniformLocations.get(p,n)
    private var progCopy = 0; private var progBlur = 0; private var progFinal = 0
    private val fbo = IntArray(3); private val fboTex = IntArray(3)
    private var smallW = 0; private var smallH = 0
    private var maskTex = 0; @Volatile private var hasMask = false
    private var maskFlipX = 0f; private var maskFlipY = 0f

    @Volatile var needsCalibration = true
        private set
    private var pendingCalibration: FloatArray? = null

    private val stMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) { request.willNotProvideSurface(); return }
        try {
            ensureEgl()
            val tex = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
            texParams(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            val st = SurfaceTexture(tex).apply { setDefaultBufferSize(request.resolution.width, request.resolution.height) }
            val surface = Surface(st)
            surfaceTexture?.release(); if (oesTex != 0) GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0)
            surfaceTexture = st; oesTex = tex; inputSize = request.resolution
            needsCalibration = true
            hasMask=false;maskBlend=0f;maskAt=0L;pendingCalibration=null
            signatureInput=null;signatureRevision++
            request.setTransformationInfoListener(executor) { info ->
                if(!released && surfaceTexture===st) {
                    signatureInput=com.novacamera.s24.processing.CinematicSignature.Input(
                        info.cropRect,info.rotationDegrees,info.isMirroring,info.sensorToBufferTransform)
                    signatureRevision++
                }
            }
            st.setOnFrameAvailableListener({ onFrame() }, handler)
            request.provideSurface(surface, executor) {
                request.clearTransformationInfoListener()
                st.setOnFrameAvailableListener(null)
                st.release(); surface.release()
                if (surfaceTexture === st) { surfaceTexture = null; GLES20.glDeleteTextures(1, intArrayOf(tex), 0); oesTex = 0 }
            }
        } catch (e: Exception) {
            request.willNotProvideSurface()
            fatal(e)
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released) { surfaceOutput.close(); return }
        try { ensureEgl() } catch (e: Exception) { surfaceOutput.close(); fatal(e); return }
        val surface = surfaceOutput.getSurface(executor) { event ->
            if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                outputs.removeAll { o -> (o.output === event.surfaceOutput).also { if (it) { if(o.logo!=0) GLES20.glDeleteTextures(1,intArrayOf(o.logo),0); EGL14.eglDestroySurface(display, o.egl) } } }
                event.surfaceOutput.close()
            }
        }
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        outputs += Out(surfaceOutput, egl, surfaceOutput.size)
    }

    private data class MaskUpdate(val bytes:ByteArray?,val w:Int,val h:Int,val atMs:Long)
    private val pendingMask=com.novacamera.s24.core.LatestPending<MaskUpdate>()
    fun postMask(mask: ByteArray?,w:Int,h:Int,atMs:Long=android.os.SystemClock.elapsedRealtime()) {
        if(released)return
        if(pendingMask.offer(MaskUpdate(mask,w,h,atMs))) {
            if(!handler.post { pendingMask.take()?.let { update ->
                try { uploadMask(update) } catch(e:Exception) { fatal(e) }
            } })pendingMask.take()
        }
    }

    private fun uploadMask(update:MaskUpdate) {
        if (released || display == EGL14.EGL_NO_DISPLAY) return
        val mask=update.bytes;val w=update.w;val h=update.h
        if (mask == null) { hasMask = false; return }
        // A fila GL não renova a validade de uma análise antiga.
        if(expireMasks && !com.novacamera.s24.core.CinematicPolicy.fresh(android.os.SystemClock.elapsedRealtime(),update.atMs)) {
            hasMask=false;return
        }
        makeCurrent(pbuffer)
        if (maskTex == 0) {
            maskTex = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex); texParams(GLES20.GL_TEXTURE_2D)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        require(w>0 && h>0 && mask.size.toLong()==w.toLong()*h*2) { "Máscara inválida" }
        val uploadAt=System.nanoTime()
        if(maskStorage.needsAllocation(w,h)) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, w, h, 0,
                GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(mask))
            maskStorage.allocated(w,h);metrics.maskAllocations++
        } else GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,w,h,
            GLES20.GL_LUMINANCE_ALPHA,GLES20.GL_UNSIGNED_BYTE,ByteBuffer.wrap(mask))
        metrics.maskUploadBytes+=mask.size
        metrics.record(com.novacamera.s24.core.GlPerformance.UPLOAD,System.nanoTime()-uploadAt)
        hasMask = !needsCalibration
        maskAt = update.atMs
    }

    fun offerCalibration(luma: FloatArray) = handler.post { if (!released && needsCalibration) pendingCalibration = luma }

    private val releaseLock=Any()
    private var releaseComplete=false
    private val releaseCallbacks=mutableListOf<() -> Unit>()
    fun release(done: (() -> Unit)? = null) {
        synchronized(releaseLock) {
            if(done!=null) { if(releaseComplete) { done(); return }; releaseCallbacks+=done }
            if(released) return
            released=true
        }
        handler.post {
            runCatching {
                surfaceTexture?.release(); surfaceTexture = null
                outputs.forEach { if(it.logo!=0) GLES20.glDeleteTextures(1,intArrayOf(it.logo),0); EGL14.eglDestroySurface(display, it.egl); it.output.close() }; outputs.clear()
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
                    EGL14.eglDestroyContext(display, context); EGL14.eglTerminate(display)
                }
            }
            metrics.publish("liberado")
            thread.quitSafely()
            val callbacks=synchronized(releaseLock) { releaseComplete=true; releaseCallbacks.toList().also { releaseCallbacks.clear() } }
            callbacks.forEach { it() }
        }
    }

    // ------------------------------------------------------------------ quadro

    private fun fatal(e: Throwable) {
        Log.e(TAG, "prévia do retrato desligada", e)
        release()
        onFatal(e)
    }

    private fun onFrame() {
        val at=System.nanoTime()
        try { renderFrame() } catch (e: Throwable) { fatal(e) }
        finally { metrics.record(com.novacamera.s24.core.GlPerformance.FRAME,System.nanoTime()-at) }
    }

    private fun renderFrame() {
        val st = surfaceTexture ?: return
        if (released) return
        makeCurrent(pbuffer)
        val updateAt=System.nanoTime()
        st.updateTexImage()
        metrics.record(com.novacamera.s24.core.GlPerformance.UPDATE,System.nanoTime()-updateAt)
        st.getTransformMatrix(stMatrix)
        if (outputs.isEmpty()) return
        val prepareAt=System.nanoTime()
        ensureFbos()
        metrics.record(com.novacamera.s24.core.GlPerformance.PREPARE,System.nanoTime()-prepareAt)
        val s = strength
        val blur = blurEnabled
        val now=android.os.SystemClock.elapsedRealtime()
        val target=if(blur && maskReady && s>0.01f)1f else 0f
        if(expireMasks) { maskBlend+=(target-maskBlend)*((now-blendAt).coerceIn(0L,100L)/180f).coerceIn(0f,1f); blendAt=now }
        else maskBlend=target
        val work=com.novacamera.s24.core.CinematicPolicy.renderWork(blur,expireMasks,target>0f,maskBlend,pendingCalibration!=null)
        val copyAt=System.nanoTime()
        if (work.copy) {
            // 1) Cópia reduzida em coordenadas do buffer (as mesmas da máscara).
            drawTo(fbo[0], smallW, smallH) {
                use(progCopy); bindOes(progCopy, "uCam", 0)
                GLES20.glUniformMatrix4fv(uniform(progCopy, "uTex"), 1, false, identity, 0)
            }
            pendingCalibration?.let { analysis -> pendingCalibration = null; calibrate(analysis) }
        }
        if(work.copy)metrics.record(com.novacamera.s24.core.GlPerformance.COPY,System.nanoTime()-copyAt)
        val blurAt=System.nanoTime()
        if (work.blur) {
            // 2) Desfoque separável (duas iterações), raio proporcional à intensidade e na escala da foto final.
            val radius = (5f + 18f * s) * maxOf(smallW, smallH) / 1000f
            repeat(2) {
                blurPass(fboTex[0], fbo[1], radius / 4f, 0f)
                blurPass(fboTex[1], fbo[2], 0f, radius / 4f)
                if (it == 0) copyTex(fboTex[2], fbo[0])
            }
        }
        if(work.blur)metrics.record(com.novacamera.s24.core.GlPerformance.BLUR,System.nanoTime()-blurAt)
        // 3) Composição para cada saída.
        for (o in outputs) {
            val composeAt=System.nanoTime()
            o.output.updateTransformMatrix(texMatrix, stMatrix)
            makeCurrent(o.egl)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, o.size.width, o.size.height)
            use(progFinal)
            bindOes(progFinal, "uCam", 0)
            bindTex(progFinal, "uBlur", 1, fboTex[2])
            bindTex(progFinal, "uMask", 2, if (maskTex != 0) maskTex else fboTex[2])
            GLES20.glUniformMatrix4fv(uniform(progFinal, "uTex"), 1, false, texMatrix, 0)
            GLES20.glUniform2f(uniform(progFinal, "uFlip"), maskFlipX, maskFlipY)
            GLES20.glUniform1f(uniform(progFinal, "uOn"), maskBlend)
            GLES20.glUniform1f(uniform(progFinal, "uTone"), tone)
            GLES20.glUniform1f(uniform(progFinal, "uWarmth"), warmth)
            val style=watermark?.takeIf { o.output.targets and androidx.camera.core.CameraEffect.VIDEO_CAPTURE != 0 }
            if (o.logo!=0 && o.logoRevision!=signatureRevision) {
                GLES20.glDeleteTextures(1,intArrayOf(o.logo),0);o.logo=0
            }
            val geometry=signatureInput
            if (style != null && geometry!=null && o.logo==0) {
                val bmp=android.graphics.Bitmap.createBitmap(o.size.width,o.size.height,android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    check(com.novacamera.s24.processing.CinematicSignature.draw(
                        android.graphics.Canvas(bmp),geometry,o.output.sensorToBufferTransform,style)) { "Geometria da assinatura indisponível" }
                    o.logoRevision=signatureRevision
                    o.logo=IntArray(1).also { GLES20.glGenTextures(1,it,0) }[0]
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,o.logo); texParams(GLES20.GL_TEXTURE_2D)
                    android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0)
                    signatureDrawings++
                } finally { bmp.recycle() }
            }
            bindTex(progFinal,"uLogo",3,if(o.logo!=0)o.logo else fboTex[0])
            GLES20.glUniform1f(uniform(progFinal,"uLogoOn"),if(style!=null && o.logo!=0)1f else 0f)
            drawQuad(progFinal)
            EGLExt.eglPresentationTimeANDROID(display, o.egl, st.timestamp)
            metrics.record(com.novacamera.s24.core.GlPerformance.COMPOSE,System.nanoTime()-composeAt)
            val swapAt=System.nanoTime()
            check(EGL14.eglSwapBuffers(display, o.egl)) { "Falha ao entregar quadro da composição" }
            metrics.record(com.novacamera.s24.core.GlPerformance.SWAP,System.nanoTime()-swapAt)
        }
        checkGl()
        metrics.frame(if(expireMasks) "vídeo Retrato" else "prévia foto/estilo")
    }

    /** Compara a cópia reduzida da câmera com a análise nas quatro orientações e fica com a melhor. */
    private fun calibrate(analysis: FloatArray) {
        val px = ByteBuffer.allocateDirect(smallW * smallH * 4).order(ByteOrder.nativeOrder())
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        metrics.readbackBytes+=smallW.toLong()*smallH*4
        GLES20.glReadPixels(0, 0, smallW, smallH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
        val luma = FloatArray(smallW * smallH) { i ->
            val o = i * 4
            (0.2126f * (px.get(o).toInt() and 0xFF) + 0.7152f * (px.get(o + 1).toInt() and 0xFF) + 0.0722f * (px.get(o + 2).toInt() and 0xFF)) / 255f
        }
        // Linha r da leitura = coordenada t = (r + 0,5)/H do buffer.
        val grid = PreviewMaskCalibration.grid(luma, smallW, smallH)
        val (fx, fy) = PreviewMaskCalibration.bestFlip(grid, analysis) ?: return
        maskFlipX = if (fx) 1f else 0f; maskFlipY = if (fy) 1f else 0f
        needsCalibration = false
        Log.i(TAG, "orientação da máscara: espelho x=$fx y=$fy")
    }

    // ------------------------------------------------------------------ GL

    private fun ensureEgl() {
        if (display != EGL14.EGL_NO_DISPLAY) return
        val initAt=System.nanoTime()
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); check(EGL14.eglInitialize(display, v, 0, v, 1)) { "EGL indisponível" }
        val attribs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) && n[0] > 0) { "sem configuração EGL" }
        config = configs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "sem contexto EGL" }
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        makeCurrent(pbuffer)
        progCopy = program(VS, FS_COPY); progBlur = program(VS, FS_BLUR); progFinal = program(VS, FS_FINAL)
        metrics.record(com.novacamera.s24.core.GlPerformance.INIT,System.nanoTime()-initAt)
    }

    private fun makeCurrent(s: EGLSurface) = check(EGL14.eglMakeCurrent(display, s, s, context)) { "eglMakeCurrent falhou" }

    private fun ensureFbos() {
        val w = maxOf(16, inputSize.width / 4); val h = maxOf(16, inputSize.height / 4)
        if (w == smallW && h == smallH && fbo[0] != 0) return
        if (fbo[0] != 0) { GLES20.glDeleteFramebuffers(3, fbo, 0); GLES20.glDeleteTextures(3, fboTex, 0) }
        metrics.fboBuilds++
        smallW = w; smallH = h
        GLES20.glGenFramebuffers(3, fbo, 0); GLES20.glGenTextures(3, fboTex, 0)
        for (i in 0 until 3) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex[i]); texParams(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex[i], 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "FBO incompleto" }
        }
    }

    private inline fun drawTo(target: Int, w: Int, h: Int, setup: () -> Unit) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target)
        GLES20.glViewport(0, 0, w, h)
        setup()
        drawQuad(currentProgram)
    }

    private var currentProgram = 0
    private fun use(p: Int) { currentProgram = p; GLES20.glUseProgram(p) }

    private fun blurPass(src: Int, dst: Int, dx: Float, dy: Float) = drawTo(dst, smallW, smallH) {
        use(progBlur); bindTex(progBlur, "uSrc", 0, src)
        GLES20.glUniform2f(uniform(progBlur, "uStep"), dx / smallW, dy / smallH)
    }

    private fun copyTex(src: Int, dst: Int) = blurPass(src, dst, 0f, 0f)

    private fun bindOes(p: Int, name: String, unit: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(uniform(p, name), unit)
    }

    private fun bindTex(p: Int, name: String, unit: Int, tex: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uniform(p, name), unit)
    }

    private fun drawQuad(p: Int) {
        val loc = attributeLocations.get(p, "aPos")
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(loc)
    }

    private fun texParams(target: Int) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
            val ok = IntArray(1); GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(it) }
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
            val ok = IntArray(1); GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "programa: " + GLES20.glGetProgramInfoLog(it) }
        }
    }

    private fun checkGl() {
        val e = GLES20.glGetError()
        check(e == GLES20.GL_NO_ERROR) { "erro GL 0x" + Integer.toHexString(e) }
    }

    companion object {
        private const val TAG = "NovaPortraitPreview"
        private const val VS = """
            attribute vec2 aPos;
            varying vec2 vUv;
            void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"""
        private const val FS_COPY = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uCam; uniform mat4 uTex; varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uCam, (uTex * vec4(vUv, 0.0, 1.0)).xy); }"""
        // Gaussiana de 9 amostras ao longo de uStep (horizontal ou vertical).
        private const val FS_BLUR = """
            precision mediump float;
            uniform sampler2D uSrc; uniform vec2 uStep; varying vec2 vUv;
            void main() {
                vec4 c = texture2D(uSrc, vUv) * 0.2270;
                c += (texture2D(uSrc, vUv + uStep * 1.3846) + texture2D(uSrc, vUv - uStep * 1.3846)) * 0.3162;
                c += (texture2D(uSrc, vUv + uStep * 3.2308) + texture2D(uSrc, vUv - uStep * 3.2308)) * 0.0703;
                gl_FragColor = c;
            }"""
        // Máscara: luminância = assunto (1 nítido), alfa = quanto desfocar pela profundidade.
        // Estilo depois do desfoque: mesma ordem da foto (render → estilo).
        private val FS_FINAL = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES uCam; uniform sampler2D uBlur; uniform sampler2D uMask;
            uniform sampler2D uLogo; uniform float uLogoOn;
            uniform mat4 uTex; uniform vec2 uFlip; uniform float uOn; varying vec2 vUv;
            ${com.novacamera.s24.core.imaging.PhotoStyle.GLSL}
            void main() {
                vec2 b = (uTex * vec4(vUv, 0.0, 1.0)).xy;
                vec4 sharp = texture2D(uCam, b);
                vec4 c = sharp;
                if (uOn > 0.001) {
                    vec4 m = texture2D(uMask, mix(b, vec2(1.0) - b, uFlip));
                    float w = (1.0 - m.r) * smoothstep(0.0, 0.35, m.a) * uOn;
                    c = mix(sharp, texture2D(uBlur, b), w);
                }
                vec3 rgb=applyStyle(c.rgb);
                if(uLogoOn>0.5) {
                    vec4 logo=texture2D(uLogo,vec2(vUv.x,1.0-vUv.y));
                    rgb=rgb*(1.0-logo.a)+logo.rgb;
                }
                gl_FragColor = vec4(rgb, 1.0);
            }"""
    }
}
