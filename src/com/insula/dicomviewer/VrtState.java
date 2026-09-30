/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import org.json.JSONArray;
import org.json.JSONObject;

/** Everything needed to reproduce a 3D view: tissue classes, rendering mode, camera, clipping, and lighting. */
final class VrtState {
    static final String[] CLASS_NAMES = {"Background", "Skin and fat", "Organs and soft tissue", "Vessels and heart chambers",
            "Bone", "Lungs", "Calcium", "Selection"};
    static final int VRT = 0, MIP = 1, MINIP = 2, SURFACE = 3;
    static final String[] MODE_NAMES = {"Volume rendering (VRT)", "Maximum intensity (MIP)", "Minimum intensity (MinIP)", "Surface"};

    static final class Cls {
        boolean visible;
        float lo, hi, opacity, r, g, b;
        Cls(boolean vis, float lo, float hi, float op, int rgb) {
            visible = vis; this.lo = lo; this.hi = hi; opacity = op;
            r = ((rgb >> 16) & 255) / 255f; g = ((rgb >> 8) & 255) / 255f; b = (rgb & 255) / 255f;
        }
        int rgb() { return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255); }
        void setRgb(int c) { r = ((c >> 16) & 255) / 255f; g = ((c >> 8) & 255) / 255f; b = (c & 255) / 255f; }
    }

    /** Orthographic camera looking at the volume; rows of R are the camera's right, up, and back axes in patient space. */
    static final class Camera {
        final double[] R = new double[9];
        double zoom = 1.25, panX, panY;

        Camera() { anterior(); }

        void set(double[] right, double[] up) {
            double[] back = Volume.norm(Volume.cross(right, up));
            double[] u = Volume.norm(Volume.cross(back, right));
            double[] r = Volume.norm(right);
            R[0] = r[0]; R[1] = r[1]; R[2] = r[2];
            R[3] = u[0]; R[4] = u[1]; R[5] = u[2];
            R[6] = back[0]; R[7] = back[1]; R[8] = back[2];
        }

        // Patient axes: x = left, y = posterior, z = head.
        void anterior() { set(new double[]{1, 0, 0}, new double[]{0, 0, 1}); }       // facing the patient
        void posterior() { set(new double[]{-1, 0, 0}, new double[]{0, 0, 1}); }
        void leftSide() { set(new double[]{0, 1, 0}, new double[]{0, 0, 1}); }      // viewer on the patient's left
        void rightSide() { set(new double[]{0, -1, 0}, new double[]{0, 0, 1}); }
        void superior() { set(new double[]{1, 0, 0}, new double[]{0, -1, 0}); }     // from above the head
        void inferior() { set(new double[]{1, 0, 0}, new double[]{0, 1, 0}); }      // from the feet

        /** Rotates by screen drag: horizontal around the screen's up axis, vertical around its right axis. */
        void rotate(double ax, double ay) {
            double[] right = {R[0], R[1], R[2]}, up = {R[3], R[4], R[5]};
            double[] r2 = Volume.rot(right, up, ax);        // horizontal drag: turn around the screen's up axis
            double[] u2 = Volume.rot(up, r2, ay);           // vertical drag: tilt around the screen's right axis
            set(r2, u2);
        }

        /** Rotation around the vertical (patient head) axis, used for spin and rotation series. */
        void spin(double a) {
            double[] z = {0, 0, 1};
            set(Volume.rot(new double[]{R[0], R[1], R[2]}, z, a), Volume.rot(new double[]{R[3], R[4], R[5]}, z, a));
        }

        /** Ray for a pixel, in model space: {ox, oy, oz, dx, dy, dz}. Same formula as the shader. */
        void ray(int px, int py, int w, int h, double[] out) {
            double aspect = w / (double) h;
            double xn = (px + 0.5) / w * 2 - 1, yn = 1 - (py + 0.5) / h * 2;
            double cx = xn * aspect / zoom - panX, cy = yn / zoom - panY, cz = 3;
            out[0] = R[0] * cx + R[3] * cy + R[6] * cz;
            out[1] = R[1] * cx + R[4] * cy + R[7] * cz;
            out[2] = R[2] * cx + R[5] * cy + R[8] * cz;
            out[3] = -R[6]; out[4] = -R[7]; out[5] = -R[8];
        }

        /** Screen position (pixels) of a model-space point. Inverse of ray(). */
        void toScreen(double x, double y, double z, int w, int h, float[] out) {
            double aspect = w / (double) h;
            double cx = R[0] * x + R[1] * y + R[2] * z, cy = R[3] * x + R[4] * y + R[5] * z;
            double xn = (cx + panX) * zoom / aspect, yn = (cy + panY) * zoom;
            out[0] = (float) ((xn + 1) / 2 * w);
            out[1] = (float) ((1 - yn) / 2 * h);
        }

        /** Columns right, up, back: the matrix taking camera coordinates to model space (column-major for GL). */
        float[] toModel() {
            return new float[]{(float) R[0], (float) R[1], (float) R[2], (float) R[3], (float) R[4], (float) R[5], (float) R[6], (float) R[7], (float) R[8]};
        }

        JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            JSONArray r = new JSONArray();
            for (double d : R) r.put(d);
            o.put("R", r); o.put("zoom", zoom); o.put("panX", panX); o.put("panY", panY);
            return o;
        }

        void read(JSONObject o) {
            JSONArray r = o.optJSONArray("R");
            if (r != null && r.length() == 9) for (int i = 0; i < 9; i++) R[i] = r.optDouble(i);
            zoom = o.optDouble("zoom", zoom); panX = o.optDouble("panX", 0); panY = o.optDouble("panY", 0);
        }
    }

    final Cls[] cls = new Cls[8];
    int mode = VRT;
    float ka = 0.30f, kd = 0.75f, ks = 0.35f, shin = 24f, surface = 0.15f;
    int bg = 0xFF000000;
    double winC = 300, winW = 800;
    final float[] clipLo = {0, 0, 0}, clipHi = {1, 1, 1};
    final Camera cam = new Camera();
    Seg.Params params = new Seg.Params();
    String name = "", seriesUid = "";
    long created;

    VrtState() { defaults(true, 200); }

    /** Default colours and ranges; vessel ramp follows the detected contrast threshold. */
    void defaults(boolean ct, double vesselThr) {
        float v = (float) vesselThr;
        cls[Seg.BG] = new Cls(false, 0, 1, 0, 0x000000);
        cls[Seg.SKIN] = new Cls(false, ct ? -250 : 0, ct ? 20 : 1, 0.03f, 0xF2C0A6);
        cls[Seg.ORGAN] = new Cls(true, ct ? 20 : 0, ct ? v : 1, 0.10f, 0xC8645A);
        cls[Seg.VESSEL] = new Cls(true, v - 60, v + 160, 0.85f, 0xE0302A);
        cls[Seg.BONE] = new Cls(true, 180, 900, 0.75f, 0xF2EBDA);
        cls[Seg.LUNG] = new Cls(false, -950, -500, 0.03f, 0xA8C4DC);
        cls[Seg.CALCIUM] = new Cls(true, 500, 1200, 0.95f, 0xFFFFFF);
        cls[Seg.SELECT] = new Cls(true, ct ? -100 : 0, ct ? 400 : 1, 0.6f, 0x4CC38A);
    }

    int visMask() { int m = 0; for (int i = 0; i < 8; i++) if (cls[i].visible) m |= 1 << i; return m; }

    /** 256 x 8 RGBA transfer-function table over [huMin, huMax]; rows are classes. */
    byte[] table(int huMin, int huMax) {
        byte[] t = new byte[256 * 8 * 4];
        for (int c = 0; c < 8; c++) {
            Cls k = cls[c];
            for (int i = 0; i < 256; i++) {
                double hu = huMin + (i + 0.5) / 256.0 * (huMax - huMin);
                double ramp = k.hi <= k.lo ? (hu >= k.lo ? 1 : 0) : smooth((hu - k.lo) / (k.hi - k.lo));
                double a = c == Seg.BG ? 0 : ramp * k.opacity, shade = 0.55 + 0.45 * ramp;
                int o = (c * 256 + i) * 4;
                t[o] = (byte) Math.round(Math.min(1, k.r * shade) * 255);
                t[o + 1] = (byte) Math.round(Math.min(1, k.g * shade) * 255);
                t[o + 2] = (byte) Math.round(Math.min(1, k.b * shade) * 255);
                t[o + 3] = (byte) Math.round(Math.min(1, a) * 255);
            }
        }
        return t;
    }

    static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    static final String[] PRESETS = {"Coronary CTA", "Vessels only", "Bones", "All tissues", "Lungs and airways", "Vessel MIP", "Skin surface"};

    void preset(int p, boolean ct) {
        for (int i = 1; i < 8; i++) cls[i].visible = false;
        mode = VRT;
        switch (p) {
            case 0: cls[Seg.VESSEL].visible = cls[Seg.CALCIUM].visible = cls[Seg.ORGAN].visible = cls[Seg.SELECT].visible = true; cls[Seg.ORGAN].opacity = 0.08f; break;
            case 1: cls[Seg.VESSEL].visible = cls[Seg.CALCIUM].visible = cls[Seg.SELECT].visible = true; break;
            case 2: cls[Seg.BONE].visible = cls[Seg.CALCIUM].visible = true; break;
            case 3: cls[Seg.SKIN].visible = cls[Seg.ORGAN].visible = cls[Seg.VESSEL].visible = cls[Seg.BONE].visible = cls[Seg.CALCIUM].visible = cls[Seg.SELECT].visible = true; cls[Seg.ORGAN].opacity = 0.12f; break;
            case 4: cls[Seg.LUNG].visible = cls[Seg.VESSEL].visible = true; cls[Seg.LUNG].opacity = 0.05f; break;
            case 5: cls[Seg.VESSEL].visible = cls[Seg.CALCIUM].visible = cls[Seg.BONE].visible = cls[Seg.ORGAN].visible = cls[Seg.SELECT].visible = true; mode = MIP; winC = ct ? 300 : winC; winW = ct ? 900 : winW; break;
            case 6: cls[Seg.SKIN].visible = true; cls[Seg.SKIN].opacity = 0.9f; mode = SURFACE; break;
        }
    }

    JSONObject json() throws Exception {
        JSONObject o = new JSONObject();
        o.put("version", 1); o.put("name", name); o.put("series", seriesUid); o.put("created", created);
        o.put("mode", mode); o.put("ka", ka); o.put("kd", kd); o.put("ks", ks); o.put("shin", shin); o.put("surface", surface);
        o.put("bg", bg); o.put("winC", winC); o.put("winW", winW);
        o.put("clipLo", new JSONArray(new double[]{clipLo[0], clipLo[1], clipLo[2]}));
        o.put("clipHi", new JSONArray(new double[]{clipHi[0], clipHi[1], clipHi[2]}));
        o.put("camera", cam.json());
        JSONArray a = new JSONArray();
        for (Cls k : cls) {
            JSONObject c = new JSONObject();
            c.put("visible", k.visible); c.put("lo", k.lo); c.put("hi", k.hi); c.put("opacity", k.opacity); c.put("rgb", k.rgb() & 0xFFFFFF);
            a.put(c);
        }
        o.put("classes", a);
        JSONObject p = new JSONObject();
        p.put("air", params.air); p.put("fat", params.fat); p.put("vessel", params.vessel); p.put("boneSeed", params.boneSeed); p.put("calcium", params.calcium);
        p.put("contrast", params.contrast); p.put("ct", params.ct); p.put("summary", params.summary);
        o.put("params", p);
        return o;
    }

    static VrtState fromJson(JSONObject o) {
        VrtState s = new VrtState();
        s.name = o.optString("name"); s.seriesUid = o.optString("series"); s.created = o.optLong("created");
        s.mode = o.optInt("mode", VRT);
        s.ka = (float) o.optDouble("ka", s.ka); s.kd = (float) o.optDouble("kd", s.kd); s.ks = (float) o.optDouble("ks", s.ks);
        s.shin = (float) o.optDouble("shin", s.shin); s.surface = (float) o.optDouble("surface", s.surface);
        s.bg = o.optInt("bg", s.bg); s.winC = o.optDouble("winC", s.winC); s.winW = o.optDouble("winW", s.winW);
        JSONArray lo = o.optJSONArray("clipLo"), hi = o.optJSONArray("clipHi");
        for (int i = 0; i < 3; i++) {
            if (lo != null) s.clipLo[i] = (float) lo.optDouble(i, 0);
            if (hi != null) s.clipHi[i] = (float) hi.optDouble(i, 1);
        }
        JSONObject cam = o.optJSONObject("camera");
        if (cam != null) s.cam.read(cam);
        JSONArray a = o.optJSONArray("classes");
        if (a != null) for (int i = 0; i < Math.min(8, a.length()); i++) {
            JSONObject c = a.optJSONObject(i);
            if (c == null) continue;
            s.cls[i].visible = c.optBoolean("visible"); s.cls[i].lo = (float) c.optDouble("lo"); s.cls[i].hi = (float) c.optDouble("hi");
            s.cls[i].opacity = (float) c.optDouble("opacity"); s.cls[i].setRgb(c.optInt("rgb"));
        }
        JSONObject p = o.optJSONObject("params");
        if (p != null) {
            s.params.air = p.optDouble("air"); s.params.fat = p.optDouble("fat"); s.params.vessel = p.optDouble("vessel");
            s.params.boneSeed = p.optDouble("boneSeed"); s.params.calcium = p.optDouble("calcium");
            s.params.contrast = p.optBoolean("contrast"); s.params.ct = p.optBoolean("ct", true); s.params.summary = p.optString("summary");
        }
        return s;
    }
}
