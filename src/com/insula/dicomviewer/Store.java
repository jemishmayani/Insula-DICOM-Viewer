/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small persisted state: albums (named groups of studies) and the transfer history. */
final class Store {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("insula_store", Context.MODE_PRIVATE); }

    // ---- Albums ----
    static synchronized Map<String, List<String>> albums(Context c) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(sp(c).getString("albums", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = o.getJSONArray(k);
                List<String> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
                m.put(k, l);
            }
        } catch (Exception ignored) { }
        return m;
    }

    static synchronized void saveAlbums(Context c, Map<String, List<String>> m) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, List<String>> e : m.entrySet()) o.put(e.getKey(), new JSONArray(e.getValue()));
            sp(c).edit().putString("albums", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void createAlbum(Context c, String name) {
        Map<String, List<String>> m = albums(c);
        if (!m.containsKey(name)) m.put(name, new ArrayList<String>());
        saveAlbums(c, m);
    }

    static void addToAlbum(Context c, String name, String studyUid) {
        Map<String, List<String>> m = albums(c);
        List<String> l = m.get(name);
        if (l == null) { l = new ArrayList<>(); m.put(name, l); }
        if (!l.contains(studyUid)) l.add(studyUid);
        saveAlbums(c, m);
    }

    static void removeFromAlbum(Context c, String name, String studyUid) {
        Map<String, List<String>> m = albums(c);
        List<String> l = m.get(name);
        if (l != null) l.remove(studyUid);
        saveAlbums(c, m);
    }

    static void deleteAlbum(Context c, String name) {
        Map<String, List<String>> m = albums(c);
        m.remove(name);
        saveAlbums(c, m);
    }

    static void renameAlbum(Context c, String from, String to) {
        Map<String, List<String>> m = albums(c), n = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : m.entrySet()) n.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        saveAlbums(c, n);
    }

    // ---- Transfers ----
    static final class Transfer {
        String kind, title, detail;
        long time;
        boolean ok;
    }

    static synchronized List<Transfer> transfers(Context c) {
        List<Transfer> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("transfers", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Transfer t = new Transfer();
                t.kind = o.optString("k"); t.title = o.optString("t"); t.detail = o.optString("d");
                t.time = o.optLong("time"); t.ok = o.optBoolean("ok", true);
                l.add(t);
            }
        } catch (Exception ignored) { }
        return l;
    }

    /** kind: file, folder, download, server, share */
    static synchronized void log(Context c, String kind, String title, String detail, boolean ok) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("transfers", "[]")), n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("k", kind); o.put("t", title); o.put("d", detail); o.put("time", System.currentTimeMillis()); o.put("ok", ok);
            n.put(o);
            for (int i = 0; i < a.length() && i < 99; i++) n.put(a.get(i));
            sp(c).edit().putString("transfers", n.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void clearTransfers(Context c) { sp(c).edit().remove("transfers").apply(); }

    // ---- PACS profiles ----
    static final class Profile {
        String id = UUID.randomUUID().toString(), name = "", institution = "", url = "", user = "", secret = "", auth = "basic";

        String base() {
            String u = url.trim();
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            return u;
        }

        String label() { return institution.isEmpty() || institution.equalsIgnoreCase(name) ? name : name + " (" + institution + ")"; }

        Profile copy() {
            Profile p = new Profile();
            p.id = id; p.name = name; p.institution = institution; p.url = url; p.user = user; p.secret = secret; p.auth = auth;
            return p;
        }
    }

    static final String ALL = "*";
    /** Secrets the device couldn't encrypt are kept only for this session, never written in plain text. */
    static final Map<String, String> sessionSecrets = new HashMap<>();

    static synchronized List<Profile> profiles(Context c) {
        migrateLegacy(c);
        List<Profile> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("pacs_profiles", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.id = o.optString("id", p.id); p.name = o.optString("name"); p.institution = o.optString("inst");
                p.url = o.optString("url"); p.user = o.optString("user"); p.auth = o.optString("auth", "basic");
                p.secret = Vault.dec(o.optString("secret"));
                if (p.secret.isEmpty() && sessionSecrets.containsKey(p.id)) p.secret = sessionSecrets.get(p.id);
                l.add(p);
            }
        } catch (Exception ignored) { }
        return l;
    }

    /** @return false if a secret couldn't be encrypted on this device (it is then kept for this session only). */
    static synchronized boolean saveProfiles(Context c, List<Profile> l) {
        boolean ok = true;
        try {
            JSONArray a = new JSONArray();
            for (Profile p : l) {
                String enc = Vault.enc(p.secret);
                if (enc == null) { ok = false; sessionSecrets.put(p.id, p.secret); enc = ""; }
                JSONObject o = new JSONObject();
                o.put("id", p.id); o.put("name", p.name); o.put("inst", p.institution); o.put("url", p.url);
                o.put("user", p.user); o.put("auth", p.auth); o.put("secret", enc);
                a.put(o);
            }
            sp(c).edit().putString("pacs_profiles", a.toString()).apply();
        } catch (Exception e) { ok = false; }
        return ok;
    }

    static Profile profile(Context c, String id) {
        for (Profile p : profiles(c)) if (p.id.equals(id)) return p;
        return null;
    }

    static String activeProfile(Context c) {
        String id = sp(c).getString("pacs_active", "");
        if (ALL.equals(id)) return ALL;
        List<Profile> l = profiles(c);
        for (Profile p : l) if (p.id.equals(id)) return id;
        return l.isEmpty() ? "" : l.get(0).id;
    }

    static void setActiveProfile(Context c, String id) { sp(c).edit().putString("pacs_active", id).apply(); }

    /** Moves the single server from version 1.0/1.1 into the first profile and removes the plain-text password. */
    static void migrateLegacy(Context c) {
        SharedPreferences old = Ui.prefs(c);
        String url = old.getString("pacs_url", "");
        if (url.trim().isEmpty() || sp(c).contains("pacs_profiles")) return;
        Profile p = new Profile();
        p.name = "My PACS";
        p.url = url.trim();
        p.user = old.getString("pacs_user", "");
        p.secret = old.getString("pacs_pass", "");
        p.auth = p.user.isEmpty() ? "none" : "basic";
        List<Profile> l = new ArrayList<>();
        l.add(p);
        saveProfiles(c, l);
        old.edit().remove("pacs_url").remove("pacs_user").remove("pacs_pass").apply();
    }

    // ---- Where each study came from ----
    static void setSource(Context c, String studyUid, String source) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("sources", "{}"));
            o.put(studyUid, source);
            sp(c).edit().putString("sources", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    static String source(Context c, String studyUid) {
        try { return new JSONObject(sp(c).getString("sources", "{}")).optString(studyUid, ""); } catch (Exception e) { return ""; }
    }

    // ---- Remembered window settings, per series ----
    static synchronized void saveWindow(Context c, String seriesUid, double wc, double ww) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("wl", "{}"));
            o.remove(seriesUid);
            JSONObject n = new JSONObject();
            Iterator<String> it = o.keys();
            int skip = Math.max(0, o.length() - 399);
            while (it.hasNext()) { String k = it.next(); if (skip > 0) { skip--; continue; } n.put(k, o.get(k)); }
            n.put(seriesUid, new JSONArray(new double[]{wc, ww}));
            sp(c).edit().putString("wl", n.toString()).apply();
        } catch (Exception ignored) { }
    }

    static synchronized double[] loadWindow(Context c, String seriesUid) {
        try {
            JSONArray a = new JSONObject(sp(c).getString("wl", "{}")).optJSONArray(seriesUid);
            if (a == null || a.length() < 2) return null;
            return new double[]{a.getDouble(0), a.getDouble(1)};
        } catch (Exception e) { return null; }
    }

    static synchronized void clearWindow(Context c, String seriesUid) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("wl", "{}"));
            o.remove(seriesUid);
            sp(c).edit().putString("wl", o.toString()).apply();
        } catch (Exception ignored) { }
    }
}
