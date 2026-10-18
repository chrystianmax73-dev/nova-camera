package com.novacamera.s24.core.imaging

import org.junit.Assert.*
import org.junit.Test

/** Hipóteses reproduzíveis de seleção, não uma máscara medida da foto do S24. */
class PortraitObjectSeedTest {
    private val w=120;private val h=160
    private fun scene()=Plane(w,h,FloatArray(w*h){i->val x=i%w;val y=i/w
        if((x in 52..68 && y in 45..120)||(x in 77..84 && y in 45..120)) .8f else .1f})
    @Test fun objetoCentralNaoIncluiIlhaDesconectadaDentroDaAreaCentral() {
        val plan=DepthPortrait.plan(scene(),null)
        println("PORTRAIT_SEED centro=${plan.subject[60,80]} ilha=${plan.subject[80,80]}")
        assertTrue(plan.subject[60,80]>.9f)
        assertTrue("outro objeto separado não é o assunto",plan.subject[80,80]<.1f)
    }
    @Test fun toqueEmObjetoFinoNaoSelecionaOutraRegiaoDentroDoDisco() {
        val depth=Plane(w,h,FloatArray(w*h){i->val x=i%w;val y=i/w
            if((x in 58..59 && y in 30..130)||(x in 61..67 && y in 30..130)) .8f else .1f})
        val plan=DepthPortrait.plan(depth,null,58f/(w-1) to 80f/(h-1))
        println("PORTRAIT_THIN tocado=${plan.subject[58,80]} vizinho=${plan.subject[64,80]}")
        assertTrue("estrutura fina precisa ser assunto, não mistura de fundo",plan.subject[58,80]>.6f)
        assertTrue("região vizinha desconectada não pode virar assunto",plan.subject[64,80]<.1f)
    }
    @Test fun pixelIsoladoNaoDefinePlanoDeFocoDoToque() {
        val depth=Plane(w,h,FloatArray(w*h){.1f});depth[60,80]=.9f
        val plan=DepthPortrait.plan(depth,null,60f/(w-1) to 80f/(h-1))
        assertTrue("mediana deve manter o plano vizinho, não a profundidade do outlier",plan.near<.5f)
    }
}
