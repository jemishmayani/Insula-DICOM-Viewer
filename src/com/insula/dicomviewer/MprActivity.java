/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MprActivity extends BaseActivity implements DicomView.Listener, DicomView.Hook {
    static final String[] NAMES = {"Axial", "Coronal", "Sagittal"};
    static final int[] COLORS = {0xFFEF5350, 0xFF66BB6A, 0xFFFFCA28, 0xFF42A5F5};
    static final int CPR_COLOR = 0xFF4DD0E1, MAXPX = 512, CPR_PAGES = 41;
    static final String[] MODES = {"Thin", "MIP", "MinIP", "Average"};
    static final String[] R3 = {"MIP", "Bone", "Soft tissue", "Vessels"};
    static final ExecutorService RENDER = Executors.newSingleThreadExecutor(Library.daemon("insula-render"));

    Library.Series series;
    Volume vol;
    final double[][] U = new double[3][], V = new double[3][];
    double[] C;
    int slabMode = Volume.THIN, layout = 0, active = 0, maximized = -1, tool = DicomView.T_CROSS, r3mode = 0;
    double slabMm = 10, yaw = 0, pitch = 0;
    boolean linkWindow = true, updating, fourthIsCpr, interactive;
    final int[] lastStride = new int[3];
    final DicomView[] views = new DicomView[4];
    final FrameLayout[] ports = new FrameLayout[4];
    final TextView[] headers = new TextView[4];
    final PlaneProv[] provs = new PlaneProv[3];
    Prov3D prov3d;
    CprProv cpr;
    final List<double[]> curvePts = new ArrayList<>();
    int curveView = -1;
    LinearLayout grid;
    View palette;
    MeasureBar mbar;
    final Map<Integer, View> toolBtns = new HashMap<>();
    Ui.Sheet drawer;
    Ui.Segmented layoutSeg;
    final Handler h = new Handler(Looper.getMainLooper());
    ProgressDialog pd;
    // drag state
    int dragMode;          // 0 move, 1 rotate
    double lastAng;
    float lastSx, lastSy, downSx, downSy;
    // 3D rendering state
    volatile boolean rBusy, rDirty, rFast;
    RawImage r3img;
    final Runnable full3d = new Runnable() { public void run() { render3D(false); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        series = Library.series(getIntent().getStringExtra("series"));
        if (series == null) { finish(); return; }
        pd = new ProgressDialog(this);
        pd.setMessage("Building volume…");
        pd.setCancelable(false);
        pd.show();
        new Thread() {
            public void run() {
                String err = null;
                try {
                    vol = Volume.build(series, new Library.Progress() {
                        public void update(final int done, final int total, String msg) {
                            h.post(new Runnable() { public void run() { if (pd != null) pd.setMessage("Building volume… " + (done * 100 / Math.max(1, total)) + "%"); } });
                        }
                    });
                } catch (OutOfMemoryError e) { Library.clearCache(); err = "Not enough memory to build this volume."; }
                catch (Exception e) { err = e.getMessage(); }
                final String fe = err;
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) pd.dismiss();
                        if (fe != null) { Ui.toast(MprActivity.this, fe); finish(); return; }
                        setup();
                    }
                });
            }
        }.start();
    }

    @Override protected boolean handleBack() {
        if (drawer != null && drawer.open) { drawer.close(); return true; }
        if (palette != null && palette.getVisibility() == View.VISIBLE) { showPalette(false); return true; }
        if (maximized >= 0) { maximize(-1); return true; }
        return false;
    }

    // ---------------- setup ----------------
    void resetFrames() {
        U[0] = new double[]{1, 0, 0}; V[0] = new double[]{0, 1, 0};   // axial, viewed from feet
        U[1] = new double[]{1, 0, 0}; V[1] = new double[]{0, 0, -1};  // coronal, viewed from front
        U[2] = new double[]{0, 1, 0}; V[2] = new double[]{0, 0, -1};  // sagittal, viewed from patient's left
    }

    double[] N(int k) { return Volume.norm(Volume.cross(U[k], V[k])); }

    void setup() {
        resetFrames();
        C = vol.center.clone();
        for (int k = 0; k < 3; k++) provs[k] = new PlaneProv(k);
        prov3d = new Prov3D();

        FrameLayout root = new FrameLayout(this);
        LinearLayout main = Ui.col(this);
        main.setBackgroundColor(Ui.BAR);
        root.addView(main);

        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        LinearLayout tc = Ui.col(this);
        tc.setPadding(Ui.dp(this, 10), 0, 0, 0);
        TextView t = Ui.title(this, "MPR");
        tc.addView(t);
        TextView st = Ui.text(this, series.label() + "   " + vol.nx + "×" + vol.ny + "×" + vol.nz, 12.5f, Ui.SUB);
        st.setSingleLine(true);
        tc.addView(st);
        top.addView(tc, Ui.wrapWeight(1));
        top.addView(Ui.icon(this, "share", new View.OnClickListener() { public void onClick(View v) { shareMenu(); } }));
        top.addView(Ui.icon(this, "menu", new View.OnClickListener() { public void onClick(View v) { drawer.open(); } }));
        main.addView(top);

        grid = Ui.col(this);
        grid.setBackgroundColor(Ui.BG);
        int gp = Ui.dp(this, 3);
        grid.setPadding(gp, gp, gp, gp);
        main.addView(grid, Ui.vweight(1));
        for (int k = 0; k < 4; k++) buildPort(k);

        mbar = new MeasureBar(this, new MeasureBar.Host() {
            public DicomView active() { return views[active]; }
            public void selectTool(int t) { MprActivity.this.selectTool(t); }
            public void closeBar() { showPalette(false); }
            public void toggleKey() { }
            public boolean isKey() { return false; }
        }, true, false);
        palette = mbar.view;
        palette.setVisibility(View.GONE);
        main.addView(palette);

        LinearLayout tools = Ui.row(this);
        tools.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 6));
        addTool(tools, "target", "Crosshair: move and rotate planes", DicomView.T_CROSS);
        addTool(tools, "layers", "Scroll through a plane", DicomView.T_SCROLL);
        addTool(tools, "brightness", "Window (brightness and contrast)", DicomView.T_WL);
        addTool(tools, "hand", "Pan", DicomView.T_PAN);
        addTool(tools, "curve", "Draw a curve (curved MPR)", DicomView.T_CURVE);
        tools.addView(Ui.vsep(this));
        View meas = Ui.toolBtn(this, "ruler", "Measure and annotate", new View.OnClickListener() { public void onClick(View v) { showPalette(true); } });
        tools.addView(meas);
        tools.addView(Ui.toolBtn(this, "cube", "3D view options", new View.OnClickListener() { public void onClick(View v) { drawer.open(); } }));
        tools.addView(Ui.toolBtn(this, "maximize", "Maximize selected view", new View.OnClickListener() { public void onClick(View v) { maximize(maximized >= 0 ? -1 : active); } }));
        main.addView(Ui.hscroll(this, tools));

        int w = Math.min(Ui.dp(this, 380), (int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
        drawer = new Ui.Sheet(this, root, true, w);
        buildDrawer();
        setContentView(root);

        for (int k = 0; k < 3; k++) {
            views[k].setProvider(provs[k], provs[k].indexFor(C));
        }
        views[3].setProvider(prov3d, 0);
        views[3].wlOnRgb = true;
        applyLayout(0);
        selectTool(DicomView.T_CROSS);
        setActive(0);
        updateCross();
        render3D(false);
        if (vol.downsample > 1) Ui.toast(this, "Large series: reconstructed at 1/" + vol.downsample + " in-plane resolution to fit in memory.");
        else if (vol.unevenSpacing) Ui.toast(this, "Slice spacing in this series is uneven, so reconstructions may be slightly distorted.");
        if (!Ui.prefs(this).getBoolean("mpr_hint", false)) {
            new AlertDialog.Builder(this).setTitle("Using MPR")
                    .setMessage("Drag the crosshair center to move through the volume.\n\nDrag a colored dot on a line to tilt the other planes (oblique MPR).\n\nThe fourth view is 3D; drag to rotate it. Switch it to curved MPR with Draw curve.\n\nThe menu at the top right has slab MIP, layouts, 3D presets, and saving reformats as a new series.")
                    .setPositiveButton("Got it", null).show();
            Ui.prefs(this).edit().putBoolean("mpr_hint", true).apply();
        }
    }

    void buildPort(final int k) {
        FrameLayout port = new FrameLayout(this);
        DicomView v = new DicomView(this);
        v.listener = this;
        v.hook = this;
        v.showBorder = true;
        v.topInset = Ui.dp(this, 36);
        v.accent = COLORS[k];
        port.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView hdr = Ui.text(this, k < 3 ? NAMES[k] : "3D", 15, COLORS[k]);
        hdr.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hdr.setShadowLayer(4, 0, 0, 0xFF000000);
        Icons chev = new Icons("chevron", COLORS[k]);
        chev.setBounds(0, 0, Ui.dp(this, 18), Ui.dp(this, 18));
        hdr.setCompoundDrawables(null, null, chev, null);
        hdr.setCompoundDrawablePadding(Ui.dp(this, 4));
        hdr.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 8));
        hdr.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { setActive(k); portMenu(k); } });
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.gravity = Gravity.TOP | Gravity.START;
        port.addView(hdr, hl);
        views[k] = v; ports[k] = port; headers[k] = hdr;
    }

    void addTool(LinearLayout r, String icon, String label, final int t) {
        View b = Ui.toolBtn(this, icon, label, new View.OnClickListener() { public void onClick(View v) { selectTool(t); } });
        toolBtns.put(t, b);
        r.addView(b);
    }

    void buildDrawer() {
        LinearLayout c = drawer.content;
        LinearLayout hdr = Ui.row(this);
        hdr.addView(Ui.spacer(this));
        hdr.addView(Ui.icon(this, "close", new View.OnClickListener() { public void onClick(View v) { drawer.close(); } }));
        c.addView(hdr);

        c.addView(Ui.heading(this, "Layout"));
        layoutSeg = new Ui.Segmented(this, new String[]{"lay4", "lay3v", "lay1"}, new Ui.Segmented.OnPick() {
            public void pick(int i) { maximized = -1; applyLayout(i); }
        });
        c.addView(layoutSeg.view);
        c.addView(Ui.text(this, "Four views, three planes, or only the selected view.", 13, Ui.SUB));

        c.addView(Ui.heading(this, "Slab"));
        LinearLayout modes = Ui.row(this);
        final List<Button> modeBtns = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button bt = Ui.btn(this, MODES[i], null);
            bt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { slabMode = k; for (int j = 0; j < modeBtns.size(); j++) Ui.setOn(modeBtns.get(j), j == k); refreshPlanes(); }
            });
            modeBtns.add(bt);
            modes.addView(bt);
        }
        Ui.setOn(modeBtns.get(0), true);
        c.addView(Ui.hscroll(this, modes));
        LinearLayout thick = Ui.row(this);
        final double[] mm = {2, 5, 10, 20, 40, 80};
        final List<Button> thickBtns = new ArrayList<>();
        for (int i = 0; i < mm.length; i++) {
            final int k = i;
            Button bt = Ui.btn(this, (int) mm[i] + " mm", null);
            bt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    slabMm = mm[k];
                    for (int j = 0; j < thickBtns.size(); j++) Ui.setOn(thickBtns.get(j), j == k);
                    if (slabMode == Volume.THIN) { slabMode = Volume.MIP; for (int j = 0; j < modeBtns.size(); j++) Ui.setOn(modeBtns.get(j), j == Volume.MIP); }
                    refreshPlanes();
                }
            });
            thickBtns.add(bt);
            thick.addView(bt);
        }
        Ui.setOn(thickBtns.get(2), true);
        c.addView(Ui.hscroll(this, thick));
        c.addView(Ui.text(this, "MIP highlights vessels and nodules, MinIP airways, Average smooths noise.", 13, Ui.SUB));

        c.addView(Ui.heading(this, "3D view"));
        LinearLayout r3 = Ui.row(this);
        final List<Button> r3Btns = new ArrayList<>();
        for (int i = 0; i < R3.length; i++) {
            final int k = i;
            Button bt = Ui.btn(this, R3[i], null);
            bt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { for (int j = 0; j < r3Btns.size(); j++) Ui.setOn(r3Btns.get(j), j == k); set3DMode(k); }
            });
            r3Btns.add(bt);
            r3.addView(bt);
        }
        Ui.setOn(r3Btns.get(0), true);
        c.addView(Ui.hscroll(this, r3));
        c.addView(Ui.text(this, "Drag the 3D view to rotate it. With the Window tool, dragging it changes what's visible.", 13, Ui.SUB));
        c.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.dp(this, 8)));
        c.addView(Ui.divider(this));

        Ui.switchRow(this, c, "crossref", "Crosshair lines", true, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { for (DicomView v : views) { v.crossVisible = on; v.invalidate(); } }
        });
        Ui.switchRow(this, c, "link", "Link window across planes", true, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { linkWindow = on; }
        });
        Ui.actionRow(this, c, "brightness", "Window presets", new View.OnClickListener() { public void onClick(View v) { drawer.close(); presets(); } });
        Ui.actionRow(this, c, "pencil", "Curved MPR: draw a path", new View.OnClickListener() { public void onClick(View v) { drawer.close(); selectTool(DicomView.T_CURVE); } });
        Ui.actionRow(this, c, "close", "Clear curve", new View.OnClickListener() { public void onClick(View v) { drawer.close(); clearCurve(); } });
        Ui.actionRow(this, c, "download", "Save planes as a new series", new View.OnClickListener() { public void onClick(View v) { drawer.close(); saveSeriesDialog(); } });
        Ui.actionRow(this, c, "help", "How MPR works here", new View.OnClickListener() { public void onClick(View v) { drawer.close(); help(); } });
        Ui.actionRow(this, c, "book", "Guide to every tool", new View.OnClickListener() { public void onClick(View v) { drawer.close(); startActivity(new android.content.Intent(MprActivity.this, GuideActivity.class)); } });

        Button reset = Ui.pill(this, "Reset orientation", "reset", new View.OnClickListener() {
            public void onClick(View v) {
                resetFrames();
                C = vol.center.clone();
                yaw = 0; pitch = 0;
                for (int k = 0; k < 4; k++) { views[k].resetView(); views[k].clearAnnotations(); }
                refreshPlanes();
                render3D(false);
                drawer.close();
            }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 24);
        c.addView(reset, rl);
    }

    // ---------------- layout & tools ----------------
    void applyLayout(int l) {
        layout = l;
        if (layoutSeg.selected != l) layoutSeg.select(l);
        grid.removeAllViews();
        for (FrameLayout p : ports) if (p.getParent() != null) ((ViewGroup) p.getParent()).removeView(p);
        int m = Ui.dp(this, 2);
        if (maximized >= 0 || l == 2) {
            int k = maximized >= 0 ? maximized : active;
            grid.addView(ports[k], Ui.vweight(1));
        } else if (l == 0) {
            int[][] rows = {{0, 2}, {1, 3}};
            for (int[] r : rows) {
                LinearLayout row = Ui.row(this);
                for (int k : r) { LinearLayout.LayoutParams lp = Ui.weight(1); lp.setMargins(m, m, m, m); row.addView(ports[k], lp); }
                grid.addView(row, Ui.vweight(1));
            }
        } else {
            LinearLayout.LayoutParams tp = Ui.vweight(1.25f);
            tp.setMargins(m, m, m, m);
            grid.addView(ports[0], tp);
            LinearLayout row = Ui.row(this);
            for (int k : new int[]{1, 2}) { LinearLayout.LayoutParams lp = Ui.weight(1); lp.setMargins(m, m, m, m); row.addView(ports[k], lp); }
            grid.addView(row, Ui.vweight(1));
        }
        for (DicomView v : views) v.invalidate();
    }

    void maximize(int k) {
        maximized = k;
        applyLayout(layout);
    }

    void setActive(int i) {
        active = i;
        for (int k = 0; k < 4; k++) { views[k].active = k == i; views[k].invalidate(); }
        if (mbar != null) mbar.onSelection(views[i], views[i].selected);
    }

    void selectTool(int t) {
        tool = t;
        for (Map.Entry<Integer, View> e : toolBtns.entrySet()) Ui.setToolOn(e.getValue(), e.getKey() == t);
        mbar.setTool(t);
        for (int k = 0; k < 3; k++) views[k].setTool(t);
        // The fourth view orbits in 3D, pages in curved MPR, and follows Window/Pan otherwise.
        int t4;
        if (fourthIsCpr) t4 = (t == DicomView.T_CROSS || t == DicomView.T_CURVE) ? DicomView.T_SCROLL : t;
        else t4 = (t == DicomView.T_WL || t == DicomView.T_PAN) ? t : DicomView.T_ORBIT;
        views[3].setTool(t4);
        if (t == DicomView.T_CURVE) Ui.toast(this, "Tap points along the structure on any plane. The curved reformat appears in the fourth view.");
    }

    void showPalette(boolean on) {
        palette.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) { if (!DicomView.isAnnTool(tool)) selectTool(DicomView.T_LENGTH); }
        else selectTool(DicomView.T_CROSS);
    }

    void portMenu(final int k) {
        final List<String> items = new ArrayList<>();
        items.add(maximized == k ? "Show all views" : "Maximize this view");
        if (k == 3) { items.add("Show 3D view"); items.add("Show curved MPR"); }
        else { items.add("Save this plane as a series"); items.add("Reset this view's zoom"); }
        new AlertDialog.Builder(this).setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                String s = items.get(w);
                if (s.startsWith("Maximize")) maximize(k);
                else if (s.startsWith("Show all")) maximize(-1);
                else if (s.equals("Show 3D view")) setFourth(false);
                else if (s.equals("Show curved MPR")) {
                    if (curvePts.size() < 2) { Ui.toast(MprActivity.this, "Draw a curve first: choose Draw curve and tap points on a plane."); selectTool(DicomView.T_CURVE); }
                    else setFourth(true);
                } else if (s.startsWith("Save")) saveSeries(k, 2, slabMode == Volume.THIN ? 0 : slabMm);
                else views[k].resetView();
            }
        }).show();
    }

    void setFourth(boolean cprOn) {
        fourthIsCpr = cprOn;
        if (cprOn) {
            if (cpr == null) cpr = new CprProv();
            views[3].wlOnRgb = false;
            views[3].accent = CPR_COLOR;
            headers[3].setText("Curved MPR");
            headers[3].setTextColor(CPR_COLOR);
            views[3].setProvider(cpr, CPR_PAGES / 2);
            views[3].setWindow(views[0].wc, views[0].ww);
        } else {
            views[3].accent = COLORS[3];
            headers[3].setText("3D");
            headers[3].setTextColor(COLORS[3]);
            views[3].wlOnRgb = true;
            views[3].setProvider(prov3d, 0);
            render3D(false);
        }
        selectTool(tool);
    }

    // ---------------- plane math ----------------
    void refreshPlanes() {
        updating = true;
        for (int k = 0; k < 3; k++) views[k].setIndex(provs[k].indexFor(C));
        if (fourthIsCpr) views[3].refresh();
        updating = false;
        updateCross();
    }

    void updateCross() {
        for (int k = 0; k < 3; k++) {
            DicomView v = views[k];
            v.crossLines.clear();
            v.curve = null;
            if (v.img == null) { v.invalidate(); continue; }
            Volume.Plane pl = provs[k].plane(v.index);
            double[] nk = N(k);
            float cx = (float) (Volume.dot(Volume.sub(C, pl.origin), pl.u) / pl.s + 0.5), cy = (float) (Volume.dot(Volume.sub(C, pl.origin), pl.v) / pl.s + 0.5);
            for (int j = 0; j < 3; j++) {
                if (j == k) continue;
                double[] dir = Volume.cross(nk, N(j));
                double dx = Volume.dot(dir, pl.u), dy = Volume.dot(dir, pl.v), L = Math.hypot(dx, dy);
                if (L < 1e-6) continue;
                DicomView.CrossLine l = new DicomView.CrossLine();
                l.x = cx; l.y = cy; l.dx = (float) (dx / L); l.dy = (float) (dy / L);
                l.color = COLORS[j];
                l.slabHalf = slabMode == Volume.THIN ? 0 : (float) (slabMm / 2 / pl.s);
                v.crossLines.add(l);
            }
            if (!curvePts.isEmpty() && curveView == k) {
                float[] q = new float[curvePts.size() * 2];
                for (int i = 0; i < curvePts.size(); i++) {
                    double[] d = Volume.sub(curvePts.get(i), pl.origin);
                    q[2 * i] = (float) (Volume.dot(d, pl.u) / pl.s + 0.5);
                    q[2 * i + 1] = (float) (Volume.dot(d, pl.v) / pl.s + 0.5);
                }
                v.curve = q;
            }
            v.invalidate();
        }
    }

    double[] patientAt(int k, float ix, float iy) {
        Volume.Plane pl = provs[k].plane(views[k].index);
        return Volume.add(pl.origin, Volume.add(Volume.mul(pl.u, (ix - 0.5) * pl.s), Volume.mul(pl.v, (iy - 0.5) * pl.s)));
    }

    double angleAt(int k, float ix, float iy) {
        Volume.Plane pl = provs[k].plane(views[k].index);
        double[] d = Volume.sub(patientAt(k, ix, iy), C);
        return Math.atan2(Volume.dot(d, pl.v), Volume.dot(d, pl.u));
    }

    // ---------------- gestures ----------------
    public void onHook(DicomView v, int phase, float ix, float iy, float sx, float sy) {
        int k = indexOf(v);
        if (k < 0) return;
        if (k == 3) { orbit(phase, sx, sy); return; }
        if (tool == DicomView.T_CURVE) { curveTouch(k, phase, ix, iy, sx, sy); return; }
        if (phase == 0) {
            interactive = true;
            dragMode = pickMode(v, sx, sy);
            if (dragMode == 1) lastAng = angleAt(k, ix, iy);
            else moveCenter(k, ix, iy);
        } else if (phase == 1) {
            if (dragMode == 0) moveCenter(k, ix, iy);
            else {
                double a = angleAt(k, ix, iy);
                double d = a - lastAng;
                if (d > Math.PI) d -= 2 * Math.PI;
                if (d < -Math.PI) d += 2 * Math.PI;
                lastAng = a;
                rotateOthers(k, d);
            }
        }
    }

    int indexOf(DicomView v) { for (int k = 0; k < 4; k++) if (views[k] == v) return k; return -1; }

    /** Rotate if the touch starts on a line's outer part (near its grip), otherwise move the center. */
    int pickMode(DicomView v, float sx, float sy) {
        float[] c = v.crossCenterScreen();
        if (c == null) return 0;
        float dp = v.density();
        double dist = Math.hypot(sx - c[0], sy - c[1]);
        if (dist < 50 * dp) return 0;
        for (DicomView.CrossLine l : v.crossLines) {
            float[] a = v.toScreen(l.x, l.y), b = v.toScreen(l.x + l.dx * 10, l.y + l.dy * 10);
            double ux = b[0] - a[0], uy = b[1] - a[1], L = Math.hypot(ux, uy);
            if (L < 1e-6) continue;
            double perp = Math.abs(((sx - a[0]) * uy - (sy - a[1]) * ux) / L);
            if (perp < 26 * dp) return 1;
        }
        return 0;
    }

    void moveCenter(int k, float ix, float iy) {
        C = patientAt(k, ix, iy);
        updating = true;
        for (int j = 0; j < 3; j++) if (j != k) views[j].setIndex(provs[j].indexFor(C));
        if (fourthIsCpr) { /* path unchanged */ }
        updating = false;
        updateCross();
    }

    void rotateOthers(int k, double ang) {
        double[] axis = N(k);
        for (int j = 0; j < 3; j++) {
            if (j == k) continue;
            U[j] = Volume.norm(Volume.rot(U[j], axis, ang));
            V[j] = Volume.norm(Volume.rot(V[j], axis, ang));
            views[j].clearAnnotations();
        }
        updating = true;
        for (int j = 0; j < 3; j++) if (j != k) views[j].setIndex(provs[j].indexFor(C));
        updating = false;
        updateHeaders();
        updateCross();
    }

    void updateHeaders() {
        double[][] base = {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}};
        for (int k = 0; k < 3; k++) {
            boolean oblique = Math.abs(Volume.dot(N(k), base[k])) < 0.9995;
            headers[k].setText(NAMES[k] + (oblique ? " (oblique)" : ""));
        }
    }

    void orbit(int phase, float sx, float sy) {
        if (phase == 0) { lastSx = sx; lastSy = sy; downSx = sx; downSy = sy; return; }
        float dp = views[3].density();
        if (phase == 1) {
            yaw += (sx - lastSx) / (140 * dp);
            pitch = Math.max(-1.45, Math.min(1.45, pitch - (sy - lastSy) / (140 * dp)));
            lastSx = sx; lastSy = sy;
            render3D(true);
        } else render3D(false);
    }

    void curveTouch(int k, int phase, float ix, float iy, float sx, float sy) {
        if (phase == 0) { downSx = sx; downSy = sy; return; }
        if (phase != 2 || Math.hypot(sx - downSx, sy - downSy) > 12 * views[k].density()) return;
        if (curveView != k) { curvePts.clear(); curveView = k; }
        curvePts.add(patientAt(k, ix, iy));
        updateCross();
        if (curvePts.size() >= 2) {
            if (cpr == null) cpr = new CprProv();
            cpr.up = N(k);
            if (!fourthIsCpr) setFourth(true); else views[3].refresh();
        }
    }

    void clearCurve() {
        curvePts.clear();
        curveView = -1;
        if (fourthIsCpr) setFourth(false);
        updateCross();
    }

    // ---------------- 3D ----------------
    void set3DMode(int m) {
        r3mode = m;
        if (fourthIsCpr) setFourth(false);
        DicomView v = views[3];
        if (m == 0) { v.wlSet = false; }
        else if (vol.ct) {
            double[][] w = {{0, 0}, {500, 700}, {-200, 600}, {300, 300}};
            v.setWindow(w[m][0], w[m][1]);
        } else v.setWindow(vol.defWc, vol.defWw);
        render3D(false);
    }

    void render3D(boolean fast) {
        if (fourthIsCpr || vol == null) return;
        rFast = fast;
        if (rBusy) { rDirty = true; return; }
        rBusy = true;
        final boolean f = fast;
        final double y = yaw, p = pitch;
        final int mode = r3mode;
        DicomView v = views[3];
        final double lo = v.wc - v.ww / 2, hi = v.wc + v.ww / 2;
        final int N = f ? 180 : Math.max(240, Math.min(480, Math.max(v.getWidth(), v.getHeight()) / 2));
        RENDER.submit(new Runnable() {
            public void run() {
                RawImage img = null;
                try { img = vol.render3D(y, p, 1.0, N, mode == 0 ? Volume.R_MIP : mode, lo, hi, f); } catch (Throwable ignored) { }
                final RawImage fi = img;
                h.post(new Runnable() {
                    public void run() {
                        rBusy = false;
                        if (fi != null) {
                            boolean first = r3img == null;
                            r3img = fi;
                            if (!fourthIsCpr) {
                                if (first && mode == 0) views[3].wlSet = false;
                                views[3].refresh();
                            }
                        }
                        if (rDirty) { rDirty = false; render3D(rFast); }
                    }
                });
            }
        });
    }

    // ---------------- DicomView.Listener ----------------
    public void onActivated(DicomView v) { int k = indexOf(v); if (k >= 0 && k != active) setActive(k); }

    public void onIndexChanged(DicomView v) {
        int k = indexOf(v);
        if (updating || k < 0 || k > 2 || provs[k] == null) return;
        double t = provs[k].t(v.index);
        double[] n = N(k);
        C = Volume.add(C, Volume.mul(n, t - Volume.dot(C, n)));
        updateCross();
    }

    public void onWindowChanged(DicomView v) {
        int k = indexOf(v);
        if (k == 3 && !fourthIsCpr) {
            if (r3mode != 0) { render3D(true); h.removeCallbacks(full3d); h.postDelayed(full3d, 350); }
            return;
        }
        if (!linkWindow) return;
        for (int j = 0; j < 3; j++) if (views[j] != v) views[j].setWindow(v.wc, v.ww);
        if (fourthIsCpr && k != 3) views[3].setWindow(v.wc, v.ww);
    }

    public void onImageTap(DicomView v, float ix, float iy) { }
    public void onImageReady(DicomView v) { }
    public void onLongPressImage(DicomView v) { presets(); }
    public void onInteractionEnd(DicomView v) {
        int k = indexOf(v);
        if (k < 0 || k > 2) return;
        if (interactive) { interactive = false; refreshPlanes(); }
        else if (lastStride[k] > 1) { updating = true; v.refresh(); updating = false; }
    }

    public void onSelection(DicomView v, DicomView.Ann a) { if (mbar != null) mbar.onSelection(v, a); }

    public void onArrowCreated(final DicomView v, final DicomView.Ann a) {
        LinearLayout l = Ui.col(this);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p / 2, p, 0);
        final EditText e = Ui.field(this, "Label (optional)");
        l.addView(e);
        new AlertDialog.Builder(this).setTitle("Arrow label").setView(l)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { v.setLabel(a, e.getText().toString()); } })
                .setNegativeButton("No label", null).show();
    }

    // ---------------- actions ----------------
    void presets() {
        String[] names = new String[ViewerActivity.PRESETS.length + 1];
        names[0] = "Default";
        for (int i = 0; i < ViewerActivity.PRESETS.length; i++) names[i + 1] = ViewerActivity.PRESETS[i][0] + "   W " + ViewerActivity.PRESETS[i][2] + " / L " + ViewerActivity.PRESETS[i][1];
        new AlertDialog.Builder(this).setTitle("Window presets").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                double wc = w == 0 ? vol.defWc : Double.parseDouble(ViewerActivity.PRESETS[w - 1][1]);
                double ww = w == 0 ? vol.defWw : Double.parseDouble(ViewerActivity.PRESETS[w - 1][2]);
                for (int k = 0; k < 3; k++) views[k].setWindow(wc, ww);
                if (fourthIsCpr) views[3].setWindow(wc, ww);
                else if (r3mode == 0) views[3].setWindow(wc, ww);
            }
        }).show();
    }

    void shareMenu() {
        Ui.choices(this, "Share", null, new String[]{"share", "download", "file"},
                new String[]{"Share this view", "Save this view as PNG", "Save planes as a new series"},
                new Ui.OnChoice() {
                    public void choose(int i) {
                        if (i == 2) { saveSeriesDialog(); return; }
                        boolean noPhi = Ui.prefs(MprActivity.this).getBoolean("teacher", false) || Ui.prefs(MprActivity.this).getBoolean("hidephi", false);
                        Bitmap b = views[active].snapshot(noPhi);
                        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
                        try {
                            if (i == 0) {
                                File f = new File(Library.exportDir, "insula_mpr_" + stamp + ".png");
                                FileOutputStream fo = new FileOutputStream(f);
                                b.compress(Bitmap.CompressFormat.PNG, 95, fo);
                                fo.close();
                                share(f, "image/png");
                            } else {
                                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                                b.compress(Bitmap.CompressFormat.PNG, 95, bo);
                                saveAs("insula_mpr_" + stamp + ".png", "image/png", bo.toByteArray(), null);
                            }
                        } catch (Exception e) { Ui.toast(MprActivity.this, "Couldn't export: " + e.getMessage()); }
                    }
                }).show();
    }

    void saveSeriesDialog() {
        final String[] planes = new String[3];
        for (int k = 0; k < 3; k++) planes[k] = headers[k].getText().toString();
        new AlertDialog.Builder(this).setTitle("Which plane?").setItems(planes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, final int k) {
                final double[] sp = {1, 2, 3, 5};
                String[] lab = {"Every 1 mm", "Every 2 mm", "Every 3 mm", "Every 5 mm"};
                new AlertDialog.Builder(MprActivity.this).setTitle("Slice spacing").setItems(lab, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d2, int w) { saveSeries(k, sp[w], slabMode == Volume.THIN ? 0 : slabMm); }
                }).show();
            }
        }).show();
    }

    /** Reformats the whole volume along plane k and imports the result as a new DICOM series in this study. */
    void saveSeries(final int k, final double spacing, final double thickness) {
        final double[] u = U[k].clone(), v = V[k].clone(), n = N(k);
        final int mode = slabMode;
        final String desc = "MPR " + headers[k].getText() + " " + fmt(spacing) + "mm" + (mode == Volume.THIN ? "" : " " + MODES[mode] + " " + fmt(thickness) + "mm");
        pd = new ProgressDialog(this);
        pd.setMessage("Saving " + desc + "…");
        pd.setCancelable(false);
        pd.show();
        new Thread() {
            public void run() {
                int saved = 0;
                String err = null;
                try {
                    Dicom.DataSet src = Library.parsedFile(series.first().file);
                    double[] r = vol.range(n);
                    int count = (int) Math.floor((r[1] - r[0]) / spacing) + 1;
                    if (count > 1500) throw new Exception("That would create " + count + " images. Choose a larger spacing.");
                    String seriesUid = DicomWriter.newUid();
                    int seriesNo = 900;
                    for (Library.Series s : series.study.series) seriesNo = Math.max(seriesNo, s.number + 1);
                    String cls = vol.ct ? "1.2.840.10008.5.1.4.1.1.2" : "MR".equals(series.modality) ? "1.2.840.10008.5.1.4.1.1.4" : "1.2.840.10008.5.1.4.1.1.7";
                    int[] stats = new int[3];
                    for (int i = 0; i < count; i++) {
                        double t = r[0] + i * spacing;
                        double[] p = Volume.mul(n, t);
                        Volume.Plane pl = vol.planeThrough(p, u, v, MAXPX);
                        RawImage im = vol.reslice(pl, thickness, mode);
                        DicomWriter w = new DicomWriter();
                        String sop = DicomWriter.newUid();
                        w.str(0x00080005, "CS", src.str(0x00080005));
                        w.str(0x00080008, "CS", "DERIVED\\SECONDARY\\MPR");
                        w.str(0x00080016, "UI", cls);
                        w.str(0x00080018, "UI", sop);
                        for (int tag : new int[]{0x00080020, 0x00080030, 0x00080050, 0x00080080, 0x00081030, 0x00100030, 0x00100040, 0x00101010, 0x00200010})
                            if (src.has(tag)) w.str(tag, Dicom.Dict.vr(tag), src.str(tag));
                        w.str(0x00080060, "CS", series.modality);
                        w.str(0x00080090, "PN", src.str(0x00080090));
                        w.str(0x0008103E, "LO", desc);
                        w.str(0x00100010, "PN", src.str(0x00100010));
                        w.str(0x00100020, "LO", src.str(0x00100020));
                        w.str(0x00180050, "DS", fmt(mode == Volume.THIN ? spacing : thickness));
                        w.str(0x0020000D, "UI", src.str(0x0020000D));
                        w.str(0x0020000E, "UI", seriesUid);
                        w.str(0x00200011, "IS", String.valueOf(seriesNo));
                        w.str(0x00200013, "IS", String.valueOf(i + 1));
                        w.ds(0x00200032, pl.origin);
                        w.ds(0x00200037, u[0], u[1], u[2], v[0], v[1], v[2]);
                        String fr = src.getString(0x00200052);
                        w.str(0x00200052, "UI", fr == null ? DicomWriter.newUid() : fr);
                        w.ds(0x00201041, t);
                        w.us(0x00280002, 1);
                        w.str(0x00280004, "CS", "MONOCHROME2");
                        w.us(0x00280010, im.h);
                        w.us(0x00280011, im.w);
                        w.ds(0x00280030, pl.s, pl.s);
                        w.us(0x00280100, 16); w.us(0x00280101, 16); w.us(0x00280102, 15); w.us(0x00280103, 1);
                        w.ds(0x00281050, views[k].wc);
                        w.ds(0x00281051, views[k].ww);
                        w.ds(0x00281052, 0);
                        w.ds(0x00281053, 1);
                        if (vol.ct) w.str(0x00281054, "LO", "HU");
                        short[] px = new short[im.pix.length];
                        for (int q = 0; q < px.length; q++) px[q] = (short) Math.max(-32768, Math.min(32767, im.pix[q]));
                        w.pixels16(px);
                        Library.importBytes(w.toBytes(cls, sop), stats);
                        saved = stats[0];
                        final int done = i + 1, tot = count;
                        if (i % 5 == 0) h.post(new Runnable() { public void run() { if (pd != null) pd.setMessage("Saving " + desc + "\n" + done + " of " + tot); } });
                    }
                } catch (Throwable e) { err = e.getMessage(); }
                final int fs = saved;
                final String fe = err;
                Store.log(MprActivity.this, "file", "Saved " + desc, fe == null ? fs + " images added to the study" : "Failed: " + fe, fe == null);
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) pd.dismiss();
                        if (fe != null) Ui.toast(MprActivity.this, "Couldn't save: " + fe);
                        else Ui.toast(MprActivity.this, "Saved \"" + desc + "\" (" + fs + " images) to this study. It's in the viewer's series strip.");
                    }
                });
            }
        }.start();
    }

    static String fmt(double d) { return Math.abs(d - Math.rint(d)) < 1e-6 ? String.valueOf((long) Math.rint(d)) : String.format(Locale.ROOT, "%.1f", d); }

    void help() {
        new AlertDialog.Builder(this).setTitle("How MPR works here")
                .setMessage("Planes are true axial, coronal, and sagittal in patient space, whatever direction the series was acquired in.\n\n"
                        + "Crosshair tool: drag near the center to move the crosshair. Drag a colored line where its dot is to rotate the other two planes around this view (oblique). Rotate in two views for double-oblique.\n\n"
                        + "Line colors match view borders: red axial, green coronal, yellow sagittal. Dashed lines show the slab thickness.\n\n"
                        + "Scroll tool: swipe to page through a plane. Pinch to zoom and use two fingers to pan in any view.\n\n"
                        + "Curved MPR: choose Draw curve and tap points along a vessel, canal, or dental arch. The fourth view shows the straightened reformat; swipe it to shift the curve sideways.\n\n"
                        + "3D: drag to rotate. Choose MIP, Bone, Soft tissue, or Vessels in the menu. With the Window tool, dragging the 3D view changes which densities are visible.\n\n"
                        + "Save planes as a new series stores the reformat as DICOM in this study, so it opens in the viewer and exports like any other series.")
                .setPositiveButton("Close", null).show();
    }

    // ---------------- providers ----------------
    abstract class Base implements SliceProvider {
        final Set<Integer> keys = new HashSet<>();
        public double[] position(int i) { return null; }
        public String frameOfRef() { return null; }
        public String[] patientLines() { return new String[0]; }
        public String[] studyLines() { return new String[0]; }
        public Set<Integer> keyImages() { return keys; }
        public Library.SliceRef ref(int i) { return null; }
        public int seriesNumber() { return series.number; }
        public boolean async() { return false; }
        public RawImage peek(int i) { return null; }
        public void load(int i, Library.Done cb) { }
    }

    final class PlaneProv extends Base {
        final int k;
        PlaneProv(int k) { this.k = k; }
        double step() { return vol.minSp; }
        double[] range() { return vol.range(N(k)); }
        public int count() { double[] r = range(); return Math.max(1, (int) Math.floor((r[1] - r[0]) / step()) + 1); }
        double t(int i) { return range()[0] + i * step(); }
        int indexFor(double[] p) { double[] r = range(); return Math.max(0, Math.min(count() - 1, (int) Math.round((Volume.dot(p, N(k)) - r[0]) / step()))); }
        Volume.Plane plane(int i) {
            double[] n = N(k);
            double[] p = Volume.add(C, Volume.mul(n, t(i) - Volume.dot(C, n)));
            return vol.planeThrough(p, U[k], V[k], MAXPX);
        }
        public RawImage image(int i) {
            // Half-resolution sampling while a finger is moving; full quality when it lifts.
            int stride = (interactive || views[k].touching || views[k].interacting) ? 2 : 1;
            lastStride[k] = stride;
            return vol.reslice(plane(i), slabMm, slabMode, stride);
        }
        public double[] orientation(int i) { return new double[]{U[k][0], U[k][1], U[k][2], V[k][0], V[k][1], V[k][2]}; }
        public String seriesName() { return headers[k].getText().toString(); }
        public String sliceInfo(int i) {
            String s = String.format(Locale.ROOT, "%.1f mm", t(i));
            if (slabMode != Volume.THIN) s += "  " + MODES[slabMode] + " " + fmt(slabMm) + " mm";
            return s;
        }
    }

    final class Prov3D extends Base {
        public int count() { return 1; }
        public RawImage image(int i) throws Exception {
            if (r3img == null) throw new Exception("Rendering 3D…");
            return r3img;
        }
        public double[] orientation(int i) { return null; }
        public String seriesName() { return "3D"; }
        public String sliceInfo(int i) { return R3[r3mode] + (r3mode == 0 ? "" : " (volume rendering)"); }
    }

    final class CprProv extends Base {
        double[] up = {0, 0, 1};
        public int count() { return CPR_PAGES; }
        public RawImage image(int i) throws Exception {
            if (curvePts.size() < 2) throw new Exception("Tap at least two points on a plane to build the curve.");
            double s = Math.max(vol.minSp, 0.5);
            return vol.curved(curvePts, up, s, (i - CPR_PAGES / 2) * s, slabMm, slabMode);
        }
        public double[] orientation(int i) { return null; }
        public String seriesName() { return "Curved MPR"; }
        public String sliceInfo(int i) {
            double s = Math.max(vol.minSp, 0.5);
            return String.format(Locale.ROOT, "Offset %+.1f mm", (i - CPR_PAGES / 2) * s) + (slabMode == Volume.THIN ? "" : "  " + MODES[slabMode] + " " + fmt(slabMm) + " mm");
        }
    }
}
