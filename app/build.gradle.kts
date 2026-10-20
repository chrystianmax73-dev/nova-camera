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

// Leitura de texto: PP-OCRv5 mobile (pesos PaddleOCR, Apache-2.0) já convertido para ncnn pelo autor do
// ncnn (repositório nihui/ncnn-assets, mesmo pré-processamento do exemplo oficial). Arquivos fixos por sha256.
val ocrAssets = layout.buildDirectory.dir("generated/ocrAssets").get().asFile
val handUrl = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
val handSha256 = "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"
val ocrModels = mapOf(
    "PP_OCRv5_mobile_det.ncnn.param" to "358f459680ae0e7a73e477469e529ce116f68c629019ec7a0b6457d2d9117934",
    "PP_OCRv5_mobile_det.ncnn.bin" to "857a96bc963725105b78a178dfcc3c0c3db1a7b9eef32244367b2cb105ccf60b",
    "PP_OCRv5_mobile_rec.ncnn.param" to "f52a6586ac3338d8c350db0c9f3c55ff2ecd3763327f9bc7f0efc091f8a63e74",
    "PP_OCRv5_mobile_rec.ncnn.bin" to "49d9907a55ba20fa6637f9f788f66ab00793bc8ce57a733a09dbe86b9a2e3db0",
)
val fetchOcrModels by tasks.registering {
    description = "Baixa os modelos de leitura de texto e o detector de mão (uma vez) e confere o sha256."
    outputs.dir(ocrAssets)
    doLast {
        val dir = File(ocrAssets, "ocr").apply { mkdirs() }
        for ((name, sha) in ocrModels) {
            val f = File(dir, name)
            fun digest() = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            if (f.exists() && digest() == sha) continue
            f.delete()
            URI("https://raw.githubusercontent.com/nihui/ncnn-assets/master/models/$name").toURL().openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            check(digest() == sha) { "sha256 de $name não confere: ${digest()}" }
        }
        // Disparo com a palma: detector de mão do MediaPipe (model card: Apache-2.0). Só os 21 pontos da mão;
        // a regra de "palma aberta" é do app (o classificador de gestos da Google não tem licença publicada).
        val hand = File(File(ocrAssets, "hand").apply { mkdirs() }, "hand_landmarker.task")
        fun handDigest() = MessageDigest.getInstance("SHA-256").digest(hand.readBytes()).joinToString("") { "%02x".format(it) }
        if (!hand.exists() || handDigest() != handSha256) {
            hand.delete()
            URI(handUrl).toURL().openStream().use { input -> hand.outputStream().use { input.copyTo(it) } }
            check(handDigest() == handSha256) { "sha256 do hand_landmarker não confere: ${handDigest()}" }
        }
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
        versionName = "0.32.0"
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

    sourceSets { getByName("main") { assets.srcDir(ocrAssets) } }

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

tasks.named("preBuild") { dependsOn(fetchNcnn, fetchOcrModels) }

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
    // Exportação local opcional; sem cliente de rede e sem alterar o arquivo de captura.
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    // Segmentação do Retrato (pessoa, cabelo, pele, roupa, acessórios): MediaPipe Tasks (Apache-2.0),
    // modelo selfie_multiclass_256x256 (Apache-2.0) embutido em assets; roda offline no aparelho.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    // Leitura de QR e códigos de barras na prévia (Apache-2.0, POM oficial).
    implementation("io.github.zxing-cpp:android:3.0.2")
    testImplementation("junit:junit:4.13.2")
    // Renderiza a interface real em JVM (sem aparelho) para teste de fumaça e capturas de layout.
    testImplementation("org.robolectric:robolectric:4.17")
}




// Falhas gráficas precisam mostrar a asserção e sua geometria no log do Actions.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    testLogging.showStandardStreams = true
}
