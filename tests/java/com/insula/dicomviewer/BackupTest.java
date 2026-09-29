/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;
import java.util.*; import java.io.*; import java.util.zip.*; import org.json.*;
public class BackupTest { static int fails=0; static void check(String n, boolean ok, String d){ System.out.println((ok?"PASS ":"FAIL ")+n+"  "+d); if(!ok) fails++; }
 public static void main(String[] a) throws Exception {
  // 1. passphrase crypto
  byte[] salt=new byte[16]; new java.security.SecureRandom().nextBytes(salt);
  javax.crypto.spec.SecretKeySpec k=Backup.derive("correct horse",salt);
  String sealed=Backup.seal("pacs-password-123",k);
  check("sealed text hides password", !sealed.contains("pacs-password"), sealed.substring(0,20)+"…");
  check("right passphrase opens it", Backup.open(sealed,Backup.derive("correct horse",salt)).equals("pacs-password-123"), "");
  boolean rejected=false; try{ Backup.open(sealed,Backup.derive("wrong horse",salt)); }catch(Exception e){ rejected=true; }
  check("wrong passphrase is rejected", rejected, "");
  // 2. AnnStore round trip
  DicomView.Ann len=new DicomView.Ann(DicomView.T_LENGTH,new float[]{10,20,110,20}); len.done=true;
  DicomView.Ann arr=new DicomView.Ann(DicomView.T_ARROW,new float[]{5,5,50,50}); arr.done=true; arr.text="Lesion";
  DicomView.Ann half=new DicomView.Ann(DicomView.T_COBB,new float[]{1,1,2,2,Float.NaN,Float.NaN,Float.NaN,Float.NaN}); half.done=false;
  AnnStore.list("1.2.3#0").add(len); AnnStore.list("1.2.3#0").add(arr); AnnStore.list("1.2.3#0").add(half);
  AnnStore.list("9.9.9#0").add(len.copy()); AnnStore.keys.add("1.2.3#0"); AnnStore.keys.add("9.9.9#0");
  JSONObject j=AnnStore.toJson(new HashSet<>(Arrays.asList("1.2.3")));
  check("export filtered to the set's images", j.getJSONObject("annotations").length()==1 && j.getJSONArray("keys").length()==1, j.toString().length()+" chars");
  check("unfinished measurement not exported", j.getJSONObject("annotations").getJSONArray("1.2.3#0").length()==2, "");
  AnnStore.map.clear(); AnnStore.keys.clear();
  int added=AnnStore.merge(j,false);
  check("import restores measurements and label", added==2 && AnnStore.list("1.2.3#0").get(1).text.equals("Lesion"), "added="+added);
  check("import restores key image", AnnStore.isKey("1.2.3#0"), "");
  check("re-import doesn't duplicate", AnnStore.merge(j,false)==0 && AnnStore.list("1.2.3#0").size()==2, "");
  AnnStore.removeSop("1.2.3");
  check("deleting a series removes its marks", AnnStore.list("1.2.3#0").isEmpty() && !AnnStore.isKey("1.2.3#0"), "");
  // 3. study set zip: DICOM + manifest detected on import
  String T=System.getProperty("testdata")+"/";
  Library.dir=new File(System.getProperty("work", "tests/.work")+"/backup-lib"); Library.dir.mkdirs();
  byte[] dcm=Library.readFile(new File(T+"CT_small.dcm"));
  Dicom.DataSet ds=Dicom.parse(dcm.clone()); String sop=ds.getString(0x00080018);
  // anonymize as the exporter does
  byte[] anon=dcm.clone(); Dicom.DataSet ad=Dicom.parse(anon); Anonymizer.scrub(ad);
  ByteArrayOutputStream bo=new ByteArrayOutputStream(); ZipOutputStream z=new ZipOutputStream(bo);
  z.putNextEntry(new ZipEntry("dicom/study1/00001.dcm")); z.write(anon); z.closeEntry();
  JSONObject man=new JSONObject(); man.put("type","insula-studyset"); man.put("name","Teaching set");
  JSONObject an=new JSONObject(); JSONArray arrj=new JSONArray(); JSONObject m1=new JSONObject(); m1.put("t",4); m1.put("p",new JSONArray(new double[]{1,2,3,4})); arrj.put(m1); an.put(sop+"#0",arrj); man.put("annotations",an);
  z.putNextEntry(new ZipEntry(Backup.SET_MANIFEST)); z.write(man.toString().getBytes("UTF-8")); z.closeEntry(); z.close();
  int[] st=new int[3]; List<byte[]> mf=new ArrayList<>();
  Library.importStream(new ByteArrayInputStream(bo.toByteArray()),st,null,mf);
  check("study set import: DICOM imported", st[0]==1, "imported="+st[0]+" skipped="+st[1]);
  check("study set import: manifest found", mf.size()==1 && new JSONObject(new String(mf.get(0),"UTF-8")).optString("name").equals("Teaching set"), "");
  Library.ImageInfo got=Library.bySop.get(sop);
  check("anonymized copy keeps SOP UID (marks still match)", got!=null, "sop="+sop);
  check("anonymized copy has no patient name", got!=null && got.patientName.equals("ANONYMOUS"), got==null?"":got.patientName);
  AnnStore.merge(new JSONObject(new String(mf.get(0),"UTF-8")),false);
  check("manifest measurements attach to the imported image", AnnStore.list(sop+"#0").size()==1, "");
  System.out.println(fails==0?"ALL PASSED":fails+" FAILED");
  if (fails > 0) System.exit(1);
 }}
