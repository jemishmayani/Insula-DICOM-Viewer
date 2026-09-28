package com.insula.dicomviewer;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends BaseActivity {
    static final int REQ_FILES = 1, REQ_FOLDER = 2;
    static final String[] SORTS = {"Newest", "Oldest", "Patient name", "Modality", "Size"};
    final Handler h = new Handler(Looper.getMainLooper());
    int sort = 0, tab = 0;
    String albumFilter;
    TextView titleView, sortView, statusView, emptyView, albumChip;
    EditText search;
    ImageButton searchBtn;
    final TextView[] tabLabels = new TextView[3];
    final View[] tabBars = new View[3];
    View studiesPanel, albumsPanel, transfersPanel;
    ListView studyList, albumList, transferList;
    TextView albumEmpty, transferEmpty;
    final List<Library.Study> shown = new ArrayList<>();
    final List<String> albumNames = new ArrayList<>();
    List<Store.Transfer> transfers = new ArrayList<>();
    StudyAdapter studyAdapter;
    BaseAdapter albumAdapter, transferAdapter;
    ProgressDialog pd;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout main = Ui.col(this);
        root.addView(main);

        // Top bar
        LinearLayout top = Ui.topBar(this);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
        ll.leftMargin = Ui.dp(this, 10);
        top.addView(logo, ll);
        titleView = Ui.title(this, "Insula");
        titleView.setTextSize(21);
        titleView.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(titleView, Ui.wrapWeight(1));
        search = Ui.field(this, "Search patients, IDs, descriptions");
        search.setVisibility(View.GONE);
        search.setBackground(null);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });
        top.addView(search, Ui.wrapWeight(1));
        searchBtn = Ui.icon(this, "search", new View.OnClickListener() { public void onClick(View v) { toggleSearch(); } });
        top.addView(searchBtn);
        top.addView(Ui.icon(this, "server", "PACS: search and download", new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(MainActivity.this, PacsActivity.class)); } }));
        top.addView(Ui.icon(this, "settings", "Settings, guide, backup", new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } }));
        main.addView(top);

        // Tabs
        LinearLayout tabs = Ui.row(this);
        tabs.setBackgroundColor(Ui.BAR);
        tabs.setGravity(Gravity.CENTER_HORIZONTAL);
        String[] names = {"Studies", "Albums", "Transfers"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            LinearLayout t = Ui.col(this);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView l = Ui.text(this, names[i], 17, Ui.SUB);
            l.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            l.setGravity(Gravity.CENTER);
            l.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
            t.addView(l);
            View bar = new View(this);
            bar.setBackgroundColor(Ui.ACCENT);
            t.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setTab(k); } });
            tabLabels[i] = l; tabBars[i] = bar;
            tabs.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        main.addView(tabs);

        FrameLayout content = new FrameLayout(this);
        main.addView(content, Ui.vweight(1));
        studiesPanel = buildStudies();
        albumsPanel = buildAlbums();
        transfersPanel = buildTransfers();
        content.addView(studiesPanel);
        content.addView(albumsPanel);
        content.addView(transfersPanel);

        // Floating import button
        ImageButton fab = new ImageButton(this);
        fab.setImageDrawable(new Icons("plus", Ui.TEXT));
        int fp = Ui.dp(this, 18);
        fab.setPadding(fp, fp, fp, fp);
        fab.setScaleType(ImageView.ScaleType.FIT_CENTER);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(Ui.BLUE);
        fab.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44FFFFFF), circle, null));
        fab.setElevation(Ui.dp(this, 8));
        fab.setContentDescription("Import studies");
        fab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { importSheet(); } });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.setMargins(0, 0, Ui.dp(this, 22), Ui.dp(this, 26));
        root.addView(fab, flp);

        setContentView(root);

        setTab(0);
        if (!Library.scanned) scanAsync(); else refresh();
        handleIntent(getIntent());
        if (!Ui.prefs(this).getBoolean("disclaimer", false)) showDisclaimer(true);
    }

    @Override public void onBackPressed() {
        if (search.getVisibility() == View.VISIBLE) { toggleSearch(); return; }
        if (albumFilter != null) { albumFilter = null; refresh(); return; }
        super.onBackPressed();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i); }
    @Override protected void onResume() { super.onResume(); if (Library.scanned) refresh(); }

    // ---------------- building ----------------
    View buildStudies() {
        LinearLayout p = Ui.col(this);
        LinearLayout hdr = Ui.row(this);
        hdr.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 4));
        sortView = Ui.text(this, "", 17, Ui.TEXT);
        sortView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Icons chev = new Icons("chevron", Ui.TEXT);
        chev.setBounds(0, 0, Ui.dp(this, 22), Ui.dp(this, 22));
        sortView.setCompoundDrawables(null, null, chev, null);
        sortView.setCompoundDrawablePadding(Ui.dp(this, 8));
        sortView.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        sortView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickSort(); } });
        hdr.addView(sortView);
        albumChip = Ui.text(this, "", 14, Ui.TEXT);
        albumChip.setBackground(Ui.rounded(Ui.BTN, Ui.dp(this, 16)));
        albumChip.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        albumChip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { albumFilter = null; refresh(); } });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = Ui.dp(this, 12);
        hdr.addView(albumChip, cl);
        hdr.addView(Ui.spacer(this));
        statusView = Ui.text(this, "", 13, Ui.SUB);
        hdr.addView(statusView);
        p.addView(hdr);

        FrameLayout f = new FrameLayout(this);
        studyList = new ListView(this);
        studyList.setDividerHeight(0);
        studyList.setClipToPadding(false);
        studyList.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 110));
        studyList.setSelector(new android.graphics.drawable.ColorDrawable(0));
        studyAdapter = new StudyAdapter();
        studyList.setAdapter(studyAdapter);
        studyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) { openStudy(shown.get(pos)); }
        });
        studyList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) { studyOptions(shown.get(pos)); return true; }
        });
        f.addView(studyList);
        emptyView = Ui.text(this, "", 16, Ui.SUB);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), Ui.dp(this, 60));
        f.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.addView(f, Ui.vweight(1));
        return p;
    }

    View buildAlbums() {
        LinearLayout p = Ui.col(this);
        LinearLayout hdr = Ui.row(this);
        hdr.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        TextView ad = Ui.text(this, "Collections you make: teaching files, a patient's follow-ups, cases to review. A study can be in several albums.", 13.5f, Ui.SUB);
        hdr.addView(ad, Ui.wrapWeight(1));
        hdr.addView(Ui.btn(this, "New album", new View.OnClickListener() { public void onClick(View v) { newAlbum(null); } }));
        hdr.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 4));
        p.addView(hdr);
        FrameLayout f = new FrameLayout(this);
        albumList = new ListView(this);
        albumList.setDividerHeight(0);
        albumAdapter = new BaseAdapter() {
            public int getCount() { return albumNames.size(); }
            public Object getItem(int i) { return albumNames.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                String n = albumNames.get(pos);
                List<String> l = Store.albums(MainActivity.this).get(n);
                int count = l == null ? 0 : l.size();
                return listRow("album", n, count + " stud" + (count == 1 ? "y" : "ies"), Ui.SUB);
            }
        };
        albumList.setAdapter(albumAdapter);
        albumList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) { albumFilter = albumNames.get(pos); setTab(0); }
        });
        albumList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) { albumOptions(albumNames.get(pos)); return true; }
        });
        f.addView(albumList);
        albumEmpty = Ui.text(this, "Albums keep related studies together, such as teaching cases or a patient's follow-ups.\n\nLong-press a study to add it to an album.", 16, Ui.SUB);
        albumEmpty.setGravity(Gravity.CENTER);
        albumEmpty.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), Ui.dp(this, 60));
        f.addView(albumEmpty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.addView(f, Ui.vweight(1));
        return p;
    }

    View buildTransfers() {
        LinearLayout p = Ui.col(this);
        LinearLayout hdr = Ui.row(this);
        hdr.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        TextView td = Ui.text(this, "A log of imports, PACS downloads, and exports, with sizes, speeds, and any errors.", 13.5f, Ui.SUB);
        hdr.addView(td, Ui.wrapWeight(1));
        hdr.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 4));
        hdr.addView(Ui.btn(this, "Clear", new View.OnClickListener() {
            public void onClick(View v) { Store.clearTransfers(MainActivity.this); refresh(); }
        }));
        p.addView(hdr);
        FrameLayout f = new FrameLayout(this);
        transferList = new ListView(this);
        transferList.setDividerHeight(0);
        transferAdapter = new BaseAdapter() {
            public int getCount() { return transfers.size(); }
            public Object getItem(int i) { return transfers.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                Store.Transfer t = transfers.get(pos);
                return listRow(t.kind, t.title, t.detail + "\n" + when(t.time), t.ok ? Ui.SUB : Ui.WARN);
            }
        };
        transferList.setAdapter(transferAdapter);
        f.addView(transferList);
        transferEmpty = Ui.text(this, "Imports, downloads, and exports appear here.", 16, Ui.SUB);
        transferEmpty.setGravity(Gravity.CENTER);
        transferEmpty.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), Ui.dp(this, 60));
        f.addView(transferEmpty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.addView(f, Ui.vweight(1));
        return p;
    }

    View listRow(String icon, String title, String sub, int subColor) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        r.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(Ui.iconView(this, icon, 28, Ui.TEXT));
        LinearLayout c = Ui.col(this);
        c.setPadding(Ui.dp(this, 18), 0, 0, 0);
        TextView a = Ui.text(this, title, 16.5f, Ui.TEXT);
        a.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(a);
        c.addView(Ui.text(this, sub, 13.5f, subColor));
        r.addView(c, Ui.wrapWeight(1));
        return r;
    }

    static final String[] TAB_NAMES = {"Studies", "Albums", "Transfers"};

    void setTab(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            tabLabels[i].setTextColor(i == t ? Ui.TEXT : Ui.SUB);
            tabBars[i].setVisibility(i == t ? View.VISIBLE : View.INVISIBLE);
        }
        studiesPanel.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        albumsPanel.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        transfersPanel.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        refresh();
    }

    void toggleSearch() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (search.getVisibility() == View.VISIBLE) {
            search.setText("");
            search.setVisibility(View.GONE);
            titleView.setVisibility(View.VISIBLE);
            searchBtn.setImageDrawable(new Icons("search", Ui.TEXT));
            im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            if (tab != 0) setTab(0);
            titleView.setVisibility(View.GONE);
            search.setVisibility(View.VISIBLE);
            searchBtn.setImageDrawable(new Icons("close", Ui.TEXT));
            search.requestFocus();
            im.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // ---------------- data ----------------
    void refresh() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Library.Study> all;
        synchronized (Library.class) { all = new ArrayList<>(Library.studies); }
        Map<String, List<String>> albums = Store.albums(this);
        List<String> inAlbum = albumFilter == null ? null : albums.get(albumFilter);
        if (albumFilter != null && inAlbum == null) albumFilter = null;
        shown.clear();
        for (Library.Study s : all) {
            if (inAlbum != null && !inAlbum.contains(s.uid)) continue;
            if (q.isEmpty() || matches(s, q)) shown.add(s);
        }
        Collections.sort(shown, new Comparator<Library.Study>() {
            public int compare(Library.Study a, Library.Study b) {
                switch (sort) {
                    case 1: return (a.date + a.time).compareTo(b.date + b.time);
                    case 2: return a.patientName.compareToIgnoreCase(b.patientName);
                    case 3: return a.modalities().compareTo(b.modalities());
                    case 4: return Long.compare(b.bytes(), a.bytes());
                    default: return (b.date + b.time).compareTo(a.date + a.time);
                }
            }
        });
        studyAdapter.notifyDataSetChanged();
        sortView.setText(SORTS[sort]);
        albumChip.setVisibility(albumFilter == null ? View.GONE : View.VISIBLE);
        albumChip.setText("Album: " + albumFilter + "  ✕");
        statusView.setText(shown.isEmpty() ? "" : shown.size() + " stud" + (shown.size() == 1 ? "y" : "ies"));
        if (!Library.scanned) emptyView.setText("Loading library…");
        else if (all.isEmpty()) emptyView.setText("No studies yet.\n\nTap + to import DICOM files, a ZIP, a folder copied from a patient CD, or studies from a PACS.");
        else if (shown.isEmpty()) emptyView.setText(albumFilter != null && q.isEmpty() ? "This album is empty. Long-press a study to add it." : "No studies match your search.");
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);

        albumNames.clear();
        albumNames.addAll(albums.keySet());
        albumAdapter.notifyDataSetChanged();
        albumEmpty.setVisibility(albumNames.isEmpty() ? View.VISIBLE : View.GONE);

        transfers = Store.transfers(this);
        transferAdapter.notifyDataSetChanged();
        int[] counts = {all.size(), albumNames.size(), transfers.size()};
        for (int k = 0; k < 3; k++) tabLabels[k].setText(TAB_NAMES[k] + (counts[k] > 0 ? "  " + counts[k] : ""));
        transferEmpty.setVisibility(transfers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    boolean matches(Library.Study s, String q) {
        String hay = (s.patientName + " " + s.patientId + " " + s.desc + " " + s.accession + " " + s.modalities() + " " + s.date).toLowerCase(Locale.ROOT);
        if (hay.contains(q)) return true;
        for (Library.Series se : s.series) if ((se.desc + " " + se.bodyPart).toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    String when(long t) {
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        if (day.format(new Date(t)).equals(day.format(new Date()))) return "Today " + time;
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(t)) + " " + time;
    }

    void scanAsync() {
        new Thread() {
            public void run() {
                Library.scan(new Library.Progress() {
                    public void update(final int done, final int total, String msg) {
                        h.post(new Runnable() { public void run() { emptyView.setText("Loading library… " + done + " / " + total); } });
                    }
                });
                h.post(new Runnable() { public void run() { refresh(); } });
            }
        }.start();
    }

    // ---------------- import ----------------
    void importSheet() {
        Ui.choices(this, "Import studies", "Select source",
                new String[]{"file", "folder", "download", "server"},
                new String[]{"File(s)", "Folder", "Download link", "DICOM query"},
                new Ui.OnChoice() {
                    public void choose(int i) {
                        if (i == 0) pickFiles();
                        else if (i == 1) pickFolder();
                        else if (i == 2) askLink();
                        else startActivity(new Intent(MainActivity.this, PacsActivity.class));
                    }
                }).show();
    }

    void handleIntent(Intent i) {
        if (i == null || i.getAction() == null) return;
        List<Uri> uris = new ArrayList<>();
        String a = i.getAction();
        if (Intent.ACTION_VIEW.equals(a) && i.getData() != null) {
            String sc = i.getData().getScheme();
            if ("http".equals(sc) || "https".equals(sc)) { download(i.getData().toString()); setIntent(new Intent(this, MainActivity.class)); return; }
            uris.add(i.getData());
        } else if (Intent.ACTION_SEND.equals(a)) {
            Parcelable p = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (p instanceof Uri) uris.add((Uri) p);
            else { String t = i.getStringExtra(Intent.EXTRA_TEXT); if (t != null && t.trim().startsWith("http")) download(t.trim()); }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(a)) {
            ArrayList<Parcelable> l = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) for (Parcelable p : l) if (p instanceof Uri) uris.add((Uri) p);
        }
        setIntent(new Intent(this, MainActivity.class));
        if (!uris.isEmpty()) importUris(uris, null);
    }

    void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_FILES);
    }

    void pickFolder() { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_FILES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            importUris(uris, null);
        } else if (req == REQ_FOLDER && data.getData() != null) {
            importUris(new ArrayList<Uri>(), data.getData());
        }
    }

    ProgressDialog progress(String msg) {
        ProgressDialog d = new ProgressDialog(this);
        d.setMessage(msg);
        d.setCancelable(false);
        d.show();
        return d;
    }

    String summary(int[] st) {
        String msg = st[0] + " image" + (st[0] == 1 ? "" : "s") + " imported";
        if (st[2] > 0) msg += ", " + st[2] + " already in library";
        if (st[1] > 0) msg += ", " + st[1] + " skipped (not DICOM or unreadable)";
        return msg;
    }

    void importUris(final List<Uri> given, final Uri tree) {
        pd = progress(tree != null ? "Scanning folder…" : "Importing…");
        new Thread() {
            public void run() {
                final int[] st = new int[3];
                final List<byte[]> manifests = new ArrayList<>();
                List<Uri> uris = given;
                if (tree != null) {
                    uris = new ArrayList<>();
                    try { collect(tree, DocumentsContract.getTreeDocumentId(tree), uris, 0); } catch (Throwable ignored) { }
                }
                final int total = uris.size();
                int k = 0;
                for (Uri u : uris) {
                    k++;
                    try {
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in != null) { Library.importStream(in, st, null, manifests); in.close(); }
                    } catch (Throwable e) { st[1]++; }
                    final int kk = k;
                    if (k % 5 == 0 || k == total) h.post(new Runnable() { public void run() { if (pd != null) pd.setMessage("Importing " + kk + " of " + total + "\n" + st[0] + " images added"); } });
                }
                final StringBuilder extra = new StringBuilder();
                for (byte[] m : manifests) { String r = Backup.applyManifest(MainActivity.this, m); if (!r.isEmpty()) extra.append(" ").append(r).append("."); }
                String name = tree != null ? "Folder import" : (total == 1 ? "File import" : total + " files imported");
                Store.log(MainActivity.this, tree != null ? "folder" : "file", name, summary(st), st[0] > 0 || st[2] > 0);
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) { pd.dismiss(); pd = null; }
                        refresh();
                        Ui.toast(MainActivity.this, summary(st) + "." + extra);
                    }
                });
            }
        }.start();
    }

    void collect(Uri tree, String docId, List<Uri> out, int depth) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                String id = c.getString(0), mime = c.getString(1);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { if (depth < 20) collect(tree, id, out, depth + 1); }
                else out.add(DocumentsContract.buildDocumentUriUsingTree(tree, id));
            }
        } finally { c.close(); }
    }

    void askLink() {
        LinearLayout l = Ui.col(this);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p / 2, p, 0);
        final EditText e = Ui.field(this, "https://…");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        l.addView(e);
        l.addView(Ui.text(this, "A direct link to a DICOM file or a ZIP of DICOM files.", 13, Ui.SUB));
        new AlertDialog.Builder(this).setTitle("Download link").setView(l)
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String u = e.getText().toString().trim();
                        if (!u.startsWith("http")) u = "https://" + u;
                        download(u);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void download(final String url) {
        pd = progress("Connecting…");
        new Thread() {
            public void run() {
                final int[] st = new int[3];
                String err = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(120000);
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    if (code >= 400) throw new IOException("the server answered HTTP " + code);
                    long len = -1;
                    try { len = Long.parseLong(c.getHeaderField("Content-Length")); } catch (Exception ignored) { }
                    final long total = len;
                    InputStream in = new FilterInputStream(c.getInputStream()) {
                        long n, last;
                        @Override public int read(byte[] b, int o, int l) throws IOException {
                            int r = super.read(b, o, l);
                            if (r > 0) { n += r; if (n - last > 512 * 1024) { last = n; post(n, total); } }
                            return r;
                        }
                        @Override public int read() throws IOException { int r = super.read(); if (r >= 0) n++; return r; }
                    };
                    Library.importStream(in, st, null);
                    in.close();
                    c.disconnect();
                } catch (Exception e) { err = e.getMessage(); }
                final String fe = err;
                String host = url;
                try { host = new URL(url).getHost(); } catch (Exception ignored) { }
                Store.log(MainActivity.this, "download", "Download from " + host, fe != null ? "Failed: " + fe : summary(st), fe == null && st[0] + st[2] > 0);
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) { pd.dismiss(); pd = null; }
                        refresh();
                        if (fe != null) Ui.toast(MainActivity.this, "Download failed: " + fe);
                        else if (st[0] + st[2] == 0) Ui.toast(MainActivity.this, "The link didn't contain DICOM files. Check that it points to a DICOM file or a ZIP, not a web page.");
                        else Ui.toast(MainActivity.this, summary(st) + ".");
                    }
                });
            }

            void post(final long n, final long total) {
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) pd.setMessage("Downloading " + Library.fmtSize(n) + (total > 0 ? " of " + Library.fmtSize(total) : ""));
                    }
                });
            }
        }.start();
    }

    // ---------------- study actions ----------------
    void openStudy(Library.Study s) {
        Library.Series se = s.firstImageSeries();
        if (se != null && se.slices().isEmpty()) {
            Library.ImageInfo i = se.first();
            if (i != null) { Intent t = new Intent(this, TagActivity.class); t.putExtra("path", i.file.getAbsolutePath()); startActivity(t); }
            return;
        }
        Intent i = new Intent(this, ViewerActivity.class);
        i.putExtra("study", s.uid);
        startActivity(i);
    }

    void studyOptions(final Library.Study s) {
        final List<String> labels = new ArrayList<>();
        final List<String> icons = new ArrayList<>();
        labels.add("Open"); icons.add("file");
        labels.add("Study details"); icons.add("info");
        labels.add("Add to album"); icons.add("album");
        if (albumFilter != null) { labels.add("Remove from this album"); icons.add("close"); }
        labels.add("Export study set"); icons.add("export");
        labels.add("Export anonymized ZIP"); icons.add("share");
        labels.add("Delete study"); icons.add("trash");
        Ui.choices(this, s.displayName(false), null, icons.toArray(new String[0]), labels.toArray(new String[0]), new Ui.OnChoice() {
            public void choose(int i) {
                String l = labels.get(i);
                if (l.equals("Open")) openStudy(s);
                else if (l.equals("Study details")) studyInfo(MainActivity.this, s, false);
                else if (l.equals("Add to album")) addToAlbum(s);
                else if (l.startsWith("Remove")) { Store.removeFromAlbum(MainActivity.this, albumFilter, s.uid); refresh(); }
                else if (l.equals("Export study set")) { List<Library.Study> one = new ArrayList<>(); one.add(s); exportStudySet(one, s.desc.isEmpty() ? "Study" : s.desc); }
                else if (l.startsWith("Export")) {
                    List<Library.ImageInfo> all = new ArrayList<>();
                    for (Library.Series se : s.series) all.addAll(se.images);
                    exportAnon(fileList(all));
                } else Ui.confirm(MainActivity.this, "Delete this study from the library?", "Delete", new Runnable() { public void run() { Library.deleteStudy(s); refresh(); } });
            }
        }).show();
    }

    static void studyInfo(Context c, Library.Study s, boolean hide) {
        StringBuilder sb = new StringBuilder();
        if (!hide) {
            sb.append("Patient: ").append(s.patientName).append('\n');
            if (!s.patientId.isEmpty()) sb.append("Patient ID: ").append(s.patientId).append('\n');
            if (!s.birthDate.isEmpty()) sb.append("Birth date: ").append(Library.fmtDateLong(s.birthDate)).append('\n');
        }
        if (!s.ageSex().isEmpty()) sb.append("Age/sex: ").append(s.ageSex()).append('\n');
        sb.append("\nStudy: ").append(s.desc.isEmpty() ? "(no description)" : s.desc).append('\n');
        sb.append("Date: ").append(Library.fmtDateLong(s.date)).append(' ').append(Library.fmtTime(s.time)).append('\n');
        if (!hide && !s.accession.isEmpty()) sb.append("Accession: ").append(s.accession).append('\n');
        if (!hide && !s.institution.isEmpty()) sb.append("Institution: ").append(s.institution).append('\n');
        sb.append("Modalities: ").append(s.modalities()).append('\n');
        String src = Store.source(c, s.uid);
        if (!src.isEmpty()) sb.append("Downloaded from: ").append(src).append('\n');
        sb.append(s.series.size()).append(" series, ").append(s.imageCount()).append(" files, ").append(Library.fmtSize(s.bytes())).append("\n\nSeries:\n");
        for (Library.Series se : s.series) sb.append("  ").append(se.number).append("  ").append(se.desc.isEmpty() ? se.modality : se.desc).append("  (").append(se.slices().size()).append(")\n");
        new AlertDialog.Builder(c).setTitle("Study details").setMessage(sb.toString().trim()).setPositiveButton("Close", null).show();
    }

    void addToAlbum(final Library.Study s) {
        final List<String> names = new ArrayList<>(Store.albums(this).keySet());
        final List<String> items = new ArrayList<>(names);
        items.add("New album…");
        new AlertDialog.Builder(this).setTitle("Add to album").setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == names.size()) newAlbum(s);
                else { Store.addToAlbum(MainActivity.this, names.get(w), s.uid); refresh(); Ui.toast(MainActivity.this, "Added to " + names.get(w) + "."); }
            }
        }).show();
    }

    void newAlbum(final Library.Study add) {
        LinearLayout l = Ui.col(this);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p / 2, p, 0);
        final EditText e = Ui.field(this, "Album name");
        l.addView(e);
        new AlertDialog.Builder(this).setTitle("New album").setView(l)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String n = e.getText().toString().trim();
                        if (n.isEmpty()) return;
                        if (add != null) Store.addToAlbum(MainActivity.this, n, add.uid); else Store.createAlbum(MainActivity.this, n);
                        refresh();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void albumOptions(final String name) {
        new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Open", "Rename", "Export as study set", "Delete album"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w == 0) { albumFilter = name; setTab(0); }
                else if (w == 1) {
                    LinearLayout l = Ui.col(MainActivity.this);
                    int p = Ui.dp(MainActivity.this, 20);
                    l.setPadding(p, p / 2, p, 0);
                    final EditText e = Ui.field(MainActivity.this, "Album name");
                    e.setText(name);
                    l.addView(e);
                    new AlertDialog.Builder(MainActivity.this).setTitle("Rename album").setView(l)
                            .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d2, int w2) {
                                    String n = e.getText().toString().trim();
                                    if (!n.isEmpty()) { Store.renameAlbum(MainActivity.this, name, n); refresh(); }
                                }
                            }).setNegativeButton("Cancel", null).show();
                } else if (w == 2) {
                    List<String> uids = Store.albums(MainActivity.this).get(name);
                    List<Library.Study> sel = new ArrayList<>();
                    synchronized (Library.class) { for (Library.Study st : Library.studies) if (uids != null && uids.contains(st.uid)) sel.add(st); }
                    exportStudySet(sel, name);
                } else Ui.confirm(MainActivity.this, "Delete the album \"" + name + "\"? The studies stay in your library.", "Delete", new Runnable() {
                    public void run() { Store.deleteAlbum(MainActivity.this, name); refresh(); }
                });
            }
        }).show();
    }

    static List<File> fileList(List<Library.ImageInfo> l) {
        List<File> f = new ArrayList<>();
        for (Library.ImageInfo i : l) f.add(i.file);
        return f;
    }

    void exportAnon(final List<File> files) {
        new AlertDialog.Builder(this).setTitle("Export anonymized copy")
                .setMessage("Names, IDs, birth dates, addresses, accession numbers, institution and physician names, and all private tags are blanked. UIDs and study dates are kept.\n\nText burned into the image pixels is not removed. Check the images before sharing.")
                .setPositiveButton("Export", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { runAnon(files); } })
                .setNegativeButton("Cancel", null).show();
    }

    void runAnon(final List<File> files) {
        pd = progress("Anonymizing…");
        new Thread() {
            public void run() {
                final boolean[] warn = {false};
                final File out = new File(Library.exportDir, "anonymized_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".zip");
                String err = null;
                int n = 0;
                try { n = Anonymizer.writeZip(files, out, warn, null); } catch (Exception e) { err = e.getMessage(); }
                final String fe = err;
                final int fn = n;
                Store.log(MainActivity.this, "share", "Anonymized export", fe == null ? fn + " images, " + Library.fmtSize(out.length()) : "Failed: " + fe, fe == null && fn > 0);
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) { pd.dismiss(); pd = null; }
                        if (fe != null || fn == 0) { Ui.toast(MainActivity.this, "Export failed" + (fe != null ? ": " + fe : ".")); return; }
                        if (warn[0]) Ui.toast(MainActivity.this, "Warning: some images declare burned-in text that may show patient details.");
                        saveOrShare(out.getName(), "application/zip", out);
                    }
                });
            }
        }.start();
    }

    void pickSort() {
        new AlertDialog.Builder(this).setTitle("Sort studies").setSingleChoiceItems(SORTS, sort, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { sort = w; d.dismiss(); refresh(); }
        }).show();
    }

    void settings() {
        final String[] keys = {"lock", "secure", "hidephi"};
        final String[] labels = {"Lock app with device PIN or biometrics", "Block screenshots and screen recording", "Hide patient details in exported images"};
        final boolean[] v = new boolean[3];
        for (int i = 0; i < 3; i++) v[i] = Ui.prefs(this).getBoolean(keys[i], false);
        final boolean oldSecure = v[1];
        new AlertDialog.Builder(this).setTitle("Settings")
                .setMultiChoiceItems(labels, v, new DialogInterface.OnMultiChoiceClickListener() { public void onClick(DialogInterface d, int w, boolean c) { v[w] = c; } })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        android.content.SharedPreferences.Editor e = Ui.prefs(MainActivity.this).edit();
                        for (int i = 0; i < 3; i++) e.putBoolean(keys[i], v[i]);
                        e.apply();
                        if (v[0]) App.unlocked = true;
                        if (v[1] != oldSecure) recreate();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void showDisclaimer(final boolean first) {
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Insula DICOM Viewer 1.5.0")
                .setMessage("For reference, teaching, and patient use only. This app is not a cleared medical device and must not be used for primary diagnosis.\n\n"
                        + "Images are stored in this app's private storage, which Android encrypts on modern devices. Nothing is uploaded unless you connect to a server yourself.");
        if (first) {
            b.setCancelable(false).setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { Ui.prefs(MainActivity.this).edit().putBoolean("disclaimer", true).apply(); }
            });
        } else b.setPositiveButton("Close", null);
        b.show();
    }

    // ---------------- study cards ----------------
    static final class Holder { ImageView thumb; TextView name, desc, source, date, size, badge; }

    final class StudyAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Holder hd;
            if (cv == null) {
                hd = new Holder();
                FrameLayout outer = new FrameLayout(MainActivity.this);
                outer.setPadding(Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 6));
                FrameLayout card = new FrameLayout(MainActivity.this);
                card.setBackground(Ui.ripple(Ui.rounded(Ui.CARD, Ui.dp(MainActivity.this, 8))));
                card.setDuplicateParentStateEnabled(true);
                LinearLayout row = Ui.row(MainActivity.this);
                row.setGravity(Gravity.TOP);
                int pd = Ui.dp(MainActivity.this, 14);
                row.setPadding(pd, pd, pd, pd);
                hd.thumb = new ImageView(MainActivity.this);
                hd.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                hd.thumb.setBackground(Ui.rounded(0xFF000000, Ui.dp(MainActivity.this, 6)));
                hd.thumb.setClipToOutline(true);
                int ts = Ui.dp(MainActivity.this, 100);
                row.addView(hd.thumb, new LinearLayout.LayoutParams(ts, ts));
                LinearLayout col = Ui.col(MainActivity.this);
                col.setPadding(Ui.dp(MainActivity.this, 18), 0, 0, 0);
                hd.name = Ui.text(MainActivity.this, "", 18, Ui.TEXT);
                hd.name.setTypeface(Typeface.DEFAULT_BOLD);
                hd.name.setMaxLines(2);
                hd.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                hd.name.setPadding(0, 0, Ui.dp(MainActivity.this, 48), 0);
                col.addView(hd.name);
                hd.desc = Ui.text(MainActivity.this, "", 15.5f, Ui.SUB);
                hd.desc.setSingleLine(true);
                hd.desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
                hd.desc.setPadding(0, Ui.dp(MainActivity.this, 4), 0, 0);
                col.addView(hd.desc);
                hd.source = Ui.text(MainActivity.this, "", 13.5f, Ui.VALUE);
                hd.source.setSingleLine(true);
                hd.source.setEllipsize(android.text.TextUtils.TruncateAt.END);
                hd.source.setPadding(0, Ui.dp(MainActivity.this, 2), 0, 0);
                col.addView(hd.source);
                col.addView(new View(MainActivity.this), Ui.vweight(1));
                LinearLayout bottom = Ui.row(MainActivity.this);
                hd.date = Ui.text(MainActivity.this, "", 15, Ui.SUB);
                bottom.addView(hd.date, Ui.wrapWeight(1));
                hd.size = Ui.text(MainActivity.this, "", 15, Ui.SUB);
                bottom.addView(hd.size);
                col.addView(bottom);
                row.addView(col, new LinearLayout.LayoutParams(0, ts, 1));
                card.addView(row);
                hd.badge = Ui.text(MainActivity.this, "", 14, Ui.TEXT);
                hd.badge.setTypeface(Typeface.DEFAULT_BOLD);
                hd.badge.setBackgroundColor(Ui.BLUE);
                hd.badge.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 4));
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bl.gravity = Gravity.TOP | Gravity.END;
                bl.topMargin = Ui.dp(MainActivity.this, 16);
                card.addView(hd.badge, bl);
                outer.addView(card);
                outer.setTag(hd);
                cv = outer;
            } else hd = (Holder) cv.getTag();
            Library.Study s = shown.get(pos);
            hd.name.setText(s.displayName(false));
            hd.desc.setText(s.desc.isEmpty() ? (s.series.isEmpty() ? "" : s.series.get(0).bodyPart) : s.desc);
            String src = Store.source(MainActivity.this, s.uid);
            hd.source.setText(src);
            hd.source.setVisibility(src.isEmpty() ? View.GONE : View.VISIBLE);
            hd.date.setText(Library.fmtDateLong(s.date));
            hd.size.setText(Library.fmtSize(s.bytes()));
            String mod = s.modalities();
            hd.badge.setText(mod.isEmpty() ? "?" : mod);
            Library.Series se = s.firstImageSeries();
            Bitmap bm = se == null ? null : Library.thumbnail(se, new Library.ThumbCallback() {
                public void done() { h.post(new Runnable() { public void run() { studyAdapter.notifyDataSetChanged(); } }); }
            });
            hd.thumb.setImageBitmap(bm);
            return cv;
        }
    }
}
