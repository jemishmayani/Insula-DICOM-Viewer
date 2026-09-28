package com.insula.dicomviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.List;
import java.util.ArrayList;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BaseActivity extends Activity {
    static final int REQ_UNLOCK = 900, REQ_SAVE = 901;
    byte[] pendingBytes;
    File pendingFile;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Ui.prefs(this).getBoolean("secure", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Ui.CHROME);
        getWindow().setNavigationBarColor(Ui.CHROME);
    }

    @Override protected void onResume() {
        super.onResume();
        if (Ui.prefs(this).getBoolean("lock", false) && !App.prompting) {
            long away = App.bgSince == 0 ? 0 : SystemClock.elapsedRealtime() - App.bgSince;
            boolean expired = App.bgSince != 0 && away > (App.external ? 180000 : 3000);
            if (!App.unlocked || expired) { App.unlocked = false; requestUnlock(); }
        }
        App.bgSince = 0;
        if (!App.prompting) App.external = false;
    }

    void requestUnlock() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) { App.unlocked = true; Ui.toast(this, "App lock needs a screen lock (PIN, pattern, or biometrics) set on this device."); return; }
        Intent i = km.createConfirmDeviceCredentialIntent("Insula DICOM Viewer", "Unlock to view medical images");
        if (i == null) { App.unlocked = true; return; }
        App.prompting = true;
        App.external = true;
        super.startActivityForResult(i, REQ_UNLOCK);
    }

    @Override public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        App.external = true;
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_UNLOCK) {
            App.prompting = false;
            if (res == RESULT_OK) App.unlocked = true; else finishAffinity();
            return;
        }
        if (req == REQ_SAVE) {
            if (res == RESULT_OK && data != null && data.getData() != null) writePending(data.getData());
            pendingBytes = null; pendingFile = null;
        }
    }

    /** Opens the system "save as" picker; the content is written when the user picks a location. */
    void saveAs(String name, String mime, byte[] bytes, File file) {
        pendingBytes = bytes; pendingFile = file;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_SAVE);
    }

    void writePending(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (pendingBytes != null) os.write(pendingBytes);
            else if (pendingFile != null) {
                InputStream in = new FileInputStream(pendingFile);
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) os.write(b, 0, n);
                in.close();
            }
            os.close();
            Ui.toast(this, "Saved.");
        } catch (Exception e) {
            Ui.toast(this, "Could not save: " + e.getMessage());
        }
    }

    void share(File f, String mime) {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType(mime);
        s.putExtra(Intent.EXTRA_STREAM, ShareProvider.uriFor(f));
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        App.external = true;
        startActivity(Intent.createChooser(s, "Share"));
    }

    /** Asks whether to save to storage or share via another app. */
    void saveOrShare(final String name, final String mime, final File f) {
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"Save to device…", "Share…"}, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) saveAs(name, mime, null, f); else share(f, mime);
                    }
                }).show();
    }

    // ---------------- study sets ----------------
    final Handler ui = new Handler(Looper.getMainLooper());

    /** Asks for a name and whether to anonymize, then packs the studies into a study-set ZIP to save or share. */
    void exportStudySet(final List<Library.Study> studies, String defaultName) {
        if (studies.isEmpty()) { Ui.toast(this, "There are no studies to export."); return; }
        LinearLayout l = Ui.col(this);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p / 2, p, 0);
        final EditText name = Ui.field(this, "Study set name");
        name.setText(defaultName);
        l.addView(name);
        final CheckBox anon = new CheckBox(this);
        anon.setText("Anonymize patient details");
        anon.setTextColor(Ui.TEXT);
        l.addView(anon);
        int imgs = 0;
        for (Library.Study s : studies) imgs += s.imageCount();
        l.addView(Ui.text(this, studies.size() + " stud" + (studies.size() == 1 ? "y" : "ies") + ", " + imgs + " files. Includes measurements, annotations, key images, and album membership. "
                + "Anyone with the file can open it in Insula or any DICOM viewer.", 13, Ui.SUB));
        new android.app.AlertDialog.Builder(this).setTitle("Export study set").setView(l)
                .setPositiveButton("Export", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        runExportSet(studies, n.isEmpty() ? "Study set" : n, anon.isChecked());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void runExportSet(final List<Library.Study> studies, final String name, final boolean anon) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Packing study set…");
        pd.setCancelable(false);
        pd.show();
        new Thread() {
            public void run() {
                String safe = name.replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '_');
                if (safe.isEmpty()) safe = "study_set";
                final File out = new File(Library.exportDir, safe + ".insula.zip");
                String err = null;
                int n = 0;
                try {
                    n = Backup.exportStudySet(BaseActivity.this, studies, name, anon, out, new Library.Progress() {
                        public void update(final int done, final int total, String m) {
                            ui.post(new Runnable() { public void run() { pd.setMessage("Packing study set… " + done + " of " + total); } });
                        }
                    });
                } catch (Exception e) { err = e.getMessage(); }
                final String fe = err;
                final int fn = n;
                Store.log(BaseActivity.this, "share", "Exported study set \"" + name + "\"", fe == null ? fn + " files, " + Library.fmtSize(out.length()) + (anon ? ", anonymized" : "") : "Failed: " + fe, fe == null);
                ui.post(new Runnable() {
                    public void run() {
                        pd.dismiss();
                        if (fe != null) { Ui.toast(BaseActivity.this, "Export failed: " + fe); return; }
                        saveOrShare(out.getName(), "application/zip", out);
                    }
                });
            }
        }.start();
    }

    /** Imports a study-set ZIP (or any DICOM/ZIP file) and applies its measurements, key images, and albums. */
    void importStudySet(final android.net.Uri uri, final Runnable after) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Importing study set…");
        pd.setCancelable(false);
        pd.show();
        new Thread() {
            public void run() {
                final int[] st = new int[3];
                final List<byte[]> man = new ArrayList<>();
                String err = null;
                try {
                    java.io.InputStream in = getContentResolver().openInputStream(uri);
                    Library.importStream(in, st, null, man);
                    in.close();
                } catch (Exception e) { err = e.getMessage(); }
                StringBuilder sb = new StringBuilder();
                for (byte[] m : man) { String r = Backup.applyManifest(BaseActivity.this, m); if (!r.isEmpty()) sb.append(r).append(". "); }
                final String fe = err, extra = sb.toString();
                Store.log(BaseActivity.this, "file", "Imported study set", fe != null ? "Failed: " + fe : st[0] + " images added" + (st[2] > 0 ? ", " + st[2] + " already present" : "") + (extra.isEmpty() ? "" : ". " + extra), fe == null);
                ui.post(new Runnable() {
                    public void run() {
                        pd.dismiss();
                        if (fe != null) Ui.toast(BaseActivity.this, "Import failed: " + fe);
                        else if (man.isEmpty() && st[0] + st[2] == 0) Ui.toast(BaseActivity.this, "No DICOM files or study set found in that file.");
                        else Ui.toast(BaseActivity.this, st[0] + " images imported" + (st[2] > 0 ? ", " + st[2] + " already in library" : "") + ". " + extra);
                        if (after != null) after.run();
                    }
                });
            }
        }.start();
    }
}
