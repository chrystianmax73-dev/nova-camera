// Profundidade relativa para o Retrato (Depth Anything V2 Small, Apache-2.0, convertido para ncnn).
// Uma inferência por foto, numa imagem reduzida e quadrada; usa a GPU via Vulkan quando disponível.
// Saída: profundidade inversa relativa (maior = mais perto), sem escala métrica.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include "net.h"
#include "gpu.h"

#define TAG "NovaDepth"

namespace {
struct DepthModel {
    ncnn::Net net;
    bool gpu = false;
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_novacamera_s24_processing_DepthEstimator_nativeLoad(JNIEnv* env, jclass, jobject assets,
                                                            jstring param, jstring bin, jboolean useGpu) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    auto* m = new DepthModel();
    m->gpu = useGpu && ncnn::get_gpu_count() > 0;
    m->net.opt.use_vulkan_compute = m->gpu;
    m->net.opt.use_fp16_packed = true;
    m->net.opt.use_fp16_storage = true;
    m->net.opt.use_fp16_arithmetic = false;  // LayerNorm/softmax do ViT: aritmética em fp32
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
    return reinterpret_cast<jlong>(m);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_novacamera_s24_processing_DepthEstimator_nativeUsesGpu(JNIEnv*, jclass, jlong handle) {
    return handle != 0 && reinterpret_cast<DepthModel*>(handle)->gpu;
}

/** [in] é ARGB quadrado (lado múltiplo de 14); [out] recebe lado² floats. */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_novacamera_s24_processing_DepthEstimator_nativeRun(JNIEnv* env, jclass, jlong handle, jobject in, jfloatArray out) {
    if (handle == 0) return JNI_FALSE;
    auto* m = reinterpret_cast<DepthModel*>(handle);
    // Normalização ImageNet sobre RGB 0..255.
    const float mean[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    const float norm[3] = {1 / (0.229f * 255.f), 1 / (0.224f * 255.f), 1 / (0.225f * 255.f)};
    ncnn::Mat input = ncnn::Mat::from_android_bitmap(env, in, ncnn::Mat::PIXEL_RGB);
    if (input.empty()) return JNI_FALSE;
    input.substract_mean_normalize(mean, norm);
    ncnn::Extractor ex = m->net.create_extractor();
    if (ex.input("in0", input) != 0) return JNI_FALSE;
    ncnn::Mat output;
    if (ex.extract("out0", output) != 0) return JNI_FALSE;
    const int n = output.w * output.h;
    if (env->GetArrayLength(out) != n) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "saída %dx%d inesperada", output.w, output.h);
        return JNI_FALSE;
    }
    env->SetFloatArrayRegion(out, 0, n, static_cast<const float*>(output.channel(0)));
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_novacamera_s24_processing_DepthEstimator_nativeRelease(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<DepthModel*>(handle);
}
