package com.novacamera.s24.processing;

/** Testa o JNI real em host, sem Android nem fotografias externas. Não comprova qualidade no telefone. */
public class JpegBands {
    private native long open(String input,String output);
    private native int[] dimensions(long handle);
    private native int read(long handle,int[] target,int rows);
    private native boolean write(long handle,int[] source,int rows);
    private native boolean close(long handle,boolean complete);
    public static void main(String[] args) {
        System.load(args[0]);JpegBands codec=new JpegBands();
        long rejected=codec.open(args[3],args[2]+".progressive");
        if(rejected!=0){codec.close(rejected,false);throw new AssertionError("JPEG progressivo não rejeitado");}
        long handle=codec.open(args[1],args[2]);if(handle==0)throw new AssertionError("Codec indisponível");
        int[] dims=codec.dimensions(handle);if((long)dims[0]*dims[1]<190_000_000L)throw new AssertionError("Resolução insuficiente");
        int[] pixels=new int[dims[0]*64];
        if(codec.write(handle,pixels,1))throw new AssertionError("Escrita fora de ordem aceita");
        if(codec.read(handle,pixels,-1)!=-1)throw new AssertionError("Faixa negativa aceita");
        int y=0;
        while(y<dims[1]){
            int rows=codec.read(handle,pixels,Math.min(64,dims[1]-y));if(rows<=0)throw new AssertionError("Leitura incompleta");
            int c=pixels[0];
            if(Math.abs(((c>>>16)&255)-150)>5||Math.abs(((c>>>8)&255)-95)>5||Math.abs((c&255)-70)>5)throw new AssertionError("Canais RGB alterados");
            if(!codec.write(handle,pixels,rows))throw new AssertionError("Escrita incompleta");y+=rows;
        }
        if(!codec.close(handle,true))throw new AssertionError("Finalização incompleta");
        long again=codec.open(args[2],args[2]+".check");if(again==0)throw new AssertionError("JPEG final inválido");
        int[] actual=codec.dimensions(again);if(actual[0]!=dims[0]||actual[1]!=dims[1])throw new AssertionError("Resolução alterada");
        codec.close(again,false);
        System.out.println("Codec JNI: "+dims[0]+"x"+dims[1]+", RGB e ordem das faixas conferidos; heap JVM 32 MB");
    }
}
