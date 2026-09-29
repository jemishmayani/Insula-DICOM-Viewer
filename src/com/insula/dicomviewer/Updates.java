/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Asks GitHub for the latest published release. Runs only when the user taps "Check for updates". */
final class Updates {
    static final String REPO = "jemishmayani/Insula-DICOM-Viewer";
    static final String REPO_URL = "https://github.com/" + REPO;
    static final String RELEASES_URL = REPO_URL + "/releases";
    static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

    static final class Release {
        String tag, name, notes, pageUrl, apkUrl;
        long apkSize;
    }

    /** Parses GitHub's "latest release" JSON. */
    static Release parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Release r = new Release();
        r.tag = o.optString("tag_name", "");
        r.name = o.optString("name", r.tag);
        r.notes = o.optString("body", "");
        r.pageUrl = o.optString("html_url", RELEASES_URL);
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                    r.apkUrl = a.optString("browser_download_url");
                    r.apkSize = a.optLong("size");
                    break;
                }
            }
        }
        return r;
    }

    static Release fetchLatest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API_LATEST).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Insula-DICOM-Viewer");
        int code;
        try { code = c.getResponseCode(); }
        catch (java.net.UnknownHostException e) { throw new Exception("No internet connection, or GitHub can't be reached."); }
        catch (java.net.SocketTimeoutException e) { throw new Exception("GitHub didn't answer in time. Try again later."); }
        if (code == 404) throw new Exception("No release has been published yet.");
        if (code == 403 || code == 429) throw new Exception("GitHub is limiting requests from this network. Try again in an hour.");
        if (code >= 400) throw new Exception("GitHub answered HTTP " + code + ".");
        InputStream in = c.getInputStream();
        byte[] d = Library.readAll(in);
        in.close();
        return parse(new String(d, "UTF-8"));
    }

    /** Compares versions like "v1.6.0" and "1.5.1". Returns a positive number if a is newer than b. */
    static int compare(String a, String b) {
        int[] x = nums(a), y = nums(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    static int[] nums(String v) {
        String s = v == null ? "" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash);
        String[] p = s.split("\\.");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try { r[i] = Integer.parseInt(p[i].replaceAll("[^0-9]", "")); } catch (Exception e) { r[i] = 0; }
        }
        return r;
    }
}
