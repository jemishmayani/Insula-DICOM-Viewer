/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;
public class UpdateCheckTest { public static void main(String[] a) throws Exception {
  int f=0;
  String[][] cmp={{"v1.6.0","1.5.1","1"},{"v1.6.0","1.6.0","0"},{"1.5.1","v1.6.0","-1"},{"v1.10.0","1.9.9","1"},{"v2.0","1.99.99","1"},{"v1.6.0-beta","1.6.0","0"}};
  for(String[] t:cmp){ int r=Integer.signum(Updates.compare(t[0],t[1])); boolean ok=r==Integer.parseInt(t[2]); if(!ok) f++; System.out.println((ok?"PASS":"FAIL")+" compare "+t[0]+" vs "+t[1]+" = "+r); }
  String json="{\"tag_name\":\"v1.6.0\",\"name\":\"Insula DICOM Viewer 1.6.0\",\"body\":\"Notes\",\"html_url\":\"https://github.com/x/releases/tag/v1.6.0\",\"assets\":[{\"name\":\"notes.txt\",\"browser_download_url\":\"u1\",\"size\":5},{\"name\":\"InsulaDICOMViewer-v1.6.0.apk\",\"browser_download_url\":\"https://github.com/x/InsulaDICOMViewer-v1.6.0.apk\",\"size\":460000}]}";
  Updates.Release r=Updates.parse(json);
  boolean ok=r.tag.equals("v1.6.0") && r.apkUrl.endsWith(".apk") && r.apkSize==460000; if(!ok) f++;
  System.out.println((ok?"PASS":"FAIL")+" parse picks the APK asset: "+r.apkUrl+" "+r.apkSize);
  Updates.Release none=Updates.parse("{\"tag_name\":\"v1.6.0\",\"assets\":[]}"); ok=none.apkUrl==null && none.pageUrl.equals(Updates.RELEASES_URL); if(!ok) f++;
  System.out.println((ok?"PASS":"FAIL")+" release without APK falls back to the releases page");
  System.out.println(f==0?"ALL PASSED":f+" FAILED");
  if (f > 0) System.exit(1);
}}
