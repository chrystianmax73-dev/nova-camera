import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ncnn (BSD-3-Clause) pré-compilado para Android com Vulkan: versão fixa e sha256 conferido.
val ncnnVersion = "20240820"
val ncnnSha256 = "c1a3d9128139237ae1a7d3019a10e8eba5af8fda514b69544f85102244d90613"
val ncnnRoot = layout.buildDirectory.dir("ncnn/ncnn-$ncnnVersion-android-vulkan").get().asFile
val fetchNcnn by tasks.registering {
    description = "Baixa e extrai o ncnn pré-compilado (uma vez)."
    outputs.dir(ncnnRoot)
    doLast {
        if (File(ncnnRoot, "arm64-v8a/lib/cmake/ncnn/ncnnConfig.cmake").exists()) return@doLast
        val zip = layout.buildDirectory.file("ncnn/ncnn.zip").get().asFile.apply { parentFile.mkdirs() }
        val url = "https://github.com/Tencent/ncnn/releases/download/$ncnnVersion/ncnn-$ncnnVersion-android-vulkan.zip"
        URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == ncnnSha256) { "sha256 do ncnn não confere: $digest" }
        copy { from(zipTree(zip)); into(ncnnRoot.parentFile) }
        zip.delete()
    }
}

android {
    lint {
        // O AppCompat pode entrar como dependência transitiva; o app não usa temas/widgets AppCompat.
        disable += "AppCompatCustomView"
    }
    namespace = "com.novacamera.s24"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.novacamera.s24"
        minSdk = 29
        targetSdk = 35
        versionCode = 1000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.20.1"
        // Alvo: Galaxy S24/S25 Ultra (arm64). Evita levar as bibliotecas nativas (MediaPipe) de 4 arquiteturas.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DNCNN_ROOT=${ncnnRoot.absolutePath}", "-DANDROID_STL=c++_static")
            }
        }
    }

    // NDK fixado (o CI instala exatamente esta versão).
    ndkVersion = "27.0.12077973"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

tasks.named("preBuild") { dependsOn(fetchNcnn) }

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
    // Assinatura no vídeo (OverlayEffect desenha sobre os quadros gravados).
    implementation("androidx.camera:camera-effects:$camerax")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    // Segmentação do Retrato (pessoa, cabelo, pele, roupa, acessórios): MediaPipe Tasks (Apache-2.0),
    // modelo selfie_multiclass_256x256 (Apache-2.0) embutido em assets; roda offline no aparelho.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    testImplementation("junit:junit:4.13.2")
    // Renderiza a interface real em JVM (sem aparelho) para teste de fumaça e capturas de layout.
    testImplementation("org.robolectric:robolectric:4.17")
}


