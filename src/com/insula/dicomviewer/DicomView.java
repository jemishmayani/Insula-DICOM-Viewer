/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DicomView extends View {
    public static final int T_WL = 0, T_PAN = 1, T_SCROLL = 2, T_ZOOM = 3, T_LENGTH = 4, T_ANGLE = 5, T_COBB = 6,
            T_ELLIPSE = 7, T_RECT = 8, T_PROBE = 9, T_ARROW = 10, T_CROSS = 11, T_ERASE = 12, T_ORBIT = 13, T_CURVE = 14, T_SELECT = 15,
            T_PTLINE = 16, T_ABC = 17;

    public interface Listener {
        void onIndexChanged(DicomView v);
        void onActivated(DicomView v);
        void onWindowChanged(DicomView v);
        void onImageTap(DicomView v, float ix, float iy);
        void onArrowCreated(DicomView v, Ann a);
        /** A measurement was completed (used for named shortcuts and the hematoma volume prompt). */
        void onMeasureDone(DicomView v, Ann a);
        /** A measurement is being edited (moved or reshaped), so displayed results can follow it. */
        void onMeasureEdited(DicomView v, Ann a);
        void onSelection(DicomView v, Ann a);
        /** A slice that was loading in the background is now shown. */
        void onImageReady(DicomView v);
        /** Long press on the image with a navigation tool (scroll, window, pan). */
        void onLongPressImage(DicomView v);
        /** The finger was lifted after scrolling, windowing, panning, or zooming. */
        void onInteractionEnd(DicomView v);
    }

    /** Receives raw touch phases (0 down, 1 move, 2 up) for crosshair, orbit, and curve tools. */
    public interface Hook { void onHook(DicomView v, int phase, float ix, float iy, float sx, float sy); }
    public Hook hook;

    /** A reference line through (x,y) with direction (dx,dy), in image pixels. */
    public static final class CrossLine {
        public float x, y, dx, dy, slabHalf;
        public int color, plane = -1;
    }
    public final List<CrossLine> crossLines = new ArrayList<>();
    public boolean crossVisible = true;
    public float[] curve;
    public int accent = Ui.ACCENT;
    public boolean wlOnRgb;

    public static final class Ann {
        public final int type;
        public final float[] p;
        public String text = "";
        public boolean done;
        Ann(int t, float[] p) { type = t; this.p = p; }
        Ann copy() { Ann a = new Ann(type, p.clone()); a.text = text; a.done = done; return a; }
    }

    public SliceProvider prov;
    public int index;
    public RawImage img;
    String error;
    Bitmap bmp;
    int[] buf;
    public double wc = 0, ww = 1;
    boolean wlSet;
    public boolean invert, flipH, flipV, overlay = true, hidePhi, active, showBorder, exporting;
    public int rot;
    public float zoom = 1, panX, panY;
    public int tool = T_WL;
    public Listener listener;
    public String emptyText = "No series loaded. Tap Series to choose one.";
    public String label;
    /** Label given to the next measurement drawn (set by a measurement shortcut such as "ADI"). */
    public String nextText = "";
    final Map<Integer, List<Ann>> anns = new HashMap<>();
    Ann pending;
    int phase;
    public Ann selected;
    int dragIdx = -1;
    boolean dragBody, editing, createUndo;
    float[] lastImg;
    static final class Undo { List<Ann> list; List<Ann> copy; }
    final java.util.ArrayDeque<Undo> undo = new java.util.ArrayDeque<>();
    final Paint selLine = new Paint(Paint.ANTI_ALIAS_FLAG), handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public float crossX = Float.NaN, crossY = Float.NaN;
    // Smooth loading and rendering
    volatile int loadToken;
    public boolean loading, touching, interacting, dirty;
    public int scrollDir = 1;
    Bitmap preview;
    int[] pbuf;
    int previewStep = 1;
    // Momentum scrolling and the edge scrub bar
    float flingV;
    long flingLast, lastScrollMs;
    boolean scrubbing;
    public boolean scrubEnabled = true;
    final Paint scrubTrack = new Paint(Paint.ANTI_ALIAS_FLAG), scrubThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Runnable flingStep = new Runnable() {
        public void run() {
            if (Math.abs(flingV) < 250 * dp || prov == null) { flingV = 0; endInteraction(); return; }
            long now = android.os.SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - flingLast) / 1000f);
            flingLast = now;
            scrollAcc += flingV * dt;
            float step = scrollStep();
            int before = index;
            while (Math.abs(scrollAcc) >= step) { int sg = scrollAcc > 0 ? 1 : -1; setIndex(index + sg); scrollAcc -= sg * step; }
            if ((index == 0 && flingV < 0) || (index == count() - 1 && flingV > 0) || (before == index && count() <= 1)) { flingV = 0; endInteraction(); return; }
            flingV *= (float) Math.exp(-dt * 2.8);
            postOnAnimation(this);
        }
    };
    public boolean showAnnotations = true, showMeasures = true;
    public float topInset;
    /** Cross-reference lines from other viewports, in this image's pixel coordinates (x1,y1,x2,y2). */
    public final List<float[]> refLines = new ArrayList<>();
    final Paint refPaint = new Paint(Paint.ANTI_ALIAS_FLAG), valueP = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Matrix m = new Matrix(), inv = new Matrix();
    final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            box = new Paint(), border = new Paint(), cross = new Paint(Paint.ANTI_ALIAS_FLAG), orientP = new Paint(Paint.ANTI_ALIAS_FLAG);
    final ScaleGestureDetector sgd;
    final GestureDetector gd;
    final float dp;
    float lastX, lastY, scrollAcc, lfx, lfy;
    boolean multi, hasFocus;

    public DicomView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        text.setColor(0xFFF2F5F7);
        text.setTextSize(14f * dp);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        text.setShadowLayer(2 * dp, 0, 0, Color.BLACK);
        orientP.set(text);
        orientP.setColor(0xFFFFD27A);
        orientP.setTextSize(13 * dp);
        orientP.setColor(0xCCFFD27A);
        orientP.setFakeBoldText(true);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f * dp);
        line.setColor(0xFFFFD000);
        selLine.setStyle(Paint.Style.STROKE);
        selLine.setStrokeWidth(2.6f * dp);
        selLine.setColor(0xFF4FC3F7);
        handleFill.setColor(0xFFFFFFFF);
        box.setColor(0xB0000000);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * dp);
        border.setColor(Ui.ACCENT);
        border.setStrokeWidth(2.5f * dp);
        refPaint.setStyle(Paint.Style.STROKE);
        refPaint.setStrokeWidth(1.3f * dp);
        refPaint.setColor(0xDDFFD54F);
        refPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * dp, 5 * dp}, 0));
        valueP.set(text);
        valueP.setColor(Ui.VALUE);
        cross.setStyle(Paint.Style.STROKE);
        cross.setStrokeWidth(1 * dp);
        cross.setColor(0xCC4FD1FF);
        setBackgroundColor(Color.BLACK);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float nz = Math.max(0.1f, Math.min(60f, zoom * f));
                f = nz / zoom;
                float cx = getWidth() / 2f, cy = getHeight() / 2f, fx = d.getFocusX(), fy = d.getFocusY();
                panX = fx - cx - (fx - cx - panX) * f;
                panY = fy - cy - (fy - cy - panY) * f;
                zoom = nz;
                invalidate();
                return true;
            }
        });
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (tool <= T_ZOOM || tool == T_CROSS) { resetView(); return true; }
                return false;
            }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (tool != T_SCROLL || multi || scrubbing || count() < 3 || Math.abs(vy) < Math.abs(vx)) return false;
                flingV = vy;
                flingLast = android.os.SystemClock.uptimeMillis();
                interacting = true;
                postOnAnimation(flingStep);
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (listener == null || multi || scrubbing) return;
                if (tool == T_SCROLL || tool == T_WL || tool == T_PAN) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    listener.onLongPressImage(DicomView.this);
                }
            }
        });
        scrubTrack.setColor(0x55FFFFFF);
        scrubTrack.setStrokeCap(Paint.Cap.ROUND);
        scrubTrack.setStrokeWidth(3 * dp);
        scrubThumb.setColor(Ui.ACCENT);
        scrubThumb.setStrokeCap(Paint.Cap.ROUND);
        scrubThumb.setStrokeWidth(6 * dp);
    }

    // ---------- data ----------
    public void setProvider(SliceProvider p, int start) {
        prov = p;
        anns.clear();
        pending = null;
        wlSet = false;
        zoom = 1; panX = panY = 0; rot = 0; flipH = flipV = invert = false;
        index = 0;
        img = null;
        if (p != null && p.count() > 0) setIndex(start);
        else { error = p != null ? "This series has no viewable images." : null; invalidate(); }
    }

    public int count() { return prov == null ? 0 : prov.count(); }

    public void setIndex(int i) {
        if (prov == null || prov.count() == 0) return;
        if (i < 0) i = 0;
        if (i >= prov.count()) i = prov.count() - 1;
        if (i != index) { scrollDir = i > index ? 1 : -1; lastScrollMs = android.os.SystemClock.uptimeMillis(); }
        index = i;
        if (pending != null && !pending.done) { removeAnn(pending); pending = null; }
        if (selected != null) { selected = null; notifySel(); }
        if (prov.async()) {
            RawImage r = prov.peek(i);
            if (r != null) { loadToken++; loading = false; apply(r); }
            else {
                // Keep showing the previous slice until this one is decoded off the UI thread.
                loading = true;
                final int tok = ++loadToken;
                prov.load(i, new Pending(tok) {
                    public void done(final RawImage r, final Throwable err) {
                        post(new Runnable() {
                            public void run() {
                                if (tok != loadToken) return;
                                loading = false;
                                if (err != null) { img = null; error = Ui.friendly(err); dirty = true; }
                                else apply(r);
                                invalidate();
                                if (listener != null) listener.onImageReady(DicomView.this);
                            }
                        });
                    }
                });
            }
        } else {
            try { apply(prov.image(i)); }
            catch (Throwable t) { img = null; error = Ui.friendly(t); dirty = true; }
        }
        invalidate();
        if (listener != null) listener.onIndexChanged(this);
    }

    /** A background load that knows whether it's still the slice on screen. */
    abstract class Pending implements Library.Done, Library.Wanted {
        final int tok;
        Pending(int tok) { this.tok = tok; }
        public boolean wanted() { return tok == loadToken; }
    }

    void apply(RawImage r) {
        img = r;
        error = null;
        if (img != null && !wlSet) { double[] d = img.defaultWindow(); wc = d[0]; ww = d[1]; wlSet = true; }
        dirty = true;
    }

    float scrollStep() { return Math.max(6 * dp, Math.min(24 * dp, getHeight() / (float) Math.max(10, count()))); }

    void endInteraction() {
        boolean was = interacting;
        interacting = false;
        if (was && previewStep > 1) { dirty = true; invalidate(); }
        if (listener != null) listener.onInteractionEnd(this);
    }

    public void stopFling() { flingV = 0; removeCallbacks(flingStep); }

    public void refresh() { if (prov != null) setIndex(index); }

    /** Shows slice i with the image fully loaded before returning (for exports that snapshot each slice). */
    public void setIndexNow(int i) {
        if (prov == null || prov.count() == 0) return;
        i = Math.max(0, Math.min(prov.count() - 1, i));
        index = i;
        loadToken++;
        loading = false;
        try { apply(prov.image(i)); } catch (Throwable t) { img = null; error = t.getMessage(); dirty = true; }
        if (dirty) doRender();
    }

    public void setWindow(double c, double w) {
        wc = c; ww = Math.max(1, w); wlSet = true;
        dirty = true;
        postInvalidateOnAnimation();
    }

    public void autoWindow() {
        if (img == null) return;
        double[] d = img.autoWindow();
        setWindow(d[0], d[1]);
    }

    public void defaultWindow() {
        if (img == null) return;
        double[] d = img.defaultWindow();
        setWindow(d[0], d[1]);
    }

    public void resetView() {
        zoom = 1; panX = panY = 0; rot = 0; flipH = flipV = false;
        invalidate();
    }

    /** Marks the image for re-rendering; the work happens once per frame in onDraw. */
    void render() { dirty = true; invalidate(); }

    /** Windowing into a bitmap. While the user drags on very large images, a reduced preview is rendered instead. */
    void doRender() {
        dirty = false;
        if (img == null) { bmp = null; return; }
        long n = (long) img.w * img.h;
        int step = !interacting ? 1 : n > 10_000_000 ? 3 : n > 2_500_000 ? 2 : 1;
        previewStep = step;
        if (step > 1) {
            int pw = Math.max(1, img.w / step), ph = Math.max(1, img.h / step);
            if (pbuf == null || pbuf.length != pw * ph) pbuf = new int[pw * ph];
            if (preview == null || preview.getWidth() != pw || preview.getHeight() != ph) preview = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
            img.render(pbuf, wc, ww, invert, step);
            preview.setPixels(pbuf, 0, pw, 0, 0, pw, ph);
            return;
        }
        int nn = img.w * img.h;
        if (buf == null || buf.length != nn) buf = new int[nn];
        if (bmp == null || bmp.getWidth() != img.w || bmp.getHeight() != img.h) bmp = Bitmap.createBitmap(img.w, img.h, Bitmap.Config.ARGB_8888);
        img.render(buf, wc, ww, invert);
        bmp.setPixels(buf, 0, img.w, 0, 0, img.w, img.h);
    }

    /** Annotations on the current image: stored per SOP instance for real series, in memory for reconstructions. */
    public List<Ann> annotations() {
        Library.SliceRef r = prov == null ? null : prov.ref(index);
        if (r != null) return AnnStore.list(AnnStore.key(r));
        List<Ann> l = anns.get(index);
        if (l == null) { l = new ArrayList<>(); anns.put(index, l); }
        return l;
    }

    void persist() {
        Library.SliceRef r = prov == null ? null : prov.ref(index);
        if (r != null) AnnStore.changed();
    }

    void notifySel() { if (listener != null) listener.onSelection(this, selected); }

    void removeAnn(Ann a) { for (List<Ann> l : anns.values()) l.remove(a); annotations().remove(a); }

    /** Clears in-memory annotations (reconstructed planes). Stored series annotations are untouched. */
    public void clearAnnotations() { anns.clear(); pending = null; undo.clear(); if (selected != null) { selected = null; notifySel(); } invalidate(); }

    static boolean isAnnTool(int t) { return (t >= T_LENGTH && t <= T_ARROW) || t == T_SELECT || t == T_ERASE || t == T_PTLINE || t == T_ABC; }
    static boolean isDrawTool(int t) { return (t >= T_LENGTH && t <= T_ARROW) || t == T_PTLINE || t == T_ABC; }

    void pushUndo() {
        Undo u = new Undo();
        u.list = annotations();
        u.copy = new ArrayList<>();
        for (Ann a : u.list) if (a.done) u.copy.add(a.copy());
        undo.push(u);
        while (undo.size() > 40) undo.removeLast();
    }

    public boolean canUndo() { return !undo.isEmpty(); }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        Undo u = undo.pop();
        u.list.clear();
        u.list.addAll(u.copy);
        pending = null; phase = 0;
        selected = null;
        notifySel();
        persist();
        invalidate();
        return true;
    }

    public void deleteSelected() {
        if (selected == null) return;
        pushUndo();
        annotations().remove(selected);
        selected = null;
        notifySel();
        persist();
        invalidate();
    }

    public void duplicateSelected() {
        if (selected == null) return;
        pushUndo();
        Ann a = selected.copy();
        float off = img != null ? Math.max(img.w, img.h) * 0.04f : 10;
        for (int k = 0; k + 1 < a.p.length; k += 2) if (!Float.isNaN(a.p[k])) { a.p[k] += off; a.p[k + 1] += off; }
        annotations().add(a);
        selected = a;
        notifySel();
        persist();
        invalidate();
    }

    public void setLabel(Ann a, String text) {
        pushUndo();
        a.text = text == null ? "" : text;
        persist();
        notifySel();
        invalidate();
    }

    public void clearSlice() {
        if (annotations().isEmpty()) return;
        pushUndo();
        annotations().clear();
        selected = null;
        notifySel();
        persist();
        invalidate();
    }

    public String describe(Ann a) {
        switch (a.type) {
            case T_LENGTH: return (a.text.isEmpty() ? "Length" : a.text) + "  " + fmtLen(a.p[0], a.p[1], a.p[2], a.p[3]);
            case T_ANGLE: return String.format("Angle  %.1f°", angleAt(a.p[0], a.p[1], a.p[2], a.p[3], a.p[4], a.p[5]));
            case T_COBB: return Float.isNaN(a.p[4]) ? "Cobb angle" : String.format("Cobb angle  %.1f°", cobb(a.p));
            case T_ELLIPSE: case T_RECT: {
                List<String> st = roiStats(a);
                return (a.type == T_ELLIPSE ? "Ellipse  " : "Rectangle  ") + st.get(0) + (st.size() > 1 ? "  " + st.get(1) : "");
            }
            case T_PROBE: return "Pixel value  " + valueAt(a.p[0], a.p[1]);
            case T_ARROW: return a.text.isEmpty() ? "Arrow (no label)" : "Arrow  \u201c" + a.text + "\u201d";
            case T_PTLINE: return (a.text.isEmpty() ? "Point to line" : a.text) + (Float.isNaN(a.p[4]) ? "" : "  " + ptLineText(a.p));
            case T_ABC: return "Hematoma ABC/2" + (Float.isNaN(a.p[4]) ? "" : "  A " + fmtLen(a.p[0], a.p[1], a.p[2], a.p[3]) + ", B " + fmtLen(a.p[4], a.p[5], a.p[6], a.p[7])) + (abcVolume(a).isEmpty() ? "" : "  " + abcVolume(a));
        }
        return "Annotation";
    }

    boolean visible(Ann a) { return a.type == T_ARROW ? showAnnotations : showMeasures; }

    /** Returns {index in list, point index} of a handle near the screen point, preferring the selected annotation. */
    int[] hitHandle(float sx, float sy) {
        computeMatrix();
        List<Ann> l = annotations();
        int[] best = null;
        double bd = 24 * dp;
        for (int i = l.size() - 1; i >= 0; i--) {
            Ann a = l.get(i);
            if (!a.done || !visible(a)) continue;
            float[] q = a.p.clone();
            m.mapPoints(q);
            for (int k = 0; k + 1 < q.length; k += 2) {
                if (Float.isNaN(q[k])) continue;
                double d = Math.hypot(q[k] - sx, q[k + 1] - sy) - (a == selected ? 8 * dp : 0);
                if (d < bd) { bd = d; best = new int[]{i, k / 2}; }
            }
        }
        return best;
    }

    static double segDist(float px, float py, float ax, float ay, float bx, float by) {
        double dx = bx - ax, dy = by - ay, L = dx * dx + dy * dy;
        double t = L < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / L));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    Ann hitBody(float sx, float sy) {
        computeMatrix();
        List<Ann> l = annotations();
        float tol = 18 * dp;
        for (int i = l.size() - 1; i >= 0; i--) {
            Ann a = l.get(i);
            if (!a.done || !visible(a)) continue;
            float[] q = a.p.clone();
            m.mapPoints(q);
            switch (a.type) {
                case T_LENGTH: case T_ARROW: if (segDist(sx, sy, q[0], q[1], q[2], q[3]) < tol) return a; break;
                case T_ANGLE: if (segDist(sx, sy, q[0], q[1], q[2], q[3]) < tol || segDist(sx, sy, q[2], q[3], q[4], q[5]) < tol) return a; break;
                case T_COBB: case T_ABC: if (segDist(sx, sy, q[0], q[1], q[2], q[3]) < tol || (!Float.isNaN(q[4]) && segDist(sx, sy, q[4], q[5], q[6], q[7]) < tol)) return a; break;
                case T_PTLINE: if (segDist(sx, sy, q[0], q[1], q[2], q[3]) < tol || (!Float.isNaN(q[4]) && Math.hypot(q[4] - sx, q[5] - sy) < tol * 1.3f)) return a; break;
                case T_PROBE: if (Math.hypot(q[0] - sx, q[1] - sy) < tol * 1.3f) return a; break;
                case T_ELLIPSE: case T_RECT: {
                    float[] ip = toImage(sx, sy);
                    float pad = tol / Math.max(1e-3f, m.mapRadius(1f));
                    if (ip[0] >= Math.min(a.p[0], a.p[2]) - pad && ip[0] <= Math.max(a.p[0], a.p[2]) + pad
                            && ip[1] >= Math.min(a.p[1], a.p[3]) - pad && ip[1] <= Math.max(a.p[1], a.p[3]) + pad) return a;
                    break;
                }
            }
        }
        return null;
    }

    public void setTool(int t) {
        if (pending != null && !pending.done) { removeAnn(pending); if (createUndo && !undo.isEmpty()) undo.pop(); }
        pending = null; phase = 0; createUndo = false;
        if (!isAnnTool(t) && selected != null) { selected = null; notifySel(); }
        tool = t;
        invalidate();
    }

    // ---------- geometry ----------
    float aspect() { return img != null && img.rowSp > 0 && img.colSp > 0 ? (float) (img.rowSp / img.colSp) : 1f; }

    void computeMatrix() {
        m.reset();
        if (img == null || getWidth() == 0) { inv.reset(); return; }
        float iw = img.w, ih = img.h, a = aspect();
        m.postTranslate(-iw / 2f, -ih / 2f);
        m.postScale(flipH ? -1 : 1, (flipV ? -1 : 1) * a);
        m.postRotate(rot);
        float dw = (rot % 180 == 0) ? iw : ih * a, dh = (rot % 180 == 0) ? ih * a : iw;
        float fit = Math.min(getWidth() / dw, getHeight() / dh);
        m.postScale(fit * zoom, fit * zoom);
        m.postTranslate(getWidth() / 2f + panX, getHeight() / 2f + panY);
        m.invert(inv);
    }

    float[] toImage(float x, float y) { computeMatrix(); float[] p = {x, y}; inv.mapPoints(p); return p; }

    // ---------- touch ----------
    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { stopFling(); touching = true; if (listener != null) listener.onActivated(this); }
        if (scrubbing || (a == MotionEvent.ACTION_DOWN && inScrubZone(e.getX(), e.getY()))) {
            scrubbing = a != MotionEvent.ACTION_UP && a != MotionEvent.ACTION_CANCEL;
            if (a == MotionEvent.ACTION_DOWN) interacting = true;
            scrubTo(e.getY());
            if (!scrubbing) { touching = false; endInteraction(); }
            return true;
        }
        sgd.onTouchEvent(e);
        gd.onTouchEvent(e);
        if (a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_POINTER_UP) { hasFocus = false; }
        if (e.getPointerCount() > 1) {
            if (!multi) {
                multi = true;
                if (pending != null && !pending.done && phase == 0) { removeAnn(pending); pending = null; if (createUndo && !undo.isEmpty()) undo.pop(); createUndo = false; }
                if (editing) { editing = false; dragIdx = -1; dragBody = false; persist(); }
            }
            float fx = 0, fy = 0;
            for (int i = 0; i < e.getPointerCount(); i++) { fx += e.getX(i); fy += e.getY(i); }
            fx /= e.getPointerCount(); fy /= e.getPointerCount();
            if (a == MotionEvent.ACTION_MOVE && hasFocus) { panX += fx - lfx; panY += fy - lfy; invalidate(); }
            lfx = fx; lfy = fy; hasFocus = true;
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                multi = false; hasFocus = false; lastX = x; lastY = y; scrollAcc = 0;
                if (img != null) down(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!multi && img != null) move(x, y, x - lastX, y - lastY);
                lastX = x; lastY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (!multi && img != null) up(x, y);
                multi = false; hasFocus = false; touching = false;
                if (flingV == 0) endInteraction();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (pending != null && !pending.done && phase == 0) { removeAnn(pending); pending = null; }
                if (editing) { editing = false; dragIdx = -1; dragBody = false; persist(); }
                multi = false; touching = false;
                endInteraction();
                break;
        }
        return true;
    }

    float scrubTop() { return topInset + 56 * dp; }
    float scrubBottom() { return getHeight() - text.getTextSize() * 1.4f * 3.6f - 16 * dp; }

    boolean inScrubZone(float x, float y) {
        return scrubEnabled && count() > 2 && !isAnnTool(tool) && !hooked() && x > getWidth() - 30 * dp && y > scrubTop() && y < scrubBottom();
    }

    void scrubTo(float y) {
        float t = (y - scrubTop()) / Math.max(1, scrubBottom() - scrubTop());
        int i = Math.round(Math.max(0, Math.min(1, t)) * (count() - 1));
        if (i != index) setIndex(i);
        invalidate();
    }

    boolean hooked() { return hook != null && (tool == T_CROSS || tool == T_ORBIT || tool == T_CURVE); }

    void down(float x, float y) {
        float[] p = toImage(x, y);
        if (hooked()) { hook.onHook(this, 0, p[0], p[1], x, y); invalidate(); return; }
        boolean midCreate = pending != null && phase == 1;
        if (!midCreate && (isAnnTool(tool) && tool != T_ERASE)) {
            int[] hit = hitHandle(x, y);
            if (hit != null) {
                pushUndo();
                selected = annotations().get(hit[0]);
                dragIdx = hit[1]; dragBody = false; editing = true; lastImg = p;
                notifySel(); invalidate();
                return;
            }
            if (tool == T_SELECT) {
                Ann b = hitBody(x, y);
                if (b != null) { pushUndo(); selected = b; dragBody = true; dragIdx = -1; editing = true; lastImg = p; }
                else selected = null;
                notifySel(); invalidate();
                return;
            }
            if (selected != null) { selected = null; notifySel(); }
        }
        if (!midCreate && isDrawTool(tool)) { pushUndo(); createUndo = true; }
        switch (tool) {
            case T_LENGTH: case T_ELLIPSE: case T_RECT: case T_ARROW:
                pending = new Ann(tool, new float[]{p[0], p[1], p[0], p[1]});
                annotations().add(pending);
                break;
            case T_ANGLE:
                if (pending != null && pending.type == T_ANGLE && phase == 1) { pending.p[4] = p[0]; pending.p[5] = p[1]; }
                else { pending = new Ann(T_ANGLE, new float[]{p[0], p[1], p[0], p[1], p[0], p[1]}); phase = 0; annotations().add(pending); }
                break;
            case T_COBB: case T_ABC:
                if (pending != null && pending.type == tool && phase == 1) { pending.p[4] = pending.p[6] = p[0]; pending.p[5] = pending.p[7] = p[1]; }
                else { pending = new Ann(tool, new float[]{p[0], p[1], p[0], p[1], Float.NaN, Float.NaN, Float.NaN, Float.NaN}); phase = 0; annotations().add(pending); }
                break;
            case T_PTLINE:
                if (pending != null && pending.type == T_PTLINE && phase == 1) { pending.p[4] = p[0]; pending.p[5] = p[1]; }
                else { pending = new Ann(T_PTLINE, new float[]{p[0], p[1], p[0], p[1], Float.NaN, Float.NaN}); phase = 0; annotations().add(pending); }
                break;
            case T_PROBE:
                pending = new Ann(T_PROBE, new float[]{p[0], p[1]});
                annotations().add(pending);
                break;
            case T_CROSS:
                if (listener != null) listener.onImageTap(this, p[0], p[1]);
                break;
        }
        if (pending != null && !midCreate && pending.text.isEmpty() && !nextText.isEmpty() && pending.type != T_ARROW && pending.type != T_ABC) pending.text = nextText;
        invalidate();
    }

    void move(float x, float y, float dx, float dy) {
        float[] p = toImage(x, y);
        if (hooked()) { hook.onHook(this, 1, p[0], p[1], x, y); invalidate(); return; }
        if (editing && selected != null) {
            if (dragIdx >= 0) { selected.p[dragIdx * 2] = p[0]; selected.p[dragIdx * 2 + 1] = p[1]; }
            else if (dragBody && lastImg != null) {
                float ddx = p[0] - lastImg[0], ddy = p[1] - lastImg[1];
                for (int k = 0; k + 1 < selected.p.length; k += 2) if (!Float.isNaN(selected.p[k])) { selected.p[k] += ddx; selected.p[k + 1] += ddy; }
                lastImg = p;
            }
            invalidate();
            if (listener != null) listener.onMeasureEdited(this, selected);
            return;
        }
        switch (tool) {
            case T_WL:
                if (img.rgb && !wlOnRgb) return;
                double range = Math.max(1, img.modMax() - img.modMin());
                double s = range / Math.max(1, getWidth()) * 1.2;
                interacting = true;
                setWindow(wc - dy * s, ww + dx * s);
                if (listener != null) listener.onWindowChanged(this);
                return;
            case T_PAN: panX += dx; panY += dy; interacting = true; break;
            case T_SCROLL: {
                interacting = true;
                scrollAcc += dy;
                float step = scrollStep();
                while (Math.abs(scrollAcc) >= step) { int sg = scrollAcc > 0 ? 1 : -1; setIndex(index + sg); scrollAcc -= sg * step; }
                break;
            }
            case T_ZOOM: zoom = (float) Math.max(0.1, Math.min(60, zoom * Math.exp(-dy / (150 * dp)))); break;
            case T_LENGTH: case T_ELLIPSE: case T_RECT: case T_ARROW:
                if (pending != null) { pending.p[2] = p[0]; pending.p[3] = p[1]; }
                break;
            case T_ANGLE:
                if (pending != null) {
                    if (phase == 0) { pending.p[2] = pending.p[4] = p[0]; pending.p[3] = pending.p[5] = p[1]; }
                    else { pending.p[4] = p[0]; pending.p[5] = p[1]; }
                }
                break;
            case T_COBB: case T_ABC:
                if (pending != null) { if (phase == 0) { pending.p[2] = p[0]; pending.p[3] = p[1]; } else { pending.p[6] = p[0]; pending.p[7] = p[1]; } }
                break;
            case T_PTLINE:
                if (pending != null) { if (phase == 0) { pending.p[2] = p[0]; pending.p[3] = p[1]; } else { pending.p[4] = p[0]; pending.p[5] = p[1]; } }
                break;
            case T_PROBE: if (pending != null) { pending.p[0] = p[0]; pending.p[1] = p[1]; } break;
            case T_CROSS: if (listener != null) listener.onImageTap(this, p[0], p[1]); break;
        }
        invalidate();
    }

    boolean tiny(float[] p, int a, int b) {
        computeMatrix();
        float[] q = {p[a], p[a + 1], p[b], p[b + 1]};
        m.mapPoints(q);
        return Math.hypot(q[2] - q[0], q[3] - q[1]) < 8 * dp;
    }

    void up(float x, float y) {
        if (hooked()) { float[] p = toImage(x, y); hook.onHook(this, 2, p[0], p[1], x, y); invalidate(); return; }
        if (editing) { editing = false; dragIdx = -1; dragBody = false; persist(); notifySel(); invalidate(); if (listener != null && selected != null) listener.onMeasureEdited(this, selected); return; }
        if (tool == T_ERASE) { erase(x, y); return; }
        if (pending == null) return;
        Ann made = pending;
        switch (tool) {
            case T_LENGTH: case T_ELLIPSE: case T_RECT: case T_ARROW:
                if (tiny(pending.p, 0, 2)) removeAnn(pending);
                else { pending.done = true; if (tool == T_ARROW && listener != null) listener.onArrowCreated(this, pending); }
                pending = null;
                break;
            case T_ANGLE:
                if (phase == 0) { if (tiny(pending.p, 0, 2)) { removeAnn(pending); pending = null; } else phase = 1; }
                else { pending.done = true; pending = null; phase = 0; }
                break;
            case T_COBB: case T_ABC:
                if (phase == 0) { if (tiny(pending.p, 0, 2)) { removeAnn(pending); pending = null; } else phase = 1; }
                else { if (tiny(pending.p, 4, 6)) { pending.p[4] = Float.NaN; return; } pending.done = true; pending = null; phase = 0; }
                break;
            case T_PTLINE:
                if (phase == 0) { if (tiny(pending.p, 0, 2)) { removeAnn(pending); pending = null; } else phase = 1; }
                else { pending.done = true; pending = null; phase = 0; }
                break;
            case T_PROBE: pending.done = true; pending = null; break;
        }
        if (made.done) { createUndo = false; selected = made; persist(); notifySel(); if (listener != null) listener.onMeasureDone(this, made); }
        else if (pending == null && createUndo) { if (!undo.isEmpty()) undo.pop(); createUndo = false; }
        invalidate();
    }

    void erase(float x, float y) {
        Ann a = null;
        int[] h = hitHandle(x, y);
        if (h != null) a = annotations().get(h[0]);
        if (a == null) a = hitBody(x, y);
        if (a == null) return;
        pushUndo();
        annotations().remove(a);
        if (a == selected) { selected = null; notifySel(); }
        persist();
        invalidate();
    }

    // ---------- measurement math ----------
    double sx() { return img != null && img.colSp > 0 ? img.colSp : 0; }
    double sy() { return img != null && img.rowSp > 0 ? img.rowSp : 0; }
    boolean calibrated() { return sx() > 0 && sy() > 0; }

    /** Foot of the perpendicular from (px, py) to the line through (ax, ay)-(bx, by). */
    static float[] foot(float ax, float ay, float bx, float by, float px, float py) {
        float ux = bx - ax, uy = by - ay, l2 = ux * ux + uy * uy;
        float t = l2 < 1e-9f ? 0 : ((px - ax) * ux + (py - ay) * uy) / l2;
        return new float[]{ax + t * ux, ay + t * uy};
    }

    /** Perpendicular distance from point p[4..5] to the line p[0..3], in the image's units. */
    String ptLineText(float[] p) {
        float[] f = foot(p[0], p[1], p[2], p[3], p[4], p[5]);
        return fmtLen(p[4], p[5], f[0], f[1]);
    }

    /** Length in millimetres, or NaN if the image has no pixel spacing. */
    double lenMm(float x1, float y1, float x2, float y2) {
        return calibrated() ? Math.hypot((x2 - x1) * sx(), (y2 - y1) * sy()) : Double.NaN;
    }

    /** "ABC/2 = 24.1 mL" once the number of slices (C) has been entered; stored in the annotation text as "C=<cm>". */
    String abcVolume(Ann a) {
        if (a.type != T_ABC || Float.isNaN(a.p[4]) || !a.text.startsWith("C=")) return "";
        try {
            double c = Double.parseDouble(a.text.substring(2));
            double A = lenMm(a.p[0], a.p[1], a.p[2], a.p[3]) / 10, B = lenMm(a.p[4], a.p[5], a.p[6], a.p[7]) / 10;
            if (Double.isNaN(A) || Double.isNaN(B)) return "";
            return String.format("ABC/2 \u2248 %.1f mL (C %.1f cm)", A * B * c / 2, c);
        } catch (Exception e) { return ""; }
    }

    String fmtLen(float x1, float y1, float x2, float y2) {
        if (calibrated()) {
            double d = Math.hypot((x2 - x1) * sx(), (y2 - y1) * sy());
            return d >= 100 ? String.format("%.1f cm", d / 10) : String.format("%.1f mm", d);
        }
        return String.format("%.1f px", Math.hypot(x2 - x1, y2 - y1));
    }

    double angleAt(float ax, float ay, float vx, float vy, float bx, float by) {
        double kx = calibrated() ? sx() : 1, ky = calibrated() ? sy() : 1;
        double ux = (ax - vx) * kx, uy = (ay - vy) * ky, wx = (bx - vx) * kx, wy = (by - vy) * ky;
        double c = (ux * wx + uy * wy) / (Math.hypot(ux, uy) * Math.hypot(wx, wy));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }

    double cobb(float[] p) {
        double kx = calibrated() ? sx() : 1, ky = calibrated() ? sy() : 1;
        double a1 = Math.atan2((p[3] - p[1]) * ky, (p[2] - p[0]) * kx), a2 = Math.atan2((p[7] - p[5]) * ky, (p[6] - p[4]) * kx);
        double d = Math.abs(Math.toDegrees(a1 - a2)) % 180;
        return d > 90 ? 180 - d : d;
    }

    String valueAt(float fx, float fy) {
        int x = (int) Math.floor(fx), y = (int) Math.floor(fy);
        if (img == null || x < 0 || y < 0 || x >= img.w || y >= img.h) return "outside image";
        if (img.rgb) { int p = img.pix[y * img.w + x]; return "RGB " + ((p >> 16) & 255) + "," + ((p >> 8) & 255) + "," + (p & 255); }
        double v = img.value(x, y);
        return fmtVal(v) + (img.units.isEmpty() ? "" : " " + img.units);
    }

    static String fmtVal(double v) { return Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf((long) Math.rint(v)) : String.format("%.2f", v); }

    List<String> roiStats(Ann a) {
        List<String> out = new ArrayList<>();
        float x0 = Math.min(a.p[0], a.p[2]), x1 = Math.max(a.p[0], a.p[2]), y0 = Math.min(a.p[1], a.p[3]), y1 = Math.max(a.p[1], a.p[3]);
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = (x1 - x0) / 2, ry = (y1 - y0) / 2;
        double area;
        if (a.type == T_ELLIPSE) area = Math.PI * rx * ry; else area = (x1 - x0) * (y1 - y0);
        if (calibrated()) { double mm2 = area * sx() * sy(); out.add(mm2 >= 100 ? String.format("Area %.2f cm²", mm2 / 100) : String.format("Area %.1f mm²", mm2)); }
        else out.add(String.format("Area %.0f px²", area));
        if (img == null || img.rgb) return out;
        int ix0 = Math.max(0, (int) Math.floor(x0)), ix1 = Math.min(img.w - 1, (int) Math.ceil(x1)),
                iy0 = Math.max(0, (int) Math.floor(y0)), iy1 = Math.min(img.h - 1, (int) Math.ceil(y1));
        double sum = 0, sum2 = 0, mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        long n = 0;
        for (int y = iy0; y <= iy1; y++) for (int x = ix0; x <= ix1; x++) {
            double px = x + 0.5, py = y + 0.5;
            if (px < x0 || px > x1 || py < y0 || py > y1) continue;
            if (a.type == T_ELLIPSE && rx > 0 && ry > 0) { double dx = (px - cx) / rx, dy = (py - cy) / ry; if (dx * dx + dy * dy > 1) continue; }
            double v = img.value(x, y);
            sum += v; sum2 += v * v; n++;
            if (v < mn) mn = v; if (v > mx) mx = v;
        }
        if (n > 0) {
            double mean = sum / n, sd = Math.sqrt(Math.max(0, sum2 / n - mean * mean));
            String u = img.units.isEmpty() ? "" : " " + img.units;
            out.add(String.format("Mean %.1f%s  SD %.1f", mean, u, sd));
            out.add("Min " + fmtVal(mn) + "  Max " + fmtVal(mx) + "  n=" + n);
        }
        return out;
    }

    // ---------- drawing ----------
    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        computeMatrix();
        int W = getWidth(), H = getHeight();
        if (dirty) doRender();
        Bitmap shown = previewStep > 1 && preview != null ? preview : bmp;
        if (shown != null) {
            c.save();
            c.concat(m);
            if (shown == preview) c.scale(img.w / (float) preview.getWidth(), img.h / (float) preview.getHeight());
            bmpPaint.setFilterBitmap(zoom < 3 || shown == preview);
            c.drawBitmap(shown, 0, 0, bmpPaint);
            c.restore();
        }
        if (img != null && !Float.isNaN(crossX)) {
            float[] q = {crossX, 0, crossX, img.h, 0, crossY, img.w, crossY};
            m.mapPoints(q);
            c.drawLine(q[0], q[1], q[2], q[3], cross);
            c.drawLine(q[4], q[5], q[6], q[7], cross);
        }
        if (img != null && crossVisible && !crossLines.isEmpty()) drawCross(c);
        if (img != null && curve != null && curve.length >= 2) {
            float[] q = curve.clone();
            m.mapPoints(q);
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setColor(0xFF4DD0E1);
            cp.setStrokeWidth(2 * dp);
            cp.setStyle(Paint.Style.STROKE);
            for (int i = 0; i + 3 < q.length; i += 2) c.drawLine(q[i], q[i + 1], q[i + 2], q[i + 3], cp);
            cp.setStyle(Paint.Style.FILL);
            for (int i = 0; i + 1 < q.length; i += 2) c.drawCircle(q[i], q[i + 1], 4 * dp, cp);
        }
        if (img != null && !refLines.isEmpty()) {
            for (float[] l : refLines) {
                float[] q = l.clone();
                m.mapPoints(q);
                c.drawLine(q[0], q[1], q[2], q[3], refPaint);
            }
        }
        if (img != null) for (Ann a : annotations()) {
            if (a.type == T_ARROW ? !showAnnotations : !showMeasures) continue;
            drawAnn(c, a);
        }

        float pad = 14 * dp, lh = text.getTextSize() * 1.4f;
        if (prov == null || (img == null && error == null)) {
            drawCentered(c, prov == null ? emptyText : "Loading…", W / 2f, H / 2f);
        } else if (error != null) {
            drawCentered(c, error, W / 2f, H / 2f);
        }
        if (overlay && prov != null) {
            if (label != null) { int oc = text.getColor(); text.setColor(accent); c.drawText(label, pad, topInset + pad + lh, text); text.setColor(oc); }
            float by = H - pad;
            if (img != null && !img.rgb) {
                segs(c, pad, by, false, "WW: ", fmtVal(Math.round(ww)));
                segs(c, pad, by - lh, false, "WL: ", fmtVal(Math.round(wc)));
            }
            segs(c, W - pad, by, true, "IM: ", String.valueOf(index + 1), "/" + prov.count());
            segs(c, W - pad, by - lh, true, "SE: ", String.valueOf(prov.seriesNumber()));
            String si = prov.count() > 0 ? prov.sliceInfo(index) : "";
            text.setAlpha(170);
            if (!si.isEmpty()) c.drawText(si, pad, by - 2 * lh, text);
            if (Math.abs(zoom - 1) > 0.01f || invert || rot != 0) {
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText((Math.round(zoom * 100) + "%") + (invert ? "  Inv" : "") + (rot != 0 ? "  " + rot + "°" : ""), W - pad, by - 2 * lh, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
            text.setAlpha(255);
            if (img != null) drawOrientation(c);
            Set<Integer> keys = prov.keyImages();
            if (keys != null && keys.contains(index)) {
                orientP.setTextAlign(Paint.Align.CENTER);
                c.drawText("★ Key image", W / 2f, topInset + pad + lh, orientP);
                orientP.setTextAlign(Paint.Align.LEFT);
            }
        }
        if (overlay && !exporting && prov != null && count() > 2 && scrubEnabled && !isAnnTool(tool) && !hooked()) {
            float x = getWidth() - 10 * dp, t0 = scrubTop(), t1 = scrubBottom();
            long since = android.os.SystemClock.uptimeMillis() - lastScrollMs;
            boolean hot = scrubbing || since < 900;
            scrubTrack.setAlpha(hot ? 110 : 45);
            c.drawLine(x, t0, x, t1, scrubTrack);
            float ty = t0 + (t1 - t0) * index / (float) (count() - 1);
            scrubThumb.setAlpha(hot ? 255 : 120);
            c.drawLine(x, ty - 10 * dp, x, ty + 10 * dp, scrubThumb);
            if (hot && !scrubbing) postInvalidateDelayed(950);
        }
        border.setColor(accent);
        if (showBorder && active && !exporting) c.drawRoundRect(new RectF(1.5f * dp, 1.5f * dp, W - 1.5f * dp, H - 1.5f * dp), 6 * dp, 6 * dp, border);
    }

    public float[] toScreen(float ix, float iy) { computeMatrix(); float[] q = {ix, iy}; m.mapPoints(q); return q; }
    public float density() { return dp; }

    /** Screen position of the crosshair center, or null. */
    public float[] crossCenterScreen() {
        if (crossLines.isEmpty() || img == null) return null;
        computeMatrix();
        float[] q = {crossLines.get(0).x, crossLines.get(0).y};
        m.mapPoints(q);
        return q;
    }

    void drawCross(Canvas c) {
        float big = (img.w + img.h) * 2f;
        float gap = 16 * dp, grip = Math.min(getWidth(), getHeight()) * 0.36f;
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lp.setStyle(Paint.Style.STROKE);
        lp.setStrokeWidth(1.4f * dp);
        for (CrossLine l : crossLines) {
            float[] q = {l.x, l.y, l.x + l.dx, l.y + l.dy};
            m.mapPoints(q);
            float sx = q[2] - q[0], sy = q[3] - q[1];
            float len = (float) Math.hypot(sx, sy);
            if (len < 1e-4) continue;
            sx /= len; sy /= len;
            lp.setColor(l.color);
            lp.setPathEffect(null);
            lp.setAlpha(230);
            c.drawLine(q[0] + sx * gap, q[1] + sy * gap, q[0] + sx * big, q[1] + sy * big, lp);
            c.drawLine(q[0] - sx * gap, q[1] - sy * gap, q[0] - sx * big, q[1] - sy * big, lp);
            // rotation grips
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setColor(l.color);
            c.drawCircle(q[0] + sx * grip, q[1] + sy * grip, 5 * dp, gp);
            c.drawCircle(q[0] - sx * grip, q[1] - sy * grip, 5 * dp, gp);
            if (l.slabHalf > 0.5f) {
                float[] off = {l.x - l.dy * l.slabHalf, l.y + l.dx * l.slabHalf, l.x + l.dy * l.slabHalf, l.y - l.dx * l.slabHalf};
                m.mapPoints(off);
                lp.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * dp, 6 * dp}, 0));
                lp.setAlpha(150);
                for (int k = 0; k < 4; k += 2) c.drawLine(off[k] - sx * big, off[k + 1] - sy * big, off[k] + sx * big, off[k + 1] + sy * big, lp);
            }
        }
        Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
        cp.setStyle(Paint.Style.STROKE);
        cp.setStrokeWidth(1.4f * dp);
        cp.setColor(0xCCFFFFFF);
        float[] ctr = {crossLines.get(0).x, crossLines.get(0).y};
        m.mapPoints(ctr);
        c.drawCircle(ctr[0], ctr[1], gap * 0.6f, cp);
    }

    /** Draws "Label: value[/suffix]" with the value in blue, left- or right-aligned at x. */
    void segs(Canvas c, float x, float y, boolean right, String label, String value, String... suffix) {
        String suf = suffix.length > 0 ? suffix[0] : "";
        float w = text.measureText(label) + valueP.measureText(value) + text.measureText(suf);
        float x0 = right ? x - w : x;
        c.drawText(label, x0, y, text);
        x0 += text.measureText(label);
        c.drawText(value, x0, y, valueP);
        x0 += valueP.measureText(value);
        if (!suf.isEmpty()) c.drawText(suf, x0, y, text);
    }

    void drawCentered(Canvas c, String s, float x, float y) {
        text.setTextAlign(Paint.Align.CENTER);
        float maxW = getWidth() - 24 * dp;
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            if (text.measureText(cur + " " + w) > maxW && cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(w); }
            else { if (cur.length() > 0) cur.append(' '); cur.append(w); }
        }
        lines.add(cur.toString());
        float lh = text.getTextSize() * 1.3f;
        float y0 = y - (lines.size() - 1) * lh / 2;
        for (String l : lines) { c.drawText(l, x, y0, text); y0 += lh; }
        text.setTextAlign(Paint.Align.LEFT);
    }

    void label(Canvas c, List<String> lines, float x, float y) {
        float lh = text.getTextSize() * 1.25f, w = 0;
        for (String s : lines) w = Math.max(w, text.measureText(s));
        x = Math.max(4 * dp, Math.min(getWidth() - w - 8 * dp, x));
        // Keep labels out of the header and the corner text (WL/WW, SE/IM) at the bottom.
        float top = topInset + lh + 8 * dp, bottom = getHeight() - text.getTextSize() * 1.4f * 3.4f - (lines.size() - 1) * lh - 18 * dp;
        y = Math.max(top, Math.min(Math.max(top, bottom), y));
        c.drawRect(x - 3 * dp, y - lh + 2 * dp, x + w + 3 * dp, y + (lines.size() - 1) * lh + 5 * dp, box);
        for (String s : lines) { c.drawText(s, x, y, text); y += lh; }
    }

    void label(Canvas c, String s, float x, float y) { List<String> l = new ArrayList<>(); l.add(s); label(c, l, x, y); }

    void drawAnn(Canvas c, Ann a) {
        float[] q = a.p.clone();
        m.mapPoints(q);
        float o = 10 * dp;
        Paint L = a == selected ? selLine : line;
        switch (a.type) {
            case T_LENGTH:
                c.drawLine(q[0], q[1], q[2], q[3], L);
                c.drawCircle(q[0], q[1], 2.5f * dp, L); c.drawCircle(q[2], q[3], 2.5f * dp, L);
                label(c, (a.text.isEmpty() ? "" : a.text + " ") + fmtLen(a.p[0], a.p[1], a.p[2], a.p[3]), q[2] + o, q[3] + o);
                break;
            case T_PTLINE: {
                // Reference line drawn across, then the perpendicular from the tapped point to it.
                float ux = q[2] - q[0], uy = q[3] - q[1];
                float len = (float) Math.hypot(ux, uy);
                if (len > 1e-3f) {
                    float ex = ux / len * 40 * dp, ey = uy / len * 40 * dp;
                    c.drawLine(q[0] - ex, q[1] - ey, q[2] + ex, q[3] + ey, L);
                }
                c.drawCircle(q[0], q[1], 2.5f * dp, L); c.drawCircle(q[2], q[3], 2.5f * dp, L);
                if (!Float.isNaN(q[4])) {
                    float[] f = foot(q[0], q[1], q[2], q[3], q[4], q[5]);
                    Paint d = new Paint(L);
                    d.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * dp, 4 * dp}, 0));
                    c.drawLine(q[4], q[5], f[0], f[1], d);
                    c.drawCircle(q[4], q[5], 3.5f * dp, L);
                    label(c, (a.text.isEmpty() ? "" : a.text + " ") + ptLineText(a.p), q[4] + o, q[5] + o);
                } else if (!a.done) label(c, a.text.isEmpty() ? "Now tap the point" : a.text + ": now tap the point", q[2] + o, q[3] + o);
                break;
            }
            case T_ABC:
                c.drawLine(q[0], q[1], q[2], q[3], L);
                label(c, "A " + fmtLen(a.p[0], a.p[1], a.p[2], a.p[3]), q[2] + o, q[3] + o);
                if (!Float.isNaN(q[4])) {
                    c.drawLine(q[4], q[5], q[6], q[7], L);
                    String v = abcVolume(a);
                    List<String> lines = new ArrayList<>();
                    lines.add("B " + fmtLen(a.p[4], a.p[5], a.p[6], a.p[7]));
                    if (!v.isEmpty()) lines.add(v);
                    label(c, lines, q[6] + o, q[7] + o);
                } else if (!a.done) label(c, "Now draw B at right angles", (q[0] + q[2]) / 2 + o, (q[1] + q[3]) / 2 + o);
                break;
            case T_ARROW: {
                c.drawLine(q[0], q[1], q[2], q[3], L);
                double ang = Math.atan2(q[1] - q[3], q[0] - q[2]);
                float hl = 12 * dp;
                Path p = new Path();
                p.moveTo(q[0], q[1]);
                p.lineTo((float) (q[0] - hl * Math.cos(ang - 0.45)), (float) (q[1] - hl * Math.sin(ang - 0.45)));
                p.moveTo(q[0], q[1]);
                p.lineTo((float) (q[0] - hl * Math.cos(ang + 0.45)), (float) (q[1] - hl * Math.sin(ang + 0.45)));
                c.drawPath(p, L);
                if (!a.text.isEmpty()) label(c, a.text, q[2] + 4 * dp, q[3] + o);
                break;
            }
            case T_ANGLE:
                c.drawLine(q[0], q[1], q[2], q[3], L);
                if (phase == 1 || a.done) {
                    c.drawLine(q[2], q[3], q[4], q[5], L);
                    if (a.p[4] != a.p[2] || a.p[5] != a.p[3]) label(c, String.format("%.1f°", angleAt(a.p[0], a.p[1], a.p[2], a.p[3], a.p[4], a.p[5])), q[2] + o, q[3] + o);
                }
                break;
            case T_COBB:
                c.drawLine(q[0], q[1], q[2], q[3], L);
                if (!Float.isNaN(q[4])) {
                    c.drawLine(q[4], q[5], q[6], q[7], L);
                    if (a.p[4] != a.p[6] || a.p[5] != a.p[7]) label(c, String.format("Cobb %.1f°", cobb(a.p)), (q[4] + q[6]) / 2 + o, (q[5] + q[7]) / 2 + o);
                } else if (!a.done) label(c, "Now draw the second line", (q[0] + q[2]) / 2 + o, (q[1] + q[3]) / 2 + o);
                break;
            case T_ELLIPSE: case T_RECT: {
                c.save();
                c.concat(m);
                float sc = m.mapRadius(1f);
                Paint lp = new Paint(L);
                lp.setStrokeWidth(L.getStrokeWidth() / Math.max(1e-3f, sc));
                RectF r = new RectF(Math.min(a.p[0], a.p[2]), Math.min(a.p[1], a.p[3]), Math.max(a.p[0], a.p[2]), Math.max(a.p[1], a.p[3]));
                if (a.type == T_ELLIPSE) c.drawOval(r, lp); else c.drawRect(r, lp);
                c.restore();
                label(c, roiStats(a), Math.max(q[0], q[2]) + 4 * dp, Math.max(q[1], q[3]) + o);
                break;
            }
            case T_PROBE:
                c.drawLine(q[0] - 6 * dp, q[1], q[0] + 6 * dp, q[1], L);
                c.drawLine(q[0], q[1] - 6 * dp, q[0], q[1] + 6 * dp, L);
                label(c, valueAt(a.p[0], a.p[1]), q[0] + o, q[1] - o);
                break;
        }
        if (a == selected) {
            for (int k = 0; k + 1 < q.length; k += 2) {
                if (Float.isNaN(q[k])) continue;
                c.drawCircle(q[k], q[k + 1], 7 * dp, handleFill);
                c.drawCircle(q[k], q[k + 1], 7 * dp, selLine);
            }
        }
    }

    void drawOrientation(Canvas c) {
        double[] o = prov.orientation(index);
        if (o == null) return;
        float[] v = {1, 0, 0, 1};
        inv.mapVectors(v);
        double kx = sx() > 0 ? sx() : 1, ky = sy() > 0 ? sy() : 1;
        String right = letters(v[0] * kx, v[1] * ky, o), bottom = letters(v[2] * kx, v[3] * ky, o);
        String left = letters(-v[0] * kx, -v[1] * ky, o), top = letters(-v[2] * kx, -v[3] * ky, o);
        int W = getWidth(), H = getHeight();
        float th = orientP.getTextSize();
        orientP.setTextAlign(Paint.Align.CENTER);
        c.drawText(top, W / 2f, topInset + th * 1.6f, orientP);
        c.drawText(bottom, W / 2f, H - 14 * dp - text.getTextSize() * 3.2f, orientP);
        orientP.setTextAlign(Paint.Align.LEFT);
        c.drawText(left, 6 * dp, H / 2f, orientP);
        orientP.setTextAlign(Paint.Align.RIGHT);
        c.drawText(right, W - 6 * dp, H / 2f, orientP);
        orientP.setTextAlign(Paint.Align.LEFT);
    }

    static String letters(double ix, double iy, double[] o) {
        double x = ix * o[0] + iy * o[3], y = ix * o[1] + iy * o[4], z = ix * o[2] + iy * o[5];
        double n = Math.sqrt(x * x + y * y + z * z);
        if (n == 0) return "";
        x /= n; y /= n; z /= n;
        double[] a = {Math.abs(x), Math.abs(y), Math.abs(z)};
        String[] l = {x > 0 ? "L" : "R", y > 0 ? "P" : "A", z > 0 ? "H" : "F"};
        int i0 = 0, i1 = 1, i2 = 2;
        if (a[i1] > a[i0]) { int t = i0; i0 = i1; i1 = t; }
        if (a[i2] > a[i0]) { int t = i0; i0 = i2; i2 = t; }
        if (a[i2] > a[i1]) { int t = i1; i1 = i2; i2 = t; }
        StringBuilder sb = new StringBuilder(l[i0]);
        if (a[i1] > 0.25) sb.append(l[i1]);
        return sb.toString();
    }

    /** Renders what the user sees (optionally without patient identifiers) at export resolution. */
    public Bitmap snapshot(boolean noPhi) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        float s = Math.max(1f, 1600f / Math.max(w, h));
        Bitmap b = Bitmap.createBitmap((int) (w * s), (int) (h * s), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(s, s);
        boolean old = hidePhi;
        hidePhi = old || noPhi;
        exporting = true;
        draw(c);
        exporting = false;
        hidePhi = old;
        return b;
    }
}
