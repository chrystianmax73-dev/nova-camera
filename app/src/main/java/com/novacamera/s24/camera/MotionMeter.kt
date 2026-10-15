package com.novacamera.s24.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Quanto o aparelho está se mexendo agora (giroscópio, rad/s, média quadrática dos últimos ~0,5 s).
 * Usado pelo Noturno para escolher o tempo de cada quadro: apoiado aceita exposição longa; na mão,
 * mais curta. Ligado só enquanto o modo precisa.
 */
class MotionMeter(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val t = LongArray(64)
    private val w2 = FloatArray(64)
    private var n = 0
    private var head = 0
    private var running = false

    fun start() {
        if (running || gyro == null) return
        running = sm?.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        if (!running) return
        sm?.unregisterListener(this); running = false
        synchronized(this) { n = 0 }
    }

    /** Null sem giroscópio ou sem leituras recentes. */
    @Synchronized
    fun rms(windowMs: Long = 500): Float? {
        if (n == 0) return null
        val since = SystemClock.elapsedRealtimeNanos() - windowMs * 1_000_000
        var sum = 0f; var k = 0
        for (j in 0 until n) {
            val i = (head - 1 - j + t.size) % t.size
            if (t[i] < since) break
            sum += w2[i]; k++
        }
        return if (k < 4) null else sqrt(sum / k)
    }

    @Synchronized
    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        t[head] = e.timestamp; w2[head] = x * x + y * y + z * z
        head = (head + 1) % t.size
        if (n < t.size) n++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
