/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A reconstructed image volume in patient (LPS) space.
 * Voxel (i,j,k) maps to patient point O + i*A + j*B + k*C, which also handles gantry tilt and anisotropic voxels.
 */
final class Volume {
    static final ExecutorService POOL = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), Library.daemon("insula-volume"));

    final short[] d;
    final int nx, ny, nz;
    final double[] O, A, B, C;       // voxel -> patient
    final double[][] inv = new double[3][3];
    double minSp, diag, defWc = 40, defWw = 400;
    final double[] center = new double[3];
    short minV = Short.MAX_VALUE, maxV = Short.MIN_VALUE;
    float bg;
    boolean ct;
    String units = "";

    Volume(short[] d, int nx, int ny, int nz, double[] O, double[] A, double[] B, double[] C) {
        this.d = d; this.nx = nx; this.ny = ny; this.nz = nz; this.O = O; this.A = A; this.B = B; this.C = C;
        double[][] m = {{A[0], B[0], C[0]}, {A[1], B[1], C[1]}, {A[2], B[2], C[2]}};
        double det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        inv[0][0] = (m[1][1] * m[2][2] - m[1][2] * m[2][1]) / det;
        inv[0][1] = (m[0][2] * m[2][1] - m[0][1] * m[2][2]) / det;
        inv[0][2] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) / det;
        inv[1][0] = (m[1][2] * m[2][0] - m[1][0] * m[2][2]) / det;
        inv[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) / det;
        inv[1][2] = (m[0][2] * m[1][0] - m[0][0] * m[1][2]) / det;
        inv[2][0] = (m[1][0] * m[2][1] - m[1][1] * m[2][0]) / det;
        inv[2][1] = (m[0][1] * m[2][0] - m[0][0] * m[2][1]) / det;
        inv[2][2] = (m[0][0] * m[1][1] - m[0][1] * m[1][0]) / det;
        minSp = Math.min(len(A), Math.min(len(B), len(C)));
        for (int k = 0; k < 3; k++) center[k] = O[k] + (nx - 1) / 2.0 * A[k] + (ny - 1) / 2.0 * B[k] + (nz - 1) / 2.0 * C[k];
        double[] ext = new double[3];
        for (int k = 0; k < 3; k++) ext[k] = (nx - 1) * A[k] + (ny - 1) * B[k] + (nz - 1) * C[k];
        diag = Math.max(len(ext), Math.max(len(sub(mul(A, nx - 1), mul(B, ny - 1))), 1)) + minSp;
        for (short v : d) { if (v < minV) minV = v; if (v > maxV) maxV = v; }
        bg = minV;
    }

    // ---- vector helpers ----
    static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] mul(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = len(a); return l < 1e-12 ? new double[]{0, 0, 1} : mul(a, 1 / l); }

    /** Rotates v about unit axis k by angle (radians), Rodrigues' formula. */
    static double[] rot(double[] v, double[] k, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        double[] kxv = cross(k, v);
        double kd = dot(k, v) * (1 - c);
        return new double[]{v[0] * c + kxv[0] * s + k[0] * kd, v[1] * c + kxv[1] * s + k[1] * kd, v[2] * c + kxv[2] * s + k[2] * kd};
    }

    double[] toVoxel(double[] p) {
        double x = p[0] - O[0], y = p[1] - O[1], z = p[2] - O[2];
        return new double[]{inv[0][0] * x + inv[0][1] * y + inv[0][2] * z, inv[1][0] * x + inv[1][1] * y + inv[1][2] * z, inv[2][0] * x + inv[2][1] * y + inv[2][2] * z};
    }

    /** Direction vector (mm) expressed as voxel steps. */
    double[] dirVoxel(double[] v) {
        return new double[]{inv[0][0] * v[0] + inv[0][1] * v[1] + inv[0][2] * v[2], inv[1][0] * v[0] + inv[1][1] * v[1] + inv[1][2] * v[2], inv[2][0] * v[0] + inv[2][1] * v[1] + inv[2][2] * v[2]};
    }

    double[] toPatient(double i, double j, double k) {
        return new double[]{O[0] + i * A[0] + j * B[0] + k * C[0], O[1] + i * A[1] + j * B[1] + k * C[1], O[2] + i * A[2] + j * B[2] + k * C[2]};
    }

    List<double[]> corners() {
        List<double[]> l = new ArrayList<>();
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int c = 0; c < 2; c++) l.add(toPatient(a * (nx - 1), b * (ny - 1), c * (nz - 1)));
        return l;
    }

    /** Range of the volume projected on a unit direction: {min, max}. */
    double[] range(double[] n) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] p : corners()) { double t = dot(p, n); lo = Math.min(lo, t); hi = Math.max(hi, t); }
        return new double[]{lo, hi};
    }

    final float tri(double x, double y, double z) {
        if (x < 0 || y < 0 || z < 0 || x > nx - 1 || y > ny - 1 || z > nz - 1) return bg;
        int x0 = (int) x, y0 = (int) y, z0 = (int) z;
        int x1 = x0 < nx - 1 ? x0 + 1 : x0, y1 = y0 < ny - 1 ? y0 + 1 : y0, z1 = z0 < nz - 1 ? z0 + 1 : z0;
        float fx = (float) (x - x0), fy = (float) (y - y0), fz = (float) (z - z0);
        int sy = nx, sz = nx * ny;
        int b00 = z0 * sz + y0 * sy, b01 = z0 * sz + y1 * sy, b10 = z1 * sz + y0 * sy, b11 = z1 * sz + y1 * sy;
        float c00 = d[b00 + x0] + (d[b00 + x1] - d[b00 + x0]) * fx;
        float c01 = d[b01 + x0] + (d[b01 + x1] - d[b01 + x0]) * fx;
        float c10 = d[b10 + x0] + (d[b10 + x1] - d[b10 + x0]) * fx;
        float c11 = d[b11 + x0] + (d[b11 + x1] - d[b11 + x0]) * fx;
        float c0 = c00 + (c01 - c00) * fy, c1 = c10 + (c11 - c10) * fy;
        return c0 + (c1 - c0) * fz;
    }

    final float near(double x, double y, double z) {
        int i = (int) (x + 0.5), j = (int) (y + 0.5), k = (int) (z + 0.5);
        if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return bg;
        return d[(k * ny + j) * nx + i];
    }

    static final int THIN = 0, MIP = 1, MINIP = 2, AVG = 3;

    /** A planar image through the volume: pixel (x,y) center = origin + x*s*u + y*s*v, slab along n. */
    static final class Plane {
        double[] origin, u, v, n;
        double s;
        int w, h;
    }

    /** Plane with axes u,v through point p, sized to the volume's footprint on that plane. */
    Plane planeThrough(double[] p, double[] u, double[] v, int maxPx) {
        Plane pl = new Plane();
        pl.u = u; pl.v = v; pl.n = norm(cross(u, v));
        double[] ru = range(u), rv = range(v);
        double ext = Math.max(ru[1] - ru[0], rv[1] - rv[0]);
        pl.s = Math.max(minSp, ext / maxPx);
        pl.w = Math.max(2, (int) Math.ceil((ru[1] - ru[0]) / pl.s) + 1);
        pl.h = Math.max(2, (int) Math.ceil((rv[1] - rv[0]) / pl.s) + 1);
        double tn = dot(p, pl.n);
        // origin = point with u-coordinate ru[0], v-coordinate rv[0], n-coordinate tn
        pl.origin = add(add(mul(u, ru[0]), mul(v, rv[0])), mul(pl.n, tn));
        return pl;
    }

    RawImage reslice(final Plane pl, double thickness, final int mode) {
        final RawImage im = new RawImage();
        im.w = pl.w; im.h = pl.h; im.rowSp = pl.s; im.colSp = pl.s;
        im.pix = new int[pl.w * pl.h];
        final double[] v0 = toVoxel(pl.origin), du = dirVoxel(mul(pl.u, pl.s)), dv = dirVoxel(mul(pl.v, pl.s));
        final int ns = mode == THIN || thickness <= minSp ? 1 : Math.min(96, (int) Math.round(thickness / minSp) + 1);
        final double[] dn = dirVoxel(mul(pl.n, ns > 1 ? thickness / (ns - 1) : 0));
        final double half = (ns - 1) / 2.0;
        parallelRows(pl.h, new RowTask() {
            public void rows(int y0, int y1) {
                for (int y = y0; y < y1; y++) {
                    double bx = v0[0] + y * dv[0], by = v0[1] + y * dv[1], bz = v0[2] + y * dv[2];
                    int o = y * pl.w;
                    for (int x = 0; x < pl.w; x++) {
                        double px = bx + x * du[0], py = by + x * du[1], pz = bz + x * du[2];
                        float val;
                        if (ns == 1) val = tri(px, py, pz);
                        else {
                            float acc = mode == MIP ? -Float.MAX_VALUE : mode == MINIP ? Float.MAX_VALUE : 0;
                            for (int k = 0; k < ns; k++) {
                                double t = k - half;
                                float s = tri(px + t * dn[0], py + t * dn[1], pz + t * dn[2]);
                                if (mode == MIP) { if (s > acc) acc = s; } else if (mode == MINIP) { if (s < acc) acc = s; } else acc += s;
                            }
                            val = mode == AVG ? acc / ns : acc;
                        }
                        im.pix[o + x] = Math.round(val);
                    }
                }
            }
        });
        im.wc = defWc; im.ww = defWw; im.units = units;
        im.computeRange();
        return im;
    }

    /**
     * Curved reformat: columns follow the resampled path (drawn on a plane with normal up),
     * rows run along -up. offset shifts the path sideways (paging), thickness makes a curved slab.
     */
    RawImage curved(final List<double[]> path, final double[] up, final double s, final double offset, double thickness, final int mode) {
        final List<double[]> pts = new ArrayList<>(), nrm = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            double[] a = path.get(i), b = path.get(i + 1);
            double L = len(sub(b, a));
            int steps = Math.max(1, (int) Math.round(L / s));
            double[] t = norm(sub(b, a));
            double[] side = norm(cross(up, t));
            for (int k = 0; k < steps; k++) { pts.add(add(a, mul(sub(b, a), k / (double) steps))); nrm.add(side); }
        }
        if (path.size() >= 2) { pts.add(path.get(path.size() - 1)); nrm.add(nrm.isEmpty() ? new double[]{1, 0, 0} : nrm.get(nrm.size() - 1)); }
        final RawImage im = new RawImage();
        double[] r = range(up);
        final double tc = (r[0] + r[1]) / 2;
        im.w = Math.max(2, pts.size());
        im.h = Math.max(2, (int) Math.ceil((r[1] - r[0]) / s) + 1);
        im.rowSp = s; im.colSp = s;
        im.pix = new int[im.w * im.h];
        final int ns = mode == THIN || thickness <= minSp ? 1 : Math.min(64, (int) Math.round(thickness / minSp) + 1);
        final double tstep = ns > 1 ? thickness / (ns - 1) : 0;
        final int H = im.h;
        parallelRows(im.w, new RowTask() {
            public void rows(int c0, int c1) {
                for (int c = c0; c < c1; c++) {
                    double[] p = pts.get(c), sd = nrm.get(c);
                    double[] base = add(p, mul(sd, offset));
                    // move base onto the top of the volume along up
                    double[] top = add(base, mul(up, (tc - dot(base, up)) + (H - 1) * s / 2.0));
                    double[] v0 = toVoxel(top), dv = dirVoxel(mul(up, -s)), dsd = dirVoxel(mul(sd, tstep));
                    for (int y = 0; y < H; y++) {
                        double px = v0[0] + y * dv[0], py = v0[1] + y * dv[1], pz = v0[2] + y * dv[2];
                        float val;
                        if (ns == 1) val = tri(px, py, pz);
                        else {
                            float acc = mode == MIP ? -Float.MAX_VALUE : mode == MINIP ? Float.MAX_VALUE : 0;
                            for (int k = 0; k < ns; k++) {
                                double t = k - (ns - 1) / 2.0;
                                float sv = tri(px + t * dsd[0], py + t * dsd[1], pz + t * dsd[2]);
                                if (mode == MIP) { if (sv > acc) acc = sv; } else if (mode == MINIP) { if (sv < acc) acc = sv; } else acc += sv;
                            }
                            val = mode == AVG ? acc / ns : acc;
                        }
                        im.pix[y * im.w + c] = Math.round(val);
                    }
                }
            }
        });
        im.wc = defWc; im.ww = defWw; im.units = units;
        im.computeRange();
        return im;
    }

    // ---- 3D rendering ----
    static final int R_MIP = 0, R_BONE = 1, R_SOFT = 2, R_VESSEL = 3, R_GRAY = 4;
    static final float[][] TINT = {{1, 1, 1}, {0.96f, 0.91f, 0.80f}, {0.95f, 0.76f, 0.66f}, {0.92f, 0.28f, 0.22f}, {0.9f, 0.9f, 0.9f}};

    /**
     * Ray casts the volume. yaw rotates about the patient's head-foot axis, pitch tilts toward the viewer.
     * MIP returns grayscale values (windowable); volume rendering returns color, with lo..hi as the opacity ramp.
     */
    RawImage render3D(double yaw, double pitch, double zoom, final int N, final int mode, final double lo, final double hi, final boolean fast) {
        double[] R0 = {1, 0, 0}, D0 = {0, 0, -1}, Z = {0, 0, 1};
        double[] R = rot(R0, Z, yaw), D = rot(D0, Z, yaw);
        D = rot(D, R, pitch);
        final double[] F = norm(cross(R, D));
        double ext = diag / Math.max(0.2, zoom);
        final double s = ext / N;
        final double step = minSp * (fast ? 2.0 : 0.8);
        final double[] start = add(add(center, mul(R, -ext / 2 + s / 2)), add(mul(D, -ext / 2 + s / 2), mul(F, -diag / 2)));
        final double[] v0 = toVoxel(start), du = dirVoxel(mul(R, s)), dvv = dirVoxel(mul(D, s)), df = dirVoxel(mul(F, step));
        final int steps = (int) Math.ceil(diag / step);
        final RawImage im = new RawImage();
        im.w = N; im.h = N; im.rowSp = s; im.colSp = s;
        im.pix = new int[N * N];
        im.rgb = mode != R_MIP;
        final float flo = (float) lo, fhi = (float) Math.max(lo + 1, hi);
        final float[] tint = TINT[Math.min(mode, TINT.length - 1)];
        final float stepScale = (float) (step / 1.0);
        final float bgF = bg;
        final double lenA = len(A), lenB = len(B), lenC = len(C);
        final double[] uA = norm(A), uB = norm(B), uC = norm(C);
        parallelRows(N, new RowTask() {
            public void rows(int y0, int y1) {
                double[] tr = new double[2];
                for (int y = y0; y < y1; y++) {
                    for (int x = 0; x < N; x++) {
                        double ox = v0[0] + x * du[0] + y * dvv[0], oy = v0[1] + x * du[1] + y * dvv[1], oz = v0[2] + x * du[2] + y * dvv[2];
                        int o = y * N + x;
                        if (!clip(ox, oy, oz, df, steps, tr)) { im.pix[o] = mode == R_MIP ? Math.round(bgF) : 0xFF000000; continue; }
                        int k0 = (int) Math.max(0, Math.floor(tr[0])), k1 = (int) Math.min(steps, Math.ceil(tr[1]));
                        if (mode == R_MIP) {
                            float m = bgF;
                            for (int k = k0; k <= k1; k++) {
                                double px = ox + k * df[0], py = oy + k * df[1], pz = oz + k * df[2];
                                float v = fast ? near(px, py, pz) : tri(px, py, pz);
                                if (v > m) m = v;
                            }
                            im.pix[o] = Math.round(m);
                        } else {
                            float cr = 0, cg = 0, cb = 0, ca = 0;
                            for (int k = k0; k <= k1 && ca < 0.97f; k++) {
                                double px = ox + k * df[0], py = oy + k * df[1], pz = oz + k * df[2];
                                float v = fast ? near(px, py, pz) : tri(px, py, pz);
                                if (v <= flo) continue;
                                float a = Math.min(1f, (v - flo) / (fhi - flo));
                                a = a * a * 0.5f;
                                a = 1f - (float) Math.pow(1f - a, stepScale);
                                // Gradient shading (central differences) so surfaces read as 3D.
                                float gx, gy, gz;
                                if (fast) { gx = near(px + 1, py, pz) - near(px - 1, py, pz); gy = near(px, py + 1, pz) - near(px, py - 1, pz); gz = near(px, py, pz + 1) - near(px, py, pz - 1); }
                                else { gx = tri(px + 1, py, pz) - tri(px - 1, py, pz); gy = tri(px, py + 1, pz) - tri(px, py - 1, pz); gz = tri(px, py, pz + 1) - tri(px, py, pz - 1); }
                                // Voxel-space gradient -> patient-space: scale by inverse voxel size so anisotropic stacks shade evenly.
                                gx /= (float) lenA; gy /= (float) lenB; gz /= (float) lenC;
                                double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                                float shade = 0.35f;
                                if (gl > 1e-3) {
                                    double pgx = gx * uA[0] + gy * uB[0] + gz * uC[0], pgy = gx * uA[1] + gy * uB[1] + gz * uC[1], pgz = gx * uA[2] + gy * uB[2] + gz * uC[2];
                                    double pl = Math.sqrt(pgx * pgx + pgy * pgy + pgz * pgz);
                                    double lam = Math.abs((pgx * F[0] + pgy * F[1] + pgz * F[2]) / pl);
                                    shade = 0.25f + 0.65f * (float) lam + 0.25f * (float) Math.pow(lam, 24);
                                }
                                float w = (1 - ca) * a;
                                cr += w * tint[0] * shade; cg += w * tint[1] * shade; cb += w * tint[2] * shade;
                                ca += w;
                            }
                            int r = Math.min(255, (int) (cr * 255 * 1.05f)), g = Math.min(255, (int) (cg * 255)), b = Math.min(255, (int) (cb * 255));
                            im.pix[o] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                    }
                }
            }
        });
        im.wc = defWc; im.ww = defWw; im.units = units;
        im.computeRange();
        return im;
    }

    /** Slab-method ray/box intersection in voxel space; tr receives the step range. */
    boolean clip(double ox, double oy, double oz, double[] df, int steps, double[] tr) {
        double t0 = 0, t1 = steps;
        double[] o = {ox, oy, oz}, mx = {nx - 1, ny - 1, nz - 1};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(df[a]) < 1e-12) { if (o[a] < 0 || o[a] > mx[a]) return false; continue; }
            double ta = (0 - o[a]) / df[a], tb = (mx[a] - o[a]) / df[a];
            if (ta > tb) { double t = ta; ta = tb; tb = t; }
            t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
            if (t0 > t1) return false;
        }
        tr[0] = t0; tr[1] = t1;
        return true;
    }

    interface RowTask { void rows(int y0, int y1); }

    static void parallelRows(int h, final RowTask t) {
        int n = Math.min(h, Math.max(1, Runtime.getRuntime().availableProcessors()));
        if (n <= 1 || h < 32) { t.rows(0, h); return; }
        List<Callable<Void>> jobs = new ArrayList<>();
        int chunk = (h + n - 1) / n;
        for (int i = 0; i < n; i++) {
            final int a = i * chunk, b = Math.min(h, a + chunk);
            if (a >= b) break;
            jobs.add(new Callable<Void>() { public Void call() { t.rows(a, b); return null; } });
        }
        try { POOL.invokeAll(jobs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Builds a volume from a sorted series, downsampling in-plane if needed to fit memory. */
    static Volume build(Library.Series series, Library.Progress prog) throws Exception {
        List<Library.SliceRef> sl = series.slices();
        int nz = sl.size();
        if (nz < 3) throw new Exception("MPR needs a series with at least 3 slices.");
        Library.Geo g0 = sl.get(0).geo(), gN = sl.get(nz - 1).geo();
        if (g0.orient == null || g0.pos == null) throw new Exception("This series has no position data, so it can't be reconstructed.");
        Library.ImageInfo i0 = sl.get(0).info;
        for (Library.SliceRef s : sl) {
            Library.Geo g = s.geo();
            if (g.orient == null || g.pos == null) throw new Exception("Some slices have no position data.");
            if (s.info.rows != i0.rows || s.info.cols != i0.cols) throw new Exception("Slices have different sizes; MPR needs a uniform stack.");
            for (int k = 0; k < 6; k++) if (Math.abs(g.orient[k] - g0.orient[k]) > 1e-3) throw new Exception("Slices have different orientations (a localizer or mixed series). Pick a single stack.");
        }
        double[] r = {g0.orient[0], g0.orient[1], g0.orient[2]}, c = {g0.orient[3], g0.orient[4], g0.orient[5]};
        double[] slab = mul(sub(gN.pos, g0.pos), 1.0 / (nz - 1));
        if (len(slab) < 1e-3) throw new Exception("All slices share one position; MPR is not possible.");
        int w = i0.cols, h = i0.rows, ds = 1;
        long budget = Runtime.getRuntime().maxMemory() / 3;
        while ((long) (w / ds) * (h / ds) * nz * 2 > budget && ds < 8) ds *= 2;
        int nx = w / ds, ny = h / ds;
        double sx = (g0.colSp > 0 ? g0.colSp : 1) * ds, sy = (g0.rowSp > 0 ? g0.rowSp : 1) * ds;
        short[] vol = new short[nx * ny * nz];
        double wc = Double.NaN, ww = Double.NaN;
        String units = "";
        for (int k = 0; k < nz; k++) {
            RawImage im = Library.load(sl.get(k));
            if (im.rgb) throw new Exception("MPR works on grayscale series only.");
            if (k == nz / 2) { double[] dw = im.defaultWindow(); wc = dw[0]; ww = dw[1]; units = im.units; }
            int base = k * nx * ny;
            for (int y = 0; y < ny; y++) {
                int row = (y * ds) * im.w;
                for (int x = 0; x < nx; x++) {
                    double v = im.pix[row + x * ds] * im.slope + im.intercept;
                    vol[base + y * nx + x] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
                }
            }
            if (prog != null && k % 8 == 0) prog.update(k, nz, "Building volume");
        }
        double[] O = g0.pos.clone();
        Volume v = new Volume(vol, nx, ny, nz, O, mul(r, sx), mul(c, sy), slab);
        v.defWc = wc; v.defWw = ww; v.units = units;
        v.ct = "CT".equals(series.modality);
        v.downsample = ds;
        // Uneven slice spacing check
        double mean = len(slab), worst = 0;
        for (int k = 1; k < nz; k++) worst = Math.max(worst, Math.abs(len(sub(sl.get(k).geo().pos, sl.get(k - 1).geo().pos)) - mean));
        v.unevenSpacing = worst > 0.2 * mean;
        return v;
    }

    int downsample = 1;
    boolean unevenSpacing;
}
