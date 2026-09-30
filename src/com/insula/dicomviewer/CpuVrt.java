/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

/**
 * Java implementation of res/raw/vrt_frag.glsl, step for step. Used on phones without OpenGL ES 3.0, for picking
 * structures under a tap, and by the tests as the reference the GPU shader is compared with.
 */
final class CpuVrt {
    final Vol3D v;
    final VrtState st;
    final float[] tf;   // 256 x 8 x RGBA in 0..1
    final double[] half;
    final int vis;

    CpuVrt(Vol3D v, VrtState st) {
        this.v = v; this.st = st;
        byte[] t = st.table(v.huMin, v.huMax);
        tf = new float[t.length];
        for (int i = 0; i < t.length; i++) tf[i] = (t[i] & 255) / 255f;
        half = v.half();
        vis = st.visMask();
    }

    /** Trilinear sample with clamp-to-edge, as GL does for a linear-filtered 3D texture. */
    double hu(double qx, double qy, double qz) {
        double gx = clamp(qx * v.nx - 0.5, 0, v.nx - 1), gy = clamp(qy * v.ny - 0.5, 0, v.ny - 1), gz = clamp(qz * v.nz - 0.5, 0, v.nz - 1);
        int x0 = (int) gx, y0 = (int) gy, z0 = (int) gz;
        int x1 = Math.min(x0 + 1, v.nx - 1), y1 = Math.min(y0 + 1, v.ny - 1), z1 = Math.min(z0 + 1, v.nz - 1);
        double fx = gx - x0, fy = gy - y0, fz = gz - z0;
        short[] h = v.hu;
        double c00 = h[v.idx(x0, y0, z0)] * (1 - fx) + h[v.idx(x1, y0, z0)] * fx;
        double c10 = h[v.idx(x0, y1, z0)] * (1 - fx) + h[v.idx(x1, y1, z0)] * fx;
        double c01 = h[v.idx(x0, y0, z1)] * (1 - fx) + h[v.idx(x1, y0, z1)] * fx;
        double c11 = h[v.idx(x0, y1, z1)] * (1 - fx) + h[v.idx(x1, y1, z1)] * fx;
        return (c00 * (1 - fy) + c10 * fy) * (1 - fz) + (c01 * (1 - fy) + c11 * fy) * fz;
    }

    /** Nearest-neighbour label, as GL does for a nearest-filtered texture. */
    int label(double qx, double qy, double qz) {
        int i = (int) clamp(Math.floor(qx * v.nx), 0, v.nx - 1), j = (int) clamp(Math.floor(qy * v.ny), 0, v.ny - 1), k = (int) clamp(Math.floor(qz * v.nz), 0, v.nz - 1);
        return v.labels[v.idx(i, j, k)] & 255;
    }

    int labelIndex(double qx, double qy, double qz) {
        int i = (int) clamp(Math.floor(qx * v.nx), 0, v.nx - 1), j = (int) clamp(Math.floor(qy * v.ny), 0, v.ny - 1), k = (int) clamp(Math.floor(qz * v.nz), 0, v.nz - 1);
        return v.idx(i, j, k);
    }

    /** Linear lookup in a transfer-function row, with GL texel-centre conventions. */
    void lookup(double u, int c, double[] out) {
        double x = clamp(u * 256 - 0.5, 0, 255);
        int x0 = (int) x, x1 = Math.min(255, x0 + 1);
        double f = x - x0;
        int o0 = (c * 256 + x0) * 4, o1 = (c * 256 + x1) * 4;
        for (int k = 0; k < 4; k++) out[k] = tf[o0 + k] * (1 - f) + tf[o1 + k] * f;
    }

    static double clamp(double x, double a, double b) { return x < a ? a : x > b ? b : x; }
    static double safe(double d) { return Math.abs(d) < 1e-6 ? 1e-6 : d; }

    /** {tnear, tfar} for the clipped box, or null if the ray misses it. */
    double[] enter(double[] r) {
        double tn = 0, tf2 = Double.MAX_VALUE;
        for (int a = 0; a < 3; a++) {
            double bmin = -half[a] + st.clipLo[a] * 2 * half[a], bmax = -half[a] + st.clipHi[a] * 2 * half[a];
            double inv = 1 / safe(r[3 + a]), ta = (bmin - r[a]) * inv, tb = (bmax - r[a]) * inv;
            tn = Math.max(tn, Math.min(ta, tb));
            tf2 = Math.min(tf2, Math.max(ta, tb));
        }
        return tf2 <= tn ? null : new double[]{tn, tf2};
    }

