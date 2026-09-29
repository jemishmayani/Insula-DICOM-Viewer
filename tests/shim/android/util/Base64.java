/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
// Desktop stand-in for android.util.Base64 so network tests run on a plain JVM.
package android.util;
public class Base64 { public static final int NO_WRAP=2;
 public static String encodeToString(byte[] b,int f){return java.util.Base64.getEncoder().encodeToString(b);}
 public static byte[] decode(String s,int f){return java.util.Base64.getDecoder().decode(s);} }
