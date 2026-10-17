package com.novacamera.s24.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Diagnóstico local, sem nomes/URIs dos arquivos nem conteúdo dos quadros. */
object VideoLog {
    @Synchronized
    fun add(context: Context, text: String) {
        val p = context.getSharedPreferences("nova_video_diag", Context.MODE_PRIVATE)
        val line = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date()) + " · " + text
        val lines = p.getString("events", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(29) + line
        p.edit().putString("events", lines.joinToString("\n")).apply()
    }

    fun report(context: Context): String = "Vídeo (configuração, efeitos e arquivo final)\n" +
        context.getSharedPreferences("nova_video_diag", Context.MODE_PRIVATE)
            .getString("events", "nenhuma gravação medida").orEmpty()
}

