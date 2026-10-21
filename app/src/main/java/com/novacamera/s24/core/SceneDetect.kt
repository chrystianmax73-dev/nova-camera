package com.novacamera.s24.core

import kotlin.math.max
import kotlin.math.min

/**
 * Cena reconhecida na prévia (pedido do dono, 20/10: ícone perto do zoom como Samsung/iPhone, sem o nome em
 * cima do objeto tocado). O ícone só aparece quando a foto muda de verdade, de leve
 * ([com.novacamera.s24.core.imaging.SceneTone]); pessoa e texto já têm tratamento próprio (contraluz, leitor).
 *
 * Fontes: classificador EfficientNet-Lite0 do MediaPipe (ImageNet, Apache-2.0 nos metadados do modelo) para
 * animal e comida; planta pela cobertura de folhagem verde (o ImageNet acerta mal planta: nas fotos do dono
 * saía "bolota", "besouro", "bobina") somada ao grupo de planta do classificador; pessoa pelos rostos.
 */
object SceneDetect {
    enum class Scene(val label: String) { NONE(""), PERSON("Pessoa"), PET("Animal"), FOOD("Comida"), PLANT("Planta") }

    /** Índices ImageNet (sem fundo) conferidos no labels_without_background.txt do modelo. */
    private val DOGS = 151..268
    private val CATS = 281..285
    private val FOOD = 923..969
    private const val HAY = 958
    private val PLANTS = setOf(580, 738, 984, 985, 986, 987, 988, 989, 990)

    class Groups(val pet: Float, val food: Float, val plant: Float)

    /** Soma as probabilidades por grupo; [scores] = pares (índice, probabilidade) do classificador. */
    fun groups(scores: List<Pair<Int, Float>>): Groups {
        var pet = 0f; var food = 0f; var plant = 0f
        for ((i, p) in scores) when {
            i in DOGS || i in CATS -> pet += p
            i in FOOD && i != HAY -> food += p
            i in PLANTS -> plant += p
        }
        return Groups(min(pet, 1f), min(food, 1f), min(plant, 1f))
    }

    /**
     * Fração de folhagem: matiz entre amarelo-esverdeado e verde-azulado (70°–165°), saturação ≥ 0,18 e luz
     * média. [argb] reduzido (ex.: 96×128).
     */
    fun foliage(argb: IntArray): Float {
        if (argb.isEmpty()) return 0f
        var n = 0
        for (c in argb) {
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            if (mx < 40 || mx > 245 || mx - mn < 0.18f * mx || mx != g) continue
            val d = (mx - mn).toFloat()
            val hue = 60f * ((b - r) / d + 2f)
            if (hue in 70f..165f) n++
        }
        return n.toFloat() / argb.size
    }

    /** Candidata do quadro: pessoa (rosto ≥ 3% do quadro) primeiro; depois a maior entre animal, comida e planta. */
    fun candidate(g: Groups, foliage: Float, faceArea: Float): Pair<Scene, Float> {
        if (faceArea >= 0.03f) return Scene.PERSON to 1f
        // Calibrado nas fotos do dono: plantas 0,06–0,24 de folhagem, garrafa/cama/TV/selfies ≤ 0,006.
        // Entra (0,6) a partir de ~0,14.
        val plant = max(g.plant, ((foliage - 0.04f) / 0.16f).coerceIn(0f, 1f))
        return listOf(Scene.PET to g.pet, Scene.FOOD to g.food, Scene.PLANT to plant).maxBy { it.second }
    }

    /**
     * Histerese: entra com confiança ≥ [ENTER] estável por [STABLE_MS]; sai abaixo de [EXIT] por [LEAVE_MS];
     * no máximo uma troca a cada [MIN_SWITCH_MS] (sem piscar).
     */
    class Tracker {
        var scene = Scene.NONE; private set
        private var pending = Scene.NONE
        private var pendingSince = 0L
        private var lowSince = -1L
        private var changedAt = -MIN_SWITCH_MS

        /** Devolve true quando a cena mostrada muda. */
        fun update(c: Scene, p: Float, nowMs: Long): Boolean {
            val strong = c != Scene.NONE && p >= ENTER
            if (scene != Scene.NONE && (c != scene || p < EXIT)) {
                if (lowSince < 0) lowSince = nowMs
            } else lowSince = -1L
            if (strong && c != scene) {
                if (pending != c) { pending = c; pendingSince = nowMs }
            } else if (!strong) pending = Scene.NONE
            if (nowMs - changedAt < MIN_SWITCH_MS) return false
            if (pending != Scene.NONE && pending != scene && nowMs - pendingSince >= STABLE_MS) {
                scene = pending; pending = Scene.NONE; lowSince = -1L; changedAt = nowMs; return true
            }
            if (scene != Scene.NONE && lowSince >= 0 && nowMs - lowSince >= LEAVE_MS) {
                scene = Scene.NONE; lowSince = -1L; changedAt = nowMs; return true
            }
            return false
        }

        fun reset() { scene = Scene.NONE; pending = Scene.NONE; lowSince = -1L }
    }

    const val ENTER = 0.6f
    const val EXIT = 0.45f
    const val STABLE_MS = 700L
    const val LEAVE_MS = 1000L
    const val MIN_SWITCH_MS = 2000L
}
