// Detector de objetos (PP-PicoDet, PaddleDetection, Apache-2.0) no ncnn, na CPU: uma inferência por toque.
// Devolve as 8 saídas cruas (notas por classe e distribuições de caixa); a decodificação fica no Kotlin.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <vector>

#include "net.h"
#include "cpu.h"

#define TAG "NovaDetect"

extern "C" JNIEXPORT jlong JNICALL
Java_com_novacamera_s24_processing_ObjectDetector_nativeLoad(JNIEnv* env, jclass, jobject assets, jstring param, jstring bin) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assets);
    auto* net = new ncnn::Net();
    net->opt.use_vulkan_compute = false;
    net->opt.num_threads = 2;
    ncnn::set_kmp_blocktime(0);
    const char* p = env->GetStringUTFChars(param, nullptr);
    const char* b = env->GetStringUTFChars(bin, nullptr);
    int r = net->load_param(mgr, p);
    if (r == 0) r = net->load_model(mgr, b);
    env->ReleaseStringUTFChars(param, p);
    env->ReleaseStringUTFChars(bin, b);
    if (r != 0) { __android_log_print(ANDROID_LOG_ERROR, TAG, "falha ao carregar o detector"); delete net; return 0; }
    return reinterpret_cast<jlong>(net);
}

/** [in] já no tamanho da rede (ARGB). Saída: [n, (h, w) × n, dados de cada saída em sequência]. */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_novacamera_s24_processing_ObjectDetector_nativeRun(JNIEnv* env, jclass, jlong handle, jobject in) {
    if (handle == 0) return nullptr;
    auto* net = reinterpret_cast<ncnn::Net*>(handle);
    ncnn::set_kmp_blocktime(0);
    ncnn::Mat input = ncnn::Mat::from_android_bitmap(env, in, ncnn::Mat::PIXEL_BGR);
    if (input.empty()) return nullptr;
    const float mean[3] = {103.53f, 116.28f, 123.675f};
    const float norm[3] = {1 / 57.375f, 1 / 57.12f, 1 / 58.395f};
    input.substract_mean_normalize(mean, norm);
    ncnn::Extractor ex = net->create_extractor();
    if (ex.input("in0", input) != 0) return nullptr;
    std::vector<ncnn::Mat> outs;
    for (int k = 0; k < 8; k++) {
        char name[8];
        snprintf(name, sizeof(name), "out%d", k);
        ncnn::Mat o;
        if (ex.extract(name, o) != 0) return nullptr;
        outs.push_back(o);
    }
    size_t total = 1 + 2 * outs.size();
    for (auto& o : outs) total += (size_t) o.w * o.h;
    jfloatArray result = env->NewFloatArray((jsize) total);
    if (result == nullptr) return nullptr;
    std::vector<float> header;
    header.push_back((float) outs.size());
    for (auto& o : outs) { header.push_back((float) o.h); header.push_back((float) o.w); }
    env->SetFloatArrayRegion(result, 0, (jsize) header.size(), header.data());
    jsize at = (jsize) header.size();
    for (auto& o : outs) {
        for (int y = 0; y < o.h; y++) env->SetFloatArrayRegion(result, at + y * o.w, o.w, o.row(y));
        at += o.w * o.h;
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_novacamera_s24_processing_ObjectDetector_nativeRelease(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<ncnn::Net*>(handle);
}
