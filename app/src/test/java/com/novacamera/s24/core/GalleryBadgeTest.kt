package com.novacamera.s24.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryBadgeTest {
    @Test fun descricaoDoArquivoValeSemSessao() {
        assertEquals(GalleryBadge.AI, GalleryBadge.of(false, "Nova Foto · ruído reduzido por IA em 41.2 s", null, ""))
        assertEquals(GalleryBadge.NOVA, GalleryBadge.of(false, "Nova Foto · tom ajustado", null, ""))
        assertEquals(GalleryBadge.NONE, GalleryBadge.of(false, null, null, ""))
    }

    @Test fun estadoDaSessaoTemPrioridade() {
        assertEquals(GalleryBadge.AI_RUNNING, GalleryBadge.of(false, "Nova Foto", "running", ""))
        assertEquals(GalleryBadge.AI_WAITING, GalleryBadge.of(false, "Nova Foto", "waiting", ""))
        assertEquals(GalleryBadge.AI_SKIPPED, GalleryBadge.of(false, "Nova Foto", "failed", "IA dispensada: foto já limpa"))
        assertEquals(GalleryBadge.AI_REFUSED, GalleryBadge.of(false, "Nova Foto", "failed", "IA recusada pela validação"))
    }

    @Test fun videoSempreVideo() = assertEquals(GalleryBadge.VIDEO, GalleryBadge.of(true, null, "running", ""))
}
