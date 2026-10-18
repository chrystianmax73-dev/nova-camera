package com.novacamera.s24.core.imaging
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Random
class NativePhotoKernelsTest {
    companion object {
        @JvmStatic @BeforeClass fun compileHost() {
            val source=listOf(File("src/main/cpp/photo_kernels.cpp"),File("app/src/main/cpp/photo_kernels.cpp")).first{it.exists()}
            val jdk=File(System.getProperty("java.home"));val output=Files.createTempDirectory("nova-kernels-").resolve("photo.so").toFile()
            val p=ProcessBuilder("g++","-std=c++17","-O3","-ffp-contract=off","-shared","-fPIC","-I${jdk}/include","-I${jdk}/include/linux",source.absolutePath,"-o",output.absolutePath).redirectErrorStream(true).start()
            val log=p.inputStream.bufferedReader().readText();assertEquals(log,0,p.waitFor());PhotoKernels.loadHost(output.absolutePath)
        }
    }
    private fun <T> backend(native:Boolean,block:()->T):T {
        PhotoKernels.useNativeForTest(native);try{return block()}finally{PhotoKernels.useNativeForTest(true)}
    }
    @Test fun deslocamentoSRMantemSuavizacaoEParabolaFracionaria() {
        val rng=Random(22);val a=IntArray(90*70){Px.argb(rng.nextInt(256),rng.nextInt(256),rng.nextInt(256))}
        val old=backend(false){SuperRes.Region(a,0,0,90,70).smooth}
        val got=backend(true){SuperRes.Region(a,0,0,90,70).smooth}
        assertArrayEquals(old,got,0f)
        val ref=SuperRes.Region(a,0,0,90,70);val mov=SuperRes.Region(a,1,-1,90,70)
        assertEquals(backend(false){SuperRes.estimateShift(ref,mov,0,0)},backend(true){SuperRes.estimateShift(ref,mov,0,0)})
    }
    @Test fun alinhamentoPreservaBuscaBordasERegioes() {
        val random=Random(10);val ref=Plane(128,96,FloatArray(128*96){random.nextFloat()})
        val mov=Plane(128,96,FloatArray(ref.data.size){i->ref.data[(i+3)%ref.data.size]})
        for(region in listOf<IntArray?>(null,intArrayOf(0,0,20,20),intArrayOf(87,65,128,96)))for(step in listOf(1,2)){
            assertEquals(backend(false){Align.search(ref,mov,5,Shift(1,-1),region,step)},backend(true){Align.search(ref,mov,5,Shift(1,-1),region,step)})
        }
        assertEquals(backend(false){Align.globalShift(ref,mov,12)},backend(true){Align.globalShift(ref,mov,12)})
    }
    @Test fun fusaoOitoQuadrosPreservaPixelsCorMovimentoBordasERuido() {
        val random=Random(12)
        val frames=List(8){k->SuperRes.Region(IntArray(88*72){i->val v=if(k==3&&i%7==0)0 else random.nextInt(256);Px.argb(v,(i*3+k*7)%256,(i*13+k)%256)},-4,-4,88,72) to ((k*.13f-.3f) to (k*.07f-.21f))}
        for(factor in listOf(1.5f,2f,2.7f)) {
            val old=backend(false){SuperRes.mergeTile(128,96,3,5,factor,1f,2f,frames,.013f)}
            val got=backend(true){SuperRes.mergeTile(128,96,3,5,factor,1f,2f,frames,.013f)}
            assertArrayEquals("Mesmo núcleo/robustez/arredondamento $factor",old,got)
        }
    }
    @Test fun detalhePreservaPeleHalosTexturaMargensEIntensidade() {
        val random=Random(4);val px=IntArray(160*120){Px.argb(random.nextInt(256),random.nextInt(256),random.nextInt(256))}
        for(amount in listOf(.3f,.4f,.6f)){
            val old=px.copyOf();val got=px.copyOf()
            assertEquals(backend(false){Detail.applyBand(old,160,120,amount,1.5f,3,117)},backend(true){Detail.applyBand(got,160,120,amount,1.5f,3,117)})
            assertArrayEquals(old,got)
        }
    }
    @Test fun benchmarkPareadoSemDeclararGanhoNoS24() {
        val random=Random(42);val ref=Plane(512,384,FloatArray(512*384){random.nextFloat()});val mov=ref.copy()
        val frames=List(8){k->SuperRes.Region(IntArray(170*138){i->Px.argb((i+k)%256,(i*3)%256,(i*7)%256)},0,0,170,138) to (k*.07f to k*.13f)}
        fun bench(native:Boolean):DoubleArray=backend(native){
            val t=System.nanoTime();repeat(3){Align.search(ref,mov,5)};val a=System.nanoTime()
            repeat(3){SuperRes.mergeTile(320,256,0,0,2f,0f,0f,frames,.01f)};val b=System.nanoTime()
            repeat(3){for(k in 1..7)SuperRes.estimateShift(frames[0].first,frames[k].first,0,0)};val c=System.nanoTime()
            repeat(3){Detail.applyBand(IntArray(1024*262){i->Px.argb(i%256,(i*7)%256,(i*13)%256)},1024,262,.4f,1f)}
            doubleArrayOf((a-t)/1e6,(b-a)/1e6,(c-b)/1e6,(System.nanoTime()-c)/1e6)
        }
        bench(false);bench(true);val old=bench(false);val got=bench(true)
        println("P0_HOST pareado3x align/SR/shift/detail: Kotlin=${old.contentToString()}ms; JNI=${got.contentToString()}ms. Não é S24/GPU/energia.")
    }
    @Test fun tomKotlinOtimizadoPreservaPixelsNoturnosBordasCoresEIntensidades() {
        val random=Random(263);val base=Plane(29,23,FloatArray(29*23){kotlin.math.ln(.004f+random.nextFloat()*.996f)})
        val mid=Plane(29,23,FloatArray(29*23){i->base.data[i]+(random.nextFloat()-.5f)*.6f})
        val px=IntArray(151*117){i->when(i%11){0->Px.argb(0,0,0);1->Px.argb(255,250,238);2->Px.argb(190,130,95);else->random.nextInt() or (0xFF shl 24)}}
        for(params in listOf(ToneParams(1.2f,.35f,.07f,.18f,1.1f,.06f),ToneParams(.2f,.1f,0f),ToneParams(0f,0f,0f,.3f),ToneParams(0f,0f,0f)))
            for(strength in listOf(0f,.3f,1f))for(protection in listOf(0f,.6f))for(m in listOf<Plane?>(null,mid)){
                val map=ToneMap(base,params,151,117,m)
                val old=px.copyOf();val got=px.copyOf()
                map.applyTileReference(old,151,117,0,0,strength,protection)
                map.applyTile(got,151,117,0,0,strength,protection)
                assertArrayEquals("Tom sem alteração de pixel: $params / $strength / $protection / ${m!=null}",old,got)
            }
        val map=ToneMap(base,ToneParams(.8f,.2f,.02f,.2f,.5f,.03f),151,117,mid)
        val whole=px.copyOf();map.applyTileReference(whole,151,117,0,0)
        val tiled=px.copyOf()
        for(y in 0 until 117 step 19)for(x in 0 until 151 step 23){
            val w=minOf(23,151-x);val h=minOf(19,117-y);val tile=IntArray(w*h){i->px[(y+i/w)*151+x+i%w]}
            map.applyTile(tile,w,h,x,y)
            for(j in 0 until h)tile.copyInto(tiled,(y+j)*151+x,j*w,(j+1)*w)
        }
        assertArrayEquals("Sem costura entre blocos",whole,tiled)
    }
    @Test fun benchmarkTonalKotlinPareadoSemAlegarGanhoFisico() {
        val rng=Random(71);val px=IntArray(8160*64){Px.argb(rng.nextInt(256),rng.nextInt(256),rng.nextInt(256))}
        val base=Plane(1024,768,FloatArray(1024*768){kotlin.math.ln(.004f+rng.nextFloat()*.996f)})
        val map=ToneMap(base,ToneParams(.8f,.35f,.04f,.18f,.7f,.03f),8160,6120,base)
        fun run(cached:Boolean):Double {val start=System.nanoTime();repeat(3){if(cached)map.applyTile(px.copyOf(),8160,64,0,2100) else map.applyTileReference(px.copyOf(),8160,64,0,2100)};return (System.nanoTime()-start)/1e6}
        repeat(2){run(false);run(true)}
        val old=ArrayList<Double>();val got=ArrayList<Double>()
        repeat(5){i->if(i%2==0){old+=run(false);got+=run(true)}else{got+=run(true);old+=run(false)}}
        println("TONE_KOTLIN faixa8160x64, grupos3x AB/BA: anterior=${old}ms; coordenadas reutilizadas=${got}ms; medianas=${old.sorted()[2]}/${got.sorted()[2]}ms. Não mede49MP/S24/GPU/energia.")
    }
}
