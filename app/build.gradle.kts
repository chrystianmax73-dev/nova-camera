plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    lint {
        // O AppCompat só entra como dependência transitiva do ML Kit; o app não usa temas/widgets AppCompat.
        disable += "AppCompatCustomView"
    }
    namespace = "com.novacamera.s24"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.novacamera.s24"
        minSdk = 29
        targetSdk = 35
        versionCode = 1000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.6.0"
        // Alvo: Galaxy S24/S25 Ultra (arm64). Evita levar as bibliotecas nativas do ML Kit de 4 arquiteturas.
        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("novaRelease") {
            val keyPath = System.getenv("NOVA_KEYSTORE_FILE")
            if (!keyPath.isNullOrBlank()) {
                storeFile = file(keyPath)
                storePassword = System.getenv("NOVA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NOVA_KEY_ALIAS")
                keyPassword = System.getenv("NOVA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("novaRelease")
        }
    }
}

android.testOptions.unitTests.isIncludeAndroidResources = true
// Ambientes sem acesso direto ao Maven podem apontar para jars do Robolectric já baixados.
tasks.withType<Test>().configureEach {
    System.getenv("ROBOLECTRIC_DEPS_DIR")?.let {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", it)
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val camerax = "1.6.2"
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    // Segmentação de pessoa do Retrato próprio: modelo embutido no APK, roda offline no aparelho.
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    testImplementation("junit:junit:4.13.2")
    // Renderiza a interface real em JVM (sem aparelho) para teste de fumaça e capturas de layout.
    testImplementation("org.robolectric:robolectric:4.17")
}
