package com.novacamera.s24.processing
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonSegmenterOwnershipTest {
    @Test fun fechaImagemPropriaSemReciclarQuadroDoChamadorInclusiveEmErro() {
        val source=Bitmap.createBitmap(256,144,Bitmap.Config.ARGB_8888)
        var owned:Bitmap?=null
        PersonSegmenter.withOwnedInput(source,256) { image ->
            owned=BitmapExtractor.extract(image)
            assertNotSame(source,owned)
            assertFalse(source.isRecycled)
        }
        assertTrue(owned!!.isRecycled);assertFalse(source.isRecycled)
        try { PersonSegmenter.withOwnedInput(source,128) { image ->owned=BitmapExtractor.extract(image);error("cancelado") } }catch(_:IllegalStateException){}
        assertTrue(owned!!.isRecycled);assertFalse(source.isRecycled)
        source.recycle()
    }
}
