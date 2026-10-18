package com.novacamera.s24.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Erro que fechou o app (Java/Kotlin): o Android só informa "crash" no histórico de encerramentos,
 * sem a causa. Aqui a pilha é guardada no próprio aparelho (nada sai dele) para o Diagnóstico.
 */
object CrashLog {
    private const val PREFS = "nova_diag"
    private const val KEY = "crashes"
    private const val KEEP = 3
    private const val SEP = "\n=====\n"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, thread: String, error: Throwable) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val entry = "${SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())} · versão $version · thread $thread\n" +
            format(error)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = (p.getString(KEY, "") ?: "").split(SEP).filter { it.isNotBlank() }.takeLast(KEEP - 1) + entry
        // commit (não apply): o processo morre logo em seguida.
        p.edit().putString(KEY, list.joinToString(SEP)).commit()
    }

    /** Tipo, mensagem e as primeiras linhas da pilha de cada causa (o suficiente para achar a linha). */
    fun format(error: Throwable): String = buildString {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth < 4) {
            if (depth > 0) append("causado por: ")
            append(e.javaClass.name).append(": ").append(e.message ?: "").append('\n')
            e.stackTrace.take(if (depth == 0) 14 else 8).forEach { append("    at ").append(it).append('\n') }
            e = e.cause.takeIf { it !== e }; depth++
        }
    }

    fun report(context: Context): String {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return "Erros que fecharam o app (últimos)" + if (s.isBlank()) ": nenhum" else "\n" +
            s.split(SEP).filter { it.isNotBlank() }.reversed().joinToString("\n") { it.lines().joinToString("\n") { l -> "  $l" } }
    }
}
