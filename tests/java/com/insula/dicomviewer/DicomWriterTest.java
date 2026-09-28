package com.insula.dicomviewer;
import java.nio.file.*;
public class DicomWriterTest { public static void main(String[] a) throws Exception {
  Volume ax=MprPhantomTest.make(new double[]{-100,-100,-40},new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,2.5},201,201,41);
  double ang=Math.toRadians(20); double[] u=Volume.rot(new double[]{1,0,0},new double[]{0,0,1},ang), v=Volume.rot(new double[]{0,0,-1},new double[]{0,0,1},ang), n=Volume.norm(Volume.cross(u,v));
  Volume.Plane pl=ax.planeThrough(Volume.mul(n,Volume.dot(new double[]{10,-5,15},n)),u,v,512); RawImage im=ax.reslice(pl,10,Volume.MIP);
  DicomWriter w=new DicomWriter(); String sop=DicomWriter.newUid(), cls="1.2.840.10008.5.1.4.1.1.2";
  w.str(0x00080008,"CS","DERIVED\\SECONDARY\\MPR"); w.str(0x00080016,"UI",cls); w.str(0x00080018,"UI",sop); w.str(0x00080060,"CS","CT");
  w.str(0x0008103E,"LO","MPR Coronal (oblique) 3mm MIP 10mm"); w.str(0x00100010,"PN","TEST^PATIENT"); w.str(0x0020000D,"UI","1.2.3.4"); w.str(0x0020000E,"UI",DicomWriter.newUid());
  w.str(0x00200013,"IS","1"); w.ds(0x00200032,pl.origin); w.ds(0x00200037,u[0],u[1],u[2],v[0],v[1],v[2]); w.us(0x00280002,1); w.str(0x00280004,"CS","MONOCHROME2");
  w.us(0x00280010,im.h); w.us(0x00280011,im.w); w.ds(0x00280030,pl.s,pl.s); w.us(0x00280100,16); w.us(0x00280101,16); w.us(0x00280102,15); w.us(0x00280103,1);
  w.ds(0x00281050,40); w.ds(0x00281051,400); w.ds(0x00281052,0); w.ds(0x00281053,1);
  short[] px=new short[im.pix.length]; for(int i=0;i<px.length;i++) px[i]=(short)im.pix[i]; w.pixels16(px);
  byte[] b=w.toBytes(cls,sop); Files.write(Paths.get(System.getProperty("work", "tests/.work") + "/mpr_saved.dcm"),b);
  Dicom.DataSet ds=Dicom.parse(b); RawImage back=PixelDecoder.decode(ds,0);
  boolean same=true; for(int i=0;i<px.length;i++) if(back.pix[i]!=px[i]) {same=false;break;}
  System.out.println("own parser: "+back.w+"x"+back.h+" pixels identical="+same+" desc="+ds.getString(0x0008103E)+" uid valid="+Dicom.uidLike(sop)+" len="+sop.length());
  if (!same) System.exit(1);
  System.out.println("orient="+ds.getString(0x00200037)+" pos="+ds.getString(0x00200032));
}}
