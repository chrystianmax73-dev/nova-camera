// Redução de ruído por IA (NAFNet convertido para ncnn). Roda só no aparelho; usa a GPU via
// Vulkan quando disponível e cai para a CPU caso contrário. Recebe um bloco ARGB e devolve outro
// do mesmo tamanho: a rede é de regressão (sem GAN), não inventa textura.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include "net.h"
#include "gpu.h"

#define TAG "NovaDenoise"

namespace {
struct Model {
    ncnn::Net net;
    bool gpu = false;
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_novacamera_s24_processing_NcnnDenoiser_nativeLoad(JNIEnv* env, jclass, jobject assets,
                                                          jstring param, jstring bin, jboolean useGpu) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    auto* m = new Model();
    m->gpu = useGpu && ncnn::get_gpu_count() > 0;
    m->net.opt.use_vulkan_compute = m->gpu;
    m->net.opt.use_fp16_packed = true;
    m->net.opt.use_fp16_storage = true;
    m->net.opt.use_fp16_arithmetic = false;  // fp16 só no armazenamento: evita perda de precisão em sombras
    m->net.opt.num_threads = 4;
    const char* p = env->GetStringUTFChars(param, nullptr);
    const char* b = env->GetStringUTFChars(bin, nullptr);
    int r1 = m->net.load_param(mgr, p);
    int r2 = m->net.load_model(mgr, b);
    env->ReleaseStringUTFChars(param, p);
    env->ReleaseStringUTFChars(bin, b);
    if (r1 != 0 || r2 != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "falha ao carregar modelo (%d, %d)", r1, r2);
        delete m;
        return 0;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "modelo carregado, gpu=%d", m->gpu);
    return reinterpret_cast<jlong>(m);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_novacamera_s24_processing_NcnnDenoiser_nativeUsesGpu(JNIEnv*, jclass, jlong handle) {
    return handle != 0 && reinterpret_cast<Model*>(handle)->gpu;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_novacamera_s24_processing_NcnnDenoiser_nativeRun(JNIEnv* env, jclass, jlong handle, jobject in, jobject out) {
    if (handle == 0) return JNI_FALSE;
    auto* m = reinterpret_cast<Model*>(handle);
    const float norm[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    const float denorm[3] = {255.f, 255.f, 255.f};
    ncnn::Mat input = ncnn::Mat::from_android_bitmap(env, in, ncnn::Mat::PIXEL_RGB);
    if (input.empty()) return JNI_FALSE;
    input.substract_mean_normalize(nullptr, norm);
    ncnn::Extractor ex = m->net.create_extractor();
    ex.input("in0", input);
    ncnn::Mat output;
    if (ex.extract("out0", output) != 0) return JNI_FALSE;
    output.substract_mean_normalize(nullptr, denorm);
    output.to_android_bitmap(env, out, ncnn::Mat::PIXEL_RGB);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_novacamera_s24_processing_NcnnDenoiser_nativeRelease(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Model*>(handle);
}
