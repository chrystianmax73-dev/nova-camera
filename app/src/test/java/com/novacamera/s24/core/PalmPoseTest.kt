package com.novacamera.s24.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pontos reais do detector de mão do MediaPipe (hand_landmarker float16/1) nas imagens de teste do próprio MediaPipe. */
class PalmPoseTest {
    private val openHand = floatArrayOf(0.8096f, 0.9245f, 0.7103f, 0.8815f, 0.6405f, 0.7583f, 0.5943f, 0.6213f, 0.5476f, 0.5351f, 0.6945f, 0.5715f, 0.6667f, 0.3697f, 0.6519f, 0.2420f, 0.6438f, 0.1348f, 0.7543f, 0.5453f, 0.7456f, 0.3177f, 0.7408f, 0.1694f, 0.7378f, 0.0542f, 0.8091f, 0.5576f, 0.8176f, 0.3491f, 0.8235f, 0.2078f, 0.8260f, 0.1022f, 0.8574f, 0.5888f, 0.8802f, 0.4258f, 0.8940f, 0.3239f, 0.9052f, 0.2370f)
    private val fist = floatArrayOf(0.4771f, 0.6613f, 0.6126f, 0.5578f, 0.7112f, 0.4317f, 0.6836f, 0.3200f, 0.5593f, 0.3206f, 0.6083f, 0.3069f, 0.6294f, 0.2141f, 0.6244f, 0.3287f, 0.6078f, 0.3685f, 0.5157f, 0.3219f, 0.5293f, 0.2477f, 0.5485f, 0.3805f, 0.5443f, 0.3881f, 0.4316f, 0.3492f, 0.4486f, 0.2765f, 0.4772f, 0.3965f, 0.4679f, 0.4083f, 0.3483f, 0.3781f, 0.3862f, 0.3282f, 0.4114f, 0.3973f, 0.3940f, 0.4115f)
    private val victory = floatArrayOf(0.5164f, 0.8041f, 0.6064f, 0.7111f, 0.6280f, 0.5885f, 0.5265f, 0.5208f, 0.4243f, 0.4993f, 0.5606f, 0.4490f, 0.5767f, 0.3226f, 0.5796f, 0.2418f, 0.5828f, 0.1694f, 0.4696f, 0.4600f, 0.4436f, 0.3169f, 0.4270f, 0.2227f, 0.4070f, 0.1428f, 0.3954f, 0.5061f, 0.3618f, 0.4388f, 0.4154f, 0.5267f, 0.4492f, 0.5944f, 0.3318f, 0.5732f, 0.3092f, 0.5040f, 0.3558f, 0.5576f, 0.3934f, 0.6119f)
    private val thumbUp = floatArrayOf(0.6196f, 0.7211f, 0.6103f, 0.6620f, 0.6238f, 0.6096f, 0.6462f, 0.5676f, 0.6482f, 0.5330f, 0.6985f, 0.6047f, 0.7115f, 0.6275f, 0.6941f, 0.6462f, 0.6768f, 0.6524f, 0.7209f, 0.6325f, 0.7204f, 0.6543f, 0.6992f, 0.6679f, 0.6821f, 0.6725f, 0.7302f, 0.6652f, 0.7258f, 0.6786f, 0.7075f, 0.6902f, 0.6898f, 0.6949f, 0.7330f, 0.6981f, 0.7315f, 0.7071f, 0.7152f, 0.7148f, 0.6996f, 0.7180f)

    @Test fun palmaAbertaDispara() = assertTrue(PalmPose.isOpenPalm(openHand, 1.8848f))
    @Test fun punhoVitoriaEPolegarNao() {
        assertFalse(PalmPose.isOpenPalm(fist, 1.0113f))
        assertFalse(PalmPose.isOpenPalm(victory, 0.9448f))
        assertFalse(PalmPose.isOpenPalm(thumbUp, 1f))
    }
    @Test fun maoMuitoPequenaIgnorada() {
        val tiny = FloatArray(42) { i -> 0.5f + (openHand[i] - 0.5f) * 0.1f }
        assertFalse(PalmPose.isOpenPalm(tiny, 1.8848f))
    }
    @Test fun gatilhoPedeMaoParadaEDepoisDescansa() {
        val t = PalmTrigger()
        assertFalse(t.add(true, 0)); assertFalse(t.add(true, 300)); assertTrue(t.add(true, 750))
        assertFalse(t.add(true, 1000)); assertFalse(t.add(true, 2000))
        val u = PalmTrigger()
        assertFalse(u.add(true, 0)); assertFalse(u.add(false, 400)); assertFalse(u.add(true, 750))
    }
}
