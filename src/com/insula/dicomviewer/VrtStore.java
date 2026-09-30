/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 3D post-processing that stays with the study. A saved state is a DICOM Raw Data object in the series
 * "Insula 3D VRT states"; its settings (JSON) and tissue labels (deflated) are private elements of the creator
 * "INSULA_VRT". Screenshots are Secondary Capture images. Both are part of the study, so they are included in
 * study-set exports and can be reopened in later sessions. Other viewers ignore the private data and show the images.
 */
final class VrtStore {
    static final Charset UTF8 = Charset.forName("UTF-8");
    static final String CREATOR = "INSULA_VRT";
    static final int T_CREATOR = 0x00710010, T_JSON = 0x00711001, T_LABELS = 0x00711002, T_REF = 0x00711003;
    static final String RAW_DATA = "1.2.840.10008.5.1.4.1.1.66", SECONDARY_CAPTURE = "1.2.840.10008.5.1.4.1.1.7";
    static final String STATES_DESC = "Insula 3D VRT states", CAPTURES_DESC = "Insula 3D VRT captures";

    static final class Saved {
        Library.ImageInfo info;
        String name;
        long created;
        JSONObject json;
    }

    /** A stable UID derived from text, so one study always uses the same states and captures series. */
    static String derivedUid(String seed) {
        UUID u = UUID.nameUUIDFromBytes(seed.getBytes(UTF8));
        byte[] b = new byte[17];
        for (int i = 0; i < 8; i++) { b[1 + i] = (byte) (u.getMostSignificantBits() >>> (56 - 8 * i)); b[9 + i] = (byte) (u.getLeastSignificantBits() >>> (56 - 8 * i)); }
        return "2.25." + new BigInteger(b).toString();
    }

    static String statesSeries(Library.Study st) { return derivedUid(st.uid + "|insula-vrt-states"); }
    static String capturesSeries(Library.Study st) { return derivedUid(st.uid + "|insula-vrt-captures"); }

    /** Patient and study attributes copied from the source series, so saved objects file into the same study. */
    static DicomWriter base(Library.Series src, String cls, String sop, String seriesUid, String desc, String modality, int seriesNo, int instance) throws Exception {
        Dicom.DataSet s = Dicom.parse(Library.readFile(src.images.get(0).file));
        DicomWriter w = new DicomWriter();
        w.str(0x00080005, "CS", s.str(0x00080005));
        w.str(0x00080016, "UI", cls);
        w.str(0x00080018, "UI", sop);
        for (int tag : new int[]{0x00080020, 0x00080030, 0x00080050, 0x00080080, 0x00080090, 0x00081030, 0x00100010, 0x00100020, 0x00100030, 0x00100040, 0x00101010, 0x00200010})
            if (s.has(tag)) w.str(tag, Dicom.Dict.vr(tag), s.str(tag));
        w.str(0x00080060, "CS", modality);
        w.str(0x0008103E, "LO", desc);
        w.str(0x0020000D, "UI", src.study.uid);
        w.str(0x0020000E, "UI", seriesUid);
        w.str(0x00200011, "IS", String.valueOf(seriesNo));
        w.str(0x00200013, "IS", String.valueOf(instance));
        String now = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(new java.util.Date());
        String time = new java.text.SimpleDateFormat("HHmmss", java.util.Locale.ROOT).format(new java.util.Date());
        w.str(0x00080023, "DA", now);
        w.str(0x00080033, "TM", time);
        String fr = s.getString(0x00200052);
        if (fr != null) w.str(0x00200052, "UI", fr);
        return w;
    }

    static int nextInstance(Library.Study st, String seriesUid) {
        int n = 0;
        for (Library.Series se : st.series) if (se.uid.equals(seriesUid)) n = se.images.size();
        return n + 1;
    }

