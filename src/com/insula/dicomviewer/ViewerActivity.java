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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ViewerActivity extends BaseActivity implements DicomView.Listener {
    static final String[][] PRESETS = {
            {"Brain", "40", "80"}, {"Subdural", "75", "215"}, {"Stroke", "40", "40"}, {"Temporal bone", "600", "2800"},
            {"Lung", "-600", "1500"}, {"Mediastinum", "50", "350"}, {"Abdomen", "40", "400"}, {"Liver", "60", "150"},
            {"Bone", "400", "1800"}, {"Angio", "300", "600"}};
    static final int[] FPS = {5, 10, 15, 24, 30};
    static final int[][] LAYOUTS = {{1, 1}, {2, 1}, {1, 2}, {3, 1}, {1, 3}, {2, 2}};
    static final String[] LAYOUT_ICONS = {"lay1", "lay2v", "lay2h", "lay3v", "lay3h", "lay4"};

    Library.Study study;
    final List<Library.Series> seriesList = new ArrayList<>();
    final DicomView[] views = new DicomView[4];
    final FrameLayout[] ports = new FrameLayout[4];
    final TextView[] headers = new TextView[4];
    int layout = 0, active = 0, fps = 10, primaryTool = DicomView.T_SCROLL, tool = DicomView.T_SCROLL;
    boolean link = true, loop, crossRefs = true, showAnn = true, showMeas = true, teacher, syncing;
    LinearLayout grid, strip;
    View palette;
    boolean railOpen;
    final ImageButton[] pens = new ImageButton[4];
    /** Per-viewport offsets (mm) that line up studies whose coordinates differ; see syncFrom(). */
    final double[] align = new double[4];
    final boolean[] wlTouched = new boolean[4];
    final Map<String, LinearLayout> quickItems = new HashMap<>();
    LinearLayout quick;
    Switch linkSwitch;
    final Runnable saveWl = new Runnable() { public void run() { saveWindows(); } };
    MeasureBar mbar;
    TextView title, fpsLabel;
    android.widget.SeekBar fpsBar;
    Ui.Sheet drawer;
    Ui.Segmented toolSeg, layoutSeg;
    Switch loopSwitch;
    final List<FrameLayout> thumbs = new ArrayList<>();
    final Handler h = new Handler(Looper.getMainLooper());
    ProgressDialog pd;

    final Runnable cine = new Runnable() {
        public void run() {
            if (!loop) return;
            DicomView v = views[active];
            if (v.count() > 1) v.setIndex((v.index + 1) % v.count());
            h.postDelayed(this, 1000 / Math.max(1, fps));
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        study = Library.study(getIntent().getStringExtra("study"));
        String startSeries = getIntent().getStringExtra("seriesUid");
        if (study == null) {
            String[] uids = getIntent().getStringArrayExtra("series");
            Library.Series s = uids != null && uids.length > 0 ? Library.series(uids[0]) : null;
            if (s != null) { study = s.study; startSeries = s.uid; }
        }
        if (study == null) { finish(); return; }
        for (Library.Series s : study.series) if (!s.slices().isEmpty()) seriesList.add(s);
        teacher = Ui.prefs(this).getBoolean("teacher", false);
        fps = Ui.prefs(this).getInt("fps", 10);

        FrameLayout root = new FrameLayout(this);
        LinearLayout main = Ui.col(this);
        main.setBackgroundColor(Ui.BAR);
        root.addView(main);

        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        title = Ui.title(this, "");
        title.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
        title.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { MainActivity.studyInfo(ViewerActivity.this, study, teacher); } });
        Ui.tooltip(title, "Study details");
        top.addView(title, Ui.wrapWeight(1));
        top.addView(Ui.icon(this, "share", new View.OnClickListener() { public void onClick(View v) { shareMenu(); } }));
        top.addView(Ui.icon(this, "menu", new View.OnClickListener() { public void onClick(View v) { drawer.open(); } }));
        main.addView(top);

        grid = Ui.col(this);
        grid.setBackgroundColor(Ui.BG);
        int gp = Ui.dp(this, 5);
        grid.setPadding(gp, gp, gp, gp);
        main.addView(grid, Ui.vweight(1));

        mbar = new MeasureBar(this, new MeasureBar.Host() {
            public DicomView active() { return views[active]; }
            public void selectTool(int t) { ViewerActivity.this.selectTool(t); }
            public void closeBar() { showPalette(false); }
            public void toggleKey() { ViewerActivity.this.toggleKey(); }
            public boolean isKey() { DicomView v = views[active]; return v.prov != null && v.prov.keyImages().contains(v.index); }
        }, true, true, true);
        palette = mbar.tools;

        main.addView(Ui.hscroll(this, buildQuickBar()));

        strip = Ui.row(this);
        int sp = Ui.dp(this, 8);
        strip.setPadding(sp, Ui.dp(this, 2), sp, sp);
        HorizontalScrollView ss = Ui.hscroll(this, strip);
        ss.setBackgroundColor(Ui.BAR);
        main.addView(ss);

        for (int k = 0; k < 4; k++) buildPort(k);

        int w = Math.min(Ui.dp(this, 380), (int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
        drawer = new Ui.Sheet(this, root, true, w);
        buildDrawer();
        setContentView(root);

        Library.Series first = startSeries != null ? Library.series(startSeries) : (seriesList.isEmpty() ? null : seriesList.get(0));
        if (first != null && !first.slices().isEmpty()) load(0, first);
        buildStrip();
        applyLayout(0);
        selectTool(DicomView.T_SCROLL);
        setActive(0);
        updateTitle();
        Library.ImageInfo fi = first != null ? first.first() : null;
        if (fi != null && fi.frameTime > 0) setFps((int) Math.max(1, Math.min(60, Math.round(1000 / fi.frameTime))));
        if (!Ui.prefs(this).getBoolean("viewer_tips_v2", false)) showTips(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (study == null || strip == null) return;
        List<Library.Series> now = new ArrayList<>();
        for (Library.Series s : study.series) if (!s.slices().isEmpty()) now.add(s);
        if (now.size() != seriesList.size()) {
            seriesList.clear();
            seriesList.addAll(now);
            buildStrip();
        }
    }

    @Override protected void onPause() { super.onPause(); if (loop && loopSwitch != null) loopSwitch.setChecked(false); }

    @Override protected boolean handleBack() {
        if (tips != null) { dismissTips(); return true; }
        if (drawer != null && drawer.open) { drawer.close(); return true; }
        if (railOpen) { showPalette(false); return true; }
        return false;
    }

    // ---------------- construction ----------------
    void buildPort(final int k) {
        FrameLayout port = new FrameLayout(this);
        DicomView v = new DicomView(this);
        v.listener = this;
        v.showBorder = true;
        v.topInset = Ui.dp(this, 40);
        v.emptyText = "Tap the series name above to choose a series.";
        port.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView hdr = Ui.text(this, "Choose series", 16, Ui.TEXT);
        hdr.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hdr.setShadowLayer(4, 0, 0, 0xFF000000);
        hdr.setSingleLine(true);
        hdr.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Icons chev = new Icons("chevron", Ui.TEXT);
        chev.setBounds(0, 0, Ui.dp(this, 22), Ui.dp(this, 22));
        hdr.setCompoundDrawables(null, null, chev, null);
        hdr.setCompoundDrawablePadding(Ui.dp(this, 6));
        hdr.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10));
        hdr.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { setActive(k); pickSeries(); } });
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.gravity = Gravity.TOP | Gravity.START;
        hl.rightMargin = Ui.dp(this, 56);
        port.addView(hdr, hl);
        ImageButton pen = Ui.icon(this, "pencil", new View.OnClickListener() {
            public void onClick(View x) { boolean wasHere = railOpen && active == k; setActive(k); showPalette(!wasHere); }
        });
        pens[k] = pen;
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
        pl.gravity = Gravity.TOP | Gravity.END;
        pl.setMargins(0, Ui.dp(this, 2), Ui.dp(this, 4), 0);
        port.addView(pen, pl);
        views[k] = v; ports[k] = port; headers[k] = hdr;
    }

    void buildDrawer() {
        LinearLayout c = drawer.content;
        LinearLayout hdr = Ui.row(this);
        hdr.addView(Ui.spacer(this));
        hdr.addView(Ui.icon(this, "close", new View.OnClickListener() { public void onClick(View v) { drawer.close(); } }));
        c.addView(hdr);

        c.addView(Ui.heading(this, "Tools"));
        toolSeg = new Ui.Segmented(this, new String[]{"layers", "brightness", "ruler"}, new Ui.Segmented.OnPick() {
            public void pick(int i) {
                if (i == 0) { primaryTool = DicomView.T_SCROLL; showPalette(false); updateQuick(); }
                else if (i == 1) { primaryTool = DicomView.T_WL; showPalette(false); updateQuick(); }
                else { drawer.close(); showPalette(true); }
            }
        });
        c.addView(toolSeg.view);
        c.addView(Ui.text(this, "Scroll images, adjust brightness and contrast, or measure. Pinch zooms and two fingers pan with any tool.", 13, Ui.SUB));
        linkSwitch = Ui.switchRow(this, c, "link", "Link", link, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { link = on; if (on) syncFrom(views[active]); updateQuick(); }
        });
        Ui.actionRow(this, c, "align", "Align linked viewports here", new View.OnClickListener() { public void onClick(View v) { drawer.close(); alignHere(); } });
        loopSwitch = Ui.switchRow(this, c, "loop", "Loop", false, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { loop = on; h.removeCallbacks(cine); if (on) h.post(cine); updateQuick(); }
        });
        fpsBar = Ui.slider(this, c, "speed", 0, "Loop speed", 1, 60, fps, "fps", new Ui.OnValue() { public void value(int v) { setFps(v); } });
        c.addView(Ui.divider(this));

        c.addView(Ui.heading(this, "Layouts"));
        layoutSeg = new Ui.Segmented(this, LAYOUT_ICONS, new Ui.Segmented.OnPick() { public void pick(int i) { applyLayout(i); } });
        c.addView(layoutSeg.view);

        c.addView(Ui.heading(this, "Image"));
        LinearLayout chips = Ui.row(this);
        chips.addView(Ui.tile(this, "sliders", "Presets", new View.OnClickListener() { public void onClick(View v) { presets(); } }));
        chips.addView(Ui.tile(this, "contrast", "Invert", new View.OnClickListener() { public void onClick(View v) { DicomView d = views[active]; d.invert = !d.invert; d.render(); d.invalidate(); } }));
        chips.addView(Ui.tile(this, "rotate", "Rotate", new View.OnClickListener() { public void onClick(View v) { DicomView d = views[active]; d.rot = (d.rot + 90) % 360; d.invalidate(); } }));
        chips.addView(Ui.tile(this, "fliph", "Flip H", new View.OnClickListener() { public void onClick(View v) { DicomView d = views[active]; d.flipH = !d.flipH; d.invalidate(); } }));
        chips.addView(Ui.tile(this, "flipv", "Flip V", new View.OnClickListener() { public void onClick(View v) { DicomView d = views[active]; d.flipV = !d.flipV; d.invalidate(); } }));
        c.addView(Ui.hscroll(this, chips));
        c.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        c.addView(Ui.divider(this));

        Ui.switchRow(this, c, "annotations", "Annotations", showAnn, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { showAnn = on; for (DicomView d : views) { d.showAnnotations = on; d.invalidate(); } }
        });
        Ui.switchRow(this, c, "ruler", "Measures", showMeas, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { showMeas = on; for (DicomView d : views) { d.showMeasures = on; d.invalidate(); } }
        });
        Ui.switchRow(this, c, "crossref", "Cross-references", crossRefs, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { crossRefs = on; updateRefs(); }
        });
        Ui.switchRow(this, c, "teacher", "Teacher mode", teacher, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                teacher = on;
                Ui.prefs(ViewerActivity.this).edit().putBoolean("teacher", on).apply();
                updateTitle();
                if (on) Ui.toast(ViewerActivity.this, "Teacher mode hides the patient's name and IDs on screen and in exports.");
            }
        });
        Ui.actionRow(this, c, "cube", "MPR (3 planes)", new View.OnClickListener() { public void onClick(View v) { drawer.close(); mpr(activeSeries()); } });
        Ui.actionRow(this, c, "tags", "DICOM tags", new View.OnClickListener() { public void onClick(View v) { drawer.close(); tags(); } });
        Ui.actionRow(this, c, "book", "Guide to every tool", new View.OnClickListener() { public void onClick(View v) { drawer.close(); startActivity(new Intent(ViewerActivity.this, GuideActivity.class)); } });

        Button reset = Ui.pill(this, "Reset", "reset", new View.OnClickListener() {
            public void onClick(View v) {
                resetAll();
                drawer.close();
            }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 28);
        c.addView(reset, rl);
    }

    void buildStrip() {
        strip.removeAllViews();
        thumbs.clear();
        int s = Ui.dp(this, 78);
        for (final Library.Series se : seriesList) {
            FrameLayout f = new FrameLayout(this);
            f.setTag(se.uid);
            final ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF000000);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            int in = Ui.dp(this, 3);
            il.setMargins(in, in, in, in);
            f.addView(iv, il);
            Bitmap bm = Library.thumbnail(se, new Library.ThumbCallback() {
                public void done() { h.post(new Runnable() { public void run() { iv.setImageBitmap(Library.thumbs.get(se.uid)); } }); }
            });
            iv.setImageBitmap(bm);
            TextView badge = Ui.text(this, String.valueOf(se.slices().size()), 13, Ui.TEXT);
            badge.setBackgroundColor(0xDD303033);
            badge.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.gravity = Gravity.BOTTOM | Gravity.END;
            bl.setMargins(0, 0, in, in);
            f.addView(badge, bl);
            f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { load(active, se); } });
            f.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { seriesOptions(se); return true; } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            strip.addView(f, lp);
            thumbs.add(f);
        }
        updateStrip();
    }

    void updateStrip() {
        String cur = activeSeries() == null ? "" : activeSeries().uid;
        for (FrameLayout f : thumbs) {
            boolean sel = cur.equals(f.getTag());
            GradientDrawable g = Ui.rounded(0xFF000000, Ui.dp(this, 5));
            g.setStroke(Ui.dp(this, sel ? 3 : 1), sel ? Ui.ACCENT : Ui.LINE);
            f.setBackground(g);
        }
    }

    // ---------------- state ----------------
    int visibleCount() { return LAYOUTS[layout][0] * LAYOUTS[layout][1]; }

    Library.Series seriesOf(DicomView v) {
        return v.prov instanceof SliceProvider.SeriesProvider ? ((SliceProvider.SeriesProvider) v.prov).series : null;
    }

    Library.Series activeSeries() { return seriesOf(views[active]); }

    void load(int k, Library.Series se) {
        if (se.slices().isEmpty()) return;
        views[k].setProvider(new SliceProvider.SeriesProvider(se), 0);
        views[k].setTool(tool);
        wlTouched[k] = false;
        double[] w = rememberWl() ? Store.loadWindow(this, se.uid) : null;
        if (w != null) views[k].setWindow(w[0], w[1]);
        autoAlign(k);
        views[k].showAnnotations = showAnn;
        views[k].showMeasures = showMeas;
        updateHeaders();
        updateStrip();
        if (link) syncFrom(views[active]);
        updateRefs();
    }

    void updateHeaders() {
        for (int k = 0; k < 4; k++) {
            Library.Series se = seriesOf(views[k]);
            String t = views[k].prov == null ? "Choose series" : views[k].prov.seriesName();
            boolean other = se != null && se.study != study;
            // A series from another study (a prior or later exam) shows its date, in amber.
            if (other) t += "  ·  " + Library.fmtDateLong(se.study.date);
            headers[k].setText(t);
            headers[k].setTextColor(other ? Ui.WARN : Ui.TEXT);
        }
    }

    void updateTitle() { title.setText(study.displayName(teacher)); }

    void applyLayout(int l) {
        layout = l;
        if (layoutSeg != null && layoutSeg.selected != l) layoutSeg.select(l);
        int rows = LAYOUTS[l][0], cols = LAYOUTS[l][1], n = rows * cols;
        grid.removeAllViews();
        for (FrameLayout p : ports) if (p.getParent() != null) ((ViewGroup) p.getParent()).removeView(p);
        // Fill empty viewports with series not shown yet, in strip order.
        for (int k = 0; k < n; k++) {
            if (views[k].prov != null) continue;
            for (Library.Series se : seriesList) {
                boolean used = false;
                for (int j = 0; j < n; j++) if (seriesOf(views[j]) == se) used = true;
                if (!used) { load(k, se); break; }
            }
        }
        int k = 0, m = Ui.dp(this, 3);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = Ui.row(this);
            for (int c = 0; c < cols; c++) {
                LinearLayout.LayoutParams lp = Ui.weight(1);
                lp.setMargins(m, m, m, m);
                row.addView(ports[k++], lp);
            }
            grid.addView(row, Ui.vweight(1));
        }
        if (active >= n) setActive(0);
        updateHeaders();
        updateRefs();
        updateQuick();
    }

    void setActive(int i) {
        active = i;
        for (int k = 0; k < 4; k++) { views[k].active = k == i; views[k].invalidate(); }
        if (mbar != null) mbar.onSelection(views[i], views[i].selected);
        if (railOpen) attachRail(i);
        updateStrip();
        updateKey();
    }

    void selectTool(int t) {
        tool = t;
        mbar.setTool(t);
        for (DicomView v : views) v.setTool(t);
        updateQuick();
    }

    /** Places the tool rail right under the pencil of viewport k, and the selection bar near its bottom. */
    void attachRail(int k) {
        View sel = mbar.selBar;
        if (palette.getParent() != null) ((ViewGroup) palette.getParent()).removeView(palette);
        if (sel.getParent() != null) ((ViewGroup) sel.getParent()).removeView(sel);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.gravity = Gravity.TOP | Gravity.END;
        rl.setMargins(0, Ui.dp(this, 56), Ui.dp(this, 8), Ui.dp(this, 64));
        ports[k].addView(palette, rl);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.gravity = Gravity.BOTTOM;
        sl.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 70), Ui.dp(this, 70));
        ports[k].addView(sel, sl);
        for (int j = 0; j < 4; j++) {
            boolean here = railOpen && j == k;
            pens[j].setImageDrawable(new Icons(here ? "check" : "pencil", Ui.TEXT));
            Ui.tooltip(pens[j], here ? "Close tools" : "Measure and annotate");
            if (here) Ui.setToolOn(pens[j], true); else pens[j].setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), null, null));
        }
    }

    void detachRail() {
        if (palette.getParent() != null) ((ViewGroup) palette.getParent()).removeView(palette);
        View sel = mbar.selBar;
        if (sel.getParent() != null) ((ViewGroup) sel.getParent()).removeView(sel);
        for (int j = 0; j < 4; j++) {
            pens[j].setImageDrawable(new Icons("pencil", Ui.TEXT));
            Ui.tooltip(pens[j], "Measure and annotate");
            pens[j].setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), null, null));
        }
    }

    void showPalette(boolean on) {
        railOpen = on;
        if (on) attachRail(active); else detachRail();
        updateQuick();
        if (on) {
            if (tool < DicomView.T_LENGTH) selectTool(DicomView.T_LENGTH);
            toolSeg.select(2);
            updateKey();
        } else {
            selectTool(primaryTool);
            toolSeg.select(primaryTool == DicomView.T_WL ? 1 : 0);
        }
    }

    void updateKey() { if (mbar != null) mbar.updateKey(); }

    void toggleKey() {
        DicomView v = views[active];
        if (v.prov == null) return;
        boolean was = v.prov.keyImages().contains(v.index);
        if (was) v.prov.keyImages().remove(v.index); else v.prov.keyImages().add(v.index);
        v.invalidate();
        updateKey();
        Ui.toast(this, was ? "Key image mark removed." : "Marked as key image. Export key images as PDF from Share.");
    }

    public void onSelection(DicomView v, DicomView.Ann a) { if (mbar != null) mbar.onSelection(v, a); }

    void setFps(int f) {
        fps = Math.max(1, Math.min(60, f));
        Ui.prefs(this).edit().putInt("fps", f).apply();
        if (fpsBar != null && fpsBar.getProgress() != fps - 1) fpsBar.setProgress(fps - 1);
    }

    // ---------------- DicomView.Listener ----------------
    public void onActivated(DicomView v) { for (int k = 0; k < 4; k++) if (views[k] == v && k != active) setActive(k); }

    public void onIndexChanged(DicomView v) {
        if (v == views[active]) {
            Library.Series se = seriesOf(v);
            if (se != null) Library.prefetch(se.slices(), v.index, v.scrollDir);
            updateKey();
            if (link && !syncing) syncFrom(v);
        }
        if (!syncing) updateRefs();
    }

    public void onWindowChanged(DicomView v) { markWl(v); }
    public void onImageReady(DicomView v) { updateRefs(); }
    public void onLongPressImage(DicomView v) { presets(); }
    public void onInteractionEnd(DicomView v) { }
    public void onImageTap(DicomView v, float x, float y) { }

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

    int viewIndex(DicomView v) { for (int k = 0; k < 4; k++) if (views[k] == v) return k; return -1; }

    /**
     * Moves linked viewports to the slice matching the source. For parallel stacks with positions, the target is the
     * slice nearest the source position plus the viewports' alignment offset (zero within one study; set from the
     * stack centres or by "Align here" across studies). Otherwise indices are matched proportionally.
     */
    void syncFrom(DicomView src) {
        if (src.prov == null) return;
        int si = viewIndex(src);
        double[] sp = src.prov.position(src.index), so = src.prov.orientation(src.index);
        syncing = true;
        for (int k = 0; k < visibleCount(); k++) {
            DicomView o = views[k];
            if (o == src || o.prov == null || o.count() == 0) continue;
            int target;
            double[] oo = o.prov.orientation(0);
            if (sp != null && so != null && oo != null && o.prov.position(0) != null) {
                double[] n = Volume.norm(Library.cross(oo));
                // Different planes (for example axial vs sagittal) are shown with cross-references instead.
                if (Math.abs(Library.dot(Volume.norm(Library.cross(so)), n)) < 0.7) continue;
                target = Compare.nearest(o.prov, n, Library.dot(sp, n) + align[k] - align[si]);
            } else {
                target = Math.round(src.index * (o.count() - 1) / (float) Math.max(1, src.count() - 1));
            }
            if (target >= 0 && target != o.index) o.setIndex(target);
        }
        syncing = false;
        updateRefs();
    }

    /** Sets viewport k's offset so its stack lines up with another shown viewport (centres match across studies). */
    void autoAlign(int k) {
        align[k] = 0;
        DicomView v = views[k];
        if (v.prov == null || v.prov.orientation(0) == null) return;
        Library.Series sk = seriesOf(v);
        for (int j = 0; j < visibleCount(); j++) {
            if (j == k || views[j].prov == null) continue;
            Library.Series sj = seriesOf(views[j]);
            String fk = v.prov.frameOfRef(), fj = views[j].prov.frameOfRef();
            boolean sameSpace = sk != null && sj != null && sk.study == sj.study && fk != null && fk.equals(fj);
            if (sameSpace) { align[k] = align[j]; return; }
            double[] n = Volume.norm(Library.cross(v.prov.orientation(0)));
            double ck = Compare.centre(v.prov, n), cj = Compare.centre(views[j].prov, n);
            if (!Double.isNaN(ck) && !Double.isNaN(cj)) { align[k] = align[j] + (ck - cj); return; }
        }
    }

    /** Treats the slices currently shown in all linked viewports as the same anatomy. */
    void alignHere() {
        DicomView src = views[active];
        if (src.prov == null || visibleCount() < 2) { Ui.toast(this, "Show two or more series side by side first, for example with Compare."); return; }
        double[] sp = src.prov.position(src.index);
        int changed = 0;
        for (int k = 0; k < visibleCount(); k++) {
            DicomView o = views[k];
            if (k == active || o.prov == null) continue;
            double[] op = o.prov.position(o.index), oo = o.prov.orientation(o.index);
            if (sp == null || op == null || oo == null) continue;
            double[] n = Volume.norm(Library.cross(oo));
            align[k] = align[active] + (Library.dot(op, n) - Library.dot(sp, n));
            changed++;
        }
        if (!link) { link = true; if (linkSwitch != null) linkSwitch.setChecked(true); }
        Ui.toast(this, changed > 0 ? "Aligned. Linked viewports now scroll together from these slices." : "These series have no position data; they're linked by slice number instead.");
        updateQuick();
    }

    // ---------------- comparison with other studies ----------------
    void compare() {
        List<Library.Study> all;
        synchronized (Library.class) { all = new ArrayList<>(Library.studies); }
        final List<Library.Study> rel = Compare.related(study, all);
        if (rel.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Compare")
                    .setMessage("There are no other studies of this patient in your library.\n\nImport or download a prior study (same patient ID), then tap Compare again. You can also show any series with the series name › From another study.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (rel.size() == 1) { startCompare(rel.get(0)); return; }
        String[] labels = new String[rel.size()];
        for (int i = 0; i < rel.size(); i++) {
            Library.Study o = rel.get(i);
            boolean prior = (o.date + o.time).compareTo(study.date + study.time) < 0;
            labels[i] = Library.fmtDateLong(o.date) + (prior ? "  (prior)" : "  (later)") + "\n" + o.modalities() + "   " + (o.desc.isEmpty() ? "" : o.desc);
        }
        new AlertDialog.Builder(this).setTitle("Compare with").setItems(labels, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { startCompare(rel.get(w)); }
        }).show();
    }

    void startCompare(Library.Study other) {
        Library.Series cur = activeSeries();
        if (cur == null && !seriesList.isEmpty()) cur = seriesList.get(0);
        if (cur == null) return;
        Library.Series match = Compare.bestMatch(cur, other);
        if (match == null) { Ui.toast(this, "That study has no images to compare."); return; }
        if (seriesOf(views[0]) != cur) load(0, cur);
        views[1].setProvider(null, 0);
        applyLayout(2);
        load(1, match);
        setActive(0);
        if (!link) { link = true; if (linkSwitch != null) linkSwitch.setChecked(true); }
        syncFrom(views[0]);
        updateHeaders();
        boolean prior = (other.date + other.time).compareTo(study.date + study.time) < 0;
        Ui.toast(this, "Comparing with the " + (prior ? "prior" : "later") + " study of " + Library.fmtDateLong(other.date)
                + ". Scrolling is linked. If the anatomy doesn't line up, scroll one side to match and tap Align.");
    }

    // ---------------- remembered window settings ----------------
    boolean rememberWl() { return Ui.prefs(this).getBoolean("remember_wl", true); }

    void markWl(DicomView v) {
        int k = viewIndex(v);
        if (k < 0) return;
        wlTouched[k] = true;
        h.removeCallbacks(saveWl);
        h.postDelayed(saveWl, 700);
    }

    void saveWindows() {
        if (!rememberWl()) return;
        for (int k = 0; k < 4; k++) {
            Library.Series se = seriesOf(views[k]);
            if (se != null && wlTouched[k]) { Store.saveWindow(this, se.uid, views[k].wc, views[k].ww); wlTouched[k] = false; }
        }
    }

    // ---------------- quick bar ----------------
    View buildQuickBar() {
        quick = Ui.row(this);
        quick.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 2));
        qItem("scroll", "layers", "Scroll", new View.OnClickListener() { public void onClick(View v) { primaryTool = DicomView.T_SCROLL; showPalette(false); } });
        qItem("window", "brightness", "Window", new View.OnClickListener() { public void onClick(View v) { primaryTool = DicomView.T_WL; showPalette(false); } });
        qItem("measure", "ruler", "Measure", new View.OnClickListener() { public void onClick(View v) { showPalette(!railOpen); } });
        qItem("presets", "sliders", "Presets", new View.OnClickListener() { public void onClick(View v) { presets(); } });
        qItem("play", "play", "Play", new View.OnClickListener() { public void onClick(View v) { if (loopSwitch != null) loopSwitch.setChecked(!loop); } });
        qItem("compare", "compare", "Compare", new View.OnClickListener() { public void onClick(View v) { compare(); } });
        qItem("layout", "lay4", "Layout", new View.OnClickListener() { public void onClick(View v) { pickLayout(); } });
        qItem("link", "link", "Link", new View.OnClickListener() { public void onClick(View v) { if (linkSwitch != null) linkSwitch.setChecked(!link); } });
        qItem("align", "align", "Align", new View.OnClickListener() { public void onClick(View v) { alignHere(); } });
        qItem("reset", "reset", "Reset", new View.OnClickListener() { public void onClick(View v) { resetAll(); } });
        qItem("more", "menu", "More", new View.OnClickListener() { public void onClick(View v) { drawer.open(); } });
        return quick;
    }

    void qItem(String key, String icon, String label, View.OnClickListener l) {
        LinearLayout t = Ui.col(this);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 5));
        ImageView iv = Ui.iconView(this, icon, 22, Ui.TEXT);
        t.addView(iv);
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
        setQuickOn(key, false);
    }

    void setQuickOn(String key, boolean on) {
        LinearLayout t = quickItems.get(key);
        if (t == null) return;
        t.setBackground(Ui.ripple(Ui.rounded(on ? Ui.BTN_ON : 0x00000000, Ui.dp(this, 10))));
        ((TextView) t.getChildAt(1)).setTextColor(on ? Ui.TEXT : Ui.SUB);
    }

    void updateQuick() {
        if (quick == null) return;
        setQuickOn("scroll", !railOpen && tool == DicomView.T_SCROLL);
        setQuickOn("window", !railOpen && tool == DicomView.T_WL);
        setQuickOn("measure", railOpen);
        setQuickOn("play", loop);
        LinearLayout p = quickItems.get("play");
        ((ImageView) p.getChildAt(0)).setImageDrawable(new Icons(loop ? "pause" : "play", Ui.TEXT));
        ((TextView) p.getChildAt(1)).setText(loop ? "Pause" : "Play");
        boolean multi = visibleCount() > 1;
        quickItems.get("link").setVisibility(multi ? View.VISIBLE : View.GONE);
        quickItems.get("align").setVisibility(multi ? View.VISIBLE : View.GONE);
        setQuickOn("link", link);
    }

    void pickLayout() {
        String[] names = {"Single", "Two stacked", "Two side by side", "Three stacked", "Three in a row", "Grid of four"};
        Ui.choices(this, "Layout", null, LAYOUT_ICONS, names, new Ui.OnChoice() {
            public void choose(int i) { applyLayout(i); }
        }).show();
    }

    void resetAll() {
        for (int k = 0; k < 4; k++) {
            DicomView d = views[k];
            d.invert = false;
            d.resetView();
            d.defaultWindow();
            Library.Series se = seriesOf(d);
            if (se != null) Store.clearWindow(this, se.uid);
            wlTouched[k] = false;
        }
        Ui.toast(this, "View reset. Window settings are back to the file's defaults.");
    }

    // ---------------- first-use tips ----------------
    View tips;

    void showTips(FrameLayout root) {
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0xCC000000);
        dim.setClickable(true);
        LinearLayout card = Ui.col(this);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 18)));
        int p = Ui.dp(this, 20);
        card.setPadding(p, p, p, Ui.dp(this, 12));
        TextView t = Ui.text(this, "Quick tips", 20, Ui.TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(t);
        String[][] rows = {
                {"layers", "Swipe up or down to scroll. Flick to glide through the series."},
                {"crossref", "Drag along the right edge to jump anywhere in the series."},
                {"move", "Pinch to zoom, drag with two fingers to pan, double-tap to fit."},
                {"brightness", "Window tool: drag sideways for contrast, up or down for brightness. Your setting is remembered."},
                {"sliders", "Long-press the image for window presets."},
                {"pencil", "The pencil opens measuring tools right beside it."},
                {"compare", "Compare shows a prior study side by side, scrolling together."}};
        for (String[] r : rows) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 10), 0, 0);
            row.addView(Ui.iconView(this, r[0], 24, Ui.ACCENT));
            TextView tv = Ui.text(this, r[1], 14.5f, Ui.TEXT);
            tv.setPadding(Ui.dp(this, 14), 0, 0, 0);
            row.addView(tv, Ui.wrapWeight(1));
            card.addView(row);
        }
        Button ok = Ui.pill(this, "Got it", null, new View.OnClickListener() { public void onClick(View v) { dismissTips(); } });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
        ol.topMargin = Ui.dp(this, 16);
        card.addView(ok, ol);
        ScrollView sv = new ScrollView(this);
        sv.addView(card);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Math.min(Ui.dp(this, 420), getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32)), ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.gravity = Gravity.CENTER;
        dim.addView(sv, cl);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        tips = dim;
    }

    void dismissTips() {
        if (tips != null && tips.getParent() != null) ((ViewGroup) tips.getParent()).removeView(tips);
        tips = null;
        Ui.prefs(this).edit().putBoolean("viewer_tips_v2", true).apply();
    }

    /** Draws where each other viewport's current slice cuts through this viewport's image. */
    void updateRefs() {
        int n = visibleCount();
        for (int k = 0; k < 4; k++) {
            DicomView t = views[k];
            t.refLines.clear();
            if (!crossRefs || n < 2 || k >= n || t.prov == null || t.img == null) { t.invalidate(); continue; }
            double[] tp = t.prov.position(t.index), to = t.prov.orientation(t.index);
            String fr = t.prov.frameOfRef();
            double cs = t.img.colSp, rs = t.img.rowSp;
            if (tp == null || to == null || fr == null || cs <= 0 || rs <= 0) { t.invalidate(); continue; }
            for (int j = 0; j < n; j++) {
                DicomView o = views[j];
                if (j == k || o.prov == null || o.count() == 0 || !fr.equals(o.prov.frameOfRef())) continue;
                double[] op = o.prov.position(o.index), oo = o.prov.orientation(o.index);
                if (op == null || oo == null) continue;
                double[] nn = Library.cross(oo);
                double a = cs * (to[0] * nn[0] + to[1] * nn[1] + to[2] * nn[2]);
                double b = rs * (to[3] * nn[0] + to[4] * nn[1] + to[5] * nn[2]);
                double d = (tp[0] - op[0]) * nn[0] + (tp[1] - op[1]) * nn[1] + (tp[2] - op[2]) * nn[2];
                float[] seg = clip(a, b, d, t.img.w, t.img.h);
                if (seg != null) t.refLines.add(seg);
            }
            t.invalidate();
        }
    }

    static float[] clip(double a, double b, double d, int w, int h) {
        if (Math.abs(a) < 1e-6 && Math.abs(b) < 1e-6) return null;
        double i0 = -0.5, i1 = w - 0.5, j0 = -0.5, j1 = h - 0.5;
        List<double[]> pts = new ArrayList<>();
        if (Math.abs(b) > 1e-9) for (double i : new double[]{i0, i1}) { double j = -(a * i + d) / b; if (j >= j0 && j <= j1) pts.add(new double[]{i, j}); }
        if (Math.abs(a) > 1e-9) for (double j : new double[]{j0, j1}) { double i = -(b * j + d) / a; if (i >= i0 && i <= i1) pts.add(new double[]{i, j}); }
        if (pts.size() < 2) return null;
        double[] p = pts.get(0), q = null;
        for (double[] x : pts) if (Math.hypot(x[0] - p[0], x[1] - p[1]) > 0.5) { q = x; break; }
        if (q == null) return null;
        return new float[]{(float) p[0] + 0.5f, (float) p[1] + 0.5f, (float) q[0] + 0.5f, (float) q[1] + 0.5f};
    }

    // ---------------- actions ----------------
    void pickSeries() {
        final List<Library.Series> opts = new ArrayList<>(seriesList);
        List<String> labels = new ArrayList<>();
        for (Library.Series s : opts) labels.add(s.number + "   " + (s.desc.isEmpty() ? s.modality : s.desc) + "   (" + s.slices().size() + ")");
        labels.add("From another study…");
        new AlertDialog.Builder(this).setTitle("Series").setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w < opts.size()) load(active, opts.get(w)); else pickOtherStudy();
            }
        }).show();
    }

    void pickOtherStudy() {
        final List<Library.Series> all = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Library.Series s : Library.allSeries()) {
            if (s.study == study || s.slices().isEmpty()) continue;
            all.add(s);
            labels.add((teacher ? "" : s.study.patientName + "   ") + Library.fmtDateLong(s.study.date) + "\n" + s.modality + "   " + s.label() + "   (" + s.slices().size() + ")");
        }
        if (all.isEmpty()) { Ui.toast(this, "There are no other studies in the library."); return; }
        new AlertDialog.Builder(this).setTitle("Compare with").setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { load(active, all.get(w)); }
        }).show();
    }

    void seriesOptions(final Library.Series se) {
        new AlertDialog.Builder(this).setTitle(se.label()).setItems(new String[]{"Show in selected viewport", "MPR (3 planes)", "DICOM tags", "Export anonymized series (ZIP)"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) load(active, se);
                else if (w == 1) mpr(se);
                else if (w == 2) { Library.ImageInfo i = se.first(); if (i != null) openTags(i.file); }
                else anon(se);
            }
        }).show();
    }

    void presets() {
        String[] names = new String[PRESETS.length + 2];
        names[0] = "Default (from file)";
        names[1] = "Full range";
        for (int i = 0; i < PRESETS.length; i++) names[i + 2] = PRESETS[i][0] + "   W " + PRESETS[i][2] + " / L " + PRESETS[i][1];
        new AlertDialog.Builder(this).setTitle("Window presets").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                DicomView v = views[active];
                if (w == 0) v.defaultWindow();
                else if (w == 1) v.autoWindow();
                else v.setWindow(Double.parseDouble(PRESETS[w - 2][1]), Double.parseDouble(PRESETS[w - 2][2]));
                markWl(v);
            }
        }).show();
    }

    void shareMenu() {
        Ui.choices(this, "Share", null,
                new String[]{"share", "download", "download", "report", "file"},
                new String[]{"Share image", "Save image as PNG", "Save image as JPEG", "Key images as PDF", "Anonymized series (ZIP)"},
                new Ui.OnChoice() {
                    public void choose(int i) {
                        if (i == 0) shareImage();
                        else if (i == 1) saveImage(false);
                        else if (i == 2) saveImage(true);
                        else if (i == 3) exportPdf();
                        else anon(activeSeries());
                    }
                }).show();
    }

    boolean noPhi() { return teacher || Ui.prefs(this).getBoolean("hidephi", false); }
    String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()); }

    void saveImage(boolean jpeg) {
        if (views[active].img == null) { Ui.toast(this, "No image to save."); return; }
        Bitmap b = views[active].snapshot(noPhi());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 95, bo);
        saveAs("insula_" + stamp() + (jpeg ? ".jpg" : ".png"), jpeg ? "image/jpeg" : "image/png", bo.toByteArray(), null);
    }

    void shareImage() {
        if (views[active].img == null) { Ui.toast(this, "No image to share."); return; }
        try {
            File f = new File(Library.exportDir, "insula_" + stamp() + ".png");
            FileOutputStream fo = new FileOutputStream(f);
            views[active].snapshot(noPhi()).compress(Bitmap.CompressFormat.PNG, 95, fo);
            fo.close();
            Store.log(this, "share", "Image shared", views[active].prov.seriesName() + ", image " + (views[active].index + 1), true);
            share(f, "image/png");
        } catch (Exception e) { Ui.toast(this, "Could not share: " + e.getMessage()); }
    }

    void exportPdf() {
        DicomView v = views[active];
        if (v.prov == null) return;
        List<Integer> idx = new ArrayList<>(v.prov.keyImages());
        Collections.sort(idx);
        if (idx.isEmpty()) { idx.add(v.index); Ui.toast(this, "No key images marked, so the current image is exported. Mark key images with the pencil tools."); }
        int keep = v.index;
        PdfDocument doc = new PdfDocument();
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(10);
        Library.Series se = activeSeries();
        int page = 1;
        for (int i : idx) {
            v.setIndexNow(i);
            Bitmap b = v.snapshot(noPhi());
            PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(595, 842, page++).create());
            Canvas c = pg.getCanvas();
            float y = 40;
            if (se != null) {
                if (!noPhi()) { c.drawText(se.study.patientName + "   " + se.study.patientId + "   " + se.study.ageSex(), 36, y, tp); y += 14; }
                c.drawText(Library.fmtDateLong(se.study.date) + "   " + se.study.desc, 36, y, tp); y += 14;
                c.drawText(se.modality + "   " + se.label() + "   Image " + (i + 1) + " of " + v.count(), 36, y, tp); y += 14;
            }
            float maxW = 523, maxH = 842 - y - 50;
            float s = Math.min(maxW / b.getWidth(), maxH / b.getHeight());
            int w = (int) (b.getWidth() * s), hh = (int) (b.getHeight() * s);
            c.drawBitmap(b, null, new Rect(36, (int) y + 8, 36 + w, (int) y + 8 + hh), new Paint(Paint.FILTER_BITMAP_FLAG));
            c.drawText("Insula DICOM Viewer — not for primary diagnosis", 36, 820, tp);
            doc.finishPage(pg);
            b.recycle();
        }
        v.setIndexNow(keep);
        try {
            File f = new File(Library.exportDir, "insula_key_images_" + stamp() + ".pdf");
            FileOutputStream fo = new FileOutputStream(f);
            doc.writeTo(fo);
            fo.close();
            doc.close();
            Store.log(this, "share", "Key images PDF", idx.size() + " page" + (idx.size() == 1 ? "" : "s"), true);
            saveOrShare(f.getName(), "application/pdf", f);
        } catch (Exception e) { Ui.toast(this, "PDF export failed: " + e.getMessage()); }
    }

    void tags() {
        SliceProvider p = views[active].prov;
        Library.SliceRef r = p == null ? null : p.ref(views[active].index);
        if (r != null) openTags(r.info.file);
    }

    void openTags(File f) {
        Intent i = new Intent(this, TagActivity.class);
        i.putExtra("path", f.getAbsolutePath());
        startActivity(i);
    }

    void mpr(Library.Series se) {
        if (se == null) return;
        Intent i = new Intent(this, MprActivity.class);
        i.putExtra("series", se.uid);
        startActivity(i);
    }

    void anon(final Library.Series se) {
        if (se == null) return;
        new AlertDialog.Builder(this).setTitle("Export anonymized series")
                .setMessage("Identifying fields and private tags are blanked. Text burned into the pixels is not removed.")
                .setPositiveButton("Export", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        pd = new ProgressDialog(ViewerActivity.this);
                        pd.setMessage("Anonymizing…");
                        pd.setCancelable(false);
                        pd.show();
                        new Thread() {
                            public void run() {
                                final File out = new File(Library.exportDir, "anonymized_" + stamp() + ".zip");
                                final boolean[] warn = {false};
                                int n = 0;
                                try { n = Anonymizer.writeZip(MainActivity.fileList(se.images), out, warn, null); } catch (Exception ignored) { }
                                final int fn = n;
                                Store.log(ViewerActivity.this, "share", "Anonymized series", fn > 0 ? se.label() + ", " + fn + " images" : "Failed", fn > 0);
                                h.post(new Runnable() {
                                    public void run() {
                                        if (pd != null) pd.dismiss();
                                        if (fn == 0) { Ui.toast(ViewerActivity.this, "Export failed."); return; }
                                        if (warn[0]) Ui.toast(ViewerActivity.this, "Warning: burned-in text may show patient details.");
                                        saveOrShare(out.getName(), "application/zip", out);
                                    }
                                });
                            }
                        }.start();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void guide() {
        new AlertDialog.Builder(this).setTitle("Gestures")
                .setMessage("Pinch: zoom\nTwo fingers: pan\nDouble tap: fit to screen\n\n"
                        + "One finger follows the tool chosen in the menu:\n"
                        + "Scroll: swipe up or down through images\n"
                        + "Brightness: swipe left/right for contrast (width), up/down for brightness (level)\n"
                        + "Measure: tap the pencil on a viewport, pick a tool, then drag on the image\n\n"
                        + "Tap the series name at the top of a viewport to change series. Tap a thumbnail to show that series in the selected viewport. Long-press a thumbnail for more options.")
                .setPositiveButton("Close", null).show();
    }
}
