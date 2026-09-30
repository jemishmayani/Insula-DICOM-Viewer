/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.Random;

/**
 * Builds a synthetic CT head phantom (no real patient) as two studies of one "patient": a prior and a current exam
 * in which a hyperdense lesion has grown and the head sits 6 mm higher in the scanner. Used to explore the app.
 * UIDs are fixed, so creating the demo twice doesn't duplicate it.
 */
final class DemoStudy {
    static final String PATIENT_ID = "INSULA-DEMO";
    static final String PATIENT_NAME = "DEMO^HEAD PHANTOM";
    static final String ROOT = "2.25.3301000000000000000000000";
    static final int SIZE = 256, SLICES = 48;
    static final double PIXEL = 0.9, THICK = 3.0;

    interface Progress { void update(int done, int total); }

    /** Imports both demo studies. Returns {imported, alreadyPresent}. */
    static int[] create(Progress p) throws Exception {
        int[] stats = new int[3];
        int total = SLICES * 2;
        make(0, stats, p, total);
        make(1, stats, p, total);
        return new int[]{stats[0], stats[2]};
    }

    static String studyUid(int which) { return ROOT + (which == 0 ? "11" : "21"); }

    static void make(int which, int[] stats, Progress p, int total) throws Exception {
        boolean current = which == 1;
        String study = studyUid(which), series = ROOT + (current ? "22" : "12"), frame = ROOT + (current ? "23" : "13");
        String date = current ? "20260312" : "20250312";
        double lesionR = current ? 12 : 8, zShift = current ? 6 : 0;
        Random rnd = new Random(current ? 17 : 11);
        double half = SIZE * PIXEL / 2;
        for (int k = 0; k < SLICES; k++) {
            double z = -SLICES * THICK / 2 + k * THICK + zShift;           // patient z of this slice (mm)
            short[] px = new short[SIZE * SIZE];
            for (int j = 0; j < SIZE; j++) {
                double y = -half + (j + 0.5) * PIXEL;
                for (int i = 0; i < SIZE; i++) {
                    double x = -half + (i + 0.5) * PIXEL;
                    px[j * SIZE + i] = (short) Math.round(value(x, y, z - zShift, lesionR) + rnd.nextGaussian() * 5);
                }
            }
            DicomWriter w = new DicomWriter();
            String sop = ROOT + (current ? "3" : "2") + String.format(java.util.Locale.ROOT, "%03d", k + 1);
            String cls = "1.2.840.10008.5.1.4.1.1.2";
            w.str(0x00080005, "CS", "ISO_IR 100");
            w.str(0x00080008, "CS", "ORIGINAL\\PRIMARY\\AXIAL");
            w.str(0x00080016, "UI", cls);
            w.str(0x00080018, "UI", sop);
            w.str(0x00080020, "DA", date);
            w.str(0x00080030, "TM", "101500");
            w.str(0x00080050, "SH", current ? "DEMO2" : "DEMO1");
            w.str(0x00080060, "CS", "CT");
            w.str(0x00080080, "LO", "Insula demo (synthetic data)");
            w.str(0x00081030, "LO", "Demo CT head (synthetic phantom)");
            w.str(0x0008103E, "LO", "Axial head 3 mm");
            w.str(0x00100010, "PN", PATIENT_NAME);
            w.str(0x00100020, "LO", PATIENT_ID);
            w.str(0x00100030, "DA", "19800101");
            w.str(0x00100040, "CS", "O");
            w.str(0x00180015, "CS", "HEAD");
            w.str(0x00180050, "DS", "3");
            w.str(0x0020000D, "UI", study);
            w.str(0x0020000E, "UI", series);
            w.str(0x00200011, "IS", "2");
            w.str(0x00200013, "IS", String.valueOf(k + 1));
            w.ds(0x00200032, -half + PIXEL / 2, -half + PIXEL / 2, z);
            w.ds(0x00200037, 1, 0, 0, 0, 1, 0);
            w.str(0x00200052, "UI", frame);
            w.ds(0x00201041, z);
            w.us(0x00280002, 1);
            w.str(0x00280004, "CS", "MONOCHROME2");
            w.us(0x00280010, SIZE);
            w.us(0x00280011, SIZE);
            w.ds(0x00280030, PIXEL, PIXEL);
            w.us(0x00280100, 16); w.us(0x00280101, 16); w.us(0x00280102, 15); w.us(0x00280103, 1);
            w.ds(0x00281050, 40);
            w.ds(0x00281051, 80);
            w.ds(0x00281052, 0);
            w.ds(0x00281053, 1);
            w.str(0x00281054, "LO", "HU");
            w.pixels16(px);
            Library.importBytes(w.toBytes(cls, sop), stats);
            if (p != null) p.update(which * SLICES + k + 1, total);
        }
    }

    /** Hounsfield value of the phantom at patient position (x, y, z) in mm. */
    static double value(double x, double y, double z, double lesionR) {
        double outer = sq(x / 74) + sq(y / 92) + sq(z / 70);
        if (outer > 1) return -1000;                                              // air
        double inner = sq(x / 67) + sq(y / 85) + sq(z / 63);
        if (inner > 1) return 1100;                                               // skull
        double v = 32;                                                            // brain
        double wm = sq(x / 45) + sq(y / 60) + sq(z / 42);
        if (wm < 1) v = 26;                                                       // white matter, a little darker
        if (sq((x - 9) / 6) + sq((y + 8) / 18) + sq(z / 20) < 1) v = 6;           // lateral ventricles
        if (sq((x + 9) / 6) + sq((y + 8) / 18) + sq(z / 20) < 1) v = 6;
        if (sq((x + 28) / lesionR) + sq((y - 22) / lesionR) + sq((z - 12) / lesionR) < 1) v = 68;   // hyperdense lesion
        return v;
    }

    static double sq(double a) { return a * a; }
}
