package com.novacamera.s24.core

import java.util.Locale

/**
 * Atraso do disparo medido no aparelho, para o diagnóstico. Tudo a partir do momento do toque
 * (chamada da captura): início da captura (callback do CameraX), início da exposição do sensor
 * (carimbo do quadro; negativo = quadro anterior ao toque, como no ZSL) e imagem entregue.
 */
object ShutterLog {
    data class Entry(val mode: String, val startMs: Long?, val sensorMs: Long?, val imageMs: Long?)

    private const val KEEP = 20
    private val entries = ArrayDeque<Entry>()

    /** Modo de captura configurado na última vinculação da câmera (texto para o relatório). */
    @Volatile var configured = "câmera ainda não vinculada"

    /**
     * Carimbo do sensor em relação ao toque, em ms. Só é aceito se cair numa janela plausível
     * (fonte de tempo do sensor = relógio do sistema); fora disso, a base é outra e não se compara.
     */
    fun sensorDelayMs(sensorTimestampNs: Long, tapNs: Long): Long? =
        ((sensorTimestampNs - tapNs) / 1_000_000L).takeIf { sensorTimestampNs > 0 && it in -1_500L..5_000L }

    @Synchronized fun add(e: Entry) {
        entries.addLast(e)
        while (entries.size > KEEP) entries.removeFirst()
    }

    @Synchronized fun clear() = entries.clear()

    private fun median(v: List<Long>): Long? = v.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    @Synchronized fun report(): String = buildString {
        append("Disparo: $configured")
        if (entries.isEmpty()) { append(" · nenhuma foto medida ainda"); return@buildString }
        entries.groupBy { it.mode }.forEach { (mode, list) ->
            fun fmt(v: Long?) = v?.let { String.format(Locale.US, "%d ms", it) } ?: "—"
            append("\n  $mode (${list.size} fotos, mediana): toque→captura ${fmt(median(list.mapNotNull { it.startMs }))}, " +
                "toque→exposição do sensor ${fmt(median(list.mapNotNull { it.sensorMs }))}, " +
                "toque→imagem ${fmt(median(list.mapNotNull { it.imageMs }))}")
        }
    }
}
