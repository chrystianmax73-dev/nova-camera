// Núcleos próprios equivalentes a Align.search/SuperRes.mergeTile. Nenhuma amostra descartada.
#include <jni.h>
#include <vector>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
static int rnd(float x){return static_cast<int>(std::floor(x+0.5f));}
static int clamp(int x,int a,int b){return std::max(a,std::min(b,x));}
static float luma(jint c){return (.2126f*((c>>16)&255)+.7152f*((c>>8)&255)+.0722f*(c&255))/255.f;}
static jint argb(int r,int g,int b){return static_cast<jint>(0xff000000U|(clamp(r,0,255)<<16)|(clamp(g,0,255)<<8)|clamp(b,0,255));}
extern "C" JNIEXPORT jintArray JNICALL Java_com_novacamera_s24_core_imaging_PhotoKernels_search(JNIEnv* e,jclass,jfloatArray ra,jfloatArray ma,jint w,jint h,jint radius,jint px,jint py,jint x0,jint y0,jint x1,jint y1,jint step){
    if(w<=0||h<=0||step<=0||radius<0||e->GetArrayLength(ra)!=w*h||e->GetArrayLength(ma)!=w*h)return nullptr;
    try {
    int rw=x1-x0,rh=y1-y0;
    int mx0=std::max(0,x0+px-radius),my0=std::max(0,y0+py-radius);
    int mx1=std::min(w,x1+px+radius),my1=std::min(h,y1+py+radius),mw=mx1-mx0,mh=my1-my0;
    if(x0<0||y0<0||x1>w||y1>h||rw<=0||rh<=0||mw<=0||mh<=0)return nullptr;
    // Copiar só as regiões usadas: copiar dois planos inteiros por célula custaria gigabytes.
    std::vector<jfloat> r(rw*rh),m(mw*mh);
    for(int y=0;y<rh;y++)e->GetFloatArrayRegion(ra,(y0+y)*w+x0,rw,r.data()+y*rw);
    for(int y=0;y<mh;y++)e->GetFloatArrayRegion(ma,(my0+y)*w+mx0,mw,m.data()+y*mw);
    if(e->ExceptionCheck())return nullptr;
    int bx=px,by=py;double best=std::numeric_limits<double>::max();
    for(int dy=py-radius;dy<=py+radius;dy++)for(int dx=px-radius;dx<=px+radius;dx++){
        int xs=std::max(x0,-dx),xe=std::min(x1,w-dx),ys=std::max(y0,-dy),ye=std::min(y1,h-dy);
        double cost=0;int n=0;
        for(int y=ys;y<ye;y+=step)for(int x=xs;x<xe;x+=step){cost+=std::abs(r[(y-y0)*rw+x-x0]-m[(y+dy-my0)*mw+x+dx-mx0]);n++;}
        if(n<16)continue;
        double c=cost/n+1e-5*(std::abs(dx-px)+std::abs(dy-py));
        if(c<best){best=c;bx=dx;by=dy;}
    }
    jint xy[2]={bx,by};auto a=e->NewIntArray(2);if(a)e->SetIntArrayRegion(a,0,2,xy);return a;
    } catch(const std::bad_alloc&) {auto c=e->FindClass("java/lang/OutOfMemoryError");if(c)e->ThrowNew(c,"Memória do alinhamento nativo");return nullptr;}
}
struct Region{std::vector<jint> px;int x,y,w,h;};
static jint bilinear(const Region& a,float qx,float qy){
    float x=qx-a.x,y=qy-a.y;int x0=clamp(static_cast<int>(std::floor(x)),0,a.w-2),y0=clamp(static_cast<int>(std::floor(y)),0,a.h-2);
    float tx=std::max(0.f,std::min(1.f,x-x0)),ty=std::max(0.f,std::min(1.f,y-y0));
    auto c=[&](int sh){float v0=(a.px[y0*a.w+x0]>>sh)&255,v1=(a.px[y0*a.w+x0+1]>>sh)&255;
        float v2=(a.px[(y0+1)*a.w+x0]>>sh)&255,v3=(a.px[(y0+1)*a.w+x0+1]>>sh)&255;
        float top=v0+(v1-v0)*tx,bot=v2+(v3-v2)*tx;return static_cast<int>(top+(bot-top)*ty+.5f);};
    return argb(c(16),c(8),c(0));
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_novacamera_s24_core_imaging_PhotoKernels_merge(JNIEnv* e,jclass,jobjectArray arrays,jintArray ga,jfloatArray sa,jint ow,jint oh,jint ox,jint oy,jfloat factor,jfloat cropX,jfloat cropY,jfloat noise,jfloatArray ka){
    int count=e->GetArrayLength(arrays);
    if(count<1||count>32||ow<=0||oh<=0||ow>2048||oh>2048||!std::isfinite(factor)||factor<=0||e->GetArrayLength(ga)!=count*4||e->GetArrayLength(sa)!=count*2||e->GetArrayLength(ka)!=1024)return nullptr;
    try {
        std::vector<jint> geo(count*4);std::vector<jfloat> shifts(count*2),kernel(1024);
        e->GetIntArrayRegion(ga,0,count*4,geo.data());e->GetFloatArrayRegion(sa,0,count*2,shifts.data());e->GetFloatArrayRegion(ka,0,1024,kernel.data());if(e->ExceptionCheck())return nullptr;
        std::vector<Region> frames;frames.reserve(count);
        for(int k=0;k<count;k++){
            auto a=static_cast<jintArray>(e->GetObjectArrayElement(arrays,k));int w=geo[k*4+2],h=geo[k*4+3];
            if(!a||w<3||h<3||e->GetArrayLength(a)!=w*h){if(a)e->DeleteLocalRef(a);return nullptr;}
            Region r{{},geo[k*4],geo[k*4+1],w,h};r.px.resize(w*h);e->GetIntArrayRegion(a,0,w*h,r.px.data());e->DeleteLocalRef(a);if(e->ExceptionCheck())return nullptr;frames.push_back(std::move(r));
        }
        int n=ow*oh;std::vector<float> ar(n),ag(n),ab(n),aw(n);
        float sigma=.28f*factor;int rad=std::max(1,static_cast<int>(2.f*sigma+.5f));
        float maxD2=2.f*(rad+1)*(rad+1),scale=1023.f/maxD2,tol=.04f+3.f*noise;
        const auto& ref=frames[0];
        // A faixa local é a mesma 3x3; cache elimina nove leituras repetidas por amostra.
        std::vector<float> lo(ref.w*ref.h,1.f),hi(ref.w*ref.h,0.f);
        for(int y=1;y<ref.h-1;y++)for(int x=1;x<ref.w-1;x++)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            float v=luma(ref.px[(y+dy)*ref.w+x+dx]);int i=y*ref.w+x;lo[i]=std::min(lo[i],v);hi[i]=std::max(hi[i],v);
        }
        for(int k=0;k<count;k++){
            const auto& r=frames[k];
            for(int j=0;j<r.h;j++)for(int i=0;i<r.w;i++){
                float qx=r.x+i-shifts[k*2],qy=r.y+j-shifts[k*2+1];
                float ux=(qx-cropX+.5f)*factor-.5f-ox,uy=(qy-cropY+.5f)*factor-.5f-oy;
                if(ux < -rad||uy < -rad||ux > ow+rad||uy > oh+rad)continue;
                jint c=r.px[j*r.w+i];float robust=1;
                if(k>0){int ix=clamp(rnd(qx-ref.x),1,ref.w-2),iy=clamp(rnd(qy-ref.y),1,ref.h-2),z=iy*ref.w+ix;
                    float v=luma(c),d=std::max(0.f,std::max(lo[z]-v,v-hi[z]));if(d>tol)robust=std::max(0.f,std::min(1.f,1.f-(d-tol)/tol));if(robust<=0)continue;}
                float cr=(c>>16)&255,cg=(c>>8)&255,cb=c&255;int cx=rnd(ux),cy=rnd(uy);
                for(int y=std::max(0,cy-rad);y<=std::min(oh-1,cy+rad);y++){
                    float dy=y-uy;
                    for(int x=std::max(0,cx-rad);x<=std::min(ow-1,cx+rad);x++){
                        float dx=x-ux,d2=dx*dx+dy*dy;if(d2>=maxD2)continue;float weight=kernel[static_cast<int>(d2*scale)]*robust;if(weight<1e-3f)continue;
                        int o=y*ow+x;ar[o]+=weight*cr;ag[o]+=weight*cg;ab[o]+=weight*cb;aw[o]+=weight;
                    }
                }
            }
        }
        std::vector<jint> out(n);
        for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){
            int o=y*ow+x;float weight=aw[o];
            out[o]=weight>=.05f?argb(static_cast<int>(ar[o]/weight+.5f),static_cast<int>(ag[o]/weight+.5f),static_cast<int>(ab[o]/weight+.5f)):
                bilinear(ref,cropX+(ox+x+.5f)/factor-.5f,cropY+(oy+y+.5f)/factor-.5f);
        }
        auto a=e->NewIntArray(n);if(a)e->SetIntArrayRegion(a,0,n,out.data());return a;
    } catch(const std::bad_alloc&) { auto c=e->FindClass("java/lang/OutOfMemoryError");if(c)e->ThrowNew(c,"Memória do bloco SR nativo");return nullptr; }
}

