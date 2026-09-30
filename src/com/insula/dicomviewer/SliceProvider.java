/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.List;
import java.util.Set;

/** Anything a DicomView can page through: a stored series or a reconstructed MPR plane. */
public interface SliceProvider {
    int count();
    RawImage image(int i) throws Exception;
    double[] orientation(int i);
    double[] position(int i);
    String frameOfRef();
    String[] patientLines();
    String[] studyLines();
    String sliceInfo(int i);
    Set<Integer> keyImages();
    Library.SliceRef ref(int i);
    String seriesName();
    int seriesNumber();
    /** True if images come from storage and should be loaded off the UI thread. */
    boolean async();
    /** The image if it's ready right now, else null (async providers only). */
    RawImage peek(int i);
    /** Loads the image in the background (async providers only). */
    void load(int i, Library.Done cb);

    final class SeriesProvider implements SliceProvider {
        public final Library.Series series;
        final List<Library.SliceRef> sl;

        public SeriesProvider(Library.Series s) { series = s; sl = s.slices(); }

        public int count() { return sl.size(); }
        public RawImage image(int i) throws Exception { return Library.load(sl.get(i)); }
        public double[] orientation(int i) { return sl.get(i).geo().orient; }
        public double[] position(int i) { return sl.get(i).geo().pos; }
        public String frameOfRef() { return sl.isEmpty() ? null : sl.get(0).info.frameOfRef; }
        /** Key-image marks are stored by SOP instance, so they persist and travel with study sets. */
        final Set<Integer> keySet = new java.util.AbstractSet<Integer>() {
            String k(Object o) { if (!(o instanceof Integer)) return null; Library.SliceRef r = ref((Integer) o); return r == null ? null : AnnStore.key(r); }
            @Override public boolean contains(Object o) { String k = k(o); return k != null && AnnStore.isKey(k); }
            @Override public boolean add(Integer i) { String k = k(i); return k != null && AnnStore.setKey(k, true); }
            @Override public boolean remove(Object o) { String k = k(o); return k != null && AnnStore.setKey(k, false); }
            @Override public java.util.Iterator<Integer> iterator() {
                List<Integer> l = new java.util.ArrayList<>();
                for (int i = 0; i < sl.size(); i++) if (AnnStore.isKey(AnnStore.key(sl.get(i)))) l.add(i);
                return l.iterator();
            }
            @Override public int size() { int n = 0; for (Library.SliceRef r : sl) if (AnnStore.isKey(AnnStore.key(r))) n++; return n; }
        };
        public Set<Integer> keyImages() { return keySet; }
        public Library.SliceRef ref(int i) { return i >= 0 && i < sl.size() ? sl.get(i) : null; }
        public String seriesName() { return series.desc.isEmpty() ? "Series " + series.number : series.desc; }
        public int seriesNumber() { return series.number; }
        public boolean async() { return true; }
        public RawImage peek(int i) { return i >= 0 && i < sl.size() ? Library.peek(sl.get(i)) : null; }
        public void load(int i, Library.Done cb) { Library.loadAsync(sl.get(i), cb); }

        public String[] patientLines() {
            Library.Study s = series.study;
            String dob = Library.fmtDate(s.birthDate);
            String ds = (dob.isEmpty() ? "" : "DOB " + dob) + (s.sex.isEmpty() ? "" : (dob.isEmpty() ? "" : "  ") + s.sex) + (s.age.isEmpty() ? "" : "  " + s.age);
            return new String[]{s.patientName, s.patientId.isEmpty() ? "" : "ID " + s.patientId, ds, s.institution};
        }

        public String[] studyLines() {
            Library.Study s = series.study;
            return new String[]{s.desc, (Library.fmtDate(s.date) + " " + Library.fmtTime(s.time)).trim(), series.modality + "  " + series.label()};
        }

        public String sliceInfo(int i) {
            Library.SliceRef r = sl.get(i);
            Library.Geo g = r.geo();
            StringBuilder sb = new StringBuilder();
            if (!Double.isNaN(g.sliceLoc)) sb.append(String.format("Loc %.1f mm", g.sliceLoc));
            else if (g.pos != null && g.orient != null) sb.append(String.format("Pos %.1f mm", Library.dot(g.pos, Library.cross(g.orient))));
            if (g.thick > 0) sb.append(String.format("  Thk %.1f mm", g.thick));
            if (r.info.frames > 1) sb.append(sb.length() > 0 ? "  " : "").append("Frame ").append(r.frame + 1).append('/').append(r.info.frames);
            return sb.toString();
        }
    }
}
