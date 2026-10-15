package com.novacamera.s24.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import com.novacamera.s24.core.SizeOption
import java.util.concurrent.Executor

/** Sessão própria temporária; só aberta depois de soltar CameraX. Não usa APIs privadas Samsung. */
class NativePhotoCapture(context: Context) {
    data class Request(val cameraId: String, val physicalId: String?, val size: SizeOption,
        val cameraZoom: Float, val rotationDegrees: Int, val evIndex: Int,
        val point: Pair<Float,Float>? = null, val flashMode: Int = androidx.camera.core.ImageCapture.FLASH_MODE_OFF,
        val locked: AutoStats? = null)
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("NovaNativePhoto").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var jpeg: ImageReader? = null
    private var meter: ImageReader? = null
    private var done = false
    private var firing = false
    private var started = 0L
    private var bytes: ByteArray? = null
    private var result: TotalCaptureResult? = null
    private var callback: ((ByteArray?, TotalCaptureResult?, String?) -> Unit)? = null
    private val timeout = Runnable { finish(null, "O sensor não concluiu a captura nativa") }

    @SuppressLint("MissingPermission")
    fun capture(request: Request, completion: (ByteArray?, TotalCaptureResult?, String?) -> Unit) {
        callback = completion
        handler.post {
            if (request.size.maximumPixelMode && Build.VERSION.SDK_INT < 31) {
                finish(null,"Resolução máxima requer Android 12 ou mais recente"); return@post
            }
            started = SystemClock.elapsedRealtime()
            handler.postDelayed(timeout, 15_000)
            runCatching { manager.openCamera(request.cameraId, object: CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (done) { camera.close(); return }
                    device = camera
                    runCatching { configure(camera, request) }.onFailure { finish(null,it.message ?: "Sessão nativa indisponível") }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); finish(null,"Câmera desconectada") }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); finish(null,"Captura nativa indisponível ($error)") }
            },handler) }.onFailure { finish(null,it.message ?: "Não foi possível abrir o sensor") }
        }
    }

    private fun configure(camera: CameraDevice, r: Request) {
        val c = manager.getCameraCharacteristics(r.cameraId)
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val small = map?.getOutputSizes(ImageFormat.YUV_420_888)?.filter { it.width<=1280 && it.height<=720 }
            ?.minByOrNull { kotlin.math.abs(it.width*it.height-640*480) } ?: error("Medição do sensor indisponível")
        jpeg = ImageReader.newInstance(r.size.width,r.size.height,ImageFormat.JPEG,1)
        meter = ImageReader.newInstance(small.width,small.height,ImageFormat.YUV_420_888,2)
        meter!!.setOnImageAvailableListener({ reader -> runCatching { reader.acquireLatestImage()?.close() } },handler)
        jpeg!!.setOnImageAvailableListener({ reader ->
            if (!done) runCatching {
                reader.acquireNextImage()?.use { image ->
                    val buf=image.planes[0].buffer
                    bytes=ByteArray(buf.remaining()).also { buf.get(it) }
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes!!.size,bounds)
                    require(bounds.outWidth.toLong()*bounds.outHeight >= r.size.pixels*0.95) { "O sensor retornou resolução inferior à solicitada" }
                }
                if (bytes!=null && result!=null) finish(bytes,null)
            }.onFailure { finish(null,it.message ?: "JPEG nativo inválido") }
        },handler)
        val outputs=listOf(OutputConfiguration(meter!!.surface),OutputConfiguration(jpeg!!.surface))
        r.physicalId?.let { id -> outputs.forEach { it.setPhysicalCameraId(id) } }
        if (Build.VERSION.SDK_INT>=31) {
            outputs[0].addSensorPixelModeUsed(CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT)
            outputs[1].addSensorPixelModeUsed(if(r.size.maximumPixelMode) CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION else CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT)
        }
        val config=SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,executor,object: CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if(done){s.close();return};session=s
                runCatching {
                    val preview=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(meter!!.surface); configureAuto(this,c,r)
                    }
                    s.setRepeatingRequest(preview.build(),object: CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(s: CameraCaptureSession, q: CaptureRequest, v: TotalCaptureResult) {
                            val ae=v.get(CaptureResult.CONTROL_AE_STATE)
                            val af=v.get(CaptureResult.CONTROL_AF_STATE)
                            val converged=r.locked!=null || ae==CaptureResult.CONTROL_AE_STATE_CONVERGED || ae==CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                            val focused=r.locked!=null || af==CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || af==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af==null
                            if(!firing && ((converged&&focused)||SystemClock.elapsedRealtime()-started>2500)) fire(camera,s,c,r)
                        }
                    },handler)
                }.onFailure { finish(null,it.message ?: "Medição nativa indisponível") }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) { s.close();finish(null,"Esta combinação de sensor e resolução não foi aceita") }
        })
        camera.createCaptureSession(config)
    }

    private fun configureAuto(b: CaptureRequest.Builder,c: CameraCharacteristics,r: Request) {
        b.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO)
        b.set(CaptureRequest.CONTROL_AE_MODE,when {
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!=true -> CameraMetadata.CONTROL_AE_MODE_ON
            r.flashMode==androidx.camera.core.ImageCapture.FLASH_MODE_ON -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            r.flashMode==androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
            else -> CameraMetadata.CONTROL_AE_MODE_ON })
        b.set(CaptureRequest.CONTROL_AWB_MODE,CameraMetadata.CONTROL_AWB_MODE_AUTO)
        val af=c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES).orEmpty()
        b.set(CaptureRequest.CONTROL_AF_MODE,when {
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in af -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            CameraMetadata.CONTROL_AF_MODE_AUTO in af -> CameraMetadata.CONTROL_AF_MODE_AUTO
            else -> CameraMetadata.CONTROL_AF_MODE_OFF })
        val array=c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if(array!=null && r.point!=null) {
            val z=r.cameraZoom.coerceAtLeast(1f)
            val cw=(array.width()/z).toInt();val ch=(array.height()/z).toInt()
            val x=(array.centerX()+(r.point.first-0.5f)*cw).toInt().coerceIn(array.left,array.right-1)
            val y=(array.centerY()+(r.point.second-0.5f)*ch).toInt().coerceIn(array.top,array.bottom-1)
            val radius=maxOf(8,minOf(cw,ch)/16)
            val rect=android.graphics.Rect((x-radius).coerceAtLeast(array.left),(y-radius).coerceAtLeast(array.top),
                (x+radius).coerceAtMost(array.right),(y+radius).coerceAtMost(array.bottom))
            val region=arrayOf(android.hardware.camera2.params.MeteringRectangle(rect,1000))
            if((c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0)>0)b.set(CaptureRequest.CONTROL_AF_REGIONS,region)
            if((c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0)>0)b.set(CaptureRequest.CONTROL_AE_REGIONS,region)
        }
        val locked=r.locked
        if(locked!=null && c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)==true) {
            b.set(CaptureRequest.CONTROL_AE_MODE,CameraMetadata.CONTROL_AE_MODE_OFF)
            c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { b.set(CaptureRequest.SENSOR_SENSITIVITY,locked.iso.coerceIn(it.lower,it.upper)) }
            c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { b.set(CaptureRequest.SENSOR_EXPOSURE_TIME,locked.exposureNs.coerceIn(it.lower,it.upper)) }
            val maxFocus=c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            if(maxFocus>0f){b.set(CaptureRequest.CONTROL_AF_MODE,CameraMetadata.CONTROL_AF_MODE_OFF);b.set(CaptureRequest.LENS_FOCUS_DISTANCE,locked.focusDiopters.coerceIn(0f,maxFocus))}
        }
        val ev=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        if(ev!=null)b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,r.evIndex.coerceIn(ev.lower,ev.upper))
        if(Build.VERSION.SDK_INT>=30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO,r.cameraZoom.coerceIn(it.lower,it.upper))
        }
        if(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)==true)
            b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
    }

    private fun fire(camera: CameraDevice,s: CameraCaptureSession,c: CameraCharacteristics,r: Request) {
        if(done||firing)return
        firing=true
        runCatching {
            val still=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jpeg!!.surface);configureAuto(this,c,r)
                set(CaptureRequest.JPEG_ORIENTATION,r.rotationDegrees)
                set(CaptureRequest.JPEG_QUALITY,95.toByte())
                if(Build.VERSION.SDK_INT>=31 && r.size.maximumPixelMode) set(CaptureRequest.SENSOR_PIXEL_MODE,CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
            }
            s.capture(still.build(),object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession,q: CaptureRequest,v: TotalCaptureResult){
                    if(Build.VERSION.SDK_INT>=31 && r.size.maximumPixelMode && v.get(CaptureResult.SENSOR_PIXEL_MODE)!=CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION) {
                        finish(null,"O firmware não confirmou o modo máximo do sensor");return
                    }
                    result=v;if(bytes!=null)finish(bytes,null)
                }
                override fun onCaptureFailed(s: CameraCaptureSession,q: CaptureRequest,f: CaptureFailure){finish(null,"O sensor recusou a fotografia nativa")}
            },handler)
        }.onFailure { finish(null,it.message ?: "Falha na captura nativa") }
    }

    private fun finish(data: ByteArray?,error: String?) {
        if(done)return;done=true;handler.removeCallbacks(timeout)
        runCatching { session?.close() };runCatching { device?.close() }
        runCatching { jpeg?.close() };runCatching { meter?.close() }
        callback?.invoke(data,result,error);callback=null;bytes=null
        thread.quitSafely()
    }
    fun cancel() { handler.post { finish(null,"Captura interrompida") } }
}
