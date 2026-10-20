package com.novacamera.s24.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Decodificação do PP-PicoDet (port do demo oficial deploy/third_engine/demo_ncnn/picodet.cpp, Apache-2.0):
 * 4 níveis (passos 8/16/32/64), nota por classe já em sigmoid, caixa por distribuição (DFL, 8 posições por
 * lado). NMS por classe. Rótulos em português para as 80 classes do COCO.
 */
object Detections {
    const val SIZE = 416
    const val SCORE = 0.40f
    const val IOU = 0.5f
    private val STRIDES = intArrayOf(8, 16, 32, 64)

    class Det(val cls: Int, val score: Float, val l: Float, val t: Float, val r: Float, val b: Float) {
        val label get() = LABELS.getOrElse(cls) { "Objeto" }
        val area get() = max(0f, r - l) * max(0f, b - t)
        fun contains(x: Float, y: Float) = x in l..r && y in t..b
    }

    /** [outs] na ordem da rede: 4 notas (N×80) e 4 caixas (N×32); [w]×[h] = tamanho da imagem de origem. */
    fun decode(outs: List<FloatArray>, w: Int, h: Int, threshold: Float = SCORE): List<Det> {
        if (outs.size != 8) return emptyList()
        val dets = ArrayList<Det>()
        val sx = w.toFloat() / SIZE; val sy = h.toFloat() / SIZE
        val prob = FloatArray(8)
        for (level in 0 until 4) {
            val cls = outs[level]; val reg = outs[4 + level]; val s = STRIDES[level]
            val n = cls.size / 80
            val fw = (SIZE + s - 1) / s
            for (i in 0 until n) {
                var best = 0; var score = 0f
                for (c in 0 until 80) { val v = cls[i * 80 + c]; if (v > score) { score = v; best = c } }
                if (score < threshold) continue
                val cx = (i % fw) * s.toFloat(); val cy = (i / fw) * s.toFloat()
                val d = FloatArray(4)
                for (k in 0 until 4) {
                    var m = Float.NEGATIVE_INFINITY
                    for (j in 0 until 8) m = max(m, reg[i * 32 + k * 8 + j])
                    var sum = 0f
                    for (j in 0 until 8) { prob[j] = exp(reg[i * 32 + k * 8 + j] - m); sum += prob[j] }
                    var e = 0f
                    for (j in 0 until 8) e += prob[j] / sum * j
                    d[k] = e * s
                }
                dets += Det(best, score, max(0f, cx - d[0]) * sx, max(0f, cy - d[1]) * sy,
                    min(SIZE.toFloat(), cx + d[2]) * sx, min(SIZE.toFloat(), cy + d[3]) * sy)
            }
        }
        return nms(dets)
    }

    fun iou(a: Det, b: Det): Float {
        val ix = max(0f, min(a.r, b.r) - max(a.l, b.l)); val iy = max(0f, min(a.b, b.b) - max(a.t, b.t))
        val i = ix * iy
        return i / (a.area + b.area - i + 1e-6f)
    }

    fun nms(dets: List<Det>): List<Det> {
        val out = ArrayList<Det>()
        for ((_, group) in dets.groupBy { it.cls }) {
            val cand = group.sortedByDescending { it.score }.toMutableList()
            while (cand.isNotEmpty()) {
                val b = cand.removeAt(0); out += b
                cand.removeAll { iou(b, it) >= IOU }
            }
        }
        return out.sortedByDescending { it.score }
    }

    /**
     * Objeto sob o toque: a menor caixa que contém o ponto (o vaso, não a mesa inteira); se nenhuma contém,
     * a mais próxima a até 8% do lado menor. Null se não houver nada perto.
     */
    fun pickAt(dets: List<Det>, x: Float, y: Float, w: Int, h: Int): Det? {
        dets.filter { it.contains(x, y) }.minByOrNull { it.area }?.let { return it }
        val limit = 0.08f * min(w, h)
        return dets.map { d -> d to max(max(d.l - x, x - d.r), max(d.t - y, y - d.b)).coerceAtLeast(0f) }
            .filter { it.second <= limit }.minByOrNull { it.second }?.first
    }

    val LABELS = listOf(
        "Pessoa", "Bicicleta", "Carro", "Moto", "Avião", "Ônibus", "Trem", "Caminhão", "Barco", "Semáforo",
        "Hidrante", "Placa de pare", "Parquímetro", "Banco", "Pássaro", "Gato", "Cachorro", "Cavalo", "Ovelha", "Vaca",
        "Elefante", "Urso", "Zebra", "Girafa", "Mochila", "Guarda-chuva", "Bolsa", "Gravata", "Mala", "Frisbee",
        "Esqui", "Snowboard", "Bola", "Pipa", "Taco de beisebol", "Luva de beisebol", "Skate", "Prancha de surfe", "Raquete", "Garrafa",
        "Taça", "Copo", "Garfo", "Faca", "Colher", "Tigela", "Banana", "Maçã", "Sanduíche", "Laranja",
        "Brócolis", "Cenoura", "Cachorro-quente", "Pizza", "Rosquinha", "Bolo", "Cadeira", "Sofá", "Planta", "Cama",
        "Mesa", "Vaso sanitário", "TV", "Notebook", "Mouse", "Controle remoto", "Teclado", "Celular", "Micro-ondas", "Forno",
        "Torradeira", "Pia", "Geladeira", "Livro", "Relógio", "Vaso", "Tesoura", "Ursinho de pelúcia", "Secador", "Escova de dentes",
    )
}
