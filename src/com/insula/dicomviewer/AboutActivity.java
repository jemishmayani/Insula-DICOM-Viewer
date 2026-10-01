/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

public class AboutActivity extends BaseActivity {
    static final String DEVELOPER = "Jemish Mayani";
    static final String EMAIL = "jemishmayani1403@gmail.com";
    static final String DOCS = Updates.REPO_URL + "/blob/main/docs/";

    final Handler h = new Handler(Looper.getMainLooper());
    String version = "";
    int versionCode;
    Button updateBtn;
    TextView updateStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) { }

        LinearLayout screen = Ui.col(this);
        screen.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.title(this, "About");
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(t, Ui.wrapWeight(1));
        screen.addView(top);
        ScrollView sv = new ScrollView(this);
        LinearLayout c = Ui.col(this);
        c.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 36));
        sv.addView(c);
        screen.addView(sv, Ui.vweight(1));

        // Hero
        LinearLayout hero = Ui.col(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 20)));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 26), Ui.dp(this, 20), Ui.dp(this, 20));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        hero.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)));
        TextView name = Ui.text(this, "Insula DICOM Viewer", 22, Ui.TEXT);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        hero.addView(name);
        TextView ver = Ui.text(this, "Version " + version + " (build " + versionCode + ")", 14, Ui.SUB);
        hero.addView(ver);
        TextView tag = Ui.text(this, "Free, open-source DICOM viewer for Android. Your images stay on your phone.", 14.5f, Ui.TEXT);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 14));
        hero.addView(tag);
        updateBtn = Ui.pill(this, "Check for updates", "download", new View.OnClickListener() { public void onClick(View v) { checkUpdates(); } });
        hero.addView(updateBtn, new LinearLayout.LayoutParams(-1, -2));
        updateStatus = Ui.text(this, "Contacts GitHub only when you tap it.", 12.5f, Ui.SUB);
        updateStatus.setGravity(Gravity.CENTER);
        updateStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        hero.addView(updateStatus);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 16);
        c.addView(hero, hl);

        // Disclaimer
        LinearLayout g = Ui.group(this, c, "Important");
        Ui.setting(this, g, "shield", Ui.WARN, "Not a medical device",
                "For reference, teaching, and patient use. Not for primary diagnosis.", null, new View.OnClickListener() {
                    public void onClick(View v) { open(DOCS + "INTENDED_USE.md"); }
                });

        g = Ui.group(this, c, "Help and project");
        Ui.setting(this, g, "book", Ui.C_PURPLE, "Guide", "What every tool does", null, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(AboutActivity.this, GuideActivity.class)); }
        });
        Ui.setting(this, g, "file", Ui.C_PURPLE, "Online user guide", "The full manual on GitHub", null, new View.OnClickListener() {
            public void onClick(View v) { open(DOCS + "USER_GUIDE.md"); }
        });
        Ui.setting(this, g, "export", Ui.C_BLUE, "Source code", "github.com/" + Updates.REPO, null, new View.OnClickListener() {
            public void onClick(View v) { open(Updates.REPO_URL); }
        });
        Ui.setting(this, g, "download", Ui.C_BLUE, "All releases", "Download any version", null, new View.OnClickListener() {
            public void onClick(View v) { open(Updates.RELEASES_URL); }
        });
        Ui.setting(this, g, "report", Ui.C_BLUE, "Report a problem or suggest a feature", "Opens GitHub issues", null, new View.OnClickListener() {
            public void onClick(View v) { reportProblem(); }
        });
        Ui.setting(this, g, "eye", Ui.C_GREEN, "Privacy policy", "The app collects no data", null, new View.OnClickListener() {
            public void onClick(View v) { open(DOCS + "PRIVACY.md"); }
        });

        g = Ui.group(this, c, "Licences");
        Ui.setting(this, g, "info", Ui.C_TEAL, "GNU General Public License v3", "Insula is free software; see the terms", null, new View.OnClickListener() {
            public void onClick(View v) { showText("GNU General Public License v3", R.raw.license_gpl3); }
        });
        Ui.setting(this, g, "info", Ui.C_TEAL, "Copyright and additional permission", "NOTICE: copyright, warranty, JJ2000 permission", null, new View.OnClickListener() {
            public void onClick(View v) { showText("Notice", R.raw.notice); }
        });
        Ui.setting(this, g, "info", Ui.C_TEAL, "JJ2000 JPEG 2000 decoder", "Third-party component, own licence", null, new View.OnClickListener() {
            public void onClick(View v) { showText("JJ2000", R.raw.license_jj2000); }
        });

        g = Ui.group(this, c, "Developer");
        Ui.setting(this, g, "teacher", Ui.C_AMBER, DEVELOPER, "Developer and maintainer", null, null);
        Ui.setting(this, g, "report", Ui.C_AMBER, "Email", EMAIL, null, new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL));
                i.putExtra(Intent.EXTRA_SUBJECT, "Insula DICOM Viewer " + version);
                try { startActivity(i); } catch (Exception e) { copy("Email", EMAIL); }
            }
        });
        Ui.setting(this, g, "copy", Ui.C_AMBER, "Copy app and device info", "Paste it into bug reports", null, new View.OnClickListener() {
            public void onClick(View v) { copy("App info", appInfo()); }
        });

        TextView legal = Ui.text(this, "Copyright © 2026 " + DEVELOPER + ".\n\n"
                + "This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License "
                + "as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.\n\n"
                + "This program comes with ABSOLUTELY NO WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A "
                + "PARTICULAR PURPOSE.", 12.5f, Ui.SUB);
        legal.setPadding(Ui.dp(this, 6), Ui.dp(this, 22), Ui.dp(this, 6), 0);
        legal.setLineSpacing(0, 1.15f);
        c.addView(legal);
        setContentView(screen);
    }

    String appInfo() {
        return "Insula DICOM Viewer " + version + " (build " + versionCode + ")\n"
                + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL;
    }

    void open(String url) {
        App.external = true;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { copy("Link", url); }
    }

    void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Ui.toast(this, label + " copied.");
    }

    void reportProblem() {
        new AlertDialog.Builder(this).setTitle("Before you report")
                .setMessage("GitHub issues are public. Never attach real patient images or screenshots showing names or IDs. "
                        + "Turn on Teacher mode or crop them first.\n\nYour app and device details will be copied so you can paste them into the report.")
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { copy("App info", appInfo()); open(Updates.REPO_URL + "/issues/new/choose"); }
                }).setNegativeButton("Cancel", null).show();
    }

    void showText(String title, int rawId) {
        String s;
        try {
            InputStream in = getResources().openRawResource(rawId);
            s = new String(Library.readAll(in), "UTF-8");
            in.close();
        } catch (Exception e) { s = "Couldn't load the text: " + e.getMessage(); }
        TextView tv = Ui.text(this, s, 12, Ui.TEXT);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int p = Ui.dp(this, 18);
        tv.setPadding(p, p / 2, p, p / 2);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("Close", null).show();
    }

    void checkUpdates() {
        updateBtn.setEnabled(false);
        updateStatus.setTextColor(Ui.SUB);
        updateStatus.setText("Checking GitHub…");
        new Thread() {
            public void run() {
                Updates.Release r = null;
                String err = null;
                try { r = Updates.fetchLatest(); } catch (Exception e) { err = e.getMessage(); }
                final Updates.Release fr = r;
                final String fe = err;
                h.post(new Runnable() {
                    public void run() {
                        updateBtn.setEnabled(true);
                        if (fe != null) { updateStatus.setTextColor(Ui.WARN); updateStatus.setText(fe); return; }
                        int cmp = Updates.compare(fr.tag, version);
                        if (cmp <= 0) {
                            updateStatus.setTextColor(0xFF81C784);
                            updateStatus.setText("You're up to date. Latest release: " + fr.tag + ".");
                            return;
                        }
                        updateStatus.setTextColor(Ui.VALUE);
                        updateStatus.setText(fr.tag + " is available.");
                        showUpdate(fr);
                    }
                });
            }
        }.start();
    }

    void showUpdate(final Updates.Release r) {
        String notes = r.notes.length() > 1500 ? r.notes.substring(0, 1500) + "…" : r.notes;
        new AlertDialog.Builder(this).setTitle(r.name.isEmpty() ? r.tag : r.name)
                .setMessage("You have " + version + ".\n\n" + notes.replace("\r", "").trim()
                        + "\n\nThe download opens in your browser. Install it over this version; your studies and settings are kept.")
                .setPositiveButton(r.apkUrl != null ? "Download" + (r.apkSize > 0 ? " (" + Library.fmtSize(r.apkSize) + ")" : "") : "Open release page",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { open(r.apkUrl != null ? r.apkUrl : r.pageUrl); }
                        })
                .setNeutralButton("Release page", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { open(r.pageUrl); }
                })
                .setNegativeButton("Later", null).show();
    }
}
