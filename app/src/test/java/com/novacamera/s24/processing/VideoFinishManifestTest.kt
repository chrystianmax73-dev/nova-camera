package com.novacamera.s24.processing
import android.Manifest
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class VideoFinishManifestTest {
    @Test fun exportadorLocalNaoAdicionaInternetAoManifestoMesclado() {
        val app=RuntimeEnvironment.getApplication()
        val permissions=app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
    }
}
