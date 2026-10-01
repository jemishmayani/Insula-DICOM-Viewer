/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 3D volume rendering with automatic tissue separation and manual post-processing. Renders on the GPU
 * (OpenGL ES 3.0) when available, otherwise on the CPU in a limited mode. Sessions are saved into the study.
 */
public class VrtActivity extends BaseActivity implements GlVrt.Host {
    static final String EXTRA_SERIES = "series", EXTRA_PRESET = "preset";
    View cutBar;
    boolean cutPending;
    static final int NAV = 0, CUT = 1, PICK = 2;
    static final String[] TIER_HELP = {
            "High quality: the volume is kept at up to 320 voxels on its longest side.",
            "Standard quality: the volume is reduced to about 256 voxels on its longest side.",
            "Low quality for this phone's memory: the volume is reduced to about 192 voxels, so fine detail such as small vessels is softer.",
            "Basic mode: this phone has no OpenGL ES 3.0 graphics, so the image is drawn by the processor at reduced resolution. Rotation is slower and renders sharpen after you let go."};

    final Handler h = new Handler(Looper.getMainLooper());
    final ExecutorService worker = Executors.newSingleThreadExecutor(Library.daemon("insula-vrt"));
    Library.Series series;
    Vol3D vol;
    VrtState st = new VrtState();
    int tier, autoTier;
    boolean gpu, gpuCapable, busyNow;
    GlVrt gl;
    CpuStage cpu;
    Overlay overlay;
    FrameLayout stage, root;
    TextView busy, badge;
    Ui.Sheet sheet;
    LinearLayout quick;
    final Map<String, LinearLayout> quickItems = new HashMap<>();
    final ArrayDeque<Seg.Edit> undo = new ArrayDeque<>();
    long undoBytes;
    int tool = NAV;
    boolean spinning;
    int captureCount;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String uid = getIntent().getStringExtra(EXTRA_SERIES);
        synchronized (Library.class) { series = uid == null ? null : Library.seriesMap.get(uid); }
        if (series == null) { Ui.toast(this, "Series not found."); finish(); return; }
        detectDevice();

        root = new FrameLayout(this);
        LinearLayout main = Ui.col(this);
        main.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        LinearLayout tc = Ui.col(this);
        tc.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
        TextView t = Ui.title(this, "3D VRT");
        tc.addView(t);
        TextView sub = Ui.text(this, series.label(), 12.5f, Ui.SUB);
        sub.setSingleLine(true);
        tc.addView(sub);
        top.addView(tc, Ui.wrapWeight(1));
        top.addView(Ui.icon(this, "camera", "Capture", new View.OnClickListener() { public void onClick(View v) { captureMenu(); } }));
        top.addView(Ui.icon(this, "save", "Sessions", new View.OnClickListener() { public void onClick(View v) { sessions(); } }));
        top.addView(Ui.icon(this, "info", "Help", new View.OnClickListener() { public void onClick(View v) { help(); } }));
        main.addView(top);

        stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF000000);
        makeStage();
        overlay = new Overlay(this);
        stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        badge = Ui.text(this, "", 11.5f, 0xCCFFFFFF);
        badge.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        badge.setBackground(Ui.rounded(0x88000000, Ui.dp(this, 10)));
        badge.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { help(); } });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bl.setMargins(Ui.dp(this, 10), Ui.dp(this, 10), 0, 0);
        stage.addView(badge, bl);
        busy = Ui.text(this, "", 15, Ui.TEXT);
        busy.setGravity(Gravity.CENTER);
        busy.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16));
        busy.setBackground(Ui.rounded(0xDD202022, Ui.dp(this, 14)));
        stage.addView(busy, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        main.addView(stage, Ui.vweight(1));
        main.addView(Ui.hscroll(this, buildQuick()));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        sheet = new Ui.Sheet(this, root, true, Math.min(Ui.dp(this, 380), (int) (getResources().getDisplayMetrics().widthPixels * 0.88)));
        setContentView(root);
        build(false);
    }

    // ---------------- device capability ----------------
    void detectDevice() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo ci = am.getDeviceConfigurationInfo();
        gpuCapable = ci != null && ci.reqGlEsVersion >= 0x30000;
        int mem = am.getLargeMemoryClass(), cores = Runtime.getRuntime().availableProcessors();
        boolean lowRam = am.isLowRamDevice();
        autoTier = !gpuCapable ? 3 : (lowRam || mem < 256) ? 2 : (mem >= 512 && cores >= 6) ? 0 : 1;
        int chosen = Ui.prefs(this).getInt("vrt_tier", -1);
        tier = chosen >= 0 && (chosen < 3 ? gpuCapable : true) ? chosen : autoTier;
        gpu = gpuCapable && tier < 3;
    }

    void makeStage() {
        if (gl != null) { stage.removeView(gl); gl = null; }
        if (cpu != null) { stage.removeView(cpu); cpu = null; }
        View v;
        if (gpu) {
            gl = new GlVrt(this, this);
            float[][] scales = {{0.75f, 0.40f}, {0.60f, 0.33f}, {0.45f, 0.25f}};
            gl.restScale = scales[tier][0];
            gl.dragScale = scales[tier][1];
            v = gl;
        } else {
            cpu = new CpuStage(this);
            v = cpu;
        }
        stage.addView(v, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    void updateBadge() {
        if (vol == null) { badge.setVisibility(View.GONE); return; }
        badge.setVisibility(View.VISIBLE);
        String s = Vol3D.TIER_NAMES[tier] + " · " + (gpu ? "GPU" : "CPU") + " · " + vol.nx + "×" + vol.ny + "×" + vol.nz;
        if (tier >= 2) s += "  ⚠ limited";
        badge.setText(s);
    }

    // ---------------- building the volume ----------------
    /** Builds the volume and separates tissues in the background. keepState keeps the current view settings. */
    void build(final boolean keepState) {
        busyNow = true;
        showBusy("Reading the series…");
        worker.submit(new Runnable() {
            public void run() {
                String err = null;
                Vol3D v = null;
                Seg.Params p = null;
                try {
                    Volume full = Volume.build(series, new Library.Progress() {
                        public void update(int done, int total, String msg) { showBusy("Reading the series… " + (total > 0 ? done * 100 / total : 0) + "%"); }
                    });
                    showBusy("Preparing the 3D volume…");
                    v = Vol3D.from(full, tier);
                    full = null;
                    p = Seg.estimate(v);
                    Seg.segment(v, p, new Seg.Progress() { public void update(String m, int pct) { showBusy(m + "… " + pct + "%"); } });
                } catch (OutOfMemoryError oom) {
                    err = "Not enough memory for this quality. Choose a lower quality in Display.";
                } catch (Throwable t) { err = Ui.friendly(t); }
                final String fe = err;
                final Vol3D fv = v;
                final Seg.Params fp = p;
                h.post(new Runnable() {
                    public void run() {
                        busyNow = false;
                        if (fe != null) {
                            showBusy(null);
                            if (tier < 2 && fe.contains("memory")) { tier++; Ui.toast(VrtActivity.this, "Retrying at " + Vol3D.TIER_NAMES[tier] + " quality."); build(keepState); return; }
                            new AlertDialog.Builder(VrtActivity.this).setTitle("3D VRT isn't possible for this series").setMessage(fe)
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { finish(); } }).setCancelable(false).show();
                            return;
                        }
                        vol = fv;
                        undo.clear(); undoBytes = 0;
                        if (!keepState) {
                            st = new VrtState();
                            st.defaults(vol.ct, fp.vessel);
                            st.preset(vol.ct ? 0 : 3, vol.ct);
                            String want = getIntent().getStringExtra(EXTRA_PRESET);
                            if ("bone".equals(want)) st.preset(2, vol.ct);
                            else if ("vessels".equals(want)) st.preset(1, vol.ct);
                            else if ("all".equals(want)) st.preset(3, vol.ct);
                            st.winC = vol.ct ? 300 : vol.defWc;
                            st.winW = vol.ct ? 900 : vol.defWw;
                        }
                        st.params = fp;
                        showBusy(null);
                        attach();
                        updateBadge();
                        updateQuick();
                        if (!keepState) afterFirstBuild();
                    }
                });
            }
        });
    }

    void afterFirstBuild() {
        final List<VrtStore.Saved> saved = VrtStore.states(series);
        if (!Ui.prefs(this).getBoolean("vrt_help_v1", false)) { help(); Ui.prefs(this).edit().putBoolean("vrt_help_v1", true).apply(); }
        else if (tier >= 2) Ui.toast(this, TIER_HELP[tier]);
        if (!saved.isEmpty()) {
            String[] items = new String[saved.size() + 1];
            items[0] = "Start fresh (automatic separation)";
            for (int i = 0; i < saved.size(); i++) items[i + 1] = saved.get(i).name + "  ·  " + fmt(saved.get(i).created);
            new AlertDialog.Builder(this).setTitle("Continue a saved session?").setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { if (w > 0) openState(saved.get(w - 1)); else if (wantsHeart()) isolateHeart(true); }
            }).show();
        } else if (wantsHeart()) isolateHeart(true);
        else if (st.params.summary.length() > 0) Ui.toast(this, st.params.summary);
    }

    /** Cardiac CT angiography: opened for coronary 3D, or recognized from the study description, with contrast. */
    boolean wantsHeart() {
        if (vol == null || !vol.ct || !st.params.contrast) return false;
        String want = getIntent().getStringExtra(EXTRA_PRESET);
        if ("coronary".equals(want)) return true;
        if (want != null) return false;
        return StudyType.CARDIAC_CTA.equals(StudyType.detect(series.modality, series.study.desc, series.desc, "", "", ""));
    }

    /** Hides everything except the heart and great vessels, as one undoable step. */
    void isolateHeart(final boolean auto) {
        if (vol == null) return;
        busyNow = true;
        showBusy("Isolating the heart…");
        worker.submit(new Runnable() {
            public void run() {
                final Seg.IntList rm = Seg.isolateHeart(vol, new Seg.Progress() { public void update(String m, int pct) { showBusy(m + "… " + pct + "%"); } });
                h.post(new Runnable() {
                    public void run() {
                        busyNow = false;
                        showBusy(null);
                        if (rm.size == 0) { Ui.toast(VrtActivity.this, "No contrast-filled heart was found, so nothing was hidden."); return; }
                        push(Seg.set(vol, "Isolate heart", rm, true, 0));
                        st.preset(0, true);
                        tfChanged();
                        Ui.toast(VrtActivity.this, auto ? "Cardiac CT recognized: the heart is isolated. Tap Undo to see the whole chest."
                                : "Heart isolated. Tap Undo to bring the rest back.");
                    }
                });
            }
        });
    }

    void attach() {
        if (gl != null) gl.setScene(vol, st);
        if (cpu != null) cpu.request(false);
        overlay.invalidate();
    }

    void showBusy(final String msg) {
        h.post(new Runnable() {
            public void run() {
                busy.setVisibility(msg == null ? View.GONE : View.VISIBLE);
                if (msg != null) busy.setText(msg);
            }
        });
    }

    // ---------------- rendering ----------------
    void redraw(boolean dragging) {
        if (vol == null) return;
        if (gl != null) gl.redraw(dragging);
        if (cpu != null) cpu.request(dragging);
        overlay.invalidate();
    }

    void labelsChanged() {
        if (gl != null) gl.labelsChanged();
        if (cpu != null) cpu.request(false);
    }

    void tfChanged() {
        if (gl != null) gl.tfChanged();
        if (cpu != null) cpu.request(false);
    }

    public void onGpuFailure(String why) {
        if (!gpu) return;
        if (tier < 2) {
            tier++;
            Ui.toast(this, "The GPU couldn't hold this volume (" + why + "). Switching to " + Vol3D.TIER_NAMES[tier] + " quality.");
            makeStage();
            build(true);
        } else {
            gpu = false;
            tier = 3;
            Ui.toast(this, "The GPU can't render this volume (" + why + "). Using basic CPU mode.");
            makeStage();
            build(true);
        }
    }

    @Override protected void onPause() { super.onPause(); if (gl != null) gl.onPause(); stopSpin(); }
    @Override protected void onResume() { super.onResume(); if (gl != null) gl.onResume(); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); }

    /** Renders with CpuVrt on a background thread; used when the phone has no OpenGL ES 3.0. */
    final class CpuStage extends View {
        final ExecutorService ex = Executors.newSingleThreadExecutor(Library.daemon("insula-vrt-cpu"));
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        volatile int token;
        Bitmap shown;

        CpuStage(Context c) { super(c); }

        void request(final boolean dragging) {
            final Vol3D v = vol;
            if (v == null || getWidth() == 0) { postInvalidate(); return; }
            final int tok = ++token;
            final double f = dragging ? 0.22 : 0.5;
            final int w = Math.max(48, Math.min(dragging ? 220 : 420, (int) (getWidth() * f))), hh = Math.max(48, (int) Math.round(w * getHeight() / (double) getWidth()));
            final VrtState s = VrtState.fromJson(safeJson(st));
            ex.submit(new Runnable() {
                public void run() {
                    if (tok != token) return;
                    int[] px = new CpuVrt(v, s).render(w, hh, dragging ? 2.0 : 1.0);
                    if (tok != token) return;
                    final Bitmap b = Bitmap.createBitmap(px, w, hh, Bitmap.Config.ARGB_8888);
                    post(new Runnable() { public void run() { shown = b; invalidate(); } });
                }
            });
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) { request(false); }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xFF000000);
            if (shown != null) c.drawBitmap(shown, null, new Rect(0, 0, getWidth(), getHeight()), p);
        }
    }

    static org.json.JSONObject safeJson(VrtState s) {
        try { return s.json(); } catch (Exception e) { return new org.json.JSONObject(); }
    }

    // ---------------- gestures and overlay ----------------
    final class Overlay extends View {
        final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG), path = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ScaleGestureDetector scale;
        final GestureDetector taps;
        final List<Float> pts = new ArrayList<>();
        float lx, ly, mx, my;
        boolean multi;

        Overlay(Context c) {
            super(c);
            label.setColor(0xDDFFFFFF);
            label.setTextSize(Ui.dp(c, 15));
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setShadowLayer(3, 0, 0, 0xFF000000);
            path.setColor(Ui.WARN); path.setStyle(Paint.Style.STROKE); path.setStrokeWidth(Ui.dp(c, 2.5f));
            fill.setColor(0x33FFC266);
            scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    st.cam.zoom = Math.max(0.3, Math.min(10, st.cam.zoom * d.getScaleFactor()));
                    redraw(true);
                    return true;
                }
            });
            taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDoubleTap(MotionEvent e) {
                    if (tool != NAV) return false;
                    st.cam.anterior(); st.cam.zoom = 1.25; st.cam.panX = st.cam.panY = 0;
                    redraw(false);
                    return true;
                }
                @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (tool == PICK) { pick(e.getX(), e.getY()); return true; }
                    return false;
                }
            });
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (vol == null || busyNow) return true;
            taps.onTouchEvent(e);
            int a = e.getActionMasked();
            if (tool == CUT) {
                float x = e.getX(), y = e.getY();
                if (a == MotionEvent.ACTION_DOWN) { hideCutBar(); pts.clear(); pts.add(x); pts.add(y); }
                else if (a == MotionEvent.ACTION_MOVE) { pts.add(x); pts.add(y); invalidate(); }
                else if (a == MotionEvent.ACTION_UP) { if (pts.size() >= 8) showCutBar(toArray(pts)); else pts.clear(); invalidate(); }
                return true;
            }
            if (tool == PICK) return true;
            scale.onTouchEvent(e);
            float x = e.getX(), y = e.getY();
            switch (a) {
                case MotionEvent.ACTION_DOWN: lx = x; ly = y; multi = false; stopSpin(); break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    multi = true;
                    mx = (e.getX(0) + e.getX(1)) / 2; my = (e.getY(0) + e.getY(1)) / 2;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() >= 2) {
                        float nx = (e.getX(0) + e.getX(1)) / 2, ny = (e.getY(0) + e.getY(1)) / 2;
                        double aspect = getWidth() / (double) Math.max(1, getHeight());
                        st.cam.panX += 2 * (nx - mx) / getWidth() * aspect / st.cam.zoom;
                        st.cam.panY -= 2 * (ny - my) / getHeight() / st.cam.zoom;
                        mx = nx; my = ny;
                        redraw(true);
                    } else if (!multi) {
                        // The object follows the finger: a full-width drag turns it half a revolution.
                        st.cam.rotate(-(x - lx) / getWidth() * Math.PI, -(y - ly) / getHeight() * Math.PI);
                        lx = x; ly = y;
                        redraw(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    redraw(false);
                    break;
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            if (vol != null) {
                // Patient orientation letters at the edges, from the camera's right and up axes.
                String r = dirLetter(st.cam.R[0], st.cam.R[1], st.cam.R[2]), u = dirLetter(st.cam.R[3], st.cam.R[4], st.cam.R[5]);
                String l = dirLetter(-st.cam.R[0], -st.cam.R[1], -st.cam.R[2]), d = dirLetter(-st.cam.R[3], -st.cam.R[4], -st.cam.R[5]);
                float pad = Ui.dp(getContext(), 10), th = label.getTextSize();
                c.drawText(r, getWidth() - pad - label.measureText(r), getHeight() / 2f + th / 3, label);
                c.drawText(l, pad, getHeight() / 2f + th / 3, label);
                c.drawText(u, getWidth() / 2f - label.measureText(u) / 2, pad + th + Ui.dp(getContext(), 28), label);
                c.drawText(d, getWidth() / 2f - label.measureText(d) / 2, getHeight() - pad, label);
            }
            if (pts.size() >= 4) {
                Path pa = new Path();
                pa.moveTo(pts.get(0), pts.get(1));
                for (int i = 2; i < pts.size(); i += 2) pa.lineTo(pts.get(i), pts.get(i + 1));
                pa.close();
                c.drawPath(pa, fill);
                c.drawPath(pa, path);
            }
            if (tool != NAV && vol != null) {
                String hint = tool == CUT ? (cutPending ? "Choose an action below. Long-press an icon to see what it does." : "Draw around what to cut") : "Tap a structure";
                float w = label.measureText(hint);
                c.drawText(hint, (getWidth() - w) / 2, getHeight() - Ui.dp(getContext(), cutPending ? 96 : 34), label);
            }
        }
    }

    static float[] toArray(List<Float> l) { float[] a = new float[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }

    /** Letter for the patient direction closest to a model-space vector (x = left, y = posterior, z = head). */
    static String dirLetter(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax >= ay && ax >= az) return x > 0 ? "L" : "R";
        if (ay >= az) return y > 0 ? "P" : "A";
        return z > 0 ? "H" : "F";
    }

    static String fmt(long t) { return t == 0 ? "" : new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(t)); }

    // ---------------- quick bar ----------------
    View buildQuick() {
        quick = Ui.row(this);
        quick.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        qItem("presets", "sliders", "Presets", new View.OnClickListener() { public void onClick(View v) { presets(); } });
        qItem("tissues", "tissue", "Tissues", new View.OnClickListener() { public void onClick(View v) { tissuesPanel(); } });
        qItem("rotate", "orbit", "Rotate", new View.OnClickListener() { public void onClick(View v) { setTool(NAV); } });
        qItem("pick", "select", "Pick", new View.OnClickListener() { public void onClick(View v) { setTool(tool == PICK ? NAV : PICK); } });
        qItem("cut", "scissors", "Cut", new View.OnClickListener() { public void onClick(View v) { setTool(tool == CUT ? NAV : CUT); } });
        qItem("clip", "crop", "Clip", new View.OnClickListener() { public void onClick(View v) { clipPanel(); } });
        qItem("views", "cube", "Views", new View.OnClickListener() { public void onClick(View v) { views(); } });
        qItem("spin", "reset", "Spin", new View.OnClickListener() { public void onClick(View v) { if (spinning) stopSpin(); else startSpin(); } });
        qItem("display", "brightness", "Display", new View.OnClickListener() { public void onClick(View v) { displayPanel(); } });
        qItem("undo", "undo", "Undo", new View.OnClickListener() { public void onClick(View v) { undoLast(); } });
        return quick;
    }

    void qItem(String key, String icon, String label, View.OnClickListener l) {
        LinearLayout t = Ui.col(this);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 5));
        t.addView(Ui.iconView(this, icon, 22, Ui.TEXT));
        TextView tv = Ui.text(this, label, 11, Ui.SUB);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(tv);
        t.setOnClickListener(l);
        Ui.tooltip(t, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 60), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        quick.addView(t, lp);
        quickItems.put(key, t);
    }

    void setOn(String key, boolean on) {
        LinearLayout t = quickItems.get(key);
        if (t == null) return;
        t.setBackground(Ui.ripple(Ui.rounded(on ? Ui.BTN_ON : 0x00000000, Ui.dp(this, 10))));
        ((TextView) t.getChildAt(1)).setTextColor(on ? Ui.TEXT : Ui.SUB);
    }

    void updateQuick() {
        setOn("rotate", tool == NAV);
        setOn("pick", tool == PICK);
        setOn("cut", tool == CUT);
        setOn("spin", spinning);
        LinearLayout u = quickItems.get("undo");
        u.setAlpha(undo.isEmpty() ? 0.4f : 1f);
    }

    void setTool(int t) {
        tool = t;
        if (t != NAV) stopSpin();
        if (t == PICK) Ui.toast(this, "Tap a structure to hide it, keep only it, or move it to another tissue class.");
        if (t == CUT) Ui.toast(this, "Draw around the part to cut with one finger. Rotate first to choose the direction you cut from.");
        updateQuick();
        overlay.invalidate();
    }

    final Runnable spinStep = new Runnable() {
        public void run() {
            if (!spinning) return;
            st.cam.spin(gpu ? 0.035 : 0.12);
            redraw(true);
            h.postDelayed(this, gpu ? 33 : 250);
        }
    };

    void startSpin() { if (vol == null) return; spinning = true; tool = NAV; h.post(spinStep); updateQuick(); }
    void stopSpin() { if (!spinning) return; spinning = false; h.removeCallbacks(spinStep); redraw(false); updateQuick(); }

    // ---------------- editing ----------------
    void push(Seg.Edit e) {
        undo.push(e);
        undoBytes += e.bytes();
        while (undoBytes > 160L * 1024 * 1024 && undo.size() > 1) undoBytes -= undo.removeLast().bytes();
        labelsChanged();
        updateQuick();
    }

    void undoLast() {
        if (undo.isEmpty()) { Ui.toast(this, "Nothing to undo."); return; }
        Seg.Edit e = undo.pop();
        undoBytes -= e.bytes();
        Seg.undo(vol, e);
        labelsChanged();
        updateQuick();
        Ui.toast(this, "Undone: " + e.what);
    }

    int viewW() { return overlay.getWidth(); }
    int viewH() { return overlay.getHeight(); }

    void pick(final float x, final float y) {
        final int w = viewW(), hh = viewH();
        showBusy("Finding the structure…");
        worker.submit(new Runnable() {
            public void run() {
                int seed = new CpuVrt(vol, st).pick(Math.round(x), Math.round(y), w, hh);
                final Seg.IntList comp = seed < 0 ? null : Seg.component(vol, seed);
                final int cls = seed < 0 ? -1 : vol.labels[seed] & 0x7F;
                h.post(new Runnable() {
                    public void run() {
                        showBusy(null);
                        if (comp == null || comp.size == 0) { Ui.toast(VrtActivity.this, "Nothing visible there. Tap directly on a structure."); return; }
                        pickMenu(comp, cls);
                    }
                });
            }
        });
    }

    void pickMenu(final Seg.IntList comp, final int cls) {
        double cm3 = comp.size * vol.sx * vol.sy * vol.sz / 1000.0;
        String title = VrtState.CLASS_NAMES[cls] + String.format(Locale.getDefault(), " · %.1f cm³", cm3);
        String[] items = {"Hide this structure", "Show only this (hide the rest of " + VrtState.CLASS_NAMES[cls].toLowerCase(Locale.getDefault()) + ")", "Move it to another tissue class…"};
        new AlertDialog.Builder(this).setTitle(title).setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) push(Seg.set(vol, "Hide structure", comp, true, 0));
                else if (w == 1) {
                    Seg.IntList others = Seg.othersOfClass(vol, comp, cls);
                    push(Seg.set(vol, "Show only this", others, true, 0));
                } else chooseClass("Move to", new Ui.OnChoice() {
                    public void choose(int c) {
                        push(Seg.set(vol, "Move to " + VrtState.CLASS_NAMES[c], comp, false, c));
                        if (!st.cls[c].visible) { st.cls[c].visible = true; tfChanged(); }
                    }
                });
            }
        }).setNegativeButton("Cancel", null).show();
    }

    void chooseClass(String title, final Ui.OnChoice cb) {
        final String[] names = new String[7];
        for (int i = 1; i < 8; i++) names[i - 1] = VrtState.CLASS_NAMES[i];
        new AlertDialog.Builder(this).setTitle(title).setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { cb.choose(w + 1); }
        }).show();
    }

    static final String[] CUT_ICONS = {"scissors", "keepin", "recolor", "close"};
    static final String[] CUT_LABELS = {"Remove what's inside the outline", "Keep only what's inside the outline", "Move visible tissue inside to a tissue class", "Cancel"};

    /** Icon bar over the 3D view after drawing an outline; long-press an icon to see what it does. */
    void showCutBar(final float[] poly) {
        hideCutBar();
        cutPending = true;
        LinearLayout bar = Ui.iconBar(this, CUT_ICONS, CUT_LABELS, new Ui.OnChoice() {
            public void choose(int w) {
                hideCutBar();
                if (w == 0) runCut(poly, true, -1);
                else if (w == 1) runCut(poly, false, -1);
                else if (w == 2) chooseClass("Move inside to", new Ui.OnChoice() { public void choose(int c) { runCut(poly, true, c); } });
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(this, 20);
        stage.addView(bar, lp);
        cutBar = bar;
        overlay.invalidate();
    }

    void hideCutBar() {
        if (cutBar != null && cutBar.getParent() != null) ((ViewGroup) cutBar.getParent()).removeView(cutBar);
        cutBar = null;
        if (cutPending) { cutPending = false; overlay.pts.clear(); overlay.invalidate(); }
    }

    /** Cuts through the whole depth, as seen from the current view. cls >= 0 moves visible tissue instead of removing. */
    void runCut(final float[] poly, final boolean inside, final int cls) {
        final int w = viewW(), hh = viewH();
        showBusy("Cutting…");
        worker.submit(new Runnable() {
            public void run() {
                Seg.IntList sel = Seg.cut(vol, st.cam, poly, w, hh, inside);
                if (cls >= 0) {
                    Seg.IntList vis = new Seg.IntList(sel.size + 1);
                    for (int i = 0; i < sel.size; i++) if (st.cls[vol.labels[sel.a[i]] & 0x7F].visible) vis.add(sel.a[i]);
                    sel = vis;
                }
                final Seg.IntList fs = sel;
                h.post(new Runnable() {
                    public void run() {
                        showBusy(null);
                        if (fs.size == 0) { Ui.toast(VrtActivity.this, "Nothing to cut there."); return; }
                        push(cls >= 0 ? Seg.set(vol, "Move to " + VrtState.CLASS_NAMES[cls], fs, false, cls)
                                : Seg.set(vol, inside ? "Cut inside" : "Keep inside", fs, true, 0));
                        if (cls >= 0 && !st.cls[cls].visible) { st.cls[cls].visible = true; tfChanged(); }
                    }
                });
            }
        });
    }

    // ---------------- panels ----------------
    LinearLayout panel(String title) {
        sheet.content.removeAllViews();
        TextView t = Ui.title(this, title);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 6));
        sheet.content.addView(t);
        sheet.open();
        return sheet.content;
    }

    void note(LinearLayout c, String s) {
        TextView n = Ui.text(this, s, 13.5f, Ui.SUB);
        n.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 8));
        c.addView(n);
    }

    static final int[] PALETTE = {0xE0302A, 0xC8645A, 0xF2EBDA, 0xFFFFFF, 0xF2C0A6, 0xA8C4DC, 0x4CC38A, 0x4A90E2, 0xF5A623, 0xB07CE8};

    void tissuesPanel() {
        if (vol == null) return;
        LinearLayout c = panel("Tissues");
        note(c, st.params.summary);
        LinearLayout g = Ui.group(this, c, "Automatic separation");
        final int[] thr = {(int) Math.round(st.params.vessel), (int) Math.round(Math.min(3000, st.params.boneSeed))};
        if (vol.ct) {
            Ui.slider(this, g, "sliders", 0, "Vessel threshold", 60, 500, thr[0], "HU", new Ui.OnValue() { public void value(int v) { thr[0] = v; } });
            Ui.slider(this, g, "sliders", 0, "Bone threshold", 300, 1500, thr[1], "HU", new Ui.OnValue() { public void value(int v) { thr[1] = v; } });
        } else {
            Ui.slider(this, g, "sliders", 0, "Bright-structure threshold", vol.huMin, vol.huMax, thr[0], "", new Ui.OnValue() { public void value(int v) { thr[0] = v; } });
        }
        Ui.actionRow(this, g, "reset", "Run automatic separation again", new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(VrtActivity.this).setTitle("Separate again?")
                        .setMessage("This replaces the tissue map, including your cuts and moved structures. View settings are kept.")
                        .setPositiveButton("Separate", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { sheet.close(); resegment(thr[0], thr[1]); }
                        }).setNegativeButton("Cancel", null).show();
            }
        });
        if (vol.ct) {
            LinearLayout hg = Ui.group(this, c, "Cardiac CT");
            Ui.actionRow(this, hg, "cube", "Isolate heart", new View.OnClickListener() { public void onClick(View v) { sheet.close(); isolateHeart(false); } });
            note(c, "Keeps the contrast-filled heart, great vessels, coronary arteries, and myocardium; hides chest wall, spine, ribs, lungs, and pulmonary vessels. Undo reverses it.");
        }
        for (int i = 1; i < 8; i++) classCard(c, i);
    }

    void classCard(LinearLayout parent, final int i) {
        final VrtState.Cls k = st.cls[i];
        LinearLayout g = Ui.group(this, parent, VrtState.CLASS_NAMES[i]);
        final View sw = new View(this);
        sw.setBackground(Ui.rounded(k.rgb(), Ui.dp(this, 8)));
        sw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int cur = k.rgb() & 0xFFFFFF, n = 0;
                for (int p = 0; p < PALETTE.length; p++) if (PALETTE[p] == cur) n = p + 1;
                k.setRgb(PALETTE[n % PALETTE.length]);
                sw.setBackground(Ui.rounded(k.rgb(), Ui.dp(VrtActivity.this, 8)));
                tfChanged();
            }
        });
        Ui.tooltip(sw, "Change colour");
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 10), 0);
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        TextView cnt = Ui.text(this, "Tap the colour to change it", 13, Ui.SUB);
        cnt.setPadding(Ui.dp(this, 12), 0, 0, 0);
        row.addView(cnt, Ui.wrapWeight(1));
        android.widget.Switch vis = new android.widget.Switch(this);
        vis.setChecked(k.visible);
        vis.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { k.visible = on; tfChanged(); }
        });
        row.addView(vis);
        g.addView(row);
        Ui.slider(this, g, "brightness", 0, "Opacity", 0, 100, Math.round(k.opacity * 100), "%", new Ui.OnValue() { public void value(int v) { k.opacity = v / 100f; tfChanged(); } });
        int lo = vol.ct ? -1000 : vol.huMin, hi = vol.ct ? 2000 : vol.huMax;
        String unit = vol.ct ? "HU" : "";
        Ui.slider(this, g, "sliders", 0, "Starts at", lo, hi, Math.round(k.lo), unit, new Ui.OnValue() { public void value(int v) { k.lo = v; tfChanged(); } });
        Ui.slider(this, g, "sliders", 0, "Fully shown from", lo, hi, Math.round(k.hi), unit, new Ui.OnValue() { public void value(int v) { k.hi = v; tfChanged(); } });
    }

    void resegment(final int vesselThr, final int boneThr) {
        busyNow = true;
        showBusy("Separating tissues…");
        worker.submit(new Runnable() {
            public void run() {
                Seg.Params p = st.params;
                p.vessel = vesselThr;
                if (vol.ct) { p.boneSeed = boneThr; p.calcium = Math.max(boneThr, p.calcium); }
                Seg.segment(vol, p, new Seg.Progress() { public void update(String m, int pct) { showBusy(m + "… " + pct + "%"); } });
                h.post(new Runnable() {
                    public void run() {
                        busyNow = false;
                        showBusy(null);
                        undo.clear(); undoBytes = 0;
                        st.cls[Seg.VESSEL].lo = vesselThr - 60; st.cls[Seg.VESSEL].hi = vesselThr + 160;
                        tfChanged(); labelsChanged(); updateQuick();
                    }
                });
            }
        });
    }

    void clipPanel() {
        if (vol == null) return;
        LinearLayout c = panel("Clip");
        note(c, "Cut away slabs of the volume from each side. Use Views to look straight at the cut.");
        String[][] names = {{"From the right", "From the left"}, {"From the front", "From the back"}, {"From the feet", "From the head"}};
        LinearLayout g = Ui.group(this, c, "Clip box");
        for (int a = 0; a < 3; a++) {
            final int ax = a;
            Ui.slider(this, g, "crop", 0, names[a][0], 0, 95, Math.round(st.clipLo[a] * 100), "%", new Ui.OnValue() {
                public void value(int v) { st.clipLo[ax] = Math.min(v / 100f, st.clipHi[ax] - 0.05f); redraw(false); }
            });
            Ui.slider(this, g, "crop", 0, names[a][1], 0, 95, Math.round((1 - st.clipHi[a]) * 100), "%", new Ui.OnValue() {
                public void value(int v) { st.clipHi[ax] = Math.max(1 - v / 100f, st.clipLo[ax] + 0.05f); redraw(false); }
            });
        }
        Ui.actionRow(this, g, "reset", "Reset clipping", new View.OnClickListener() {
            public void onClick(View v) { for (int a = 0; a < 3; a++) { st.clipLo[a] = 0; st.clipHi[a] = 1; } redraw(false); clipPanel(); }
        });
    }

    void displayPanel() {
        if (vol == null) return;
        LinearLayout c = panel("Display");
        LinearLayout g = Ui.group(this, c, "Rendering");
        Ui.actionRow(this, g, "cube", "Mode: " + VrtState.MODE_NAMES[st.mode], new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(VrtActivity.this).setTitle("Rendering mode").setItems(VrtState.MODE_NAMES, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { st.mode = w; redraw(false); displayPanel(); }
                }).show();
            }
        });
        if (st.mode == VrtState.MIP || st.mode == VrtState.MINIP) {
            Ui.slider(this, g, "brightness", 0, "Window level", -1000, 2000, (int) st.winC, "", new Ui.OnValue() { public void value(int v) { st.winC = v; redraw(false); } });
            Ui.slider(this, g, "brightness", 0, "Window width", 10, 4000, (int) st.winW, "", new Ui.OnValue() { public void value(int v) { st.winW = v; redraw(false); } });
        }
        LinearLayout l = Ui.group(this, c, "Lighting");
        Ui.slider(this, l, "brightness", 0, "Ambient", 0, 100, Math.round(st.ka * 100), "%", new Ui.OnValue() { public void value(int v) { st.ka = v / 100f; redraw(false); } });
        Ui.slider(this, l, "brightness", 0, "Diffuse", 0, 100, Math.round(st.kd * 100), "%", new Ui.OnValue() { public void value(int v) { st.kd = v / 100f; redraw(false); } });
        Ui.slider(this, l, "brightness", 0, "Shine", 0, 100, Math.round(st.ks * 100), "%", new Ui.OnValue() { public void value(int v) { st.ks = v / 100f; redraw(false); } });
        Ui.actionRow(this, l, "info", "Background: " + (st.bg == 0xFF000000 ? "black" : st.bg == 0xFFFFFFFF ? "white" : "grey"), new View.OnClickListener() {
            public void onClick(View v) { st.bg = st.bg == 0xFF000000 ? 0xFF505055 : st.bg == 0xFF505055 ? 0xFFFFFFFF : 0xFF000000; redraw(false); displayPanel(); }
        });
        LinearLayout q = Ui.group(this, c, "Quality");
        note(q, "Now: " + Vol3D.TIER_NAMES[tier] + ". Recommended for this phone: " + Vol3D.TIER_NAMES[autoTier] + ". " + TIER_HELP[tier]);
        Ui.actionRow(this, q, "sliders", "Change quality…", new View.OnClickListener() {
            public void onClick(View v) {
                final List<Integer> opts = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (int t = 0; t < 4; t++) {
                    if (t < 3 && !gpuCapable) continue;
                    opts.add(t);
                    names.add(Vol3D.TIER_NAMES[t] + (t == autoTier ? "  (recommended)" : t < autoTier ? "  (may be slow or fail)" : ""));
                }
                new AlertDialog.Builder(VrtActivity.this).setTitle("Quality").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        int t = opts.get(w);
                        Ui.prefs(VrtActivity.this).edit().putInt("vrt_tier", t == autoTier ? -1 : t).apply();
                        if (t == tier) return;
                        tier = t; gpu = gpuCapable && t < 3;
                        sheet.close();
                        makeStage();
                        build(true);
                    }
                }).show();
            }
        });
    }

    void presets() {
        if (vol == null) return;
        new AlertDialog.Builder(this).setTitle("Presets").setItems(VrtState.PRESETS, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { st.preset(w, vol.ct); tfChanged(); redraw(false); }
        }).show();
    }

    void views() {
        if (vol == null) return;
        String[] v = {"Front (anterior)", "Back (posterior)", "Patient's left", "Patient's right", "From the head", "From the feet"};
        new AlertDialog.Builder(this).setTitle("Standard views").setItems(v, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                VrtState.Camera c = st.cam;
                if (w == 0) c.anterior(); else if (w == 1) c.posterior(); else if (w == 2) c.leftSide(); else if (w == 3) c.rightSide(); else if (w == 4) c.superior(); else c.inferior();
                redraw(false);
            }
        }).show();
    }

    // ---------------- captures ----------------
    static final String TAG_SAVE = "save", TAG_SHARE = "share", TAG_STUDY = "study";

    void captureMenu() {
        if (vol == null) return;
        String[] items = {"Save image to this phone", "Share image", "Add image to the study", "Add a 36-view rotation series to the study"};
        new AlertDialog.Builder(this).setTitle("Capture").setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 3) { rotationSeries(); return; }
                capture(w == 0 ? TAG_SAVE : w == 1 ? TAG_SHARE : TAG_STUDY);
            }
        }).show();
    }

    int[] captureSize() {
        int w = Math.min(1024, Math.max(256, viewW())), hh = Math.round(w * viewH() / (float) Math.max(1, viewW()));
        if (!gpu) { w = Math.min(w, 512); hh = Math.round(w * viewH() / (float) Math.max(1, viewW())); }
        return new int[]{w, hh};
    }

    void capture(final Object tag) {
        int[] s = captureSize();
        if (gl != null) { gl.capture(s[0], s[1], tag); return; }
        final int w = s[0], hh = s[1];
        final VrtState snap = VrtState.fromJson(safeJson(st));
        showBusy("Rendering…");
        worker.submit(new Runnable() {
            public void run() {
                final int[] px = new CpuVrt(vol, snap).render(w, hh, 1.0);
                h.post(new Runnable() { public void run() { showBusy(null); onCaptured(px, w, hh, tag); } });
            }
        });
    }

    public void onCaptured(final int[] argb, final int w, final int hh, Object tag) {
        if (tag instanceof Integer) { rotationFrame(argb, w, hh, (Integer) tag); return; }
        final String name = "insula_3d_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".png";
        if (TAG_STUDY.equals(tag)) {
            showBusy("Adding to the study…");
            worker.submit(new Runnable() {
                public void run() {
                    String err = null;
                    try {
                        String ser = VrtStore.capturesSeries(series.study);
                        VrtStore.saveCapture(series, argb, w, hh, ser, VrtStore.CAPTURES_DESC, 9902, VrtStore.nextInstance(series.study, ser));
                    } catch (Exception e) { err = Ui.friendly(e); }
                    final String fe = err;
                    h.post(new Runnable() {
                        public void run() {
                            showBusy(null);
                            Ui.toast(VrtActivity.this, fe == null ? "Added to the study as \"" + VrtStore.CAPTURES_DESC + "\"." : "Couldn't add the image: " + fe);
                        }
                    });
                }
            });
            return;
        }
        Bitmap b = Bitmap.createBitmap(argb, w, hh, Bitmap.Config.ARGB_8888);
        try {
            if (TAG_SAVE.equals(tag)) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 95, bo);
                saveAs(name, "image/png", bo.toByteArray(), null);
            } else {
                File f = new File(Library.exportDir, name);
                FileOutputStream fo = new FileOutputStream(f);
                b.compress(Bitmap.CompressFormat.PNG, 95, fo);
                fo.close();
                share(f, "image/png");
            }
        } catch (Exception e) { Ui.toast(this, "Couldn't save the image: " + e.getMessage()); }
    }

    // Rotation series: 36 views, 10° apart around the head-feet axis, added to the study as one series.
    String rotSeries;
    double[] rotCam;
    static final int ROT_FRAMES = 36;

    void rotationSeries() {
        stopSpin();
        rotSeries = DicomWriter.newUid();
        rotCam = st.cam.R.clone();
        showBusy("Rendering view 1 of " + ROT_FRAMES + "…");
        busyNow = true;
        captureFrame(0);
    }

    void captureFrame(final int i) {
        int[] s = captureSize();
        if (gl != null) { gl.capture(s[0], s[1], i); return; }
        final int w = s[0], hh = s[1];
        final VrtState snap = VrtState.fromJson(safeJson(st));
        worker.submit(new Runnable() {
            public void run() {
                final int[] px = new CpuVrt(vol, snap).render(w, hh, 1.0);
                h.post(new Runnable() { public void run() { rotationFrame(px, w, hh, i); } });
            }
        });
    }

    void rotationFrame(final int[] argb, final int w, final int hh, final int i) {
        worker.submit(new Runnable() {
            public void run() {
                String err = null;
                try { VrtStore.saveCapture(series, argb, w, hh, rotSeries, "3D VRT rotation (" + ROT_FRAMES + " views)", 9903, i + 1); }
                catch (Exception e) { err = Ui.friendly(e); }
                final String fe = err;
                h.post(new Runnable() {
                    public void run() {
                        if (fe != null || i + 1 >= ROT_FRAMES) {
                            System.arraycopy(rotCam, 0, st.cam.R, 0, 9);
                            busyNow = false;
                            showBusy(null);
                            redraw(false);
                            Ui.toast(VrtActivity.this, fe == null ? "Rotation series added to the study (" + ROT_FRAMES + " images). Scroll through it in the viewer like any series." : "Stopped: " + fe);
                            return;
                        }
                        st.cam.spin(2 * Math.PI / ROT_FRAMES);
                        showBusy("Rendering view " + (i + 2) + " of " + ROT_FRAMES + "…");
                        captureFrame(i + 1);
                    }
                });
            }
        });
    }

    // ---------------- sessions ----------------
    void sessions() {
        if (vol == null) return;
        final List<VrtStore.Saved> saved = VrtStore.states(series);
        List<String> items = new ArrayList<>();
        items.add("Save this session…");
        for (VrtStore.Saved s : saved) items.add(s.name + "  ·  " + fmt(s.created));
        new AlertDialog.Builder(this).setTitle("Sessions")
                .setMessage(saved.isEmpty() ? "Sessions are saved inside the study (series \"" + VrtStore.STATES_DESC + "\"), so they travel with study-set exports and can be continued later." : null)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) saveSession();
                        else sessionMenu(saved.get(w - 1));
                    }
                }).show();
    }

    void saveSession() {
        final EditText name = Ui.field(this, "Name, for example Coronary review");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(st.name.isEmpty() ? "3D session " + new SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(new Date()) : st.name);
        LinearLayout box = Ui.col(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(name);
        new AlertDialog.Builder(this).setTitle("Save session").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final VrtState s = VrtState.fromJson(safeJson(st));
                        s.params = st.params;
                        s.name = name.getText().toString().trim().isEmpty() ? "3D session" : name.getText().toString().trim();
                        s.created = System.currentTimeMillis();
                        st.name = s.name;
                        showBusy("Saving into the study…");
                        worker.submit(new Runnable() {
                            public void run() {
                                String err = null;
                                try { VrtStore.saveState(series, s, vol); } catch (Exception e) { err = Ui.friendly(e); }
                                final String fe = err;
                                h.post(new Runnable() {
                                    public void run() {
                                        showBusy(null);
                                        Ui.toast(VrtActivity.this, fe == null ? "Session saved in the study." : "Couldn't save: " + fe);
                                    }
                                });
                            }
                        });
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void sessionMenu(final VrtStore.Saved s) {
        new AlertDialog.Builder(this).setTitle(s.name).setMessage("Saved " + fmt(s.created))
                .setPositiveButton("Open", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { openState(s); } })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        new AlertDialog.Builder(VrtActivity.this).setTitle("Delete this session?").setMessage("It's removed from the study on this phone.")
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dd, int ww) { VrtStore.delete(s); Ui.toast(VrtActivity.this, "Session deleted."); }
                                }).setNegativeButton("Cancel", null).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    void openState(final VrtStore.Saved s) {
        busyNow = true;
        showBusy("Opening " + s.name + "…");
        worker.submit(new Runnable() {
            public void run() {
                String err = null;
                try { VrtStore.loadLabels(s, vol); } catch (Exception e) { err = Ui.friendly(e); }
                final String fe = err;
                h.post(new Runnable() {
                    public void run() {
                        busyNow = false;
                        showBusy(null);
                        if (fe != null) { Ui.toast(VrtActivity.this, "Couldn't open the session: " + fe); return; }
                        VrtState n = VrtState.fromJson(s.json);
                        st = n;
                        undo.clear(); undoBytes = 0;
                        attach();
                        updateQuick();
                        Ui.toast(VrtActivity.this, "Opened \"" + s.name + "\".");
                    }
                });
            }
        });
    }

    // ---------------- help ----------------
    void help() {
        String s = "Insula separates the scan into tissue classes automatically, then lets you refine them.\n\n"
                + "Rotate: drag with one finger. Zoom: pinch. Move: drag with two fingers. Double-tap: front view.\n\n"
                + "Presets: coronary CTA, vessels, bones, all tissues, lungs, vessel MIP, skin.\n"
                + "Tissues: show or hide each class, set its colour, opacity, and density range, or rerun the automatic separation with other thresholds.\n"
                + "Pick: tap a structure to hide it, keep only it, or move it to another class (for example, move the heart chambers out of Vessels).\n"
                + "Cut: draw around a region to remove it, keep only it, or reclassify it, through the full depth as seen from the current view.\n"
                + "Clip: cut slabs from each side. Views: standard directions. Spin: turn continuously. Undo: step back.\n\n"
                + "Capture (camera): save or share an image, add it to the study, or add a 36-view rotation series.\n"
                + "Sessions (save): store everything, including your cuts, inside the study and continue later.\n\n"
                + "Automatic separation is designed for contrast CT (for example coronary CTA). It separates tissue types, not individual organs, and can mislabel structures such as dense contrast in veins or unusual anatomy: check the result and correct it with Pick and Cut. MR and other scans use simple intensity bands.\n\n"
                + "This phone: " + TIER_HELP[tier] + (tier != autoTier ? " (Recommended here: " + Vol3D.TIER_NAMES[autoTier] + ".)" : "")
                + "\n\nNot for primary diagnosis.";
        new AlertDialog.Builder(this).setTitle("3D VRT").setMessage(s).setPositiveButton("OK", null).show();
    }

    @Override protected boolean handleBack() {
        if (sheet != null && sheet.open) { sheet.close(); return true; }
        if (tool != NAV) { setTool(NAV); return true; }
        return false;
    }
}
