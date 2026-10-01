/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based tissue separation for 3D rendering, plus the manual edits users make afterwards.
 * CT: thresholds are estimated from each study's histogram. Bone is cortical bone plus what it encloses in the axial
 * cross-section (marrow), so vessels pressed against bone stay vessels; small isolated dense spots are calcium; remaining
 * contrast-dense tissue is vessel.
 * Other modalities use intensity bands. It separates tissue types, not individual organs.
 */
final class Seg {
    static final int BG = 0, SKIN = 1, ORGAN = 2, VESSEL = 3, BONE = 4, LUNG = 5, CALCIUM = 6, SELECT = 7;
    static final int REMOVED = 0x80;
    // Temporary codes used only while segmenting
    static final byte OUT = 100, DENSE = 101, CORE = 102;
    /** Layers (through dense tissue) around bone that go to bone unless a vessel reaches them first. */
    static int BAND = 3;

    static final class Params {
        double air = -400, fat = -40, vessel = 200, boneSeed = 650, calcium = 800;
        double softPeak = Double.NaN, bloodPeak = Double.NaN;
        boolean contrast, ct = true;
        String summary = "";
    }

    interface Progress { void update(String msg, int pct); }

    // ---------------- threshold estimation ----------------
    static Params estimate(Vol3D v) {
        Params p = new Params();
        p.ct = v.ct;
        int n = v.n(), stride = Math.max(1, n / 2_000_000);
        if (!v.ct) {
            // Intensity bands for MR and other modalities: background by Otsu, bright structures at the 97th percentile.
            int lo = v.huMin, hi = v.huMax, bins = 512;
            long[] h = new long[bins];
            for (int i = 0; i < n; i += stride) h[Math.min(bins - 1, Math.max(0, (int) ((v.hu[i] - lo) * (long) bins / Math.max(1, hi - lo + 1))))]++;
            int otsu = otsu(h);
            double tOtsu = lo + (otsu + 0.5) * (hi - lo) / bins;
            long above = 0; for (int b = otsu; b < bins; b++) above += h[b];
            long acc = 0; int p97 = bins - 1;
            for (int b = bins - 1; b >= otsu; b--) { acc += h[b]; if (acc >= above * 0.03) { p97 = b; break; } }
            p.air = tOtsu;
            p.fat = tOtsu;
            p.vessel = lo + (p97 + 0.5) * (hi - lo) / bins;
            p.boneSeed = Double.MAX_VALUE;
            p.calcium = Double.MAX_VALUE;
            p.summary = String.format("Intensity bands: tissue above %.0f, bright structures above %.0f", p.air, p.vessel);
            return p;
        }
        // CT histogram of body tissue, 5 HU bins from -200 to 1300
        int base = -200, bins = 300;
        long[] h = new long[bins];
        long total = 0;
        for (int i = 0; i < n; i += stride) {
            int x = v.hu[i];
            if (x < base || x >= base + bins * 5) continue;
            h[(x - base) / 5]++; total++;
        }
        long[] s = smooth(h);
        int soft = argmax(s, bin(-20), bin(100)), blood = argmax(s, bin(150), bin(650));
        int valley = argmin(s, soft, blood);
        p.softPeak = base + soft * 5 + 2.5;
        p.bloodPeak = base + blood * 5 + 2.5;
        p.contrast = s[blood] > 1.6 * Math.max(1, s[valley]) && s[blood] > total * 0.0015 && blood > soft;
        if (p.contrast) {
            // Between the peaks (not at the valley, which partial-volume blur fills in): 45% of the way to the blood pool.
            p.vessel = clamp(p.softPeak + 0.45 * (p.bloodPeak - p.softPeak), 130, 300);
            p.boneSeed = Math.max(650, p.bloodPeak + 250);
            p.calcium = Math.max(p.boneSeed, p.bloodPeak + 350);
            p.summary = String.format("Contrast detected: blood pool about %.0f HU, soft tissue about %.0f HU. Vessels from %.0f HU, bone cores from %.0f HU.", p.bloodPeak, p.softPeak, p.vessel, p.boneSeed);
        } else {
            p.vessel = 300;
            p.boneSeed = 500;
            p.calcium = 600;
            p.summary = "No contrast enhancement detected: dense structures are treated as bone. Adjust the thresholds if this is a contrast study.";
        }
        return p;
    }

