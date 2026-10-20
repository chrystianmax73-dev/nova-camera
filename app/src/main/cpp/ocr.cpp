// Leitura de texto (PP-OCRv5 mobile: detector DB + reconhecedor CTC; pesos PaddleOCR, Apache-2.0;
// conversão ncnn e pré-processamento conforme o exemplo oficial do ncnn, BSD-3-Clause). Sob demanda.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include "net.h"
#include "gpu.h"
#include "cpu.h"

#define TAG "NovaOcr"

namespace {
struct OcrModel {
    ncnn::Net det;
    ncnn::Net rec;
    bool gpu = false;
};

int load(ncnn::Net& net, AAssetManager* mgr, JNIEnv* env, jstring param, jstring bin) {
    const char* p = env->GetStringUTFChars(param, nullptr);
    const char* b = env->GetStringUTFChars(bin, nullptr);
    int r = net.load_param(mgr, p);
    if (r == 0) r = net.load_model(mgr, b);
    env->ReleaseStringUTFChars(param, p);
    env->ReleaseStringUTFChars(bin, b);
    return r;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_novacamera_s24_processing_TextReader_nativeLoad(JNIEnv* env, jclass, jobject assets, jstring detParam, jstring detBin,
                                                         jstring recParam, jstring recBin, jboolean useGpu) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    auto* m = new OcrModel();
    m->gpu = useGpu && ncnn::get_gpu_count() > 0;
    for (ncnn::Net* n : {&m->det, &m->rec}) {
        n->opt.use_vulkan_compute = m->gpu;
        n->opt.num_threads = 4;
    }
    ncnn::set_kmp_blocktime(0);
    if (load(m->det, mgr, env, detParam, detBin) != 0 || load(m->rec, mgr, env, recParam, recBin) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "falha ao carregar os modelos de texto");
        delete m;
        return 0;
    }
    return reinterpret_cast<jlong>(m);
}

/** [in]: imagem já reduzida e com borda até múltiplos de 32; [out]: probabilidade de texto por pixel (w*h). */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_novacamera_s24_processing_TextReader_nativeDetect(JNIEnv* env, jclass, jlong handle, jobject in, jfloatArray out) {
    if (handle == 0) return JNI_FALSE;
    auto* m = reinterpret_cast<OcrModel*>(handle);
    ncnn::set_kmp_blocktime(0);
    ncnn::Mat input = ncnn::Mat::from_android_bitmap(env, in, ncnn::Mat::PIXEL_BGR);
    if (input.empty()) return JNI_FALSE;
    const float mean[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    const float norm[3] = {1 / 0.229f / 255.f, 1 / 0.224f / 255.f, 1 / 0.225f / 255.f};
    input.substract_mean_normalize(mean, norm);
    ncnn::Extractor ex = m->det.create_extractor();
    if (ex.input("in0", input) != 0) return JNI_FALSE;
    ncnn::Mat output;
    if (ex.extract("out0", output) != 0) return JNI_FALSE;
    const int n = output.w * output.h;
    if (env->GetArrayLength(out) != n) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "mapa %dx%d inesperado", output.w, output.h);
        return JNI_FALSE;
    }
    env->SetFloatArrayRegion(out, 0, n, static_cast<const float*>(output.channel(0)));
    return JNI_TRUE;
}

/**
 * [in]: recorte de uma linha com 48 px de altura. Devolve, por passo de tempo, (classe de maior
 * probabilidade, probabilidade) — 2 floats por passo; a decodificação CTC fica no Kotlin.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_novacamera_s24_processing_TextReader_nativeRecognize(JNIEnv* env, jclass, jlong handle, jobject in) {
    if (handle == 0) return nullptr;
    auto* m = reinterpret_cast<OcrModel*>(handle);
    ncnn::set_kmp_blocktime(0);
    ncnn::Mat input = ncnn::Mat::from_android_bitmap(env, in, ncnn::Mat::PIXEL_BGR);
    if (input.empty()) return nullptr;
    const float mean[3] = {127.5f, 127.5f, 127.5f};
    const float norm[3] = {1 / 127.5f, 1 / 127.5f, 1 / 127.5f};
    input.substract_mean_normalize(mean, norm);
    ncnn::Extractor ex = m->rec.create_extractor();
    if (ex.input("in0", input) != 0) return nullptr;
    ncnn::Mat output;
    if (ex.extract("out0", output) != 0) return nullptr;
    const int steps = output.h;
    jfloatArray result = env->NewFloatArray(steps * 2);
    if (result == nullptr) return nullptr;
    float* buf = new float[steps * 2];
    for (int i = 0; i < steps; i++) {
        const float* p = output.row(i);
        int best = 0;
        float score = -1e30f;
        for (int j = 0; j < output.w; j++) if (p[j] > score) { score = p[j]; best = j; }
        buf[i * 2] = static_cast<float>(best);
        buf[i * 2 + 1] = score;
    }
    env->SetFloatArrayRegion(result, 0, steps * 2, buf);
    delete[] buf;
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_novacamera_s24_processing_TextReader_nativeUsesGpu(JNIEnv*, jclass, jlong handle) {
    return handle != 0 && reinterpret_cast<OcrModel*>(handle)->gpu;
}

extern "C" JNIEXPORT void JNICALL
Java_com_novacamera_s24_processing_TextReader_nativeRelease(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<OcrModel*>(handle);
}