    double[] shade(double[] base, double qx, double qy, double qz, double[] r, double[] vox) {
        double dx = 1.0 / v.nx, dy = 1.0 / v.ny, dz = 1.0 / v.nz;
        double gx = (hu(qx + dx, qy, qz) - hu(qx - dx, qy, qz)) / vox[0];
        double gy = (hu(qx, qy + dy, qz) - hu(qx, qy - dy, qz)) / vox[1];
        double gz = (hu(qx, qy, qz + dz) - hu(qx, qy, qz - dz)) / vox[2];
        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz), diff = 1, spec = 0;
        if (gl > 1e-3) {
            double ndl = Math.abs((gx * -r[3] + gy * -r[4] + gz * -r[5]) / gl);
            diff = ndl; spec = Math.pow(ndl, st.shin);
        }
        double k = st.ka + st.kd * diff, s = st.ks * spec;
        return new double[]{base[0] * k + s, base[1] * k + s, base[2] * k + s};
    }

    /** Renders one pixel; returns r, g, b in 0..1. */
    double[] pixel(int px, int py, int w, int h, double stepScale) {
        double[] r = new double[6];
        st.cam.ray(px, py, w, h, r);
        double bgR = ((st.bg >> 16) & 255) / 255.0, bgG = ((st.bg >> 8) & 255) / 255.0, bgB = (st.bg & 255) / 255.0;
        double[] bg = {bgR, bgG, bgB};
        double[] tt = enter(r);
        if (tt == null) return bg;
        double[] vox = {2 * half[0] / v.nx, 2 * half[1] / v.ny, 2 * half[2] / v.nz};
        double step = Math.min(vox[0], Math.min(vox[1], vox[2])) * stepScale;
        double ar = 0, ag = 0, ab = 0, aa = 0, m = st.mode == VrtState.MINIP ? 1e9 : -1e9;
        boolean found = false;
        double[] t4 = new double[4];
        for (int i = 0; i < 4096; i++) {
            double t = tt[0] + (i + 0.5) * step;
            if (t > tt[1]) break;
            double qx = (r[0] + r[3] * t + half[0]) / (2 * half[0]);
            double qy = (r[1] + r[4] * t + half[1]) / (2 * half[1]);
            double qz = (r[2] + r[5] * t + half[2]) / (2 * half[2]);
            int lab = label(qx, qy, qz);
            if (lab >= 128 || ((vis >> lab) & 1) == 0) continue;
            double hv = hu(qx, qy, qz);
            if (st.mode == VrtState.MIP) { m = Math.max(m, hv); found = true; continue; }
            if (st.mode == VrtState.MINIP) { m = Math.min(m, hv); found = true; continue; }
            double u = clamp((hv - v.huMin) / (double) (v.huMax - v.huMin), 0, 1);
            lookup(u, lab, t4);
            if (st.mode == VrtState.SURFACE) {
                if (t4[3] >= st.surface) return shade(t4, qx, qy, qz, r, vox);
                continue;
            }
            if (t4[3] > 0.002) {
                double a = 1 - Math.pow(1 - t4[3], stepScale);
                double[] c = shade(t4, qx, qy, qz, r, vox);
                ar += (1 - aa) * a * c[0]; ag += (1 - aa) * a * c[1]; ab += (1 - aa) * a * c[2];
                aa += (1 - aa) * a;
                if (aa > 0.97) break;
            }
        }
        if (st.mode == VrtState.MIP || st.mode == VrtState.MINIP) {
            if (!found) return bg;
            double g = clamp((m - (st.winC - st.winW * 0.5)) / st.winW, 0, 1);
            return new double[]{g, g, g};
        }
        if (st.mode == VrtState.SURFACE) return bg;
        return new double[]{ar + (1 - aa) * bgR, ag + (1 - aa) * bgG, ab + (1 - aa) * bgB};
    }

    /** Renders a w x h ARGB image, rows split across CPU cores. */
    int[] render(final int w, final int h, final double stepScale) {
        final int[] out = new int[w * h];
        Volume.parallelRows(h, new Volume.RowTask() {
            public void rows(int y0, int y1) {
                for (int y = y0; y < y1; y++) for (int x = 0; x < w; x++) {
                    double[] c = pixel(x, y, w, h, stepScale);
                    out[y * w + x] = 0xFF000000 | (to8(c[0]) << 16) | (to8(c[1]) << 8) | to8(c[2]);
                }
            }
        });
        return out;
    }

    static int to8(double c) { return (int) Math.round(clamp(c, 0, 1) * 255); }

    /** Index of the first visible, non-transparent voxel under a screen point, or -1. */
    int pick(int px, int py, int w, int h) {
        double[] r = new double[6];
        st.cam.ray(px, py, w, h, r);
        double[] tt = enter(r);
        if (tt == null) return -1;
        double[] vox = {2 * half[0] / v.nx, 2 * half[1] / v.ny, 2 * half[2] / v.nz};
        double step = Math.min(vox[0], Math.min(vox[1], vox[2])) * 0.5;
        double[] t4 = new double[4];
        for (int i = 0; i < 20000; i++) {
            double t = tt[0] + (i + 0.5) * step;
            if (t > tt[1]) break;
            double qx = (r[0] + r[3] * t + half[0]) / (2 * half[0]);
            double qy = (r[1] + r[4] * t + half[1]) / (2 * half[1]);
            double qz = (r[2] + r[5] * t + half[2]) / (2 * half[2]);
            int lab = label(qx, qy, qz);
            if (lab >= 128 || lab == Seg.BG || ((vis >> lab) & 1) == 0) continue;
            double u = clamp((hu(qx, qy, qz) - v.huMin) / (double) (v.huMax - v.huMin), 0, 1);
            lookup(u, lab, t4);
            if (st.mode != VrtState.VRT && st.mode != VrtState.SURFACE || t4[3] > 0.05) return labelIndex(qx, qy, qz);
        }
        return -1;
    }
}
