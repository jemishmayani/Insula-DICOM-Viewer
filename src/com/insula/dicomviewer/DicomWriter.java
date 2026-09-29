/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Writes Explicit VR Little Endian Part 10 files. Enough for derived grayscale images such as saved MPR series. */
final class DicomWriter {
    static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    final TreeMap<Long, byte[]> els = new TreeMap<>();

    static String newUid() {
        UUID u = UUID.randomUUID();
        BigInteger hi = BigInteger.valueOf(u.getMostSignificantBits()).and(new BigInteger("FFFFFFFFFFFFFFFF", 16));
        BigInteger lo = BigInteger.valueOf(u.getLeastSignificantBits()).and(new BigInteger("FFFFFFFFFFFFFFFF", 16));
        return "2.25." + hi.shiftLeft(64).or(lo).toString();
    }

    static boolean longVR(String vr) { return vr.equals("OB") || vr.equals("OW") || vr.equals("SQ") || vr.equals("UT") || vr.equals("UN"); }

    static byte[] element(int tag, String vr, byte[] val) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(val.length + 12);
        int g = tag >>> 16, e = tag & 0xFFFF;
        b.write(g & 255); b.write(g >> 8); b.write(e & 255); b.write(e >> 8);
        b.write(vr.charAt(0)); b.write(vr.charAt(1));
        if (longVR(vr)) { b.write(0); b.write(0); w32(b, val.length); }
        else { b.write(val.length & 255); b.write((val.length >> 8) & 255); }
        b.write(val, 0, val.length);
        return b.toByteArray();
    }

    static void w32(ByteArrayOutputStream b, int v) { b.write(v & 255); b.write((v >> 8) & 255); b.write((v >> 16) & 255); b.write((v >>> 24) & 255); }

    void str(int tag, String vr, String s) {
        if (s == null) s = "";
        byte[] v = s.getBytes(LATIN1);
        if (v.length % 2 == 1) { byte[] p = new byte[v.length + 1]; System.arraycopy(v, 0, p, 0, v.length); p[v.length] = (byte) (vr.equals("UI") ? 0 : ' '); v = p; }
        els.put(tag & 0xFFFFFFFFL, element(tag, vr, v));
    }

    void us(int tag, int v) { els.put(tag & 0xFFFFFFFFL, element(tag, "US", new byte[]{(byte) v, (byte) (v >> 8)})); }

    void ds(int tag, double... vals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            if (i > 0) sb.append('\\');
            String t = String.format(java.util.Locale.ROOT, "%.6g", vals[i]);
            if (t.contains("e") || t.contains("E")) t = String.format(java.util.Locale.ROOT, "%.4f", vals[i]);
            if (t.contains(".")) { t = t.replaceAll("0+$", ""); if (t.endsWith(".")) t = t.substring(0, t.length() - 1); }
            if (t.length() > 16) t = t.substring(0, 16);
            sb.append(t);
        }
        str(tag, "DS", sb.toString());
    }

    void pixels16(short[] px) {
        byte[] b = new byte[px.length * 2];
        for (int i = 0; i < px.length; i++) { b[2 * i] = (byte) px[i]; b[2 * i + 1] = (byte) (px[i] >> 8); }
        els.put(Dicom.PIXEL_DATA & 0xFFFFFFFFL, element(Dicom.PIXEL_DATA, "OW", b));
    }

    byte[] toBytes(String sopClass, String sopUid) {
        ByteArrayOutputStream meta = new ByteArrayOutputStream();
        writeTo(meta, element(0x00020001, "OB", new byte[]{0, 1}));
        writeTo(meta, strEl(0x00020002, "UI", sopClass));
        writeTo(meta, strEl(0x00020003, "UI", sopUid));
        writeTo(meta, strEl(0x00020010, "UI", Dicom.TS_EXPLICIT_LE));
        writeTo(meta, strEl(0x00020012, "UI", "2.25.182406418203495711231137284109815413937"));
        writeTo(meta, strEl(0x00020013, "SH", "INSULA_1_5"));
        byte[] m = meta.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[128], 0, 128);
        out.write('D'); out.write('I'); out.write('C'); out.write('M');
        ByteArrayOutputStream gl = new ByteArrayOutputStream();
        w32(gl, m.length);
        writeTo(out, element(0x00020000, "UL", gl.toByteArray()));
        writeTo(out, m);
        for (Map.Entry<Long, byte[]> e : els.entrySet()) writeTo(out, e.getValue());
        return out.toByteArray();
    }

    static byte[] strEl(int tag, String vr, String s) {
        byte[] v = s.getBytes(LATIN1);
        if (v.length % 2 == 1) { byte[] p = new byte[v.length + 1]; System.arraycopy(v, 0, p, 0, v.length); p[v.length] = (byte) (vr.equals("UI") ? 0 : ' '); v = p; }
        return element(tag, vr, v);
    }

    static void writeTo(ByteArrayOutputStream o, byte[] b) { o.write(b, 0, b.length); }
}
