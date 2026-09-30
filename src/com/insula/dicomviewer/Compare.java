/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds a patient's other studies and the series in them that best matches the one being read. */
final class Compare {

    static String norm(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""); }

    /** Same patient: matching Patient ID when both have one, otherwise matching name and birth date. */
    static boolean samePatient(Library.Study a, Library.Study b) {
        String ia = norm(a.patientId), ib = norm(b.patientId);
        if (!ia.isEmpty() && !ib.isEmpty()) return ia.equals(ib);
        String na = norm(a.patientName), nb = norm(b.patientName);
        return !na.isEmpty() && na.equals(nb) && norm(a.birthDate).equals(norm(b.birthDate));
    }

    /** Other studies of the same patient that have images, newest first. */
    static List<Library.Study> related(Library.Study s, List<Library.Study> all) {
        List<Library.Study> out = new ArrayList<>();
        for (Library.Study o : all) {
            if (o == s || o.uid.equals(s.uid) || !samePatient(s, o)) continue;
            boolean images = false;
            for (Library.Series se : o.series) if (!se.slices().isEmpty()) images = true;
            if (images) out.add(o);
        }
        Collections.sort(out, new Comparator<Library.Study>() {
            public int compare(Library.Study a, Library.Study b) { return (b.date + b.time).compareTo(a.date + a.time); }
        });
        return out;
    }

    static Set<String> tokens(String s) {
        Set<String> t = new HashSet<>();
        for (String w : (s == null ? "" : s.toUpperCase(Locale.ROOT)).split("[^A-Z0-9]+")) if (w.length() > 0) t.add(w);
        return t;
    }

    static double[] normal(Library.Series se) {
        List<Library.SliceRef> sl = se.slices();
        if (sl.isEmpty() || sl.get(0).geo().orient == null) return null;
        return Library.cross(sl.get(0).geo().orient);
    }

    /** Scores how comparable two series are: modality, plane, description, slice thickness, and size. */
    static double score(Library.Series ref, Library.Series c) {
        if (c.slices().isEmpty()) return -1;
        double s = 0;
        if (norm(ref.modality).equals(norm(c.modality))) s += 5;
        double[] a = normal(ref), b = normal(c);
        if (a != null && b != null) s += 3 * Math.abs(Library.dot(a, b));
        Set<String> ta = tokens(ref.desc), tb = tokens(c.desc);
        if (!ta.isEmpty() && !tb.isEmpty()) {
            Set<String> inter = new HashSet<>(ta); inter.retainAll(tb);
            Set<String> uni = new HashSet<>(ta); uni.addAll(tb);
            s += 4.0 * inter.size() / uni.size();
        }
        double t1 = ref.slices().get(0).geo().thick, t2 = c.slices().get(0).geo().thick;
        if (t1 > 0 && t2 > 0) s += 1.0 / (1 + Math.abs(t1 - t2));
        int n1 = ref.slices().size(), n2 = c.slices().size();
        s += Math.min(n1, n2) / (double) Math.max(1, Math.max(n1, n2));
        return s;
    }

    static Library.Series bestMatch(Library.Series ref, Library.Study other) {
        Library.Series best = null;
        double bs = -1;
        for (Library.Series c : other.series) {
            double sc = score(ref, c);
            if (sc > bs) { bs = sc; best = c; }
        }
        return best;
    }

    /** Position of slice i along a unit normal, or NaN if unknown. */
    static double along(SliceProvider p, int i, double[] n) {
        double[] pos = p.position(i);
        return pos == null ? Double.NaN : Library.dot(pos, n);
    }

    /** Stack centre along a normal, used to line up two studies whose coordinates differ. */
    static double centre(SliceProvider p, double[] n) {
        double a = along(p, 0, n), b = along(p, p.count() - 1, n);
        return Double.isNaN(a) || Double.isNaN(b) ? Double.NaN : (a + b) / 2;
    }

    /** Index in 'to' whose position is closest to target (mm along n), or -1. */
    static int nearest(SliceProvider to, double[] n, double target) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < to.count(); i++) {
            double t = along(to, i, n);
            if (Double.isNaN(t)) continue;
            double d = Math.abs(t - target);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }
}
