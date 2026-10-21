package com.novacamera.s24.core

/**
 * Selo de cada item da galeria do app. Fonte da verdade: a descrição EXIF que o próprio app grava
 * ("… ruído reduzido por IA …") vale para qualquer foto; o estado da sessão de revisão (só as últimas)
 * acrescenta "na fila", "aplicando" e "recusada/dispensada", que não ficam no arquivo.
 */
enum class GalleryBadge(val label: String) {
    VIDEO("VÍDEO"), AI("IA"), AI_RUNNING("IA…"), AI_WAITING("IA na fila"), AI_REFUSED("IA recusada"),
    AI_SKIPPED("sem ruído"), NOVA("NOVA"), NONE("");

    companion object {
        /** [refine]: "running", "waiting", "done", "failed" ou null (sem sessão); [report]: motivo gravado na sessão. */
        fun of(isVideo: Boolean, description: String?, refine: String?, report: String?): GalleryBadge = when {
            isVideo -> VIDEO
            refine == "running" -> AI_RUNNING
            refine == "waiting" -> AI_WAITING
            description?.contains("por IA") == true -> AI
            refine == "failed" && report?.startsWith("IA dispensada") == true -> AI_SKIPPED
            refine == "failed" -> AI_REFUSED
            description?.startsWith("Nova") == true -> NOVA
            else -> NONE
        }
    }
}
