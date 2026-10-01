/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shown once on first launch: what Insula does, the intended-use confirmation, and a first step. */
public class WelcomeActivity extends BaseActivity {
    static final String ACTION = "action", DEMO = "demo", IMPORT = "import";
    Button demo, imp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 24);
        c.setPadding(p, Ui.dp(this, 36), p, p);
        sv.addView(c);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        c.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88)));
        TextView t = Ui.text(this, "Welcome to Insula", 26, Ui.TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        c.addView(t);
        TextView st = Ui.text(this, "A free DICOM viewer that keeps your images on this phone.", 15.5f, Ui.SUB);
        st.setGravity(Gravity.CENTER);
        c.addView(st);

        LinearLayout feats = Ui.col(this);
        feats.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
        feature(feats, "import", Ui.C_AMBER, "Open studies from anywhere", "Files, ZIPs, patient CDs, USB drives, download links, and hospital PACS.");
        feature(feats, "layers", Ui.C_BLUE, "Read them comfortably", "Smooth scrolling, window presets, measurements, MPR and 3D, and side-by-side comparison with prior studies.");
        feature(feats, "lock", Ui.C_GREEN, "Private by design", "No accounts, ads, or tracking. Nothing leaves the phone unless you share it.");
        c.addView(feats, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout warn = Ui.col(this);
        warn.setBackground(Ui.rounded(0x33FFC266, Ui.dp(this, 14)));
        warn.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 10));
        TextView wt = Ui.text(this, "Not a medical device", 16, Ui.WARN);
        wt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        warn.addView(wt);
        warn.addView(Ui.text(this, "Insula is for reference, teaching, and patient use. Phone screens aren't calibrated for diagnosis, and the app isn't cleared by any regulator.", 14, Ui.TEXT));
        final CheckBox ok = new CheckBox(this);
        ok.setText("I understand Insula must not be used for primary diagnosis");
        ok.setTextColor(Ui.TEXT);
        ok.setTextSize(14);
        ok.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        warn.addView(ok);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = Ui.dp(this, 10);
        c.addView(warn, wl);

        demo = Ui.pill(this, "Explore with a demo study", "cube", new View.OnClickListener() { public void onClick(View v) { finishWith(DEMO); } });
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = Ui.dp(this, 22);
        c.addView(demo, dl);
        imp = Ui.btn(this, "Import my own studies", new View.OnClickListener() { public void onClick(View v) { finishWith(IMPORT); } });
        imp.setTextSize(15);
        imp.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, -2);
        il.topMargin = Ui.dp(this, 8);
        c.addView(imp, il);
        TextView note = Ui.text(this, "The demo is a synthetic CT head phantom, not a real patient, as a prior and a current study. Delete it any time.", 12.5f, Ui.SUB);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 12), 0, 0);
        c.addView(note);

        ok.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton x, boolean on) { enable(on); }
        });
        enable(false);
        setContentView(sv);
    }

    void feature(LinearLayout parent, String icon, int color, String title, String body) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        r.addView(Ui.badge(this, icon, color));
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView t = Ui.text(this, title, 16, Ui.TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(t);
        col.addView(Ui.text(this, body, 14, Ui.SUB));
        r.addView(col, Ui.wrapWeight(1));
        parent.addView(r);
    }

    void enable(boolean on) {
        demo.setEnabled(on); imp.setEnabled(on);
        demo.setAlpha(on ? 1f : 0.4f); imp.setAlpha(on ? 1f : 0.4f);
    }

    void finishWith(String action) {
        Ui.prefs(this).edit().putBoolean("disclaimer", true).apply();
        setResult(RESULT_OK, new Intent().putExtra(ACTION, action));
        finish();
    }

    /** Leaving without confirming closes the app; it can't be used until the user confirms. */
    @Override protected boolean handleBack() { finishAffinity(); return true; }
}
