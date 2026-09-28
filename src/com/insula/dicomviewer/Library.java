package com.insula.dicomviewer;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Library {

    public static final class Geo {
        public double[] pos, orient;
        public double rowSp, colSp, thick, slope = 1, intercept = 0, wc = Double.NaN, ww = Double.NaN, sliceLoc = Double.NaN;
    }

    public static final class ImageInfo {
        public File file;
        public String sopUid, sopClass, studyUid, seriesUid, frameOfRef, patientName, patientId, birthDate, sex, age,
                studyDate, studyTime, studyDesc, accession, seriesDesc, modality, bodyPart, institution, ts;
        public int seriesNumber, instanceNumber, frames, rows, cols;
        public boolean burnedIn, hasPixels;
        public double frameTime;
        public Geo[] geo;

        public Geo geo(int f) { return geo[Math.min(f, geo.length - 1)]; }

        static ImageInfo from(File f, Dicom.DataSet ds) {
            ImageInfo i = new ImageInfo();
            i.file = f;
            i.sopUid = ds.str(0x00080018);
            i.sopClass = ds.str(0x00080016);
            i.studyUid = ds.str(0x0020000D);
            if (i.studyUid.isEmpty()) i.studyUid = "unknown-study";
            i.seriesUid = ds.str(0x0020000E);
            if (i.seriesUid.isEmpty()) i.seriesUid = i.studyUid + ".unknown-series";
            i.frameOfRef = ds.getString(0x00200052);
            i.patientName = pn(ds.str(0x00100010));
            i.patientId = ds.str(0x00100020);
            i.birthDate = ds.str(0x00100030);
            i.sex = ds.str(0x00100040);
            i.age = ds.str(0x00101010);
            i.studyDate = ds.str(0x00080020);
            i.studyTime = ds.str(0x00080030);
            i.studyDesc = ds.str(0x00081030);
            i.accession = ds.str(0x00080050);
            i.seriesDesc = ds.str(0x0008103E);
            i.modality = ds.str(0x00080060);
            i.bodyPart = ds.str(0x00180015);
            i.institution = ds.str(0x00080080);
            i.ts = ds.transferSyntax;
            i.seriesNumber = ds.getInt(0x00200011, 0);
            i.instanceNumber = ds.getInt(0x00200013, 0);
            i.frames = Math.max(1, ds.getInt(0x00280008, 1));
            i.rows = ds.getInt(0x00280010, 0);
            i.cols = ds.getInt(0x00280011, 0);
            i.burnedIn = "YES".equalsIgnoreCase(ds.getString(0x00280301));
            i.frameTime = ds.getDouble(0x00181063, 0);
            i.hasPixels = ds.has(Dicom.PIXEL_DATA) && i.rows > 0 && i.cols > 0;
            boolean enh = ds.has(0x52009230) || ds.has(0x52009229);
            if (enh && i.frames > 1) {
                i.geo = new Geo[i.frames];
                for (int k = 0; k < i.frames; k++) i.geo[k] = geoFrom(ds, k, true);
            } else i.geo = new Geo[]{geoFrom(ds, 0, enh)};
            return i;
        }
    }

    static String pn(String s) {
        int eq = s.indexOf('=');
        if (eq >= 0) s = s.substring(0, eq);
        String[] p = s.split("\\^");
        StringBuilder sb = new StringBuilder();
        // Family^Given^Middle^Prefix^Suffix -> "Family, Given Middle"
        if (p.length > 0) sb.append(p[0].trim());
        if (p.length > 1 && !p[1].trim().isEmpty()) { sb.append(", ").append(p[1].trim()); if (p.length > 2 && !p[2].trim().isEmpty()) sb.append(' ').append(p[2].trim()); }
        return sb.toString().trim();
    }

    static Dicom.DataSet fg(Dicom.DataSet ds, int frame, int seqTag) {
        Dicom.Element pf = ds.get(0x52009230);
        if (pf != null && pf.items != null && frame < pf.items.size()) {
            Dicom.Element s = pf.items.get(frame).get(seqTag);
            if (s != null && s.items != null && !s.items.isEmpty()) return s.items.get(0);
        }
        Dicom.Element sh = ds.get(0x52009229);
        if (sh != null && sh.items != null && !sh.items.isEmpty()) {
            Dicom.Element s = sh.items.get(0).get(seqTag);
            if (s != null && s.items != null && !s.items.isEmpty()) return s.items.get(0);
        }
        return null;
    }

    static Geo geoFrom(Dicom.DataSet ds, int frame, boolean enhanced) {
        Geo g = new Geo();
        g.pos = ds.getDoubles(0x00200032);
        g.orient = ds.getDoubles(0x00200037);
        double[] ps = ds.getDoubles(0x00280030);
        if (ps == null) ps = ds.getDoubles(0x00181164);
        g.thick = ds.getDouble(0x00180050, 0);
        g.slope = ds.getDouble(0x00281053, 1);
        g.intercept = ds.getDouble(0x00281052, 0);
        double[] wc = ds.getDoubles(0x00281050), ww = ds.getDoubles(0x00281051);
        if (enhanced) {
            Dicom.DataSet it = fg(ds, frame, 0x00209113);
            if (it != null) { double[] p = it.getDoubles(0x00200032); if (p != null) g.pos = p; }
            it = fg(ds, frame, 0x00209116);
            if (it != null) { double[] o = it.getDoubles(0x00200037); if (o != null) g.orient = o; }
            it = fg(ds, frame, 0x00289110);
            if (it != null) { double[] p = it.getDoubles(0x00280030); if (p != null) ps = p; double t = it.getDouble(0x00180050, Double.NaN); if (!Double.isNaN(t)) g.thick = t; }
            it = fg(ds, frame, 0x00289145);
            if (it != null) { g.slope = it.getDouble(0x00281053, g.slope); g.intercept = it.getDouble(0x00281052, g.intercept); }
            it = fg(ds, frame, 0x00289132);
            if (it != null) { double[] a = it.getDoubles(0x00281050), b = it.getDoubles(0x00281051); if (a != null) wc = a; if (b != null) ww = b; }
        }
        if (ps != null && ps.length >= 2 && ps[0] > 0 && ps[1] > 0) { g.rowSp = ps[0]; g.colSp = ps[1]; }
        if (wc != null && ww != null && wc.length > 0 && ww.length > 0 && ww[0] > 0) { g.wc = wc[0]; g.ww = ww[0]; }
        if (g.pos != null && g.pos.length < 3) g.pos = null;
        if (g.orient != null && g.orient.length < 6) g.orient = null;
        g.sliceLoc = ds.getDouble(0x00201041, Double.NaN);
        if (g.slope == 0) g.slope = 1;
        return g;
    }

    public static final class SliceRef {
        public final ImageInfo info;
        public final int frame;
        SliceRef(ImageInfo i, int f) { info = i; frame = f; }
        public Geo geo() { return info.geo(frame); }
    }

    public static final class Series {
        public final String uid;
        public String desc, modality, bodyPart;
        public int number;
        public final Study study;
        public final List<ImageInfo> images = new ArrayList<>();
        public final Set<Integer> keyImages = new HashSet<>();
        private List<SliceRef> slices;

        Series(ImageInfo i, Study s) { uid = i.seriesUid; desc = i.seriesDesc; modality = i.modality; number = i.seriesNumber; bodyPart = i.bodyPart; study = s; }
        synchronized void add(ImageInfo i) { images.add(i); slices = null; }
        public synchronized List<SliceRef> slices() { if (slices == null) slices = build(); return slices; }
        public String label() { return "Series " + number + (desc.isEmpty() ? "" : ": " + desc); }
        public synchronized ImageInfo first() { return images.isEmpty() ? null : images.get(0); }

        List<SliceRef> build() {
            List<SliceRef> l = new ArrayList<>();
            for (ImageInfo img : images) { if (!img.hasPixels) continue; for (int f = 0; f < img.frames; f++) l.add(new SliceRef(img, f)); }
            boolean geo = l.size() > 1;
            double[] o0 = null;
            for (SliceRef s : l) {
                Geo g = s.geo();
                if (g.pos == null || g.orient == null) { geo = false; break; }
                if (o0 == null) o0 = g.orient;
                else for (int k = 0; k < 6; k++) if (Math.abs(g.orient[k] - o0[k]) > 1e-3) { geo = false; break; }
                if (!geo) break;
            }
            Comparator<SliceRef> byInstance = new Comparator<SliceRef>() {
                public int compare(SliceRef a, SliceRef b) {
                    int c = Integer.compare(a.info.instanceNumber, b.info.instanceNumber);
                    return c != 0 ? c : Integer.compare(a.frame, b.frame);
                }
            };
            if (geo) {
                final double[] n = cross(o0);
                Collections.sort(l, new Comparator<SliceRef>() {
                    public int compare(SliceRef a, SliceRef b) { return Double.compare(dot(a.geo().pos, n), dot(b.geo().pos, n)); }
                });
                // Keep acquisition direction: if the lowest-instance slice sits at the far end, reverse.
                SliceRef firstAcq = Collections.min(l, byInstance);
                if (l.indexOf(firstAcq) > l.size() / 2) Collections.reverse(l);
            } else Collections.sort(l, byInstance);
            return l;
        }
    }

    public static final class Study {
        public final String uid;
        public String patientName, patientId, birthDate, sex, age, date, time, desc, accession, institution;
        public final List<Series> series = new ArrayList<>();
        Study(ImageInfo i) {
            uid = i.studyUid; patientName = i.patientName; patientId = i.patientId; birthDate = i.birthDate; sex = i.sex; age = i.age;
            date = i.studyDate; time = i.studyTime; desc = i.studyDesc; accession = i.accession; institution = i.institution;
        }
        public String modalities() {
            LinkedHashSet<String> m = new LinkedHashSet<>();
            for (Series s : series) if (!s.modality.isEmpty()) m.add(s.modality);
            StringBuilder sb = new StringBuilder();
            for (String s : m) { if (sb.length() > 0) sb.append('/'); sb.append(s); }
            return sb.toString();
        }
        public int imageCount() { int n = 0; for (Series s : series) n += s.images.size(); return n; }
        long size = -1;

        public synchronized long bytes() {
            if (size < 0) { long b = 0; for (Series s : series) for (ImageInfo i : s.images) b += i.file.length(); size = b; }
            return size;
        }

        /** "11Y/M" from Patient's Age, or computed from birth and study dates. */
        public String ageSex() {
            String a = age == null ? "" : age.trim();
            if (a.length() == 4 && Character.isDigit(a.charAt(0))) {
                String num = a.substring(0, 3).replaceFirst("^0+(?=\\d)", "");
                a = num + a.charAt(3);
            } else if (a.isEmpty() && birthDate.length() == 8 && date.length() == 8) {
                try {
                    int by = Integer.parseInt(birthDate.substring(0, 4)), bm = Integer.parseInt(birthDate.substring(4, 6)), bd = Integer.parseInt(birthDate.substring(6));
                    int sy = Integer.parseInt(date.substring(0, 4)), sm = Integer.parseInt(date.substring(4, 6)), sd = Integer.parseInt(date.substring(6));
                    int y = sy - by - ((sm < bm || (sm == bm && sd < bd)) ? 1 : 0);
                    if (y >= 2) a = y + "Y";
                    else { int m = (sy - by) * 12 + sm - bm - (sd < bd ? 1 : 0); a = Math.max(0, m) + "M"; }
                } catch (Exception ignored) { }
            }
            String sx = sex == null ? "" : sex.trim();
            if (a.isEmpty()) return sx;
            return sx.isEmpty() ? a : a + "/" + sx;
        }

        public String displayName(boolean teacher) {
            String n = teacher ? "Teaching case" : (patientName.isEmpty() ? "No name" : patientName.toUpperCase());
            String as = ageSex();
            return as.isEmpty() ? n : n + " " + as;
        }

        public Series firstImageSeries() {
            for (Series s : series) if (!s.slices().isEmpty()) return s;
            return series.isEmpty() ? null : series.get(0);
        }
    }

    public interface Progress { void update(int done, int total, String msg); }

    public static final List<Study> studies = new ArrayList<>();
    static final Map<String, Study> studyMap = new HashMap<>();
    static final Map<String, Series> seriesMap = new HashMap<>();
    static final Map<String, ImageInfo> bySop = new HashMap<>();
    public static File dir, exportDir;
    public static volatile boolean scanned;

    public static void init(Context c) {
        dir = new File(c.getFilesDir(), "library");
        dir.mkdirs();
        exportDir = new File(c.getCacheDir(), "exports");
        exportDir.mkdirs();
        maxCache = Runtime.getRuntime().maxMemory() / 4;
        AnnStore.init(c.getFilesDir());
    }

    public static synchronized List<Series> allSeries() {
        List<Series> l = new ArrayList<>();
        for (Study s : studies) l.addAll(s.series);
        return l;
    }

    public static synchronized Series series(String uid) { return seriesMap.get(uid); }

    public static void scan(Progress p) {
        File[] fs = dir.listFiles();
        if (fs == null) fs = new File[0];
        int i = 0;
        for (File f : fs) {
            i++;
            if (!f.getName().endsWith(".dcm")) continue;
            try {
                Dicom.DataSet ds = null;
                long len = f.length();
                try {
                    ds = Dicom.parse(readHead(f, 262144));
                    if (!ds.has(Dicom.PIXEL_DATA) && len > 262144) ds = null;
                } catch (Throwable t) { ds = null; }
                if (ds == null) ds = Dicom.parse(readFile(f));
                add(ImageInfo.from(f, ds));
            } catch (Throwable ignored) { }
            if (p != null && (i % 10 == 0 || i == fs.length)) p.update(i, fs.length, "Loading library");
        }
        scanned = true;
    }

    static synchronized boolean add(ImageInfo info) {
        if (!info.sopUid.isEmpty() && bySop.containsKey(info.sopUid)) return false;
        bySop.put(info.sopUid, info);
        Study st = studyMap.get(info.studyUid);
        if (st == null) { st = new Study(info); studyMap.put(st.uid, st); studies.add(st); }
        Series se = seriesMap.get(info.seriesUid);
        if (se == null) {
            se = new Series(info, st);
            seriesMap.put(se.uid, se);
            st.series.add(se);
            Collections.sort(st.series, new Comparator<Series>() { public int compare(Series a, Series b) { return Integer.compare(a.number, b.number); } });
        }
        se.add(info);
        st.size = -1;
        return true;
    }

    /** stats: [0]=imported, [1]=skipped (not DICOM / unreadable), [2]=duplicates */
    public static ImageInfo importBytes(byte[] d, int[] stats) {
        Dicom.DataSet ds;
        try { ds = Dicom.parse(d); } catch (Throwable t) { stats[1]++; return null; }
        if (!ds.looksValid()) { stats[1]++; return null; }
        String cls = ds.getString(0x00020002);
        if (cls == null) cls = ds.getString(0x00080016);
        if ("1.2.840.10008.1.3.10".equals(cls)) { stats[1]++; return null; } // DICOMDIR: referenced files are imported directly
        String sop = ds.getString(0x00080018);
        if (sop == null || sop.isEmpty()) sop = "noid-" + Integer.toHexString(Arrays.hashCode(d));
        synchronized (Library.class) { if (bySop.containsKey(sop)) { stats[2]++; return null; } }
        File f = new File(dir, safe(sop) + ".dcm");
        try {
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(d);
            fo.close();
        } catch (IOException e) { stats[1]++; return null; }
        ImageInfo info = ImageInfo.from(f, ds);
        if (info.sopUid.isEmpty()) info.sopUid = sop;
        if (add(info)) stats[0]++; else stats[2]++;
        return info;
    }

    public static void importStream(InputStream in, int[] stats, Progress p) throws IOException { importStream(in, stats, p, null); }

    /** @param manifests receives the contents of any study-set manifest found inside a ZIP. */
    public static void importStream(InputStream in, int[] stats, Progress p, List<byte[]> manifests) throws IOException {
        BufferedInputStream b = new BufferedInputStream(in, 65536);
        b.mark(256);
        byte[] head = new byte[132];
        int r = readFully(b, head);
        b.reset();
        if (r >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            ZipInputStream z = new ZipInputStream(b);
            ZipEntry e;
            int n = 0;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (e.getSize() > 1024L * 1024 * 1024) continue;
                String nm = e.getName();
                if (nm.endsWith(Backup.SET_MANIFEST)) { byte[] mf = readAll(z); if (manifests != null) manifests.add(mf); continue; }
                byte[] d = readAll(z);
                if (looksDicom(d, d.length)) importBytes(d, stats); else stats[1]++;
                n++;
                if (p != null && n % 5 == 0) p.update(stats[0], -1, "Importing ZIP");
            }
            return;
        }
        if (!looksDicom(head, r)) { stats[1]++; return; }
        importBytes(readAll(b), stats);
    }

    static boolean looksDicom(byte[] h, int n) {
        if (n >= 132 && h[128] == 'D' && h[129] == 'I' && h[130] == 'C' && h[131] == 'M') return true;
        if (n < 8) return false;
        int g = (h[0] & 255) | ((h[1] & 255) << 8);
        return g == 0x0002 || g == 0x0008 || g == 0x0010 || g == 0x0018 || g == 0x0020 || g == 0x0028;
    }

    static String safe(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 120; i++) {
            char c = s.charAt(i);
            sb.append((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || c == '-' ? c : '_');
        }
        return sb.toString();
    }

    public static synchronized void deleteSeries(Series se) {
        for (ImageInfo i : new ArrayList<>(se.images)) { i.file.delete(); bySop.remove(i.sopUid); AnnStore.removeSop(i.sopUid); }
        seriesMap.remove(se.uid);
        se.study.series.remove(se);
        if (se.study.series.isEmpty()) { studies.remove(se.study); studyMap.remove(se.study.uid); }
        thumbs.remove(se.uid);
        clearCache();
    }

    public static synchronized void deleteStudy(Study st) {
        for (Series se : new ArrayList<>(st.series)) deleteSeries(se);
    }

    public static synchronized void deleteAll() {
        for (Study st : new ArrayList<>(studies)) deleteStudy(st);
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    // ---- Frame cache ----
    static final LinkedHashMap<String, RawImage> cache = new LinkedHashMap<>(64, 0.75f, true);
    static long cacheBytes, maxCache = 64L * 1024 * 1024;
    static final LinkedHashMap<String, Dicom.DataSet> parsed = new LinkedHashMap<>(4, 0.75f, true);
    static final Object LOAD = new Object();
    static final ExecutorService EX = Executors.newSingleThreadExecutor();
    static final ExecutorService THUMB = Executors.newSingleThreadExecutor();
    static volatile int gen;
    static final Map<String, Bitmap> thumbs = Collections.synchronizedMap(new HashMap<String, Bitmap>());

    public static void clearCache() {
        synchronized (cache) { cache.clear(); cacheBytes = 0; }
        synchronized (LOAD) { parsed.clear(); }
    }

    public static Dicom.DataSet parsedFile(File f) throws Exception {
        synchronized (LOAD) {
            Dicom.DataSet ds = parsed.get(f.getAbsolutePath());
            if (ds == null) {
                ds = Dicom.parse(readFile(f));
                parsed.put(f.getAbsolutePath(), ds);
                while (parsed.size() > 2) { String k = parsed.keySet().iterator().next(); parsed.remove(k); }
            }
            return ds;
        }
    }

    public static RawImage load(SliceRef s) throws Exception {
        String k = s.info.file.getAbsolutePath() + "#" + s.frame;
        synchronized (cache) { RawImage c = cache.get(k); if (c != null) return c; }
        synchronized (LOAD) {
            synchronized (cache) { RawImage c = cache.get(k); if (c != null) return c; }
            RawImage r;
            try {
                Dicom.DataSet ds = parsedFile(s.info.file);
                r = PixelDecoder.decode(ds, s.frame);
            } catch (OutOfMemoryError oom) {
                clearCache();
                throw new Exception("Not enough memory to decode this image.");
            }
            Geo g = s.geo();
            r.slope = g.slope; r.intercept = g.intercept; r.rowSp = g.rowSp; r.colSp = g.colSp;
            r.wc = g.wc; r.ww = g.ww;
            r.units = "CT".equals(s.info.modality) ? "HU" : "";
            r.computeRange();
            synchronized (cache) {
                cache.put(k, r);
                cacheBytes += r.bytes();
                while (cacheBytes > maxCache && cache.size() > 1) {
                    String first = cache.keySet().iterator().next();
                    RawImage old = cache.remove(first);
                    if (old != null) cacheBytes -= old.bytes();
                }
            }
            return r;
        }
    }

    public static void prefetch(final List<SliceRef> list, final int center) {
        final int g = ++gen;
        EX.submit(new Runnable() {
            public void run() {
                for (int d = 1; d <= 4; d++) {
                    for (int s : new int[]{center + d, center - d}) {
                        if (gen != g) return;
                        if (s >= 0 && s < list.size()) { try { load(list.get(s)); } catch (Throwable ignored) { } }
                    }
                }
            }
        });
    }

    public interface ThumbCallback { void done(); }

    public static Bitmap thumbnail(final Series se, final ThumbCallback cb) {
        Bitmap b = thumbs.get(se.uid);
        if (b != null || thumbs.containsKey(se.uid)) return b;
        thumbs.put(se.uid, null);
        THUMB.submit(new Runnable() {
            public void run() {
                try {
                    List<SliceRef> sl = se.slices();
                    if (sl.isEmpty()) return;
                    RawImage r = load(sl.get(sl.size() / 2));
                    thumbs.put(se.uid, r.thumbnail(160));
                    cb.done();
                } catch (Throwable ignored) { }
            }
        });
        return null;
    }

    // ---- IO helpers ----
    public static byte[] readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            long len = f.length();
            if (len > Integer.MAX_VALUE - 16) throw new IOException("File too large");
            byte[] d = new byte[(int) len];
            int o = 0;
            while (o < d.length) { int n = in.read(d, o, d.length - o); if (n < 0) break; o += n; }
            return d;
        } finally { in.close(); }
    }

    static byte[] readHead(File f, int max) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] d = new byte[(int) Math.min(max, f.length())];
            int o = 0;
            while (o < d.length) { int n = in.read(d, o, d.length - o); if (n < 0) break; o += n; }
            return d;
        } finally { in.close(); }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
        byte[] b = new byte[1 << 16];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }

    static int readFully(InputStream in, byte[] b) throws IOException {
        int o = 0;
        while (o < b.length) { int n = in.read(b, o, b.length - o); if (n < 0) break; o += n; }
        return o;
    }

    // ---- vector math ----
    public static double[] cross(double[] o) {
        return new double[]{o[1] * o[5] - o[2] * o[4], o[2] * o[3] - o[0] * o[5], o[0] * o[4] - o[1] * o[3]};
    }
    public static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    public static String fmtDate(String d) {
        if (d == null || d.length() != 8) return d == null ? "" : d;
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
    }
    public static String fmtDateLong(String d) {
        if (d == null || d.length() != 8) return d == null ? "" : d;
        try {
            java.util.Date dt = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).parse(d);
            return new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(dt);
        } catch (Exception e) { return fmtDate(d); }
    }

    public static String fmtSize(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f GB", b / 1073741824.0);
        if (b >= 1024L * 1024) return Math.round(b / 1048576.0) + " MB";
        return Math.max(1, Math.round(b / 1024.0)) + " KB";
    }

    public static synchronized Study study(String uid) { return studyMap.get(uid); }

    public static String fmtTime(String t) {
        if (t == null || t.length() < 4) return "";
        return t.substring(0, 2) + ":" + t.substring(2, 4);
    }
}
