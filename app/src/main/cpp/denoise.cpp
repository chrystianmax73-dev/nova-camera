// Redução de ruído por IA (NAFNet convertido para ncnn). Roda só no aparelho; usa a GPU via
// Vulkan quando disponível e cai para a CPU caso contrário. Recebe um bloco ARGB e devolve outro
// do mesmo tamanho: rede de regressão sem GAN; fidelidade e textura exigem validação real.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include "net.h"
#include "allocator.h"
#include "gpu.h"

#define TAG "NovaDenoise"

namespace {
struct Model {
    // Pools sobrevivem aos Extractors e são reutilizados entre blocos/fotos.
    ncnn::PoolAllocator blob;
    ncnn::PoolAllocator workspace;
    ncnn::Net net;
    bool gpu = false;
    const ncnn::VulkanDevice* device = nullptr;
    ncnn::VkAllocator* vkBlob = nullptr;
    ncnn::VkAllocator* vkStaging = nullptr;
    ~Model() {
        net.clear();
        if (device) {
            if (vkBlob) device->reclaim_blob_allocator(vkBlob);
            if (vkStaging) device->reclaim_staging_allocator(vkStaging);
        }
    }
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
    // CPU: limitar concorrência com preview/segmentação. Velocidade deve ser medida no aparelho.
    m->net.opt.num_threads = 2;
    if (m->gpu) {
        m->device = ncnn::get_gpu_device(0);
        m->net.set_vulkan_device(m->device);
        m->vkBlob = m->device->acquire_blob_allocator();
        m->vkStaging = m->device->acquire_staging_allocator();
    }
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
    ex.set_blob_allocator(&m->blob);
    ex.set_workspace_allocator(&m->workspace);
    if (m->gpu) {
        ex.set_blob_vkallocator(m->vkBlob);
        ex.set_workspace_vkallocator(m->vkBlob);
        ex.set_staging_vkallocator(m->vkStaging);
    }
    if (ex.input("in0", input) != 0) return JNI_FALSE;
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