extern "C" JNIEXPORT jint JNICALL Java_com_novacamera_s24_core_imaging_PhotoKernels_detail(JNIEnv* e,jclass,jintArray input,jint w,jint h,jfloat amount,jfloat sigma,jint top,jint bottom){
    if(w<3||h<3||amount<=0||e->GetArrayLength(input)!=w*h)return 0;
    try {
        int n=w*h;std::vector<jint> px(n);e->GetIntArrayRegion(input,0,n,px.data());if(e->ExceptionCheck())return 0;
        std::vector<int> lum(n),tmp(n),blur(n);
        for(int i=0;i<n;i++)lum[i]=(((px[i]>>16)&255)*77+((px[i]>>8)&255)*150+(px[i]&255)*29)>>8;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {int o=y*w;
            tmp[o+x]=lum[o+std::max(0,x-2)]+4*lum[o+std::max(0,x-1)]+6*lum[o+x]+4*lum[o+std::min(w-1,x+1)]+lum[o+std::min(w-1,x+2)];}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)blur[y*w+x]=(tmp[std::max(0,y-2)*w+x]+4*tmp[std::max(0,y-1)*w+x]+6*tmp[y*w+x]+4*tmp[std::min(h-1,y+1)*w+x]+tmp[std::min(h-1,y+2)*w+x]+128)>>8;
        float lo=2.f*sigma,hi=4.f*sigma;int changed=0;
        for(int y=std::max(1,top);y<std::min(h-1,bottom);y++)for(int x=1;x<w-1;x++){
            int i=y*w+x;float d=lum[i]-blur[i],ad=std::abs(d);if(ad<=lo)continue;
            float t=(ad-lo)/(hi-lo),gate=ad>=hi?1.f:t*t*(3.f-2.f*t);
            int mn=255,mx=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int v=lum[i+dy*w+dx];mn=std::min(mn,v);mx=std::max(mx,v);}
            jint c=px[i];float r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            float cb=128.f-.168736f*r-.331264f*g+.5f*b,cr=128.f+.5f*r-.418688f*g-.081312f*b;
            float skin=(cb>=77.f&&cb<=127.f&&cr>=133.f&&cr<=173.f)?.3f:1.f;
            float target=std::max(static_cast<float>(mn),std::min(static_cast<float>(mx),lum[i]+amount*gate*skin*d));
            int delta=static_cast<int>(target-lum[i]);if(delta==0)continue;
            px[i]=argb(static_cast<int>(r)+delta,static_cast<int>(g)+delta,static_cast<int>(b)+delta);changed++;
        }
        e->SetIntArrayRegion(input,0,n,px.data());return changed;
    } catch(const std::bad_alloc&) {auto c=e->FindClass("java/lang/OutOfMemoryError");if(c)e->ThrowNew(c,"Memória do detalhe nativo");return 0;}
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_novacamera_s24_core_imaging_PhotoKernels_smooth(JNIEnv* e,jclass,jintArray input,jint w,jint h){
    if(w<=0||h<=0||e->GetArrayLength(input)!=w*h)return nullptr;
    try {int n=w*h;std::vector<jint> px(n);std::vector<float> lum(n),out(n);e->GetIntArrayRegion(input,0,n,px.data());if(e->ExceptionCheck())return nullptr;
        for(int i=0;i<n;i++)lum[i]=luma(px[i]);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){float s=0;int count=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx>=0&&xx<w&&yy>=0&&yy<h){s+=lum[yy*w+xx];count++;}}
            out[y*w+x]=s/count;
        }
        auto a=e->NewFloatArray(n);if(a)e->SetFloatArrayRegion(a,0,n,out.data());return a;
    }catch(const std::bad_alloc&){auto c=e->FindClass("java/lang/OutOfMemoryError");if(c)e->ThrowNew(c,"Memória do deslocamento SR");return nullptr;}
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_novacamera_s24_core_imaging_PhotoKernels_estimate(JNIEnv* e,jclass,jfloatArray ra,jfloatArray ma,jintArray ga,jint gx,jint gy,jint radius){
    if(e->GetArrayLength(ga)!=8||radius<0)return nullptr;
    jint g[8];e->GetIntArrayRegion(ga,0,8,g);int rw=g[2],rh=g[3],mw=g[6],mh=g[7];
    if(rw<=0||rh<=0||mw<=0||mh<=0||e->GetArrayLength(ra)!=rw*rh||e->GetArrayLength(ma)!=mw*mh)return nullptr;
    try {
        std::vector<float> r(rw*rh),m(mw*mh);e->GetFloatArrayRegion(ra,0,rw*rh,r.data());e->GetFloatArrayRegion(ma,0,mw*mh,m.data());if(e->ExceptionCheck())return nullptr;
        auto ssd=[&](int dx,int dy){double err=0;int n=0;
            for(int y=0;y<rh;y++){int my=g[1]+y+dy-g[5];if(my<0||my>=mh)continue;
                for(int x=0;x<rw;x++){int mx=g[0]+x+dx-g[4];if(mx>=0&&mx<mw){float d=r[y*rw+x]-m[my*mw+mx];err+=d*d;n++;}}
            }return n==0?std::numeric_limits<double>::max():err/n;};
        int bx=gx,by=gy;double best=std::numeric_limits<double>::max();
        for(int dy=gy-radius;dy<=gy+radius;dy++)for(int dx=gx-radius;dx<=gx+radius;dx++){double cost=ssd(dx,dy);if(cost<best){best=cost;bx=dx;by=dy;}}
        auto vertex=[](double a,double b,double c){double den=a-2*b+c;return den<=1e-12?0.f:std::max(-.5f,std::min(.5f,static_cast<float>(.5*(a-c)/den)));};
        float xy[2]={bx+vertex(ssd(bx-1,by),best,ssd(bx+1,by)),by+vertex(ssd(bx,by-1),best,ssd(bx,by+1))};
        auto out=e->NewFloatArray(2);if(out)e->SetFloatArrayRegion(out,0,2,xy);return out;
    }catch(const std::bad_alloc&){auto c=e->FindClass("java/lang/OutOfMemoryError");if(c)e->ThrowNew(c,"Memória do refinamento SR");return nullptr;}
}

