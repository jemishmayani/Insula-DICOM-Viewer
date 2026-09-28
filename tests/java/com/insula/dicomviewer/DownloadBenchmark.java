package com.insula.dicomviewer;
import java.io.*; import java.util.*; import java.util.concurrent.atomic.*;
public class DownloadBenchmark { public static void main(String[] a) throws Exception {
  Store.Profile p=new Store.Profile(); p.auth="none"; p.url="http://127.0.0.1:8801/dicom-web";
  // OLD approach: sequential per-instance
  Library.dir=new File(System.getProperty("work", "tests/.work")+"/dl_old"); Library.dir.mkdirs();
  long t0=System.currentTimeMillis(); int old=0;
  org.json.JSONArray ser=new org.json.JSONArray(new String(PacsActivity.json(p,p.base()+"/studies/1.2.3.4.5/series"),"UTF-8"));
  for(int i=0;i<ser.length();i++){ String se=PacsActivity.jv(ser.getJSONObject(i),"0020000E");
    org.json.JSONArray in=new org.json.JSONArray(new String(PacsActivity.json(p,p.base()+"/studies/1.2.3.4.5/series/"+se+"/instances"),"UTF-8"));
    for(int k=0;k<in.length();k++){ String[] ct=new String[1]; byte[] b=PacsActivity.http(p,p.base()+"/studies/1.2.3.4.5/series/"+se+"/instances/"+PacsActivity.jv(in.getJSONObject(k),"00080018"),"multipart/related; type=\"application/dicom\"",ct); old+=PacsActivity.multipart(b,ct[0]).size(); } }
  long tOld=System.currentTimeMillis()-t0;
  System.out.println("old one-by-one:     "+old+" images in "+tOld+" ms");
  // NEW: series-level streaming, parallel
  Library.bySop.clear(); Library.studies.clear(); Library.seriesMap.clear(); Library.studyMap.clear();
  Library.dir=new File(System.getProperty("work", "tests/.work")+"/dl_new"); Library.dir.mkdirs();
  Downloader d=new Downloader(p,"1.2.3.4.5"); t0=System.currentTimeMillis();
  String err=d.run(new Downloader.Listener(){ public void progress(int x,int y,long b){} });
  long tNew=System.currentTimeMillis()-t0;
  System.out.println("new series+parallel: "+d.stats[0]+" images in "+tNew+" ms  err="+err+"  ("+String.format("%.1f",tOld/(double)tNew)+"x faster)");
  // FALLBACK: server without series retrieval
  Library.bySop.clear(); Library.studies.clear(); Library.seriesMap.clear(); Library.studyMap.clear();
  Library.dir=new File(System.getProperty("work", "tests/.work")+"/dl_fb"); Library.dir.mkdirs();
  p.url="http://127.0.0.1:8802/dicom-web"; d=new Downloader(p,"1.2.3.4.5"); t0=System.currentTimeMillis();
  err=d.run(new Downloader.Listener(){ public void progress(int x,int y,long b){} });
  System.out.println("fallback per-image x4: "+d.stats[0]+" images in "+(System.currentTimeMillis()-t0)+" ms  fallback="+d.usedFallback+" err="+err);
  // PARSER stress: random tiny reads
  Random rnd=new Random(7); String B="bnd_7a3f"; ByteArrayOutputStream bo=new ByteArrayOutputStream(); List<byte[]> want=new ArrayList<>();
  bo.write(("preamble text\r\n").getBytes());
  for(int i=0;i<25;i++){ byte[] x=new byte[1+rnd.nextInt(5000)]; rnd.nextBytes(x); if(i%5==0) x=("\r\n--"+B.substring(0,4)).getBytes(); want.add(x); bo.write(("--"+B+"\r\nContent-Type: application/dicom\r\n\r\n").getBytes()); bo.write(x); bo.write("\r\n".getBytes()); }
  bo.write(("--"+B+"--").getBytes());
  final byte[] all=bo.toByteArray();
  InputStream frag=new InputStream(){ int pos=0; Random r=new Random(1);
    public int read(){ return pos<all.length? all[pos++]&255 : -1; }
    public int read(byte[] b,int o,int l){ if(pos>=all.length) return -1; int n=Math.min(l,Math.min(1+r.nextInt(9),all.length-pos)); System.arraycopy(all,pos,b,o,n); pos+=n; return n; } };
  final List<byte[]> got=new ArrayList<>();
  Downloader.streamParts(frag,B,new Downloader.Sink(){ public void part(byte[] x){ got.add(x);} },null,null);
  boolean same=got.size()==want.size(); for(int i=0;same&&i<got.size();i++) same=Arrays.equals(got.get(i),want.get(i));
  System.out.println("parser, 1-9 byte reads, tricky payloads: "+got.size()+"/"+want.size()+" parts, identical="+same);
  if (!same || d.stats[0] != 180) System.exit(1);
}}
