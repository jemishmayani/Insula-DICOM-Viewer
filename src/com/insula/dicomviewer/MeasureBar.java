/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/** Icon toolbar for measuring and annotating, plus a bar for editing the selected measurement. */
final class MeasureBar {
    interface Host {
        DicomView active();
        void selectTool(int t);
        void closeBar();
        void toggleKey();
        boolean isKey();
    }

    final Context c;
    final Host host;
    final LinearLayout view, selBar;
    final View tools;
    final boolean vertical;
    final TextView selText;
    final ImageButton labelBtn, undoBtn;
    ImageButton keyBtn;
    final Map<Integer, ImageButton> btns = new HashMap<>();

    static final Object[][] TOOLS = {
            {DicomView.T_SELECT, "select", "Select and edit"},
            {DicomView.T_LENGTH, "length", "Length"},
            {DicomView.T_ANGLE, "angle", "Angle"},
            {DicomView.T_COBB, "cobb", "Cobb angle"},
            {DicomView.T_PTLINE, "ptline", "Point to line (midline shift, Chamberlain)"},
            {DicomView.T_ABC, "abc", "Hematoma volume (ABC/2)"},
            {DicomView.T_ELLIPSE, "ellipse", "Ellipse ROI (area, mean, SD)"},
            {DicomView.T_RECT, "rect", "Rectangle ROI (area, mean, SD)"},
            {DicomView.T_PROBE, "probe", "Pixel value / HU"},
            {DicomView.T_ARROW, "arrow", "Arrow with label"},
            {DicomView.T_ERASE, "eraser", "Eraser (tap a mark)"},
    };

    MeasureBar(Context c, Host host, boolean cobb, boolean keyImages) { this(c, host, cobb, keyImages, false); }

    /** @param vertical true for a floating rail next to the viewport's pencil button. */
    MeasureBar(final Context c, final Host host, boolean cobb, boolean keyImages, boolean vertical) {
        this.c = c;
        this.host = host;
        this.vertical = vertical;
        view = Ui.col(c);
        view.setBackgroundColor(Ui.BAR);

        selBar = Ui.row(c);
        selBar.setBackgroundColor(Ui.CARD);
        selBar.setPadding(Ui.dp(c, 14), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2));
        selText = Ui.text(c, "", 14.5f, Ui.TEXT);
        selText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        selText.setSingleLine(true);
        selText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        selBar.addView(selText, Ui.wrapWeight(1));
        labelBtn = Ui.icon(c, "label", "Edit label", new View.OnClickListener() { public void onClick(View v) { editLabel(); } });
        selBar.addView(labelBtn);
        selBar.addView(Ui.icon(c, "copy", "Duplicate", new View.OnClickListener() { public void onClick(View v) { host.active().duplicateSelected(); } }));
        selBar.addView(Ui.icon(c, "trash", "Delete", new View.OnClickListener() { public void onClick(View v) { host.active().deleteSelected(); } }));
        selBar.addView(Ui.icon(c, "close", "Deselect", new View.OnClickListener() {
            public void onClick(View v) { DicomView d = host.active(); d.selected = null; d.notifySel(); d.invalidate(); }
        }));
        selBar.setVisibility(View.GONE);
        if (vertical) {
            selBar.setBackground(Ui.rounded(0xF22A2A2E, Ui.dp(c, 14)));
            selBar.setElevation(Ui.dp(c, 6));
        } else view.addView(selBar);

        LinearLayout r = vertical ? Ui.col(c) : Ui.row(c);
        if (vertical) r.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        r.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (Object[] t : TOOLS) {
            final int id = (Integer) t[0];
            if (id == DicomView.T_COBB && !cobb) continue;
            ImageButton b = Ui.toolBtn(c, (String) t[1], (String) t[2], new View.OnClickListener() { public void onClick(View v) { host.selectTool(id); } });
            btns.put(id, b);
            r.addView(b);
        }
        r.addView(vertical ? hsep(c) : Ui.vsep(c));
        undoBtn = Ui.toolBtn(c, "undo", "Undo", new View.OnClickListener() {
            public void onClick(View v) { if (!host.active().undo()) Ui.toast(c, "Nothing to undo on this image."); }
        });
        r.addView(undoBtn);
        if (keyImages) {
            keyBtn = Ui.toolBtn(c, "star", "Mark as key image", new View.OnClickListener() { public void onClick(View v) { host.toggleKey(); updateKey(); } });
            r.addView(keyBtn);
        }
        r.addView(Ui.toolBtn(c, "clearimg", "Clear marks on this image", new View.OnClickListener() {
            public void onClick(View v) {
                if (host.active().annotations().isEmpty()) return;
                Ui.confirm(c, "Remove every measurement and annotation on this image? You can undo this.", "Clear", new Runnable() { public void run() { host.active().clearSlice(); } });
            }
        }));
        ImageButton done = Ui.toolBtn(c, "check", "Done", new View.OnClickListener() { public void onClick(View v) { host.closeBar(); } });
        Ui.setToolOn(done, true);
        r.addView(done);
        if (vertical) {
            android.widget.ScrollView sv = new android.widget.ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(r);
            sv.setBackground(Ui.rounded(0xE61E1E22, Ui.dp(c, 28)));
            sv.setElevation(Ui.dp(c, 6));
            tools = sv;
        } else {
            tools = Ui.hscroll(c, r);
            view.addView(tools);
        }
    }

    static View hsep(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 28), Math.max(1, Ui.dp(c, 1)));
        lp.setMargins(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));
        v.setLayoutParams(lp);
        return v;
    }

    void setTool(int t) {
        for (Map.Entry<Integer, ImageButton> e : btns.entrySet()) Ui.setToolOn(e.getValue(), e.getKey() == t);
    }

    void updateKey() {
        if (keyBtn == null) return;
        boolean on = host.isKey();
        keyBtn.setImageDrawable(new Icons(on ? "starfill" : "star", on ? 0xFFFFD54F : Ui.TEXT));
        Ui.tooltip(keyBtn, on ? "Unmark key image" : "Mark as key image");
    }

    void onSelection(DicomView v, DicomView.Ann a) {
        if (v != host.active()) return;
        if (a == null) { selBar.setVisibility(View.GONE); return; }
        selBar.setVisibility(View.VISIBLE);
        selText.setText(v.describe(a));
        labelBtn.setVisibility(a.type == DicomView.T_ARROW ? View.VISIBLE : View.GONE);
    }

    void editLabel() {
        final DicomView v = host.active();
        final DicomView.Ann a = v.selected;
        if (a == null) return;
        LinearLayout l = Ui.col(c);
        int p = Ui.dp(c, 20);
        l.setPadding(p, p / 2, p, 0);
        final EditText e = Ui.field(c, "Label");
        e.setText(a.text);
        l.addView(e);
        new android.app.AlertDialog.Builder(c).setTitle("Arrow label").setView(l)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { v.setLabel(a, e.getText().toString()); }
                }).setNegativeButton("Cancel", null).show();
    }
}
