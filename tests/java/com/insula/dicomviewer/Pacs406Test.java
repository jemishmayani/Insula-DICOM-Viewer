package com.insula.dicomviewer;
public class Pacs406Test {
  static int fails = 0;
  static void check(boolean ok, String what) { System.out.println((ok ? "   PASS " : "   FAIL ") + what); if (!ok) fails++; }
  public static void main(String[] a) throws Exception {
  Store.Profile p=new Store.Profile(); p.auth="none";
  p.url="http://127.0.0.1:8701"; String[] r=PacsActivity.probe(p);
  System.out.println("1. portal root + json-only DICOMweb:\n   found="+r[0]+"\n   "+r[1]);
  check(r[0] != null && r[0].endsWith("/dicom-web"), "finds the DICOMweb path behind a portal");
  p.url=r[0]; String body=new String(PacsActivity.json(p,p.base()+"/studies?PatientName=*doe*"),"UTF-8");
  check(body.contains("DOE^JANE"), "search works via the corrected address and JSON fallback");
  p.url="http://127.0.0.1:8702/"; r=PacsActivity.probe(p);
  System.out.println("2. dcm4chee path:\n   found="+r[0]);
  check(r[0] != null && r[0].endsWith("/dcm4chee-arc/aets/DCM4CHEE/rs"), "finds the dcm4chee path");
  p.url="http://127.0.0.1:8703"; 
  try { PacsActivity.json(p,p.base()+"/studies?limit=100&includefield=00081030&PatientName=*doe*&fuzzymatching=true"); System.out.println("3. FAILED: expected 400"); }
  catch (PacsActivity.HttpErr e) { byte[] d=PacsActivity.json(p,p.base()+"/studies?limit=100&PatientName=*doe*"); System.out.println("3. server rejects extras: got "+e.code); check(new String(d,"UTF-8").contains("DOE^JANE"), "plain retry after HTTP 400 finds the study"); }
  p.url="http://127.0.0.1:8701/some/wrong/path"; try { PacsActivity.json(p,p.base()+"/studies"); } catch(Exception e){ System.out.println("4. raw 406 message:\n   "+e.getMessage()); }
  p.url="https://pacs.invalid-host-for-test.example"; r=PacsActivity.probe(p); System.out.println("5. unknown host:\n   "+r[1]);
  check(r[0] == null && r[1].startsWith("Can't find"), "explains an unknown host");
  System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
  if (fails > 0) System.exit(1);
}}
