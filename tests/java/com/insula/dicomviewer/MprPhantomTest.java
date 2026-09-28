package com.insula.dicomviewer;
import java.util.*; import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File;
public class MprPhantomTest {
  static final String WORK = System.getProperty("work", "tests/.work");
  static int fails=0;
  static void check(String name, boolean ok, String detail){ System.out.println((ok?"PASS ":"FAIL ")+name+"  "+detail); if(!ok) fails++; }
  static void png(RawImage im, String f, double lo, double hi) throws Exception {
    BufferedImage b=new BufferedImage(im.w,im.h,BufferedImage.TYPE_INT_RGB);
    for(int y=0;y<im.h;y++)for(int x=0;x<im.w;x++){ int p=im.pix[y*im.w+x]; int g; if(im.rgb){ b.setRGB(x,y,p&0xFFFFFF); continue;} g=(int)Math.max(0,Math.min(255,(p-lo)/(hi-lo)*255)); b.setRGB(x,y,g*0x010101);} ImageIO.write(b,"png",new File(f)); }
  // Phantom in patient mm: sphere r=20 at (10,-5,15) value 1000; rod along z at x=-30,y=20 r=4 value 500; air -1000; soft 0 inside body cylinder r=80 about z
  static short smooth(double[] p){
    double ds=Math.sqrt((p[0]-10)*(p[0]-10)+(p[1]+5)*(p[1]+5)+(p[2]-15)*(p[2]-15))-20;
    double dr=Math.sqrt((p[0]+30)*(p[0]+30)+(p[1]-20)*(p[1]-20))-4;
    double db=Math.sqrt(p[0]*p[0]+p[1]*p[1])-80;
    double body=-1000+1000/(1+Math.exp(db/0.8));
    double v=body+ (1000-body)/(1+Math.exp(ds/0.8)); v=Math.max(v, body+(500-body)/(1+Math.exp(dr/0.8)));
    return (short)Math.round(v);
  }
  static boolean SMOOTH=false;
  static short phantom(double[] p){ if(SMOOTH) return smooth(p);
    double dx=p[0]-10,dy=p[1]+5,dz=p[2]-15;
    if(dx*dx+dy*dy+dz*dz<=400) return 1000;
    double rx=p[0]+30, ry=p[1]-20; if(rx*rx+ry*ry<=16) return 500;
    if(p[0]*p[0]+p[1]*p[1]<=6400) return 0;
    return -1000;
  }
  static Volume make(double[] O,double[] A,double[] B,double[] C,int nx,int ny,int nz){
    short[] d=new short[nx*ny*nz];
    for(int k=0;k<nz;k++)for(int j=0;j<ny;j++)for(int i=0;i<nx;i++){ double[] p={O[0]+i*A[0]+j*B[0]+k*C[0],O[1]+i*A[1]+j*B[1]+k*C[1],O[2]+i*A[2]+j*B[2]+k*C[2]}; d[(k*ny+j)*nx+i]=phantom(p);}
    Volume v=new Volume(d,nx,ny,nz,O,A,B,C); v.defWc=40; v.defWw=400; return v;
  }
  // measure sphere diameter along image row through a patient point
  static double sphereWidth(Volume v, double[] u, double[] w, double[] through){
    Volume.Plane pl=v.planeThrough(through,u,w,512); RawImage im=v.reslice(pl,0,Volume.THIN);
    double[] d=Volume.sub(new double[]{10,-5,15},pl.origin); int cy=(int)Math.round(Volume.dot(d,pl.v)/pl.s); int cx=(int)Math.round(Volume.dot(d,pl.u)/pl.s);
    int n=0; for(int x=0;x<im.w;x++) if(im.pix[cy*im.w+x]>500) n++;
    boolean centerBright = im.pix[cy*im.w+cx]>900;
    return centerBright? n*pl.s : -1;
  }
  public static void main(String[] a) throws Exception {
    double[] X={1,0,0},Y={0,1,0},Z={0,0,1},mZ={0,0,-1};
    // 1. Axial acquisition, 1x1 in-plane, 2.5mm slices
    Volume ax=make(new double[]{-100,-100,-40},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,2.5},201,201,41);
    double[] ctr={10,-5,15};
    check("axial acq: sphere in axial plane", Math.abs(sphereWidth(ax,X,Y,ctr)-40)<3, "width="+sphereWidth(ax,X,Y,ctr)+"mm (expect 40)");
    check("axial acq: sphere in coronal plane", Math.abs(sphereWidth(ax,X,mZ,ctr)-40)<3, "width="+sphereWidth(ax,X,mZ,ctr));
    check("axial acq: sphere in sagittal plane", Math.abs(sphereWidth(ax,Y,mZ,ctr)-40)<3, "width="+sphereWidth(ax,Y,mZ,ctr));
    // 2. Sagittal acquisition: rows along +y, columns along -z, slices along x (1.5mm)
    Volume sag=make(new double[]{-60,-100,90},new double[]{0,1,0},new double[]{0,0,-1},new double[]{1.5,0,0},201,201,81);
    check("sagittal acq -> true axial reconstruction", Math.abs(sphereWidth(sag,X,Y,ctr)-40)<3.5, "width="+sphereWidth(sag,X,Y,ctr));
    // 3. Gantry tilt: slice step has a y component
    Volume tilt=make(new double[]{-100,-100,-40},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0.45,2.46},201,201,41);
    check("gantry-tilt acq -> undistorted sagittal", Math.abs(sphereWidth(tilt,Y,mZ,ctr)-40)<3.5, "width="+sphereWidth(tilt,Y,mZ,ctr));
    // 4. Rod along z: in an axial plane it's a dot at (-30,20); in a 30deg-oblique coronal plane through the rod it stays a vertical line
    double ang=Math.toRadians(30);
    double[] u=Volume.rot(X,Z,ang), v=Volume.rot(mZ,Z,ang);
    Volume.Plane pl=ax.planeThrough(new double[]{-30,20,0},u,v,512); RawImage ob=ax.reslice(pl,0,Volume.THIN);
    double[] d=Volume.sub(new double[]{-30,20,0},pl.origin); int rc=(int)Math.round(Volume.dot(d,pl.u)/pl.s);
    int rows=0; for(int y=0;y<ob.h;y++){ int vv=ob.pix[y*ob.w+rc]; if(vv>300&&vv<700) rows++; }
    check("oblique plane (30deg) contains the rod", rows>ob.h*0.5, "rod rows="+rows+"/"+ob.h);
    png(ob,WORK+"/oblique.png",-200,1000);
    // 5. Slab MIP picks up the sphere 12mm away, thin slice does not
    double[] off={10,-5,15+30};  // 30mm above sphere centre: outside sphere (r=20)
    Volume.Plane p5=ax.planeThrough(off,X,Y,512);
    int thinMax=max(ax.reslice(p5,0,Volume.THIN)), mipMax=max(ax.reslice(p5,30,Volume.MIP)), minMin=min(ax.reslice(p5,30,Volume.MINIP)), avg=ax.reslice(p5,30,Volume.AVG).pix[0];
    check("thin slice 30mm above sphere misses it", thinMax<600, "max="+thinMax);
    check("30mm MIP slab reaches the sphere", mipMax>=990, "max="+mipMax);
    check("MinIP slab keeps air at corners", minMin<=-990, "min="+minMin);
    // 6. Curved MPR along an arc of radius 30 around (10,-5): a sphere-centred arc passes through the sphere for ~40mm of arc
    List<double[]> arc=new ArrayList<>(); for(int i=0;i<=12;i++){ double t=Math.toRadians(-60+i*10); arc.add(new double[]{10+19*Math.cos(t),-5+19*Math.sin(t),15}); }
    RawImage cp=ax.curved(arc,Z,1.0,0,0,Volume.THIN);
    double[] zr=ax.range(Z); int row=(int)Math.round((zr[1]-15)/1.0);
    int bright=0; for(int x=0;x<cp.w;x++) if(cp.pix[row*cp.w+x]>900) bright++;
    check("curved MPR follows the arc inside the sphere", bright>cp.w*0.8, "bright cols="+bright+"/"+cp.w+" size="+cp.w+"x"+cp.h);
    png(cp,WORK+"/curved.png",-200,1000);
    // 7. 3D MIP: sphere projects to a disc; VR gives colored pixels
    RawImage mip=ax.render3D(0,0,1,200,Volume.R_MIP,0,0,false);
    int disc=0; for(int p:mip.pix) if(p>=990) disc++;
    double discArea=disc*mip.rowSp*mip.colSp, expect=Math.PI*400;
    check("3D MIP disc area matches sphere", Math.abs(discArea-expect)/expect<0.15, String.format("area=%.0f mm2 expect %.0f",discArea,expect));
    png(mip,WORK+"/mip3d.png",-200,1000);
    RawImage vr=ax.render3D(0.6,0.3,1,220,Volume.R_BONE,300,900,false);
    int lit=0; for(int p:vr.pix) if((p&0xFFFFFF)!=0) lit++;
    check("3D volume rendering shows bone", lit>500, "lit px="+lit);
    png(vr,WORK+"/vr3d.png",0,1);
    RawImage vr2=ax.render3D(0.6,0.3,1,220,Volume.R_SOFT,-300,100,false); png(vr2,WORK+"/vr3d_soft.png",0,1);
    // 8. Speed (desktop JVM)
    long t0=System.nanoTime(); for(int i=0;i<10;i++) ax.reslice(ax.planeThrough(ctr,u,v,512),0,Volume.THIN); long t1=System.nanoTime();
    for(int i=0;i<3;i++) ax.render3D(i,0.2,1,180,Volume.R_MIP,0,0,true); long t2=System.nanoTime();
    for(int i=0;i<3;i++) ax.reslice(ax.planeThrough(ctr,X,Y,512),20,Volume.MIP); long t3=System.nanoTime();
    System.out.printf("timing: oblique thin reslice %.1f ms, 3D fast MIP %.1f ms, 20mm MIP slab %.1f ms%n",(t1-t0)/1e7,(t2-t1)/3e6,(t3-t2)/3e6);
    SMOOTH=true;
    Volume sm=make(new double[]{-100,-100,-40},new double[]{0.8,0,0},new double[]{0,0.8,0},new double[]{0,0,1.25},251,251,81);
    png(sm.render3D(0.6,0.3,1,300,Volume.R_BONE,300,900,false),WORK+"/vr_s_bone.png",0,1);
    png(sm.render3D(0.6,0.3,1,300,Volume.R_SOFT,-300,100,false),WORK+"/vr_s_soft.png",0,1);
    System.out.println(fails==0?"ALL PASSED":fails+" FAILED");
    if (fails > 0) System.exit(1);
  }
  static int max(RawImage im){int m=Integer.MIN_VALUE; for(int p:im.pix) m=Math.max(m,p); return m;}
  static int min(RawImage im){int m=Integer.MAX_VALUE; for(int p:im.pix) m=Math.min(m,p); return m;}
}