// ToneMap.applyTile: mesma amostragem bilinear, pesos e arredondamento. Sem LUT aproximada.
static float unit(float v){return std::max(0.f,std::min(1.f,v));}
static float toneSmooth(float a,float b,float v){float t=unit((v-a)/(b-a));return t*t*(3.f-2.f*t);}
struct FloatElements {
    JNIEnv* env; jfloatArray array; jfloat* values;
    FloatElements(JNIEnv* e,jfloatArray a):env(e),array(a),values(a?e->GetFloatArrayElements(a,nullptr):nullptr){}
    ~FloatElements(){if(values)env->ReleaseFloatArrayElements(array,values,JNI_ABORT);}
};
static float toneSample(const float* a,int w,int h,float x,float y){
    float fx=std::max(0.f,std::min(static_cast<float>(w-1),x)),fy=std::max(0.f,std::min(static_cast<float>(h-1),y));
    int x0=static_cast<int>(fx),y0=static_cast<int>(fy),x1=std::min(x0+1,w-1),y1=std::min(y0+1,h-1);
    float tx=fx-x0,ty=fy-y0;
    float top=a[y0*w+x0]+(a[y0*w+x1]-a[y0*w+x0])*tx;
    float bot=a[y1*w+x0]+(a[y1*w+x1]-a[y1*w+x0])*tx;
    return top+(bot-top)*ty;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_novacamera_s24_core_imaging_PhotoKernels_tone(JNIEnv* e,jclass,jintArray input,jint tw,jint th,jint x0,jint y0,jint fw,jint fh,jfloatArray base,jint bw,jint bh,jfloatArray mid,jfloatArray settings){
    if(tw<=0||th<=0||tw>32768||th>4096||static_cast<int64_t>(tw)*th>4194304||fw<=0||fh<=0||bw<=0||bh<=0||bw>4096||bh>4096||
        e->GetArrayLength(input)!=tw*th||e->GetArrayLength(base)!=bw*bh||e->GetArrayLength(settings)!=16||
        (mid&&e->GetArrayLength(mid)!=bw*bh))return JNI_FALSE;
    try {
        float p[16];e->GetFloatArrayRegion(settings,0,16,p);if(e->ExceptionCheck())return JNI_FALSE;
        for(float v:p)if(!std::isfinite(v))return JNI_FALSE;
        FloatElements b(e,base);if(!b.values)return JNI_FALSE;
        FloatElements m(e,mid);if(mid&&!m.values)return JNI_FALSE;
        // JNI Elements podem copiar os planos reduzidos. Nunca GetPrimitiveArrayCritical durante
        // o cálculo, nem ponteiro Java retido. A saída é copiada só após completar este bloco.
        std::vector<jint> px(tw*th);e->GetIntArrayRegion(input,0,tw*th,px.data());if(e->ExceptionCheck())return JNI_FALSE;
        float s=p[6],black=p[2]*s,bl=black*255.f,blScale=1.f/(1.f-black),lb=p[5]*s,lbScale=1.f/(1.f-lb);
        float sx=static_cast<float>(bw)/fw,sy=static_cast<float>(bh)/fh;
        for(int ty=0;ty<th;ty++){
            float by=(y0+ty+.5f)*sy-.5f;
            for(int tx=0;tx<tw;tx++){
                int i=ty*tw+tx;auto c=px[i];
                float r=std::max(0.f,(((c>>16)&255)-bl)*blScale),g=std::max(0.f,(((c>>8)&255)-bl)*blScale),v=std::max(0.f,((c&255)-bl)*blScale);
                if(lb>0){float y=.2126f*r+.7152f*g+.0722f*v;float k=y<=.5f?0.f:std::max(0.f,(y-lb*255.f)*lbScale)/y;r*=k;g*=k;v*=k;}
                float bx=(x0+tx+.5f)*sx-.5f,log=toneSample(b.values,bw,bh,bx,by);
                float shadow=toneSmooth(p[7],p[8],log)*toneSmooth(p[10],p[11],log),high=toneSmooth(p[9],0.f,log);
                float delta=p[0]*.6931f*shadow-p[1]*high*(log-p[9]);
                if(p[4]>0)delta+=p[4]*.6931f*toneSmooth(p[13],p[12],log)*toneSmooth(p[14],p[15],log);
                if(mid&&p[3]>0){float md=toneSample(m.values,bw,bh,bx,by);delta+=p[3]*std::max(-.35f,std::min(.35f,md-log));}
                // Kotlin exp(Float) calcula Math.exp(Double).toFloat(), não expf.
                float gain=static_cast<float>(std::exp(static_cast<double>(delta*s)));
                float peak=std::max(r,std::max(g,v));if(peak*gain>255.f)gain=255.f/std::max(peak,1.f);
                px[i]=argb(static_cast<int>(r*gain+.5f),static_cast<int>(g*gain+.5f),static_cast<int>(v*gain+.5f));
            }
        }
        e->SetIntArrayRegion(input,0,tw*th,px.data());return e->ExceptionCheck()?JNI_FALSE:JNI_TRUE;
    }catch(const std::bad_alloc&){auto c=e->FindClass("java/lang/OutOfMemoryError");if(c)e->ThrowNew(c,"Memória do bloco tonal nativo");return JNI_FALSE;}
}
