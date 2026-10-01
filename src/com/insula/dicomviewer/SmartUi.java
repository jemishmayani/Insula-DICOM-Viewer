/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Dialogs shared by the viewer and MPR: the hematoma volume prompt and the Study Quality report. */
final class SmartUi {

    /** Asks how many slices show the hematoma, then stores C and shows the ABC/2 volume on the measurement. */
    static void askAbcSlices(Activity act, DicomView v, DicomView.Ann a, double spacingMm) { askAbcSlices(act, v, a, spacingMm, null); }

    static void askAbcSlices(final Activity act, final DicomView v, final DicomView.Ann a, final double spacingMm, final Runnable after) {
        LinearLayout box = Ui.col(act);
        int p = Ui.dp(act, 20);
        box.setPadding(p, Ui.dp(act, 8), p, 0);
        TextView info = Ui.text(act, "Count the slices on which the hematoma is visible. Slice spacing here is "
                + String.format(Locale.ROOT, "%.2f mm", spacingMm) + ", so C = slices x spacing.", 14, Ui.SUB);
        box.addView(info);
        final EditText n = Ui.field(act, "Number of slices");
        n.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(n);
        new AlertDialog.Builder(act).setTitle("Hematoma volume (ABC/2)").setView(box)
                .setPositiveButton("Calculate", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        int k;
                        try { k = Integer.parseInt(n.getText().toString().trim()); } catch (Exception e) { k = 0; }
                        if (k <= 0) { Ui.toast(act, "Enter the number of slices (1 or more)."); return; }
                        a.text = "C=" + String.format(Locale.ROOT, "%.3f", k * spacingMm / 10);
                        v.persist();
                        v.invalidate();
                        if (after != null) after.run();
                        String vol = v.abcVolume(a);
                        Ui.toast(act, vol.isEmpty() ? "This image has no pixel spacing, so the volume can't be calculated." : vol + ". A volume of 30 mL or more is part of the ICH score.");
                    }
                }).setNegativeButton("Skip", null).show();
    }

    /** Analyzes the series in the background and shows the report. */
    static void showQuality(final Activity act, final Library.Series se) {
        if (se == null) return;
        final Ui.Busy pd = new Ui.Busy(act);
        pd.setMessage("Checking study quality…");
        pd.setCancelable(false);
        pd.show();
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread() {
            public void run() {
                final StudyQuality.Report r = StudyQuality.analyze(se);
                h.post(new Runnable() {
                    public void run() {
                        pd.dismiss();
                        if (act.isFinishing()) return;
                        show(act, se, r);
                    }
                });
            }
        }.start();
    }

    static void show(Activity act, Library.Series se, StudyQuality.Report r) {
        LinearLayout c = Ui.col(act);
        int p = Ui.dp(act, 20);
        c.setPadding(p, Ui.dp(act, 4), p, Ui.dp(act, 8));
        TextView s = Ui.text(act, se.label(), 13, Ui.SUB);
        c.addView(s);
        TextView sum = Ui.text(act, r.summary, 15.5f, r.overall == StudyQuality.GOOD ? 0xFF81C784 : r.overall == StudyQuality.FAIR ? Ui.WARN : 0xFFE57373);
        sum.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sum.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 10));
        c.addView(sum);
        for (StudyQuality.Item it : r.items) {
            LinearLayout row = Ui.row(act);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
            TextView dot = Ui.text(act, "\u25CF", 13, it.rating == StudyQuality.GOOD ? 0xFF81C784 : it.rating == StudyQuality.FAIR ? Ui.WARN : it.rating == StudyQuality.POOR ? 0xFFE57373 : Ui.SUB);
            dot.setPadding(0, Ui.dp(act, 1), Ui.dp(act, 10), 0);
            row.addView(dot);
            LinearLayout col = Ui.col(act);
            LinearLayout line = Ui.row(act);
            line.addView(Ui.text(act, it.name, 14.5f, Ui.TEXT), Ui.wrapWeight(1));
            line.addView(Ui.text(act, it.value, 14.5f, Ui.VALUE));
            col.addView(line);
            if (!it.note.isEmpty()) col.addView(Ui.text(act, it.note, 12.5f, Ui.SUB));
            row.addView(col, Ui.wrapWeight(1));
            c.addView(row);
        }
        TextView foot = Ui.text(act, "Green is good, amber limits some uses, red limits most. Grey items are for information.", 12, Ui.SUB);
        foot.setPadding(0, Ui.dp(act, 10), 0, 0);
        c.addView(foot);
        ScrollView sv = new ScrollView(act);
        sv.addView(c);
        new AlertDialog.Builder(act).setTitle("Study quality").setView(sv).setPositiveButton("Close", null).show();
    }
}
