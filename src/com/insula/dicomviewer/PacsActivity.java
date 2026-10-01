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
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** DICOMweb client with one profile per institution: QIDO-RS study search, WADO-RS retrieval. */
public class PacsActivity extends BaseActivity {
    static final String[] AUTH = {"basic", "bearer", "none"};
    static final String[] AUTH_LABELS = {"Username and password", "Access token", "No sign-in"};

    static final class Result { Store.Profile p; String uid, name, id, date, mods, desc, count; }

    EditText name, pid, date, modality;
    TextView status, profileName, profileSub;
    final List<Result> results = new ArrayList<>();
    BaseAdapter adapter;
    final Handler h = new Handler(Looper.getMainLooper());
    Ui.Busy pd;
    volatile boolean cancel;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout screen = Ui.col(this);
        screen.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        top.addView(Ui.icon(this, "back", new View.OnClickListener() { public void onClick(View v) { finish(); } }));
        TextView t = Ui.title(this, "DICOM query");
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(t, Ui.wrapWeight(1));
        top.addView(Ui.icon(this, "settings", new View.OnClickListener() { public void onClick(View v) { manageProfiles(); } }));
        screen.addView(top);

        // Profile selector card
        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.ripple(Ui.rounded(Ui.CARD, Ui.dp(this, 8))));
        int cp = Ui.dp(this, 14);
        card.setPadding(cp, cp, cp, cp);
        card.addView(Ui.iconView(this, "server", 30, Ui.ACCENT));
        LinearLayout cc = Ui.col(this);
        cc.setPadding(Ui.dp(this, 14), 0, 0, 0);
        profileName = Ui.text(this, "", 17, Ui.TEXT);
        profileName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        profileSub = Ui.text(this, "", 13, Ui.SUB);
        profileSub.setSingleLine(true);
        profileSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cc.addView(profileName);
        cc.addView(profileSub);
        card.addView(cc, Ui.wrapWeight(1));
        card.addView(Ui.iconView(this, "chevron", 24, Ui.TEXT));
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickProfile(); } });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(this, 12);
        cl.setMargins(m, m, m, Ui.dp(this, 4));
        screen.addView(card, cl);

        LinearLayout body = Ui.col(this);
        body.setPadding(m, 0, m, 0);
        name = Ui.field(this, "Patient name (wildcards * allowed)");
        pid = Ui.field(this, "Patient ID");
        LinearLayout r2 = Ui.row(this);
        date = Ui.field(this, "Date YYYYMMDD or range");
        date.setInputType(InputType.TYPE_CLASS_TEXT);
        modality = Ui.field(this, "Modality");
        r2.addView(date, Ui.wrapWeight(1.6f));
        r2.addView(modality, Ui.wrapWeight(1));
        body.addView(name);
        body.addView(pid);
        body.addView(r2);
        LinearLayout chips = Ui.row(this);
        final String[] dl = {"Any date", "Today", "Yesterday", "Last 7 days", "Last 30 days"};
        for (int i = 0; i < dl.length; i++) {
            final int k = i;
            chips.addView(Ui.btn(this, dl[i], new View.OnClickListener() { public void onClick(View v) { date.setText(dateRange(k)); } }));
        }
        body.addView(Ui.hscroll(this, chips));
        Button search = Ui.pill(this, "Search", "search", new View.OnClickListener() { public void onClick(View v) { search(); } });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 6);
        body.addView(search, sl);
        status = Ui.text(this, "", 13, Ui.SUB);
        status.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, Ui.dp(this, 4));
        body.addView(status);
        screen.addView(body);

        ListView lv = new ListView(this);
        lv.setDividerHeight(0);
        adapter = new BaseAdapter() {
            public int getCount() { return results.size(); }
            public Object getItem(int i) { return results.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) { return resultRow(results.get(pos)); }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                final Result r = results.get(pos);
                new AlertDialog.Builder(PacsActivity.this).setTitle("Download study?")
                        .setMessage((r.name.isEmpty() ? "(no name)" : r.name) + "\n" + Library.fmtDateLong(r.date) + "   " + r.mods + "\n" + r.desc + "\n\nFrom " + r.p.label())
                        .setPositiveButton("Download", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { download(r); } })
                        .setNegativeButton("Cancel", null).show();
            }
        });
        screen.addView(lv, Ui.vweight(1));
        setContentView(screen);

        updateProfileCard();
        if (Store.profiles(this).isEmpty()) {
            status.setText("Add a PACS profile for each hospital or clinic you work with.");
            editProfile(null);
        }
    }

    View resultRow(Result r) {
        LinearLayout row = Ui.col(this);
        row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        TextView a = Ui.text(this, (r.name.isEmpty() ? "(no name)" : r.name) + (r.id.isEmpty() ? "" : "   ID " + r.id), 16, Ui.TEXT);
        a.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(a);
        row.addView(Ui.text(this, Library.fmtDateLong(r.date) + "   " + r.mods + "   " + r.desc + (r.count.isEmpty() ? "" : "   " + r.count + " images"), 13.5f, Ui.SUB));
        TextView src = Ui.text(this, r.p.label(), 13, Ui.VALUE);
        row.addView(src);
        return row;
    }

    // ---------------- profiles ----------------
    List<Store.Profile> targets() {
        List<Store.Profile> all = Store.profiles(this), out = new ArrayList<>();
        String act = Store.activeProfile(this);
        for (Store.Profile p : all) if (Store.ALL.equals(act) || p.id.equals(act)) out.add(p);
        return out;
    }

    void updateProfileCard() {
        List<Store.Profile> all = Store.profiles(this);
        String act = Store.activeProfile(this);
        if (all.isEmpty()) { profileName.setText("No PACS profile yet"); profileSub.setText("Tap to add your first server"); return; }
        if (Store.ALL.equals(act)) {
            profileName.setText("All profiles");
            StringBuilder sb = new StringBuilder();
            for (Store.Profile p : all) { if (sb.length() > 0) sb.append(", "); sb.append(p.name); }
            profileSub.setText("Searches " + all.size() + " servers: " + sb);
            return;
        }
        Store.Profile p = Store.profile(this, act);
        if (p == null) p = all.get(0);
        profileName.setText(p.name);
        profileSub.setText((p.institution.isEmpty() ? "" : p.institution + "   ") + p.base());
    }

    void pickProfile() {
        final List<Store.Profile> all = Store.profiles(this);
        if (all.isEmpty()) { editProfile(null); return; }
        final List<String> labels = new ArrayList<>();
        final List<String> icons = new ArrayList<>();
        for (Store.Profile p : all) { labels.add(p.label()); icons.add("server"); }
        if (all.size() > 1) { labels.add("All profiles"); icons.add("layers"); }
        labels.add("Add profile"); icons.add("plus");
        labels.add("Manage profiles"); icons.add("settings");
        Ui.choices(this, "PACS profile", null, icons.toArray(new String[0]), labels.toArray(new String[0]), new Ui.OnChoice() {
            public void choose(int i) {
                if (i < all.size()) Store.setActiveProfile(PacsActivity.this, all.get(i).id);
                else if (all.size() > 1 && i == all.size()) Store.setActiveProfile(PacsActivity.this, Store.ALL);
                else if (labels.get(i).equals("Add profile")) { editProfile(null); return; }
                else { manageProfiles(); return; }
                results.clear();
                adapter.notifyDataSetChanged();
                status.setText("");
                updateProfileCard();
            }
        }).show();
    }

    void manageProfiles() {
        final List<Store.Profile> all = Store.profiles(this);
        final List<String> labels = new ArrayList<>();
        final List<String> icons = new ArrayList<>();
        for (Store.Profile p : all) { labels.add(p.label()); icons.add("server"); }
        labels.add("Add profile"); icons.add("plus");
        Ui.choices(this, "Manage profiles", all.isEmpty() ? null : "Tap a profile to edit it", icons.toArray(new String[0]), labels.toArray(new String[0]), new Ui.OnChoice() {
            public void choose(int i) { editProfile(i < all.size() ? all.get(i) : null); }
        }).show();
    }

    void editProfile(final Store.Profile existing) {
        final Store.Profile p = existing == null ? new Store.Profile() : existing.copy();
        LinearLayout l = Ui.col(this);
        int pad = Ui.dp(this, 20);
        l.setPadding(pad, pad / 2, pad, 0);
        final EditText nm = Ui.field(this, "Profile name, e.g. City Hospital");
        final EditText inst = Ui.field(this, "Institution (optional)");
        final EditText url = Ui.field(this, "https://pacs.example.org/dicom-web");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText user = Ui.field(this, "Username");
        final EditText secret = Ui.field(this, "Password");
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        nm.setText(p.name); inst.setText(p.institution); url.setText(p.url); user.setText(p.user); secret.setText(p.secret);
        l.addView(label("Name")); l.addView(nm);
        l.addView(label("Institution")); l.addView(inst);
        l.addView(label("DICOMweb address")); l.addView(url);
        l.addView(Ui.text(this, "The DICOMweb root, e.g. https://host/dicom-web. For Orthanc: http://host:8042/dicom-web", 12, Ui.SUB));
        l.addView(label("Sign-in"));
        final int[] auth = {Math.max(0, java.util.Arrays.asList(AUTH).indexOf(p.auth))};
        final Button authBtn = Ui.btn(this, AUTH_LABELS[auth[0]], null);
        l.addView(authBtn);
        final Runnable applyAuth = new Runnable() {
            public void run() {
                authBtn.setText(AUTH_LABELS[auth[0]] + "  ▾");
                user.setVisibility(auth[0] == 0 ? View.VISIBLE : View.GONE);
                secret.setVisibility(auth[0] == 2 ? View.GONE : View.VISIBLE);
                secret.setHint(auth[0] == 1 ? "Access token" : "Password");
            }
        };
        authBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(PacsActivity.this).setTitle("Sign-in").setSingleChoiceItems(AUTH_LABELS, auth[0], new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { auth[0] = w; applyAuth.run(); d.dismiss(); }
                }).show();
            }
        });
        applyAuth.run();
        l.addView(user);
        l.addView(secret);
        TextView secNote = Ui.text(this, "Passwords and tokens are encrypted with this phone's hardware-backed keystore.", 12, Ui.SUB);
        l.addView(secNote);
        final TextView testResult = Ui.text(this, "", 13.5f, Ui.SUB);
        testResult.setPadding(0, Ui.dp(this, 6), 0, 0);
        Button test = Ui.btn(this, "Test connection", new View.OnClickListener() {
            public void onClick(View v) {
                final Store.Profile t = p.copy();
                t.url = url.getText().toString().trim(); t.user = user.getText().toString().trim();
                t.secret = secret.getText().toString(); t.auth = AUTH[auth[0]];
                testResult.setTextColor(Ui.SUB);
                testResult.setText("Connecting… this can take up to a minute if the app has to look for the DICOMweb path.");
                new Thread() {
                    public void run() {
                        final String[] r = probe(t);
                        final boolean fo = r[0] != null && !r[1].contains("refused");
                        h.post(new Runnable() {
                            public void run() {
                                if (r[0] != null && !r[0].equals(t.base())) url.setText(r[0]);
                                testResult.setTextColor(fo ? 0xFF81C784 : Ui.WARN);
                                testResult.setText(r[1]);
                            }
                        });
                    }
                }.start();
            }
        });
        l.addView(test);
        l.addView(testResult);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(existing == null ? "New PACS profile" : "Edit profile").setView(sv)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null);
        if (existing != null) b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                Ui.confirm(PacsActivity.this, "Delete the profile \"" + existing.name + "\"? Downloaded studies stay in your library.", "Delete", new Runnable() {
                    public void run() {
                        List<Store.Profile> all = Store.profiles(PacsActivity.this);
                        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(existing.id)) { all.remove(i); break; }
                        Store.saveProfiles(PacsActivity.this, all);
                        updateProfileCard();
                    }
                });
            }
        });
        final AlertDialog d = b.create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String u = url.getText().toString().trim();
                if (nm.getText().toString().trim().isEmpty()) { nm.setError("Give this profile a name"); return; }
                if (!u.startsWith("http://") && !u.startsWith("https://")) { url.setError("Start with https:// (or http:// on a local network)"); return; }
                p.name = nm.getText().toString().trim();
                p.institution = inst.getText().toString().trim();
                p.url = u;
                p.auth = AUTH[auth[0]];
                p.user = p.auth.equals("basic") ? user.getText().toString().trim() : "";
                p.secret = p.auth.equals("none") ? "" : secret.getText().toString();
                List<Store.Profile> all = Store.profiles(PacsActivity.this);
                boolean found = false;
                for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(p.id)) { all.set(i, p); found = true; }
                if (!found) all.add(p);
                boolean secure = Store.saveProfiles(PacsActivity.this, all);
                if (!found) Store.setActiveProfile(PacsActivity.this, p.id);
                d.dismiss();
                updateProfileCard();
                if (!secure) Ui.toast(PacsActivity.this, "This phone couldn't encrypt the password, so it's kept only until you close the app.");
                else if (u.startsWith("http://")) Ui.toast(PacsActivity.this, "Saved. Plain http:// is unencrypted; use it only on a trusted local network.");
                else Ui.toast(PacsActivity.this, "Saved " + p.name + ".");
            }
        });
    }

    TextView label(String s) {
        TextView t = Ui.text(this, s, 13, Ui.ACCENT);
        t.setPadding(0, Ui.dp(this, 12), 0, 0);
        return t;
    }

    String dateRange(int k) {
        if (k == 0) return "";
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        Calendar c = Calendar.getInstance();
        String today = f.format(c.getTime());
        if (k == 1) return today;
        if (k == 2) { c.add(Calendar.DAY_OF_YEAR, -1); return f.format(c.getTime()); }
        c.add(Calendar.DAY_OF_YEAR, k == 3 ? -6 : -29);
        return f.format(c.getTime()) + "-" + today;
    }

    // ---------------- network ----------------
    /** An HTTP error with its status code and a plain-language explanation. */
    static final class HttpErr extends Exception {
        final int code;
        HttpErr(int code, String msg) { super(msg); this.code = code; }
    }

    static String explain(int code) {
        switch (code) {
            case 400: return "HTTP 400: the server rejected the search. It may not support some search options.";
            case 401: case 403: return "HTTP " + code + ": sign-in refused. Check the username and password or token.";
            case 404: return "HTTP 404: nothing at this address. Check the DICOMweb path (for example /dicom-web).";
            case 405: return "HTTP 405: this address doesn't accept DICOMweb requests. It's probably not the DICOMweb root.";
            case 406: case 415: return "HTTP " + code + ": the server refused the DICOMweb data format. The address is probably the PACS website, not its DICOMweb root. Use Test connection in the profile to look for the right path, or ask IT for the DICOMweb (QIDO-RS) URL.";
            case 407: return "HTTP 407: a network proxy needs sign-in. Try another network.";
            case 429: return "HTTP 429: too many requests. Wait a minute and try again.";
            default: return code >= 500 ? "HTTP " + code + ": the server had an internal error. Try again later or contact its IT team." : "HTTP " + code;
        }
    }

    static byte[] http(Store.Profile p, String url, String accept, String[] ctOut) throws Exception { return http(p, url, accept, ctOut, 15000); }

    static byte[] http(Store.Profile p, String url, String accept, String[] ctOut, int timeout) throws Exception {
        HttpURLConnection c;
        try { c = (HttpURLConnection) new URL(url).openConnection(); }
        catch (java.net.MalformedURLException e) { throw new Exception("The address isn't a valid URL."); }
        c.setConnectTimeout(timeout);
        c.setReadTimeout(Math.max(timeout, 60000));
        c.setRequestProperty("Accept", accept);
        if ("basic".equals(p.auth) && !p.user.isEmpty()) {
            String tok = Base64.encodeToString((p.user + ":" + p.secret).getBytes("UTF-8"), Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + tok);
        } else if ("bearer".equals(p.auth) && !p.secret.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + p.secret.trim());
        }
        int code;
        try { code = c.getResponseCode(); }
        catch (java.net.UnknownHostException e) { throw new Exception("Can't find the server " + new URL(url).getHost() + ". Check the address and your internet connection. Hospital PACS servers are often reachable only on the hospital network or VPN."); }
        catch (java.net.SocketTimeoutException e) { throw new Exception("The server didn't answer in time. It may be reachable only on the hospital network or VPN."); }
        catch (javax.net.ssl.SSLException e) { throw new Exception("Secure connection failed (" + e.getMessage() + "). The server's certificate may be self-signed or expired."); }
        catch (java.net.ConnectException e) { throw new Exception("Connection refused. Check the address and port."); }
        if (code == 204) return new byte[0];
        if (code >= 400) {
            try { InputStream es = c.getErrorStream(); if (es != null) { Library.readAll(es); es.close(); } } catch (Exception ignored) { }
            throw new HttpErr(code, explain(code));
        }
        if (ctOut != null) ctOut[0] = c.getContentType();
        InputStream in = c.getInputStream();
        byte[] d = Library.readAll(in);
        in.close();   // closing (not disconnecting) keeps the connection alive for the next request
        return d;
    }

    static final String[] JSON_TYPES = {"application/dicom+json", "application/json", "application/dicom+json, application/json;q=0.9, */*;q=0.1"};

    /** GETs a DICOMweb JSON resource, trying other Accept types if the server refuses the standard one. */
    static byte[] json(Store.Profile p, String url) throws Exception { return json(p, url, 15000); }

    static byte[] json(Store.Profile p, String url, int timeout) throws Exception {
        HttpErr last = null;
        for (String a : JSON_TYPES) {
            String[] ct = new String[1];
            byte[] d;
            try { d = http(p, url, a, ct, timeout); }
            catch (HttpErr e) { if (e.code == 406 || e.code == 415) { last = e; continue; } throw e; }
            String t = ct[0] == null ? "" : ct[0].toLowerCase(Locale.ROOT);
            String head = new String(d, 0, Math.min(d.length, 64), "UTF-8").trim().toLowerCase(Locale.ROOT);
            if (t.contains("text/html") || head.startsWith("<!doctype") || head.startsWith("<html"))
                throw new Exception("The address returned a web page instead of DICOMweb data. It's probably the PACS website or its login page, not the DICOMweb root.");
            if (d.length > 0 && !(head.startsWith("[") || head.startsWith("{")))
                throw new Exception("The server's answer isn't DICOMweb JSON. Check that the address is the DICOMweb (QIDO-RS) root.");
            return d;
        }
        throw last;
    }

    static final String[] PATHS = {"/dicom-web", "/dicomweb", "/dicomWeb", "/wado-rs", "/rs", "/dcm4chee-arc/aets/DCM4CHEE/rs", "/orthanc/dicom-web", "/pacs/dicom-web", "/api/dicom-web", "/DICOMweb"};

    /** Tries the address, then common DICOMweb paths. Returns {workingBase or null, message}. */
    static String[] probe(Store.Profile t) {
        String base = t.base();
        String first;
        try {
            json(t, base + "/studies?limit=1", 10000);
            return new String[]{base, "Connected. The server answered a study search."};
        } catch (HttpErr e) {
            if (e.code == 401 || e.code == 403) return new String[]{null, e.getMessage() + " The address itself looks right."};
            first = e.getMessage();
        } catch (Exception e) {
            first = e.getMessage();
            if (first != null && (first.startsWith("Can't find") || first.startsWith("Connection refused") || first.startsWith("The server didn't") || first.startsWith("Secure connection") || first.startsWith("The address isn't")))
                return new String[]{null, first};
        }
        java.util.LinkedHashSet<String> cands = new java.util.LinkedHashSet<>();
        String root = base;
        try { URL u = new URL(base); root = u.getProtocol() + "://" + u.getAuthority(); } catch (Exception ignored) { }
        for (String s : PATHS) { cands.add(base + s); cands.add(root + s); }
        cands.remove(base);
        for (String cnd : cands) {
            try {
                json(t, cnd + "/studies?limit=1", 6000);
                return new String[]{cnd, "Found DICOMweb at " + cnd + ". The address has been updated; tap Save."};
            } catch (HttpErr e) {
                if (e.code == 401 || e.code == 403) return new String[]{cnd, "Found DICOMweb at " + cnd + ", but sign-in was refused. The address has been updated; check the sign-in details, then tap Save."};
            } catch (Exception ignored) { }
        }
        return new String[]{null, first + "\n\nAlso tried " + cands.size() + " common DICOMweb paths without success. Ask the hospital's IT team for the DICOMweb (QIDO-RS/WADO-RS) URL, and whether it's reachable from outside the hospital network."};
    }

    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }

    static String jv(JSONObject o, String tag) {
        JSONObject a = o.optJSONObject(tag);
        if (a == null) return "";
        JSONArray v = a.optJSONArray("Value");
        if (v == null || v.length() == 0) return "";
        Object x = v.opt(0);
        if (x instanceof JSONObject) return Library.pn(((JSONObject) x).optString("Alphabetic", ""));
        if (v.length() > 1 && "CS".equals(a.optString("vr"))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < v.length(); i++) { if (i > 0) sb.append('/'); sb.append(v.optString(i)); }
            return sb.toString();
        }
        return String.valueOf(x);
    }

    void search() {
        final List<Store.Profile> ts = targets();
        if (ts.isEmpty()) { editProfile(null); return; }
        StringBuilder q = new StringBuilder();
        String nm = name.getText().toString().trim();
        if (!nm.isEmpty()) q.append("&PatientName=").append(enc(nm.contains("*") ? nm : nm + "*"));
        String id = pid.getText().toString().trim();
        if (!id.isEmpty()) q.append("&PatientID=").append(enc(id));
        String dt = date.getText().toString().trim();
        if (!dt.isEmpty()) q.append("&StudyDate=").append(enc(dt));
        String mo = modality.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (!mo.isEmpty()) q.append("&ModalitiesInStudy=").append(enc(mo));
        final String filters = q.toString();
        final String query = "/studies?limit=100&includefield=00081030&includefield=00080061&includefield=00201206&includefield=00201208" + filters + (nm.isEmpty() ? "" : "&fuzzymatching=true");
        final String plain = "/studies?limit=100" + filters;
        status.setText(ts.size() == 1 ? "Searching " + ts.get(0).name + "…" : "Searching " + ts.size() + " servers…");
        new Thread() {
            public void run() {
                final List<Result> res = new ArrayList<>();
                final StringBuilder errs = new StringBuilder();
                for (Store.Profile p : ts) {
                    try {
                        byte[] d;
                        try { d = json(p, p.base() + query); }
                        catch (HttpErr e) { if (e.code == 400) d = json(p, p.base() + plain); else throw e; }
                        JSONArray a = d.length == 0 ? new JSONArray() : new JSONArray(new String(d, "UTF-8"));
                        for (int i = 0; i < a.length(); i++) {
                            JSONObject o = a.getJSONObject(i);
                            Result r = new Result();
                            r.p = p; r.uid = jv(o, "0020000D"); r.name = jv(o, "00100010"); r.id = jv(o, "00100020");
                            r.date = jv(o, "00080020"); r.mods = jv(o, "00080061"); r.desc = jv(o, "00081030"); r.count = jv(o, "00201208");
                            res.add(r);
                        }
                    } catch (Exception e) {
                        if (errs.length() > 0) errs.append("\n");
                        errs.append(p.name).append(": ").append(e.getMessage());
                    }
                }
                java.util.Collections.sort(res, new java.util.Comparator<Result>() {
                    public int compare(Result a, Result b) { return b.date.compareTo(a.date); }
                });
                h.post(new Runnable() {
                    public void run() {
                        results.clear();
                        results.addAll(res);
                        adapter.notifyDataSetChanged();
                        String s = res.size() + " stud" + (res.size() == 1 ? "y" : "ies") + " found" + (res.isEmpty() ? "." : ". Tap one to download.");
                        status.setText(errs.length() > 0 ? s + "\n" + errs : s);
                        status.setTextColor(errs.length() > 0 && res.isEmpty() ? Ui.WARN : Ui.SUB);
                    }
                });
            }
        }.start();
    }

    Downloader current;

    void download(final Result r) {
        final Downloader dl = new Downloader(r.p, r.uid);
        current = dl;
        pd = new Ui.Busy(this);
        pd.setMessage("Listing series…");
        pd.setCancelable(false);
        pd.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { dl.cancel = true; } });
        pd.show();
        final long t0 = System.currentTimeMillis();
        new Thread() {
            public void run() {
                final String err = dl.run(new Downloader.Listener() {
                    public void progress(final int done, final int total, final long bytes) {
                        h.post(new Runnable() {
                            public void run() {
                                if (pd == null) return;
                                double secs = Math.max(0.5, (System.currentTimeMillis() - t0) / 1000.0);
                                String rate = Library.fmtSize((long) (bytes / secs)) + "/s";
                                pd.setMessage("Downloading from " + r.p.name + "\n" + done + (total > 0 ? " of " + total : "") + " images\n"
                                        + Library.fmtSize(bytes) + " at " + rate);
                            }
                        });
                    }
                });
                final int[] st = dl.stats;
                if (st[0] + st[2] > 0) Store.setSource(PacsActivity.this, r.uid, r.p.label());
                final long secs = Math.max(1, (System.currentTimeMillis() - t0) / 1000);
                final String speed = Library.fmtSize(dl.bytes.get()) + " in " + secs + " s";
                Store.log(PacsActivity.this, "server", "Download from " + r.p.name,
                        (err != null ? "Stopped: " + err + ". " : "") + st[0] + " images saved" + (st[2] > 0 ? ", " + st[2] + " already in library" : "") + ", " + speed, err == null);
                h.post(new Runnable() {
                    public void run() {
                        if (pd != null) pd.dismiss();
                        if (err != null) Ui.toast(PacsActivity.this, "Download stopped: " + err + " (" + st[0] + " images saved)");
                        else Ui.toast(PacsActivity.this, (dl.cancel ? "Cancelled. " : "") + st[0] + " images downloaded" + (st[2] > 0 ? ", " + st[2] + " already in library" : "") + " (" + speed + ").");
                    }
                });
            }
        }.start();
    }

    static List<byte[]> multipart(byte[] body, String ct) {
        List<byte[]> parts = new ArrayList<>();
        if (ct == null || !ct.toLowerCase(Locale.ROOT).contains("multipart")) { parts.add(body); return parts; }
        String boundary = null;
        for (String p : ct.split(";")) {
            p = p.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("boundary=")) { boundary = p.substring(9).trim(); if (boundary.startsWith("\"")) boundary = boundary.substring(1, boundary.length() - 1); }
        }
        if (boundary == null) { parts.add(body); return parts; }
        byte[] delim = ("--" + boundary).getBytes(), next = ("\r\n--" + boundary).getBytes(), hdrEnd = "\r\n\r\n".getBytes();
        int idx = indexOf(body, delim, 0);
        while (idx >= 0) {
            int s = idx + delim.length;
            if (s + 1 < body.length && body[s] == '-' && body[s + 1] == '-') break;
            int he = indexOf(body, hdrEnd, s);
            if (he < 0) break;
            int ds = he + 4;
            int ne = indexOf(body, next, ds);
            if (ne < 0) ne = body.length;
            byte[] part = new byte[ne - ds];
            System.arraycopy(body, ds, part, 0, part.length);
            parts.add(part);
            idx = ne + 2;
            if (ne >= body.length) break;
        }
        return parts;
    }

    static int indexOf(byte[] a, byte[] p, int from) {
        outer:
        for (int i = Math.max(0, from); i <= a.length - p.length; i++) {
            if (a[i] != p[0]) continue;
            for (int j = 1; j < p.length; j++) if (a[i + j] != p[j]) continue outer;
            return i;
        }
        return -1;
    }
}
