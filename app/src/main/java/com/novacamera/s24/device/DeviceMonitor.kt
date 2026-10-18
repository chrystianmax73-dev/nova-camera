package com.novacamera.s24.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.novacamera.s24.core.DeviceHeat

/**
 * Acompanha o estado térmico do Android e a bateria por eventos (sem polling).
 * O estado térmico é do aparelho inteiro: carregamento, sol ou outros apps também o elevam.
 */
class DeviceMonitor(private val context: Context, private val onChange: (DeviceHeat) -> Unit) {
    private val power = context.getSystemService(PowerManager::class.java)
    private var thermalStatus = runCatching { power.currentThermalStatus }.getOrDefault(0)
    private var batteryTempC: Float? = null
    private var charging = false
    private var batteryPercent = -1
    private var voltageMv: Int? = null
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private var started = false
    private var thermalRegistered = false

    var current: DeviceHeat = snapshot(); private set

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermalStatus = status
        publish()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) readBattery(intent)
            publish()
        }
    }

    fun start() {
        if (started) return
        started = true
        // O serviço térmico pode recusar o registro; nesse caso seguimos só com bateria e leituras pontuais.
        thermalRegistered = runCatching {
            power.addThermalStatusListener(ContextCompat.getMainExecutor(context), thermalListener)
        }.onFailure { Log.w("NovaCamera", "Monitor térmico indisponível", it) }.isSuccess
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let(::readBattery)
        thermalStatus = runCatching { power.currentThermalStatus }.getOrDefault(thermalStatus)
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        if (thermalRegistered) runCatching { power.removeThermalStatusListener(thermalListener) }
        thermalRegistered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** Previsão do próprio Android (API 30+): ~1.0 significa limite térmico em [seconds]. */
    fun headroom(seconds: Int = 10): Float? =
        if (Build.VERSION.SDK_INT < 30) null else runCatching { power.getThermalHeadroom(seconds) }.getOrNull()?.takeIf { !it.isNaN() }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else null
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
    }

    /** Amostra de energia para o [com.novacamera.s24.core.EnergyLog] (corrente instantânea do sistema). */
    fun energySample(activity: String, nowMs: Long): com.novacamera.s24.core.EnergyLog.Sample {
        val ua = runCatching { batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
            // Preservar também sentinelas no bruto; EnergyLog rejeita na potência.
        val h = current
        return com.novacamera.s24.core.EnergyLog.Sample(nowMs, activity, ua, voltageMv, h.batteryTempC, h.batteryPercent,
            h.charging, h.thermalStatus, headroom(),
            averageRaw=property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            chargeCounterRaw=property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            energyCounterRaw=property(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
    }

    private fun property(id:Int):Long?=runCatching { batteryManager?.getLongProperty(id) }.getOrNull()
        ?.takeIf { it!=Long.MIN_VALUE && it!=Int.MIN_VALUE.toLong() }

    /** Chamada na fila de IO, nunca na fila de captura/GL. Sem inferir ocupação da GPU. */
    fun performanceSample(activity: String, frames: Int, dropped: Int): com.novacamera.s24.core.SessionPerformance.Sample {
        val start = android.os.SystemClock.elapsedRealtimeNanos()
        val pss = runCatching { android.os.Debug.getPss() }.getOrNull()?.takeIf { it > 0 }
        return com.novacamera.s24.core.SessionPerformance.Sample(android.os.SystemClock.elapsedRealtime(),
            android.os.Process.getElapsedCpuTime(), pss, activity, frames, dropped,
            (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
    }

    private fun snapshot() = DeviceHeat(thermalStatus, batteryTempC, charging, power.isPowerSaveMode, batteryPercent)

    private fun publish() {
        val next = snapshot()
        if (next != current) {
            current = next
            onChange(next)
        }
    }
}
