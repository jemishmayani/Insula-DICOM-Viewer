/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** What limits how well a series can be read, reformatted, and measured. */
final class StudyQuality {
    static final int GOOD = 0, FAIR = 1, POOR = 2, INFO = 3;

    static final class Item {
        final String name, value, note;
        final int rating;
        Item(String name, String value, int rating, String note) { this.name = name; this.value = value; this.rating = rating; this.note = note; }
    }

    static final class Report {
        final List<Item> items = new ArrayList<>();
        String mpr = "", threeD = "", summary = "";
        int overall = GOOD;
        void add(String n, String v, int r, String note) { items.add(new Item(n, v, r, note == null ? "" : note)); if (r != INFO) overall = Math.max(overall, r); }
    }

    static String mm(double d) { return String.format(Locale.ROOT, d < 10 ? "%.2f mm" : "%.1f mm", d); }

    static Report analyze(Library.Series se) {
        Report r = new Report();
        List<Library.SliceRef> sl = se.slices();
        if (sl.isEmpty()) { r.summary = "This series has no images."; r.overall = POOR; return r; }
        Library.SliceRef mid = sl.get(sl.size() / 2);
        Library.Geo g = mid.geo();
        Dicom.DataSet ds = null;
        try { ds = Dicom.parse(Library.readFile(mid.info.file)); } catch (Exception ignored) { }
        boolean ct = "CT".equals(se.modality);

        r.add("Images", String.valueOf(sl.size()), INFO, null);
        r.add("Matrix", mid.info.cols + " x " + mid.info.rows, mid.info.cols >= 512 || !ct ? GOOD : FAIR, mid.info.cols < 512 && ct ? "Below 512 x 512: fine detail is limited" : null);

        double px = Math.max(g.rowSp, g.colSp);
        if (px > 0) r.add("Pixel size", mm(g.colSp) + " x " + mm(g.rowSp), px <= 0.8 ? GOOD : px <= 1.2 ? FAIR : POOR, px > 1.2 ? "Coarse pixels: small structures blur" : null);
        else r.add("Pixel size", "Not recorded", POOR, "Measurements will be in pixels, not millimetres");

        double thick = g.thick;
        if (thick > 0) r.add("Slice thickness", mm(thick), thick <= 1.25 ? GOOD : thick <= 3 ? FAIR : POOR, thick > 3 ? "Thick slices: reformats look stepped" : null);

        // Spacing between slice centres, from positions
        double spacing = Double.NaN, maxGap = 0;
        boolean uneven = false;
        double cover = 0;
        if (sl.size() > 1 && sl.get(0).geo().pos != null && g.orient != null) {
            double[] n = Library.cross(g.orient);
            double[] t = new double[sl.size()];
            boolean ok = true;
            for (int i = 0; i < sl.size(); i++) {
                double[] p = sl.get(i).geo().pos;
                if (p == null) { ok = false; break; }
                t[i] = Library.dot(p, n);
            }
            if (ok) {
                double[] d = new double[t.length - 1];
                for (int i = 0; i < d.length; i++) d[i] = Math.abs(t[i + 1] - t[i]);
                double[] s = d.clone();
                Arrays.sort(s);
                spacing = s[s.length / 2];
                maxGap = s[s.length - 1];
                uneven = spacing > 0 && (maxGap > spacing * 1.5 + 0.01 || s[0] < spacing * 0.5 - 0.01);
                cover = Math.abs(t[t.length - 1] - t[0]) + (Double.isNaN(spacing) ? 0 : spacing);
            }
        }
        if (!Double.isNaN(spacing)) {
            r.add("Slice spacing", mm(spacing), spacing <= 1 ? GOOD : spacing <= 2 ? FAIR : POOR, spacing > 2 ? "Wide spacing: coronal, sagittal, and 3D views lose detail" : null);
            if (uneven) r.add("Spacing", "Uneven (largest step " + mm(maxGap) + ")", FAIR, "Missing or repeated slices; reformats may show steps");
            if (thick > 0 && spacing > thick * 1.1) r.add("Gaps", mm(spacing - thick) + " between slices", FAIR, "Anatomy between slices is not imaged");
            r.add("Coverage", String.format(Locale.ROOT, "%.0f mm", cover), INFO, null);
            double iso = px > 0 ? spacing / px : Double.NaN;
            if (!Double.isNaN(iso)) r.add("Voxel shape", String.format(Locale.ROOT, "%.1f : 1", iso), iso <= 1.5 ? GOOD : iso <= 3 ? FAIR : POOR, iso > 3 ? "Far from isotropic" : iso <= 1.5 ? "Near isotropic: ideal for MPR and 3D" : null);
        } else if (sl.size() > 1) r.add("Slice positions", "Not recorded", POOR, "MPR and 3D are not possible");

        if (ds != null) {
            String tilt = ds.str(0x00181120).trim();
            if (!tilt.isEmpty()) {
                try { double tv = Double.parseDouble(tilt); if (Math.abs(tv) > 0.1) r.add("Gantry tilt", String.format(Locale.ROOT, "%.1f\u00b0", tv), INFO, "Corrected automatically in MPR"); } catch (Exception ignored) { }
            }
            boolean lossy = "01".equals(ds.str(0x00282110).trim());
            String ts = ds.transferSyntax;
            if (ts.equals("1.2.840.10008.1.2.4.50") || ts.equals("1.2.840.10008.1.2.4.51") || ts.equals("1.2.840.10008.1.2.4.91")) lossy = true;
            r.add("Compression", lossy ? "Lossy" : "None or lossless", lossy ? FAIR : GOOD, lossy ? "Lossy compression can hide or create fine detail" : null);
            String kv = ds.str(0x00180060).trim(), ma = ds.str(0x00181151).trim(), mas = ds.str(0x00181152).trim(), ctdi = ds.str(0x00189345).trim(), kern = ds.str(0x00181210).trim();
            if (!kv.isEmpty()) r.add("Tube voltage", kv + " kV", INFO, null);
            if (!ma.isEmpty() || !mas.isEmpty()) r.add("Tube current", (!ma.isEmpty() ? ma + " mA" : "") + (!ma.isEmpty() && !mas.isEmpty() ? ", " : "") + (!mas.isEmpty() ? mas + " mAs" : ""), INFO, null);
            if (!ctdi.isEmpty()) r.add("CTDIvol", ctdi + " mGy", INFO, null);
            if (!kern.isEmpty()) r.add("Kernel", kern.replace('\\', ' '), INFO, null);
            String agent = ds.str(0x00180010).trim();
            if (ct) r.add("Contrast", agent.isEmpty() ? "Not recorded" : agent, INFO, null);
        }

        if (ct) {
            double noise = noiseHU(mid);
            if (!Double.isNaN(noise))
                r.add("Image noise", String.format(Locale.ROOT, "about %.0f HU", noise), noise <= 12 ? GOOD : noise <= 25 ? FAIR : POOR,
                        noise > 25 ? "Noisy: low-contrast detail is hard to see; a thicker slab or smoother window helps" : null);
        }

        // Suitability verdicts
        double sp = Double.isNaN(spacing) ? thick : spacing;
        if (sl.size() < 3 || Double.isNaN(sp) || sp <= 0) { r.mpr = "Not possible"; r.threeD = "Not possible"; }
        else {
            r.mpr = sp <= 1.25 ? "Excellent" : sp <= 2.5 ? "Good" : sp <= 5 ? "Limited (stepped)" : "Poor";
            r.threeD = sl.size() < 10 ? "Too few slices" : sp <= 1 ? "Excellent" : sp <= 1.5 ? "Good" : sp <= 3 ? "Limited (blocky surfaces)" : "Poor";
        }
        r.summary = (r.overall == GOOD ? "Good quality. " : r.overall == FAIR ? "Usable, with limitations. " : "Significant limitations. ")
                + "MPR: " + r.mpr + ". 3D: " + r.threeD + ".";
        return r;
    }

    /** Noise as the robust SD of neighbouring-pixel differences in soft tissue (-100 to 150 HU), which ignores edges. */
    static double noiseHU(Library.SliceRef s) {
        try {
            RawImage im = Library.load(s);
            if (im.rgb) return Double.NaN;
            int n = 0;
            float[] d = new float[Math.min(200000, im.w * im.h)];
            for (int y = 0; y < im.h && n < d.length; y += 2)
                for (int x = 0; x + 1 < im.w && n < d.length; x += 2) {
                    double a = im.pix[y * im.w + x] * im.slope + im.intercept, b = im.pix[y * im.w + x + 1] * im.slope + im.intercept;
                    if (a > -100 && a < 150 && b > -100 && b < 150) d[n++] = (float) Math.abs(a - b);
                }
            if (n < 500) return Double.NaN;
            float[] v = Arrays.copyOf(d, n);
            Arrays.sort(v);
            double med = v[n / 2];
            return 1.4826 * med / Math.sqrt(2);
        } catch (Throwable t) { return Double.NaN; }
    }
}
