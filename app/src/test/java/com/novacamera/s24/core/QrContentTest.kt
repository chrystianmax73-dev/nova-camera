package com.novacamera.s24.core

import org.junit.Assert.*
import org.junit.Test

class QrContentTest {
    @Test fun linkMostraODominioEAvisaSemHttps() {
        val l = QrContent.classify("https://pix.exemplo.com.br/pagar?id=1") as QrContent.Link
        assertEquals("pix.exemplo.com.br", l.host); assertTrue(l.secure); assertEquals("pix.exemplo.com.br", l.summary())
        val h = QrContent.classify("http://Banco-Seguro.xyz/login") as QrContent.Link
        assertFalse(h.secure); assertEquals("⚠ banco-seguro.xyz", h.summary())
        assertTrue(QrContent.classify("www.exemplo.com/a") is QrContent.Link)
    }

    @Test fun wifiComEscapes() {
        val w = QrContent.classify("WIFI:T:WPA;S:Casa\\;2G;P:se\\:nha;H:false;;") as QrContent.Wifi
        assertEquals("Casa;2G", w.ssid); assertEquals("se:nha", w.password); assertEquals("WPA", w.security); assertFalse(w.hidden)
        val open = QrContent.classify("WIFI:S:Livre;;") as QrContent.Wifi
        assertNull(open.password); assertEquals("NOPASS", open.security)
    }

    @Test fun telefoneEmailETexto() {
        assertEquals("+5511999990000", (QrContent.classify("tel:+5511999990000") as QrContent.Phone).number)
        val e = QrContent.classify("mailto:ola@ex.com?subject=Oi%20tudo&body=x") as QrContent.Email
        assertEquals("ola@ex.com", e.address); assertEquals("Oi tudo", e.subject)
        assertEquals("a@b.c", (QrContent.classify("MATMSG:TO:a@b.c;SUB:s;BODY:b;;") as QrContent.Email).address)
        assertTrue(QrContent.classify("7891234567895") is QrContent.Text) // código de barras de produto
    }

    @Test fun avisaUmaVezPorCodigo() {
        val d = QrDedupe(4000)
        assertTrue(d.fresh("a", 0)); assertFalse(d.fresh("a", 500)); assertFalse(d.fresh("a", 3000))
        assertTrue(d.fresh("b", 3100)); assertFalse(d.gone(5000)); assertTrue(d.gone(7200))
        assertTrue(d.fresh("b", 7300)) // sumiu e voltou: avisa de novo
    }
}
