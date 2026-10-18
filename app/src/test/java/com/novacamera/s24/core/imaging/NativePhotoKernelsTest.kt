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
            repeat(3){Detail.applyBand(IntArray(1024*262){i->Px.argb(i%256,(i*7)%256,(i*13)%256)},1024,262,.4f,1f)}
            doubleArrayOf((a-t)/1e6,(b-a)/1e6,(System.nanoTime()-b)/1e6)
        }
        bench(false);bench(true);val old=bench(false);val got=bench(true)
        println("P0_HOST pareado3x align/SR/detail: Kotlin=${old.contentToString()}ms; JNI=${got.contentToString()}ms. Não é S24/GPU/energia.")
    }
}