    static byte[] deflate(byte[] a) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(a); d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream(a.length / 8 + 64);
        byte[] buf = new byte[1 << 16];
        while (!d.finished()) { int n = d.deflate(buf); o.write(buf, 0, n); }
        d.end();
        return o.toByteArray();
    }

    static byte[] inflate(byte[] a, int size) throws Exception {
        Inflater f = new Inflater();
        f.setInput(a);
        byte[] out = new byte[size];
        int n = 0;
        while (n < size && !f.finished()) { int k = f.inflate(out, n, size - n); if (k == 0 && (f.needsInput() || f.needsDictionary())) break; n += k; }
        f.end();
        if (n != size) throw new Exception("The saved tissue map is incomplete.");
        return out;
    }

    /** Saves the state (settings plus tissue map) into the study. Returns the new SOP Instance UID. */
    static String saveState(Library.Series src, VrtState st, Vol3D v) throws Exception {
        String series = statesSeries(src.study), sop = DicomWriter.newUid();
        int inst = nextInstance(src.study, series);
        st.seriesUid = src.uid;
        if (st.created == 0) st.created = System.currentTimeMillis();
        JSONObject o = st.json();
        o.put("nx", v.nx); o.put("ny", v.ny); o.put("nz", v.nz);
        o.put("origin", new org.json.JSONArray(v.origin)); o.put("spacing", new org.json.JSONArray(new double[]{v.sx, v.sy, v.sz}));
        DicomWriter w = base(src, RAW_DATA, sop, series, STATES_DESC, "OT", 9901, inst);
        w.str(0x00080008, "CS", "DERIVED\\SECONDARY");
        w.str(T_CREATOR, "LO", CREATOR);
        w.bytes(T_JSON, "OB", o.toString().getBytes(UTF8));
        w.bytes(T_LABELS, "OB", deflate(v.labels));
        w.str(T_REF, "LO", src.uid);
        int[] stats = new int[3];
        Library.importBytes(w.toBytes(RAW_DATA, sop), stats);
        return sop;
    }

    static byte[] element(Dicom.DataSet ds, int tag) {
        Dicom.Element e = ds.get(tag);
        if (e == null || e.length <= 0) return null;
        return java.util.Arrays.copyOfRange(ds.buf, e.offset, e.offset + e.length);
    }

    /** Saved states for a series, newest first. */
    static List<Saved> states(Library.Series src) {
        List<Saved> out = new ArrayList<>();
        String series = statesSeries(src.study);
        List<Library.ImageInfo> infos = new ArrayList<>();
        synchronized (Library.class) { for (Library.Series se : src.study.series) if (se.uid.equals(series)) infos.addAll(se.images); }
        for (Library.ImageInfo ii : infos) {
            try {
                Dicom.DataSet ds = Dicom.parse(Library.readFile(ii.file));
                if (!CREATOR.equals(ds.str(T_CREATOR).trim()) || !src.uid.equals(ds.str(T_REF).trim())) continue;
                byte[] js = element(ds, T_JSON);
                if (js == null) continue;
                Saved s = new Saved();
                s.info = ii;
                s.json = new JSONObject(new String(js, UTF8).trim());
                s.name = s.json.optString("name", "Saved state");
                s.created = s.json.optLong("created");
                out.add(s);
            } catch (Exception ignored) { }
        }
        Collections.sort(out, new Comparator<Saved>() { public int compare(Saved a, Saved b) { return Long.compare(b.created, a.created); } });
        return out;
    }

    /** Restores a saved tissue map into v (nearest-neighbour if the saved grid differs from the current one). */
    static void loadLabels(Saved s, Vol3D v) throws Exception {
        Dicom.DataSet ds = Dicom.parse(Library.readFile(s.info.file));
        byte[] z = element(ds, T_LABELS);
        int nx = s.json.getInt("nx"), ny = s.json.getInt("ny"), nz = s.json.getInt("nz");
        byte[] lab = inflate(z, nx * ny * nz);
        if (nx == v.nx && ny == v.ny && nz == v.nz) { System.arraycopy(lab, 0, v.labels, 0, lab.length); return; }
        for (int k = 0; k < v.nz; k++) {
            int kk = Math.min(nz - 1, (int) ((k + 0.5) * nz / v.nz));
            for (int j = 0; j < v.ny; j++) {
                int jj = Math.min(ny - 1, (int) ((j + 0.5) * ny / v.ny));
                for (int i = 0; i < v.nx; i++) {
                    int ii = Math.min(nx - 1, (int) ((i + 0.5) * nx / v.nx));
                    v.labels[v.idx(i, j, k)] = lab[(kk * ny + jj) * nx + ii];
                }
            }
        }
    }

    static void delete(Saved s) { Library.deleteImage(s.info); }

    /** Adds a screenshot to the study as a Secondary Capture image. */
    static String saveCapture(Library.Series src, int[] argb, int w, int h, String seriesUid, String desc, int seriesNo, int instance) throws Exception {
        String sop = DicomWriter.newUid();
        DicomWriter wr = base(src, SECONDARY_CAPTURE, sop, seriesUid, desc, "OT", seriesNo, instance);
        wr.str(0x00080008, "CS", "DERIVED\\SECONDARY\\VOLUME_RENDERING");
        wr.str(0x00080064, "CS", "WSD");
        wr.str(0x00280301, "CS", "NO");
        wr.pixelsRGB(argb, w, h);
        int[] stats = new int[3];
        Library.importBytes(wr.toBytes(SECONDARY_CAPTURE, sop), stats);
        return sop;
    }
}
