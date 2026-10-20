package com.novacamera.s24.core

/**
 * O que um QR/código de barras contém, para mostrar e oferecer ação — nunca abrir sozinho (golpe com QR
 * existe: o link aparece inteiro, com o domínio em destaque, antes de qualquer toque).
 */
sealed class QrContent(val raw: String) {
    class Link(raw: String, val url: String, val host: String, val secure: Boolean) : QrContent(raw)
    class Wifi(raw: String, val ssid: String, val password: String?, val security: String, val hidden: Boolean) : QrContent(raw)
    class Phone(raw: String, val number: String) : QrContent(raw)
    class Email(raw: String, val address: String, val subject: String?, val body: String?) : QrContent(raw)
    class Text(raw: String) : QrContent(raw)

    /** Uma linha curta para o chip da câmera. */
    /** Rótulo do aviso no visor (como "Link do Instagram" na Samsung): o que é + o essencial. */
    fun label(): String = when (this) {
        is Link -> (if (secure) "" else "⚠ ") + "Link · " + host.removePrefix("www.")
        is Wifi -> "Wi-Fi · $ssid"
        is Phone -> "Ligar · $number"
        is Email -> "E-mail · $address"
        is Text -> "Texto · " + raw.replace('\n', ' ').take(28)
    }

    fun summary(): String = when (this) {
        is Link -> (if (secure) "" else "⚠ ") + host
        is Wifi -> "Wi-Fi: $ssid"
        is Phone -> "Telefone: $number"
        is Email -> "E-mail: $address"
        is Text -> raw.replace('\n', ' ').take(40)
    }

    companion object {
        private val URL = Regex("^(https?)://([^/?#:\\s]+)", RegexOption.IGNORE_CASE)
        private val BARE = Regex("^(www\\.)[^\\s]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE)

        fun classify(text: String): QrContent {
            val t = text.trim()
            URL.find(t)?.let { m -> return Link(text, t, m.groupValues[2].lowercase(), m.groupValues[1].equals("https", true)) }
            if (BARE.containsMatchIn(t)) return Link(text, "https://$t", t.substringBefore('/').lowercase(), true)
            if (t.startsWith("WIFI:", true)) return wifi(text, t.substring(5))
            if (t.startsWith("tel:", true)) return Phone(text, t.substring(4).trim())
            if (t.startsWith("mailto:", true)) {
                val rest = t.substring(7)
                val addr = rest.substringBefore('?')
                val q = rest.substringAfter('?', "").split('&').associate { it.substringBefore('=').lowercase() to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
                return Email(text, addr, q["subject"], q["body"])
            }
            if (t.startsWith("MATMSG:", true)) {
                val f = fields(t.substring(7))
                return Email(text, f["TO"].orEmpty(), f["SUB"], f["BODY"])
            }
            return Text(text)
        }

        private fun wifi(raw: String, body: String): QrContent {
            val f = fields(body)
            val ssid = f["S"] ?: return Text(raw)
            return Wifi(raw, ssid, f["P"]?.takeIf { it.isNotEmpty() }, (f["T"] ?: "nopass").uppercase(), f["H"].equals("true", true))
        }

        /** Campos "K:valor;" com escape por barra invertida (\; \: \, \\). */
        private fun fields(body: String): Map<String, String> {
            val out = HashMap<String, String>()
            var i = 0
            while (i < body.length) {
                val colon = body.indexOf(':', i)
                if (colon < 0) break
                val key = body.substring(i, colon).uppercase()
                val value = StringBuilder()
                var j = colon + 1
                while (j < body.length && body[j] != ';') {
                    if (body[j] == '\\' && j + 1 < body.length) { value.append(body[j + 1]); j += 2 } else { value.append(body[j]); j++ }
                }
                if (key.isNotEmpty()) out.putIfAbsent(key, value.toString())
                i = j + 1
            }
            return out
        }
    }
}

/** Mesmo código visto várias vezes por segundo: avisa uma vez e esquece depois de um tempo sem ver. */
class QrDedupe(private val forgetMs: Long = 4_000L) {
    private var last: String? = null
    private var lastSeen = 0L
    fun fresh(text: String, nowMs: Long): Boolean {
        val isNew = text != last || nowMs - lastSeen > forgetMs
        last = text; lastSeen = nowMs
        return isNew
    }
    fun gone(nowMs: Long) = last != null && nowMs - lastSeen > forgetMs
    fun reset() { last = null }
}