    static int bin(int hu) { return (hu + 200) / 5; }
    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    static long[] smooth(long[] h) {
        long[] s = new long[h.length];
        for (int i = 0; i < h.length; i++) { long a = 0; int c = 0; for (int k = -3; k <= 3; k++) if (i + k >= 0 && i + k < h.length) { a += h[i + k]; c++; } s[i] = a / c; }
        return s;
    }
    static int argmax(long[] s, int a, int b) { int m = a; for (int i = a; i <= b && i < s.length; i++) if (s[i] > s[m]) m = i; return m; }
    static int argmin(long[] s, int a, int b) { int m = a; for (int i = a; i <= b && i < s.length; i++) if (s[i] < s[m]) m = i; return m; }

    static int otsu(long[] h) {
        long total = 0; double sum = 0;
        for (int i = 0; i < h.length; i++) { total += h[i]; sum += i * (double) h[i]; }
        double sumB = 0, best = -1; long wB = 0; int t = 0;
        for (int i = 0; i < h.length; i++) {
            wB += h[i]; if (wB == 0) continue;
            long wF = total - wB; if (wF == 0) break;
            sumB += i * (double) h[i];
            double mB = sumB / wB, mF = (sum - sumB) / wF, between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) { best = between; t = i; }
        }
        return t;
    }

    // ---------------- automatic separation ----------------
    static void segment(Vol3D v, Params p, Progress prog) {
        final byte[] L = v.labels;
        final short[] H = v.hu;
        final int nx = v.nx, ny = v.ny, nz = v.nz, n = v.n(), sxy = nx * ny;
        java.util.Arrays.fill(L, (byte) 0);
        if (prog != null) prog.update("Finding the body outline", 5);
        // 1. Air outside the body: flood from the volume's faces through voxels below the air threshold.
        IntQueue q = new IntQueue(1 << 16);
        // CT: only true air (below -950 HU) or scanner padding counts as outside. Lung tissue (about -850 HU) stops the
        // flood, so lungs survive where a small field of view cuts through them.
        final double outside = p.ct ? -950 : p.air;
        for (int x = 0; x < n; x++) if (p.ct && H[x] <= -1100) L[x] = OUT;
        for (int x = 0; x < n; x++) if (L[x] == OUT) q.add(x);
        for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            // Side faces only: lungs and airways often reach the top or bottom of a chest scan.
            if (i != 0 && j != 0 && i != nx - 1 && j != ny - 1) continue;
            int x = (k * ny + j) * nx + i;
            if (H[x] < outside && L[x] == 0) { L[x] = OUT; q.add(x); }
        }
        while (!q.isEmpty()) {
            int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
            if (i > 0) visitOut(L, H, x - 1, outside, q);
            if (i < nx - 1) visitOut(L, H, x + 1, outside, q);
            if (j > 0) visitOut(L, H, x - nx, outside, q);
            if (j < ny - 1) visitOut(L, H, x + nx, outside, q);
            if (k > 0) visitOut(L, H, x - sxy, outside, q);
            if (k < nz - 1) visitOut(L, H, x + sxy, outside, q);
        }
        if (prog != null) prog.update("Separating lungs, fat, and soft tissue", 25);
        // 2. Simple classes inside the body
        for (int x = 0; x < n; x++) {
            if (L[x] == OUT) continue;
            int hv = H[x];
            if (hv < p.air) L[x] = (byte) (p.ct ? LUNG : BG);
            else if (hv < p.fat) L[x] = (byte) SKIN;
            else if (hv < p.vessel) L[x] = (byte) ORGAN;
            else L[x] = DENSE;
        }
        if (prog != null) prog.update("Separating bone from vessels", 45);
        // 3. Cortical bone: very dense voxels. Small isolated dense spots are calcifications, not bone.
        double voxMm3 = v.sx * v.sy * v.sz;
        IntList comp = new IntList(1 << 12);
        for (int start = 0; start < n; start++) {
            if (L[start] != DENSE || H[start] < p.boneSeed) continue;
            comp.clear();
            L[start] = CORE; q.add(start);
            while (!q.isEmpty()) {
                int x = q.poll();
                comp.add(x);
                int i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] == DENSE && H[y] >= p.boneSeed) { L[y] = CORE; q.add(y); }
            }
            // Calcifications are compact (under 15 mm across and 1 cm3); bones are extended.
            int[] lo = {nx, ny, nz}, hi = {-1, -1, -1};
            for (int c = 0; c < comp.size; c++) {
                int x = comp.a[c], ci = x % nx, cj = (x / nx) % ny, ck = x / sxy;
                lo[0] = Math.min(lo[0], ci); hi[0] = Math.max(hi[0], ci);
                lo[1] = Math.min(lo[1], cj); hi[1] = Math.max(hi[1], cj);
                lo[2] = Math.min(lo[2], ck); hi[2] = Math.max(hi[2], ck);
            }
            double extent = Math.max((hi[0] - lo[0] + 1) * v.sx, Math.max((hi[1] - lo[1] + 1) * v.sy, (hi[2] - lo[2] + 1) * v.sz));
            double mm3 = comp.size * voxMm3;
            byte cls = (byte) (mm3 < 60 || (mm3 < 1000 && extent < 15) ? CALCIUM : BONE);
            for (int c = 0; c < comp.size; c++) L[comp.a[c]] = cls;
        }
        if (prog != null) prog.update("Filling bone marrow", 60);
        // 4. Marrow: dense voxels enclosed by cortical bone in the axial cross-section. The cortex is thickened by one
        //    voxel first so small gaps don't open the ring. A vessel pressed against bone is not enclosed, so it stays a vessel.
        byte[] ring = new byte[sxy];
        IntQueue q2 = new IntQueue(1 << 12);
        for (int k = 0; k < nz; k++) {
            int off = k * sxy;
            java.util.Arrays.fill(ring, (byte) 0);
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                if (L[off + j * nx + i] != BONE) continue;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int ii = i + di, jj = j + dj;
                    if (ii >= 0 && jj >= 0 && ii < nx && jj < ny) ring[jj * nx + ii] = 1;
                }
            }
            // flood the slice from its border through everything that isn't (thickened) cortex: unreached = enclosed
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                if (i != 0 && j != 0 && i != nx - 1 && j != ny - 1) continue;
                int x = j * nx + i;
                if (ring[x] == 0) { ring[x] = 2; q2.add(x); }
            }
            while (!q2.isEmpty()) {
                int x = q2.poll(), i = x % nx, j = x / nx;
                if (i > 0 && ring[x - 1] == 0) { ring[x - 1] = 2; q2.add(x - 1); }
                if (i < nx - 1 && ring[x + 1] == 0) { ring[x + 1] = 2; q2.add(x + 1); }
                if (j > 0 && ring[x - nx] == 0) { ring[x - nx] = 2; q2.add(x - nx); }
                if (j < ny - 1 && ring[x + nx] == 0) { ring[x + nx] = 2; q2.add(x + nx); }
            }
            for (int x = 0; x < sxy; x++) {
                if (L[off + x] != DENSE || ring[x] == 2) continue;
                // Enclosed marrow, or a thickened-cortex voxel with no contact to the outside region.
                int i = x % nx, j = x / nx;
                boolean touchesOut = ring[x] == 1 && ((i > 0 && ring[x - 1] == 2) || (i < nx - 1 && ring[x + 1] == 2) || (j > 0 && ring[x - nx] == 2) || (j < ny - 1 && ring[x + nx] == 2));
                if (!touchesOut) L[off + x] = BONE;
            }
        }
        if (prog != null) prog.update("Finding vessels", 80);
        // 5. Remaining dense tissue: bone's blurred edge or contrast-filled vessel. Competitive region growing decides:
        //    bone grows from the cortex, vessel grows from dense tissue more than 2 voxels (through dense tissue) from
        //    any bone, one layer at a time, and each voxel joins whichever front reaches it first. Thin bone edges are
        //    reached by bone first; a vessel pressed against bone loses only its single contact layer.
        byte[] dist = new byte[n];                         // geodesic layers from bone through dense tissue (0 = not reached)
        IntQueue fb = new IntQueue(1 << 14);
        for (int x = 0; x < n; x++) if (L[x] == BONE) fb.add(x);
        for (int layer = 1; layer <= BAND && !fb.isEmpty(); layer++) {
            IntQueue next = new IntQueue(1 << 14);
            while (!fb.isEmpty()) {
                int x = fb.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] == DENSE && dist[y] == 0) { dist[y] = (byte) layer; next.add(y); }
            }
            fb = next;
        }
        // Seeds: all bone, and dense voxels beyond the 2-layer band. Multi-source FIFO growth = nearest seed wins.
        IntQueue grow = new IntQueue(1 << 16);
        for (int x = 0; x < n; x++) {
            if (L[x] == BONE) grow.add(x);
            else if (L[x] == DENSE && dist[x] == 0) { L[x] = VESSEL; grow.add(x); }
        }
        while (!grow.isEmpty()) {
            int x = grow.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
            byte c = L[x];
            int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
            for (int y : nb) if (y >= 0 && L[y] == DENSE) { L[y] = c; grow.add(y); }
        }
        // Dense specks under 8 mm3 inside soft tissue are noise, not vessels.
        for (int start = 0; start < n; start++) {
            if (L[start] != VESSEL) continue;
            comp.clear();
            L[start] = CORE; q.add(start);
            while (!q.isEmpty()) {
                int x = q.poll();
                comp.add(x);
                int i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] == VESSEL) { L[y] = CORE; q.add(y); }
            }
            byte cls = (byte) (comp.size * voxMm3 < 8 ? ORGAN : 0x70);   // 0x70: confirmed vessel (restored below)
            for (int c = 0; c < comp.size; c++) L[comp.a[c]] = cls;
        }
        for (int x = 0; x < n; x++) if (L[x] == 0x70) L[x] = VESSEL;
        for (int x = 0; x < n; x++) if (L[x] == OUT) L[x] = BG;
        if (p.ct) removeExternal(v, prog);
        if (prog != null) prog.update("Done", 100);
    }

    static boolean isDense(byte b) { return b == DENSE || b == CORE; }

    static void visitOut(byte[] L, short[] H, int y, double air, IntQueue q) {
        if (L[y] == 0 && H[y] < air) { L[y] = OUT; q.add(y); }
    }

    // ---------------- external objects and heart isolation ----------------
    /**
     * Removes objects outside the body: the CT table (a thin, wide plate running the scan's length behind the patient)
     * and small disconnected items such as ECG leads. Large separate parts, such as arms, are kept.
     */
    static void removeExternal(Vol3D v, Progress prog) {
        if (prog != null) prog.update("Removing the table", 92);
        byte[] L = v.labels;
        short[] H = v.hu;
        int nx = v.nx, ny = v.ny, nz = v.nz, n = v.n(), sxy = nx * ny;
        // Components of solid material (above -500 HU). Mattress foam (about -900 HU) separates the table from the body.
        java.util.BitSet seen = new java.util.BitSet(n);
        List<long[]> comps = new ArrayList<>();   // seed, size, xmin, xmax, ymin, ymax, zmin, zmax, ysum
        IntQueue q = new IntQueue(1 << 14);
        for (int s0 = 0; s0 < n; s0++) {
            if (L[s0] == BG || H[s0] <= -500 || seen.get(s0)) continue;
            long[] b = {s0, 0, nx, -1, ny, -1, nz, -1, 0};
            seen.set(s0); q.add(s0);
            while (!q.isEmpty()) {
                int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                b[1]++; b[8] += j;
                b[2] = Math.min(b[2], i); b[3] = Math.max(b[3], i); b[4] = Math.min(b[4], j); b[5] = Math.max(b[5], j); b[6] = Math.min(b[6], k); b[7] = Math.max(b[7], k);
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] != BG && H[y] > -500 && !seen.get(y)) { seen.set(y); q.add(y); }
            }
            comps.add(b);
        }
        if (comps.isEmpty()) return;
        long[] body = comps.get(0);
        for (long[] c : comps) if (c[1] > body[1]) body = c;
        double bodyY = body[8] / (double) body[1], vox = v.sx * v.sy * v.sz;
        // Clear the table (thin, wide, running the scan's length, behind the body) and small separate objects.
        for (long[] b : comps) {
            if (b == body) continue;
            double w = (b[3] - b[2] + 1) * v.sx, d = (b[5] - b[4] + 1) * v.sy, len = (b[7] - b[6] + 1) * v.sz;
            double cy = b[8] / (double) b[1];
            boolean table = d < 45 && w > 100 && len > 0.5 * nz * v.sz && cy > bodyY;
            boolean small = b[1] * vox < 2000;
            if (!table && !small) continue;
            int s0 = (int) b[0];
            L[s0] = BG; q.add(s0);
            while (!q.isEmpty()) {
                int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] != BG && H[y] > -500) { L[y] = BG; q.add(y); }
            }
        }
        // Mark the body, then clear low-density voxels (mattress, air pockets) outside its outline in each slice.
        java.util.BitSet inBody = new java.util.BitSet(n);
        int s0 = (int) body[0];
        inBody.set(s0); q.add(s0);
        while (!q.isEmpty()) {
            int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
            int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
            for (int y : nb) if (y >= 0 && L[y] != BG && H[y] > -500 && !inBody.get(y)) { inBody.set(y); q.add(y); }
        }
        // Low-density regions (lungs, airways, mattress) are judged as a whole: kept if mostly inside the body's
        // outline (lungs, even where a small field of view cuts them), cleared if mostly outside it (mattress).
        byte[] inHull = new byte[n], hull = new byte[sxy];
        for (int k = 0; k < nz; k++) if (hullOf(inBody, k * sxy, nx, ny, hull)) System.arraycopy(hull, 0, inHull, k * sxy, sxy);
        java.util.BitSet seenLow = new java.util.BitSet(n);
        for (int t0 = 0; t0 < n; t0++) {
            if (L[t0] == BG || H[t0] > -500 || seenLow.get(t0)) continue;
            long total = 0, inside = 0;
            seenLow.set(t0); q.add(t0);
            while (!q.isEmpty()) {
                int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                total++; if (inHull[x] != 0) inside++;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] != BG && H[y] <= -500 && !seenLow.get(y)) { seenLow.set(y); q.add(y); }
            }
            if (inside * 2 >= total) continue;
            L[t0] = BG; q.add(t0);
            while (!q.isEmpty()) {
                int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] != BG && H[y] <= -500) { L[y] = BG; q.add(y); }
            }
        }
    }

    /** Convex hull (filled into 'hull') of the set bits in one slice. False if the slice has too few. */
    static boolean hullOf(java.util.BitSet set, int off, int nx, int ny, byte[] hull) {
        java.util.Arrays.fill(hull, (byte) 0);
        List<int[]> pts = new ArrayList<>();
        int count = 0;
        for (int j = 0; j < ny; j++) {
            int lo = -1, hi = -1;
            for (int i = 0; i < nx; i++) if (set.get(off + j * nx + i)) { count++; if (lo < 0) lo = i; hi = i; }
            if (lo >= 0) { pts.add(new int[]{lo, j}); if (hi != lo) pts.add(new int[]{hi, j}); }
        }
        if (count < 50 || pts.size() < 3) return false;
        fillHull(pts, nx, ny, hull);
        return true;
    }

    /** Fills the convex hull of the points: each row of a convex polygon is one span, so rows are filled directly. */
    static void fillHull(List<int[]> pts, int nx, int ny, byte[] hull) {
        java.util.Collections.sort(pts, new java.util.Comparator<int[]>() { public int compare(int[] a, int[] b) { return a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]; } });
        int m = pts.size();
        int[][] h = new int[2 * m][];
        int t = 0;
        for (int i = 0; i < m; i++) { while (t >= 2 && cross(h[t - 2], h[t - 1], pts.get(i)) <= 0) t--; h[t++] = pts.get(i); }
        for (int i = m - 2, lower = t + 1; i >= 0; i--) { while (t >= lower && cross(h[t - 2], h[t - 1], pts.get(i)) <= 0) t--; h[t++] = pts.get(i); }
        int nv = t - 1;
        if (nv < 1) return;
        int ymin = Integer.MAX_VALUE, ymax = Integer.MIN_VALUE;
        for (int e = 0; e < nv; e++) { ymin = Math.min(ymin, h[e][1]); ymax = Math.max(ymax, h[e][1]); }
        for (int y = Math.max(0, ymin); y <= Math.min(ny - 1, ymax); y++) {
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int e = 0; e < nv; e++) {
                int[] a = h[e], b = h[(e + 1) % nv];
                if (a[1] == b[1]) { if (a[1] == y) { lo = Math.min(lo, Math.min(a[0], b[0])); hi = Math.max(hi, Math.max(a[0], b[0])); } continue; }
                if (y < Math.min(a[1], b[1]) || y > Math.max(a[1], b[1])) continue;
                double x = a[0] + (b[0] - a[0]) * (y - a[1]) / (double) (b[1] - a[1]);
                lo = Math.min(lo, x); hi = Math.max(hi, x);
            }
            if (lo > hi) continue;
            int x0 = Math.max(0, (int) Math.ceil(lo - 0.01)), x1 = Math.min(nx - 1, (int) Math.floor(hi + 0.01));
            for (int x = x0; x <= x1; x++) hull[y * nx + x] = 1;
        }
    }

    /**
     * Heart isolation for cardiac CT angiography: keeps the contrast-filled heart and great vessels (the largest
     * blood-pool component) and the tissue within 25 mm of it between the lungs (myocardium, coronary arteries,
     * epicardial fat), and returns everything else to hide: chest wall, spine, ribs, lungs, and pulmonary vessels
     * inside the lungs. Returned as a list so the caller can apply it as one undoable edit.
     */
    static IntList isolateHeart(Vol3D v, Progress prog) {
        byte[] L = v.labels;
        int nx = v.nx, ny = v.ny, nz = v.nz, n = v.n(), sxy = nx * ny;
        if (prog != null) prog.update("Finding pulmonary vessels", 10);
        // 1. Vessels surrounded by lung in their slice are pulmonary vessels
        java.util.BitSet pulm = new java.util.BitSet(n);
        int r = Math.max(2, (int) Math.round(5 / v.sx));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            int x = (k * ny + j) * nx + i;
            int c = L[x] & 0x7F;
            if ((L[x] & REMOVED) != 0 || (c != VESSEL && c != CALCIUM)) continue;
            int lung = 0;
            for (int[] d : dirs) {
                int ii = i + d[0] * r, jj = j + d[1] * r;
                if (ii < 0 || jj < 0 || ii >= nx || jj >= ny) continue;
                if ((L[(k * ny + jj) * nx + ii] & 0x7F) == LUNG) lung++;
            }
            if (lung >= 5) pulm.set(x);
        }
        if (prog != null) prog.update("Finding the heart", 30);
        // 2. Largest blood-pool component, not counting pulmonary vessels
        java.util.BitSet seen = new java.util.BitSet(n), pool = new java.util.BitSet(n);
        IntQueue q = new IntQueue(1 << 14);
        IntList cur = new IntList(1 << 12), best = new IntList(4);
        for (int s0 = 0; s0 < n; s0++) {
            if (seen.get(s0) || !isPool(L, s0) || pulm.get(s0)) continue;
            cur = new IntList(1 << 12);
            seen.set(s0); q.add(s0);
            while (!q.isEmpty()) {
                int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                cur.add(x);
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && !seen.get(y) && isPool(L, y) && !pulm.get(y)) { seen.set(y); q.add(y); }
            }
            if (cur.size > best.size) best = cur;
        }
        IntList remove = new IntList(1 << 14);
        if (best.size == 0) return remove;
        for (int c = 0; c < best.size; c++) pool.set(best.a[c]);
        if (prog != null) prog.update("Measuring distance from the heart", 55);
        // 3. Distance (in steps) from the blood pool through tissue that isn't lung or bone
        double step = Math.min(v.sx, Math.min(v.sy, v.sz));
        int maxSteps = (int) Math.min(250, Math.round(25 / step));
        byte[] dist = new byte[n];
        java.util.Arrays.fill(dist, (byte) 255);
        for (int c = 0; c < best.size; c++) { dist[best.a[c]] = 0; q.add(best.a[c]); }
        while (!q.isEmpty()) {
            int x = q.poll(), dx = dist[x] & 255;
            if (dx >= maxSteps) continue;
            int i = x % nx, j = (x / nx) % ny, k = x / sxy;
            int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
            for (int y : nb) {
                if (y < 0 || (dist[y] & 255) <= dx + 1) continue;
                int c = L[y] & 0x7F;
                if (c == BG || c == LUNG || c == BONE || pulm.get(y)) continue;
                dist[y] = (byte) (dx + 1);
                q.add(y);
            }
        }
        if (prog != null) prog.update("Separating the chest wall", 75);
        // 4. Region between the lungs: convex hull of lung tissue in each slice
        byte[] hull = new byte[sxy];
        int nearSteps = (int) Math.round(15 / step);
        for (int k = 0; k < nz; k++) {
            boolean hasHull = sliceHull(L, nx, ny, k, hull);
            for (int x2 = 0; x2 < sxy; x2++) {
                int x = k * sxy + x2;
                int c = L[x] & 0x7F;
                if (c == BG || (L[x] & REMOVED) != 0) continue;
                if (pool.get(x)) continue;
                int d = dist[x] & 255;
                boolean keep = d <= maxSteps && (hasHull ? hull[x2] != 0 : d <= nearSteps) && c != LUNG && c != BONE && !pulm.get(x);
                if (!keep) remove.add(x);
            }
        }
        if (prog != null) prog.update("Done", 100);
        return remove;
    }

    static boolean isPool(byte[] L, int x) { int c = L[x] & 0x7F; return (L[x] & REMOVED) == 0 && (c == VESSEL || c == CALCIUM); }

    /** Fills 'hull' with the convex hull of lung voxels in slice k. False if the slice has too little lung. */
    static boolean sliceHull(byte[] L, int nx, int ny, int k, byte[] hull) {
        java.util.Arrays.fill(hull, (byte) 0);
        List<int[]> pts = new ArrayList<>();
        int off = k * nx * ny, count = 0;
        for (int j = 0; j < ny; j++) {
            int lo = -1, hi = -1;
            for (int i = 0; i < nx; i++) if ((L[off + j * nx + i] & 0x7F) == LUNG) { count++; if (lo < 0) lo = i; hi = i; }
            if (lo >= 0) { pts.add(new int[]{lo, j}); if (hi != lo) pts.add(new int[]{hi, j}); }
        }
        if (count < 200 || pts.size() < 3) return false;
        fillHull(pts, nx, ny, hull);
        return true;
    }

    static long cross(int[] o, int[] a, int[] b) { return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]); }

    // ---------------- manual editing ----------------
    /** One undoable change: the voxels touched and their previous labels. */
    static final class Edit {
        final String what;
        final int[] idx;
        final byte[] old;
        Edit(String what, int[] idx, byte[] old) { this.what = what; this.idx = idx; this.old = old; }
        long bytes() { return idx.length * 5L; }
    }

    static void undo(Vol3D v, Edit e) { for (int c = 0; c < e.idx.length; c++) v.labels[e.idx[c]] = e.old[c]; }

    static Edit set(Vol3D v, String what, IntList idx, boolean remove, int cls) {
        int[] a = java.util.Arrays.copyOf(idx.a, idx.size);
        byte[] old = new byte[a.length];
        for (int c = 0; c < a.length; c++) {
            int x = a[c];
            old[c] = v.labels[x];
            v.labels[x] = (byte) (remove ? (v.labels[x] | REMOVED) : ((v.labels[x] & REMOVED) | cls));
        }
        return new Edit(what, a, old);
    }

    /** Connected voxels (6-neighbour) of the same visible class as the seed. */
    static IntList component(Vol3D v, int seed) {
        IntList out = new IntList(1 << 12);
        if (seed < 0 || (v.labels[seed] & REMOVED) != 0) return out;
        int cls = v.labels[seed], nx = v.nx, ny = v.ny, nz = v.nz, sxy = nx * ny;
        java.util.BitSet seen = new java.util.BitSet(v.n());
        IntQueue q = new IntQueue(1 << 14);
        q.add(seed); seen.set(seed);
        while (!q.isEmpty()) {
            int x = q.poll();
            out.add(x);
            int i = x % nx, j = (x / nx) % ny, k = x / sxy;
            int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
            for (int y : nb) if (y >= 0 && !seen.get(y) && v.labels[y] == cls) { seen.set(y); q.add(y); }
        }
        return out;
    }

    /** Everything of the component's class except the component itself (for "show only this"). */
    static IntList othersOfClass(Vol3D v, IntList comp, int cls) {
        java.util.BitSet in = new java.util.BitSet(v.n());
        for (int c = 0; c < comp.size; c++) in.set(comp.a[c]);
        IntList out = new IntList(1 << 12);
        for (int x = 0; x < v.n(); x++) if (v.labels[x] == cls && !in.get(x)) out.add(x);
        return out;
    }

    /** Voxels whose centre projects inside (or outside) a screen polygon, for the scalpel. */
    static IntList cut(final Vol3D v, final VrtState.Camera cam, final float[] poly, final int w, final int h, final boolean inside) {
        final IntList[] parts = new IntList[v.nz];
        final double[] half = v.half();
        Volume.parallelRows(v.nz, new Volume.RowTask() {
            public void rows(int k0, int k1) {
                float[] s = new float[2];
                for (int k = k0; k < k1; k++) {
                    IntList l = new IntList(256);
                    double z = ((k + 0.5) / v.nz) * 2 * half[2] - half[2];
                    for (int j = 0; j < v.ny; j++) {
                        double y = ((j + 0.5) / v.ny) * 2 * half[1] - half[1];
                        int base = (k * v.ny + j) * v.nx;
                        for (int i = 0; i < v.nx; i++) {
                            int x = base + i;
                            int lab = v.labels[x];
                            if ((lab & REMOVED) != 0 || lab == BG) continue;
                            double xx = ((i + 0.5) / v.nx) * 2 * half[0] - half[0];
                            cam.toScreen(xx, y, z, w, h, s);
                            if (pointInPolygon(s[0], s[1], poly) == inside) l.add(x);
                        }
                    }
                    parts[k] = l;
                }
            }
        });
        IntList all = new IntList(1 << 12);
        for (IntList l : parts) if (l != null) for (int c = 0; c < l.size; c++) all.add(l.a[c]);
        return all;
    }

    static boolean pointInPolygon(float x, float y, float[] p) {
        boolean in = false;
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = p[2 * i], yi = p[2 * i + 1], xj = p[2 * j], yj = p[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-12f) + xi) in = !in;
        }
        return in;
    }

    // ---------------- small collections without boxing ----------------
    static final class IntList {
        int[] a; int size;
        IntList(int cap) { a = new int[Math.max(4, cap)]; }
        void add(int x) { if (size == a.length) a = java.util.Arrays.copyOf(a, a.length * 2); a[size++] = x; }
        void clear() { size = 0; }
    }

    /** FIFO ring buffer that only grows to the size of the flood-fill frontier. */
    static final class IntQueue {
        int[] a; int head, tail, count;
        IntQueue(int cap) { a = new int[cap]; }
        boolean isEmpty() { return count == 0; }
        void add(int x) {
            if (count == a.length) {
                int[] b = new int[a.length * 2];
                for (int i = 0; i < count; i++) b[i] = a[(head + i) % a.length];
                a = b; head = 0; tail = count;
            }
            a[tail] = x; tail = (tail + 1) % a.length; count++;
        }
        int poll() { int x = a[head]; head = (head + 1) % a.length; count--; return x; }
    }

    static int classCount(Vol3D v, int cls) { int c = 0; for (byte b : v.labels) if (b == cls) c++; return c; }
}
