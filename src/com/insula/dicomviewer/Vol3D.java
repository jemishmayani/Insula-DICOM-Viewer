/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

/**
 * A volume resampled onto a grid aligned with the patient axes (x = patient left, y = posterior, z = head),
 * sized to the device's quality tier. Used by 3D rendering (GPU and CPU) and tissue separation.
 */
final class Vol3D {
    final int nx, ny, nz;
    final short[] hu;            // modality values (HU for CT)
    final byte[] labels;         // tissue class per voxel; bit 7 = removed by the user
    final double sx, sy, sz;     // voxel size in mm
    final double[] origin;       // patient position of voxel (0,0,0) centre
    final boolean ct;
    final double defWc, defWw;
    final int huMin, huMax;      // range mapped onto the transfer-function axis

    Vol3D(int nx, int ny, int nz, double sx, double sy, double sz, double[] origin, boolean ct, double wc, double ww, int huMin, int huMax) {
        this.nx = nx; this.ny = ny; this.nz = nz; this.sx = sx; this.sy = sy; this.sz = sz; this.origin = origin; this.ct = ct;
        hu = new short[nx * ny * nz];
        labels = new byte[nx * ny * nz];
        defWc = wc; defWw = ww; this.huMin = huMin; this.huMax = huMax;
    }

    int n() { return nx * ny * nz; }
    int idx(int i, int j, int k) { return (k * ny + j) * nx + i; }

    /** Box half-extents in normalized model space: the longest side spans -1..1. */
    double[] half() {
        double ex = nx * sx, ey = ny * sy, ez = nz * sz, m = Math.max(ex, Math.max(ey, ez));
        return new double[]{ex / m, ey / m, ez / m};
    }

    /** Quality tiers: longest side and total voxel budget. */
    static final int[][] TIERS = {{320, 24_000_000}, {256, 12_000_000}, {192, 5_000_000}, {160, 3_000_000}};
    static final String[] TIER_NAMES = {"High", "Standard", "Low", "Basic (CPU)"};

    /** Resamples an MPR volume onto a patient-aligned grid within the tier's budget (trilinear, in parallel). */
    static Vol3D from(final Volume v, int tier) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : v.corners()) for (int a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], p[a]); hi[a] = Math.max(hi[a], p[a]); }
        double ex = hi[0] - lo[0] + v.minSp, ey = hi[1] - lo[1] + v.minSp, ez = hi[2] - lo[2] + v.minSp;
        int maxDim = TIERS[tier][0];
        long budget = TIERS[tier][1];
        double s = Math.max(v.minSp, Math.max(ex, Math.max(ey, ez)) / maxDim);
        while ((long) Math.ceil(ex / s) * (long) Math.ceil(ey / s) * (long) Math.ceil(ez / s) > budget) s *= 1.05;
        final int nx = Math.max(2, (int) Math.ceil(ex / s)), ny = Math.max(2, (int) Math.ceil(ey / s)), nz = Math.max(2, (int) Math.ceil(ez / s));
        final double[] org = {lo[0] - v.minSp / 2 + s / 2, lo[1] - v.minSp / 2 + s / 2, lo[2] - v.minSp / 2 + s / 2};
        int hmin = v.ct ? -1024 : Math.min(0, v.minV), hmax = v.ct ? 3071 : Math.max(1, v.maxV);
        final Vol3D r = new Vol3D(nx, ny, nz, s, s, s, org, v.ct, v.defWc, v.defWw, hmin, hmax);
        final double fs = s;
        final double[] step = v.dirVoxel(new double[]{s, 0, 0});
        Volume.parallelRows(nz, new Volume.RowTask() {
            public void rows(int k0, int k1) {
                for (int k = k0; k < k1; k++)
                    for (int j = 0; j < ny; j++) {
                        double[] vx = v.toVoxel(new double[]{org[0], org[1] + j * fs, org[2] + k * fs});
                        int base = (k * ny + j) * nx;
                        for (int i = 0; i < nx; i++) {
                            float val = v.tri(vx[0] + i * step[0], vx[1] + i * step[1], vx[2] + i * step[2]);
                            r.hu[base + i] = (short) Math.round(val);
                        }
                    }
            }
        });
        return r;
    }

    /** Float to IEEE 754 half-precision bits, for uploading HU values as a GL_R16F texture. */
    static short toHalf(float f) {
        int b = Float.floatToIntBits(f);
        int sign = (b >>> 16) & 0x8000;
        int val = (b & 0x7fffffff) + 0x1000;
        if (val >= 0x47800000) {
            if ((b & 0x7fffffff) >= 0x47800000) return (short) (sign | 0x7c00 | (val >= 0x7f800000 && (b & 0x7fffff) != 0 ? 0x200 : 0));
            return (short) (sign | 0x7bff);
        }
        if (val >= 0x38800000) return (short) (sign | ((val - 0x38000000) >>> 13));
        if (val < 0x33000000) return (short) sign;
        val = (b & 0x7fffffff) >>> 23;
        return (short) (sign | (((b & 0x7fffff) | 0x800000) + (0x800000 >>> (val - 102)) >>> (126 - val)));
    }

    static float fromHalf(short h) {
        int s = (h >> 15) & 1, e = (h >> 10) & 0x1f, m = h & 0x3ff;
        float v;
        if (e == 0) v = (float) (m * Math.pow(2, -24));
        else if (e == 31) v = m == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        else v = (float) ((1 + m / 1024.0) * Math.pow(2, e - 15));
        return s == 1 ? -v : v;
    }
}
