package com.novacamera.s24.core

import android.content.Context

/**
 * Prévia parada (a câmera abre mas para de entregar quadros), registrada com o contexto exato
 * (modo, lente, zoom, efeito/análise ativos) e guardada entre aberturas do app, para o Diagnóstico.
 */
object StallLog {
    private const val PREFS = "nova_diag"
    private const val KEY = "stalls"
    private const val KEEP = 10

    fun add(context: Context, line: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = (p.getString(KEY, "") ?: "").lines().filter { it.isNotBlank() }.takeLast(KEEP - 1) + line
        p.edit().putString(KEY, list.joinToString("\n")).apply()
    }

    fun report(context: Context): String {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return "Prévia parada (últimos registros)" + if (s.isBlank()) ": nenhum" else "\n" + s.lines().joinToString("\n") { "  $it" }
    }
}
