package com.insula.dicomviewer;
import java.util.*; import org.json.*;
public class PacsProfilesTest { public static void main(String[] a) throws Exception {
  Library.dir = new java.io.File(System.getProperty("work", "tests/.work") + "/pacs-lib"); Library.dir.mkdirs();
  Store.Profile city = new Store.Profile(); city.name="City Hospital"; city.url="http://127.0.0.1:8601/dicom-web/"; city.auth="basic"; city.user="drsharma"; city.secret="s3cret";
  Store.Profile img = new Store.Profile(); img.name="Imaging Centre"; img.institution="Sunrise Diagnostics"; img.url="http://127.0.0.1:8602/dicom-web"; img.auth="bearer"; img.secret="tok-ABC123";
  Store.Profile bad = city.copy(); bad.secret="wrong";
  for (Store.Profile p : new Store.Profile[]{city, img, bad}) {
    try {
      byte[] d = PacsActivity.http(p, p.base()+"/studies?limit=100", "application/dicom+json", null);
      JSONObject o = new JSONArray(new String(d,"UTF-8")).getJSONObject(0);
      String st = PacsActivity.jv(o,"0020000D");
      String se = PacsActivity.jv(new JSONArray(new String(PacsActivity.http(p,p.base()+"/studies/"+st+"/series","application/dicom+json",null),"UTF-8")).getJSONObject(0),"0020000E");
      String sop = PacsActivity.jv(new JSONArray(new String(PacsActivity.http(p,p.base()+"/studies/"+st+"/series/"+se+"/instances","application/dicom+json",null),"UTF-8")).getJSONObject(0),"00080018");
      String[] ct = new String[1];
      byte[] body = PacsActivity.http(p, p.base()+"/studies/"+st+"/series/"+se+"/instances/"+sop, "multipart/related; type=\"application/dicom\"", ct);
      int[] stats = new int[3];
      for (byte[] part : PacsActivity.multipart(body, ct[0])) Library.importBytes(part, stats);
      System.out.println(p.label()+": found "+PacsActivity.jv(o,"00100010")+" "+PacsActivity.jv(o,"00080061")+" -> imported="+stats[0]+" skipped="+stats[1]);
    } catch (Exception e) { System.out.println(p.label()+" [wrong password]: "+e.getMessage()); }
  }
  System.out.println("library studies: "+Library.studies.size());
  if (Library.studies.size() != 2) System.exit(1);
}}
