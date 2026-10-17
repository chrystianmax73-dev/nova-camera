#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <csetjmp>
#include <vector>
#include <algorithm>
#include <cstring>
#include <jpeglib.h>

struct Error { jpeg_error_mgr base; jmp_buf jump; };
static void errorExit(j_common_ptr c) { longjmp(reinterpret_cast<Error*>(c->err)->jump,1); }
struct Stream {
    jpeg_decompress_struct dec{}; jpeg_compress_struct enc{};
    Error de{}, en{}; FILE *in=nullptr,*out=nullptr;
    bool hasDec=false,hasEnc=false; int pending=0;
    std::vector<unsigned char> row;
    std::vector<jint> argbRow;
};
static void dispose(Stream* s) {
    if(!s)return;
    if(s->hasDec)jpeg_destroy_decompress(&s->dec);
    if(s->hasEnc)jpeg_destroy_compress(&s->enc);
    if(s->in)fclose(s->in);if(s->out)fclose(s->out);delete s;
}
extern "C" JNIEXPORT jlong JNICALL Java_com_novacamera_s24_processing_JpegBands_open(JNIEnv* e,jobject,jstring input,jstring output) {
    auto* s=new Stream();
    s->dec.err=jpeg_std_error(&s->de.base);s->de.base.error_exit=errorExit;
    s->enc.err=jpeg_std_error(&s->en.base);s->en.base.error_exit=errorExit;
    if(setjmp(s->de.jump)||setjmp(s->en.jump)){dispose(s);return 0;}
    const char* a=e->GetStringUTFChars(input,nullptr);s->in=fopen(a,"rb");e->ReleaseStringUTFChars(input,a);
    const char* b=e->GetStringUTFChars(output,nullptr);s->out=fopen(b,"wb");e->ReleaseStringUTFChars(output,b);
    if(!s->in||!s->out){dispose(s);return 0;}
    jpeg_create_decompress(&s->dec);s->hasDec=true;jpeg_stdio_src(&s->dec,s->in);jpeg_save_markers(&s->dec,JPEG_APP0+2,0xffff);jpeg_read_header(&s->dec,TRUE);
    // Progressivo exige coeficientes da imagem inteira; preservar JPEG original no fallback.
    if(s->dec.progressive_mode||static_cast<uint64_t>(s->dec.image_width)*s->dec.image_height>240000000ULL){dispose(s);return 0;}
    s->dec.out_color_space=JCS_RGB;jpeg_start_decompress(&s->dec);
    jpeg_create_compress(&s->enc);s->hasEnc=true;jpeg_stdio_dest(&s->enc,s->out);
    s->enc.image_width=s->dec.output_width;s->enc.image_height=s->dec.output_height;
    s->enc.input_components=3;s->enc.in_color_space=JCS_RGB;
    jpeg_set_defaults(&s->enc);jpeg_set_quality(&s->enc,95,TRUE);jpeg_start_compress(&s->enc,TRUE);
    for(auto* m=s->dec.marker_list;m;m=m->next)
        if(m->marker==JPEG_APP0+2 && m->data_length>=12 && std::memcmp(m->data,"ICC_PROFILE",12)==0)
            jpeg_write_marker(&s->enc,m->marker,m->data,m->data_length);
    s->row.resize(s->dec.output_width*3);s->argbRow.resize(s->dec.output_width);
    return reinterpret_cast<jlong>(s);
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_novacamera_s24_processing_JpegBands_dimensions(JNIEnv* e,jobject,jlong handle) {
    auto* s=reinterpret_cast<Stream*>(handle);if(!s)return nullptr;
    jint dims[2]={static_cast<jint>(s->dec.output_width),static_cast<jint>(s->dec.output_height)};
    auto a=e->NewIntArray(2);e->SetIntArrayRegion(a,0,2,dims);return a;
}
extern "C" JNIEXPORT jint JNICALL Java_com_novacamera_s24_processing_JpegBands_read(JNIEnv* e,jobject,jlong handle,jintArray target,jint count) {
    auto* s=reinterpret_cast<Stream*>(handle);if(!s||s->pending||count<=0)return -1;
    const auto w=s->dec.output_width;
    const int rows=static_cast<int>(std::min<JDIMENSION>(count,s->dec.output_height-s->dec.output_scanline));
    if(e->GetArrayLength(target)<static_cast<jint>(w*rows))return -1;
    if(setjmp(s->de.jump))return -1;
    // Não manter array Java preso durante erro libjpeg / longjmp.
    auto* converted=s->argbRow.data();
    for(int y=0;y<rows;y++) {
        JSAMPROW row=s->row.data();if(jpeg_read_scanlines(&s->dec,&row,1)!=1)return -1;
        for(unsigned x=0;x<w;x++)converted[x]=static_cast<jint>(0xff000000U|(row[x*3]<<16)|(row[x*3+1]<<8)|row[x*3+2]);
        e->SetIntArrayRegion(target,y*w,w,converted);if(e->ExceptionCheck())return -1;
    }
    s->pending=rows;return rows;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_novacamera_s24_processing_JpegBands_write(JNIEnv* e,jobject,jlong handle,jintArray source,jint rows) {
    auto* s=reinterpret_cast<Stream*>(handle);if(!s||rows!=s->pending)return JNI_FALSE;
    const auto w=s->dec.output_width;
    if(e->GetArrayLength(source)<static_cast<jint>(w*rows))return JNI_FALSE;
    if(setjmp(s->en.jump))return JNI_FALSE;
    auto* converted=s->argbRow.data();
    for(int y=0;y<rows;y++) {
        e->GetIntArrayRegion(source,y*w,w,converted);if(e->ExceptionCheck())return JNI_FALSE;
        for(unsigned x=0;x<w;x++){s->row[x*3]=(converted[x]>>16)&255;s->row[x*3+1]=(converted[x]>>8)&255;s->row[x*3+2]=converted[x]&255;}
        JSAMPROW row=s->row.data();if(jpeg_write_scanlines(&s->enc,&row,1)!=1)return JNI_FALSE;
    }
    s->pending=0;return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_novacamera_s24_processing_JpegBands_close(JNIEnv*,jobject,jlong handle,jboolean complete) {
    auto* s=reinterpret_cast<Stream*>(handle);if(!s)return JNI_FALSE;
    bool ok=false;
    if(complete && !s->pending && s->dec.output_scanline==s->dec.output_height && s->enc.next_scanline==s->enc.image_height) {
        if(!setjmp(s->de.jump)&&!setjmp(s->en.jump)){jpeg_finish_decompress(&s->dec);jpeg_finish_compress(&s->enc);ok=true;}
    }
    dispose(s);return ok ? JNI_TRUE : JNI_FALSE;
}
