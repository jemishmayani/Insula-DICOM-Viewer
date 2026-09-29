/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends BaseActivity {
    static final int REQ_SETTINGS_FILE = 31, REQ_STUDY_SET = 32;
    static final String SOURCE_URL = "https://github.com/jemishmayani/Insula-DICOM-Viewer";
    TextView storage, pacsRow;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout screen = Ui.col(this);
        screen.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.title(this, "Settings");
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(t, Ui.wrapWeight(1));
        screen.addView(top);
        ScrollView sv = new ScrollView(this);
        LinearLayout c = Ui.col(this);
        c.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 32));
        sv.addView(c);
        screen.addView(sv, Ui.vweight(1));

        // Header card
        LinearLayout head = Ui.row(this);
        head.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 16)));
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        LinearLayout hc = Ui.col(this);
        hc.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView hn = Ui.text(this, "Insula DICOM Viewer", 18, Ui.TEXT);
        hn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        hc.addView(hn);
        storage = Ui.text(this, "", 13, Ui.SUB);
        hc.addView(storage);
        head.addView(hc, Ui.wrapWeight(1));
        head.addView(Ui.iconView(this, "chevronr", 20, Ui.SUB));
        head.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, AboutActivity.class)); } });
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 14);
        c.addView(head, hl);

        LinearLayout g = Ui.group(this, c, "Viewer");
        Ui.slider(this, g, "speed", Ui.C_BLUE, "Default loop speed", 1, 60, Ui.prefs(this).getInt("fps", 10), "fps", new Ui.OnValue() {
            public void value(int v) { Ui.prefs(SettingsActivity.this).edit().putInt("fps", v).apply(); }
        });
        Ui.settingSwitch(this, g, "teacher", Ui.C_BLUE, "Teacher mode", "Hide patient name and IDs on screen", pref("teacher"), new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton x, boolean on) { put("teacher", on); }
        });

        g = Ui.group(this, c, "Privacy and security");
        Ui.settingSwitch(this, g, "lock", Ui.C_GREEN, "App lock", "Ask for your PIN or biometrics when opening the app", pref("lock"), new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton x, boolean on) { put("lock", on); if (on) App.unlocked = true; }
        });
        Ui.settingSwitch(this, g, "shield", Ui.C_GREEN, "Block screenshots", "Also blocks screen recording and app previews", pref("secure"), new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton x, boolean on) { put("secure", on); recreate(); }
        });
        Ui.settingSwitch(this, g, "eye", Ui.C_GREEN, "Hide patient details in exports", "Images and PDFs you share leave out names and IDs", pref("hidephi"), new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton x, boolean on) { put("hidephi", on); }
        });

        g = Ui.group(this, c, "PACS");
        pacsRow = (TextView) Ui.setting(this, g, "server", Ui.C_TEAL, "PACS profiles", "", null, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, PacsActivity.class)); }
        })[1];

        g = Ui.group(this, c, "Backup and transfer");
        Ui.setting(this, g, "export", Ui.C_AMBER, "Export settings", "Preferences, albums, PACS profiles", null, new View.OnClickListener() { public void onClick(View v) { exportSettings(); } });
        Ui.setting(this, g, "import", Ui.C_AMBER, "Import settings", "Restore on this phone from a settings file", null, new View.OnClickListener() { public void onClick(View v) { pick(REQ_SETTINGS_FILE); } });
        Ui.setting(this, g, "export", Ui.C_AMBER, "Export study set", "Studies with measurements, key images, albums", null, new View.OnClickListener() { public void onClick(View v) { chooseStudySet(); } });
        Ui.setting(this, g, "import", Ui.C_AMBER, "Import study set", "Add a shared set or restore a backup", null, new View.OnClickListener() { public void onClick(View v) { pick(REQ_STUDY_SET); } });

        g = Ui.group(this, c, "Help and legal");
        Ui.setting(this, g, "book", Ui.C_PURPLE, "Guide", "What every tool does, with search", null, new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, GuideActivity.class)); } });
        Ui.setting(this, g, "info", Ui.C_PURPLE, "About Insula", "Version, updates, licences, source code, contact", null, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, AboutActivity.class)); }
        });

        g = Ui.group(this, c, "Storage");
        Ui.setting(this, g, "trash", Ui.C_RED, "Delete all studies", "Removes every study and its measurements from this phone", null, new View.OnClickListener() {
            public void onClick(View v) {
                Ui.confirm(SettingsActivity.this, "Delete every study stored in this app? Measurements on them are removed too. This can't be undone.", "Delete all", new Runnable() {
                    public void run() { Library.deleteAll(); updateLabels(); }
                });
            }
        });
        setContentView(screen);
        updateLabels();
    }

    boolean pref(String k) { return Ui.prefs(this).getBoolean(k, false); }
    void put(String k, boolean v) { Ui.prefs(this).edit().putBoolean(k, v).apply(); }

    void updateLabels() {
        long bytes = 0;
        int n = 0;
        synchronized (Library.class) { for (Library.Study st : Library.studies) { bytes += st.bytes(); n++; } }
        String ver = "";
        try { ver = "Version " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName + "\n"; } catch (Exception ignored) { }
        storage.setText(ver + n + " stud" + (n == 1 ? "y" : "ies") + ", " + Library.fmtSize(bytes) + ", " + AnnStore.count() + " saved measurements");
        int np = Store.profiles(this).size();
        pacsRow.setText(np == 0 ? "No servers yet. Add one for each institution." : np + " server" + (np == 1 ? "" : "s") + " configured");
    }

    @Override protected void onResume() { super.onResume(); if (storage != null) updateLabels(); }

    void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, req);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_SETTINGS_FILE) importSettings(data.getData());
        else if (req == REQ_STUDY_SET) importStudySet(data.getData(), new Runnable() { public void run() { updateLabels(); } });
    }

    // ---------------- settings file ----------------
    void exportSettings() {
        boolean hasSecrets = false;
        for (Store.Profile p : Store.profiles(this)) if (!p.secret.isEmpty()) hasSecrets = true;
        if (!hasSecrets) { writeSettings(null); return; }
        new AlertDialog.Builder(this).setTitle("Include PACS passwords?")
                .setItems(new String[]{"No, leave passwords and tokens out", "Yes, protect them with a passphrase"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { if (w == 0) writeSettings(null); else askPassphrase(true, null); }
                }).show();
    }

    /** @param forExport true to set a new passphrase; otherwise asks to unlock the file content in json. */
    void askPassphrase(final boolean forExport, final String json) {
        LinearLayout l = Ui.col(this);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p / 2, p, 0);
        final EditText e = Ui.field(this, "Passphrase");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(e);
        final EditText e2 = Ui.field(this, "Repeat passphrase");
        e2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (forExport) l.addView(e2);
        l.addView(Ui.text(this, forExport ? "At least 8 characters. You'll need it to import the passwords; it can't be recovered." : "Enter the passphrase used when this file was exported, or skip to import without passwords.", 13, Ui.SUB));
        final AlertDialog d = new AlertDialog.Builder(this).setTitle(forExport ? "Set a passphrase" : "Passphrase").setView(l)
                .setPositiveButton(forExport ? "Export" : "Import", null)
                .setNegativeButton(forExport ? "Cancel" : "Skip passwords", forExport ? null : new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) { applySettings(json, null); }
                }).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String pass = e.getText().toString();
                if (forExport) {
                    if (pass.length() < 8) { e.setError("Use at least 8 characters"); return; }
                    if (!pass.equals(e2.getText().toString())) { e2.setError("The passphrases don't match"); return; }
                    d.dismiss();
                    writeSettings(pass);
                } else {
                    d.dismiss();
                    applySettings(json, pass);
                }
            }
        });
    }

    void writeSettings(String pass) {
        try {
            String json = Backup.exportSettings(this, pass);
            File f = new File(Library.exportDir, "insula-settings-" + new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()) + ".json");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(json.getBytes("UTF-8"));
            fo.close();
            Store.log(this, "share", "Exported settings", pass != null ? "Including passphrase-protected passwords" : "Without passwords", true);
            saveOrShare(f.getName(), "application/json", f);
        } catch (Exception e) { Ui.toast(this, "Couldn't export settings: " + e.getMessage()); }
    }

    void importSettings(Uri u) {
        try {
            InputStream in = getContentResolver().openInputStream(u);
            byte[] d = Library.readAll(in);
            in.close();
            String json = new String(d, "UTF-8");
            if (!Backup.isSettings(json)) {
                Ui.toast(this, "That file isn't an Insula settings file. To import studies, use Import study set or the + button on the home screen.");
                return;
            }
            if (Backup.needsPassphrase(json)) askPassphrase(false, json); else applySettings(json, null);
        } catch (Exception e) { Ui.toast(this, "Couldn't read the file: " + e.getMessage()); }
    }

    void applySettings(String json, String pass) {
        try {
            boolean oldSecure = pref("secure");
            String summary = Backup.importSettings(this, json, pass);
            Store.log(this, "file", "Imported settings", summary, true);
            Ui.toast(this, summary);
            if (pref("secure") != oldSecure) recreate(); else { finish(); startActivity(getIntent()); }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Couldn't import settings").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    // ---------------- study sets ----------------
    void chooseStudySet() {
        final List<Library.Study> all;
        synchronized (Library.class) { all = new ArrayList<>(Library.studies); }
        if (all.isEmpty()) { Ui.toast(this, "There are no studies to export yet."); return; }
        final Map<String, List<String>> albums = Store.albums(this);
        List<String> opts = new ArrayList<>();
        opts.add("All studies (" + all.size() + ")");
        if (!albums.isEmpty()) opts.add("An album");
        opts.add("Choose studies");
        final List<String> fo = opts;
        new AlertDialog.Builder(this).setTitle("Export which studies?").setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                String o = fo.get(w);
                if (o.startsWith("All")) exportStudySet(all, "Insula library");
                else if (o.startsWith("An album")) {
                    final List<String> names = new ArrayList<>(albums.keySet());
                    new AlertDialog.Builder(SettingsActivity.this).setTitle("Album").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d2, int w2) {
                            List<Library.Study> sel = new ArrayList<>();
                            for (Library.Study s : all) if (albums.get(names.get(w2)).contains(s.uid)) sel.add(s);
                            exportStudySet(sel, names.get(w2));
                        }
                    }).show();
                } else pickStudies(all);
            }
        }).show();
    }

    void pickStudies(final List<Library.Study> all) {
        String[] labels = new String[all.size()];
        final boolean[] on = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) labels[i] = all.get(i).displayName(false) + "\n" + Library.fmtDateLong(all.get(i).date) + "   " + all.get(i).modalities();
        new AlertDialog.Builder(this).setTitle("Choose studies")
                .setMultiChoiceItems(labels, on, new DialogInterface.OnMultiChoiceClickListener() { public void onClick(DialogInterface d, int w, boolean c) { on[w] = c; } })
                .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        List<Library.Study> sel = new ArrayList<>();
                        for (int i = 0; i < all.size(); i++) if (on[i]) sel.add(all.get(i));
                        exportStudySet(sel, sel.size() == 1 ? sel.get(0).desc.isEmpty() ? "Study" : sel.get(0).desc : "Study set");
                    }
                }).setNegativeButton("Cancel", null).show();
    }

}
