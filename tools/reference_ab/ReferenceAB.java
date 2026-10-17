import com.novacamera.s24.core.imaging.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

// Relatório local adicional: classes compiladas do aplicativo, sem enviar fotografias.
public class ReferenceAB {
  static int[] render(int[] px,int w,int h,Adaptive.Plan p) {
    int[] out=px.clone();
    if(!p.getSkip()) new ToneMap(p.getBase(),p.getParams(),w,h,p.getMid()).applyTile(out,w,h,0,0,p.getStrength(),p.getImaging()==null?0f:p.getImaging().getProtection());
    return out;
  }
  public static void main(String[] args) throws Exception {
    StringBuilder report=new StringBuilder("arquivo;preto;forca;protecao;difRGB;maxDeltaLuma;verdesAlterados;safeAB\n");
    File[] files=new File(args[0]).listFiles(f->f.getName().endsWith(".rgb"));
    Arrays.sort(files);
    for(File f:files) try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
      int w=in.readInt(),h=in.readInt();int[] px=new int[w*h];
      for(int i=0;i<px.length;i++) px[i]=Px.INSTANCE.argb(in.readUnsignedByte(),in.readUnsignedByte(),in.readUnsignedByte());
      int pw=w,ph=h; int[] preview=px;
      if(args.length>1)try(DataInputStream small=new DataInputStream(new BufferedInputStream(new FileInputStream(new File(args[1],f.getName()))))){
        pw=small.readInt();ph=small.readInt();preview=new int[pw*ph];
        for(int i=0;i<preview.length;i++)preview[i]=Px.INSTANCE.argb(small.readUnsignedByte(),small.readUnsignedByte(),small.readUnsignedByte());
      }
      float zoom=f.getName().startsWith("10x")?10f:1f;
      Adaptive.Plan old=Adaptive.INSTANCE.plan(preview,pw,ph,ToneIntent.LIGHT,zoom);
      Adaptive.Plan next=AdaptiveImaging.INSTANCE.plan(preview,pw,ph,ToneIntent.LIGHT,zoom,new ImagingFacts(),new CalibrationRegistry());
      int[] a=render(px,w,h,old),b=render(px,w,h,next);int changes=0,greenChanges=0;float delta=0;
      for(int i=0;i<px.length;i++){ if(a[i]!=b[i]){changes++;if(AdaptiveImaging.INSTANCE.green(px[i]))greenChanges++;} delta=Math.max(delta,Math.abs(Px.INSTANCE.luma(a[i])-Px.INSTANCE.luma(b[i]))); }
      boolean safe=AdaptiveImaging.INSTANCE.safeAgainstBaseline(px,a,b,w,h);
      if(!safe)throw new AssertionError(f.getName());
      report.append(String.format(Locale.US,"%s;%.4f;%.2f;%.2f;%d;%.6f;%d;%s%n",f.getName(),old.getParams().getBlackLevel(),old.getStrength(),next.getImaging().getProtection(),changes,delta,greenChanges,safe));
    }
    Files.writeString(Path.of(args[0],"reference-detail-ab.csv"),report.toString());System.out.print(report);
  }
}
