package com.insula.dicomviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Export and import of settings files and study sets. */
final class Backup {
    static final String SET_MANIFEST = "insula-studyset.json";
    static final String[] BOOL_PREFS = {"lock", "secure", "hidephi", "teacher"};

    static final class NeedPassphrase extends Exception { NeedPassphrase() { super("This file's passwords are protected. Enter the passphrase used when it was exported."); } }

    // ---------------- passphrase crypto (PBKDF2 + AES-GCM) ----------------
    static SecretKeySpec derive(String pass, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] k = f.generateSecret(new PBEKeySpec(pass.toCharArray(), salt, 20000, 256)).getEncoded();
        return new SecretKeySpec(k, "AES");
    }

    static String seal(String plain, SecretKeySpec key) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
        byte[] all = new byte[12 + ct.length];
        System.arraycopy(iv, 0, all, 0, 12);
        System.arraycopy(ct, 0, all, 12, ct.length);
        return "p1:" + Base64.encodeToString(all, Base64.NO_WRAP);
    }

    static String open(String sealed, SecretKeySpec key) throws Exception {
        byte[] all = Base64.decode(sealed.substring(3), Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), "UTF-8");
    }

    // ---------------- settings ----------------
    /** @param passphrase null to leave passwords and tokens out of the file. */
    static String exportSettings(Context c, String passphrase) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "insula-settings");
        o.put("version", 1);
        o.put("exported", System.currentTimeMillis());
        SharedPreferences p = Ui.prefs(c);
        JSONObject prefs = new JSONObject();
        for (String k : BOOL_PREFS) prefs.put(k, p.getBoolean(k, false));
        prefs.put("fps", p.getInt("fps", 10));
        o.put("prefs", prefs);
        JSONObject albums = new JSONObject();
        for (Map.Entry<String, List<String>> e : Store.albums(c).entrySet()) albums.put(e.getKey(), new JSONArray(e.getValue()));
        o.put("albums", albums);
        o.put("sources", new JSONObject(Store.sp(c).getString("sources", "{}")));
        SecretKeySpec key = null;
        if (passphrase != null) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            key = derive(passphrase, salt);
            o.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
            o.put("check", seal("insula", key));
        }
        JSONArray pacs = new JSONArray();
        for (Store.Profile pr : Store.profiles(c)) {
            JSONObject j = new JSONObject();
            j.put("id", pr.id); j.put("name", pr.name); j.put("inst", pr.institution); j.put("url", pr.url);
            j.put("user", pr.user); j.put("auth", pr.auth);
            if (key != null && !pr.secret.isEmpty()) j.put("secret", seal(pr.secret, key));
            pacs.put(j);
        }
        o.put("pacs", pacs);
        o.put("pacsActive", Store.activeProfile(c));
        return o.toString(2);
    }

    static boolean isSettings(String json) {
        try { return "insula-settings".equals(new JSONObject(json).optString("type")); } catch (Exception e) { return false; }
    }

    static boolean needsPassphrase(String json) {
        try { return new JSONObject(json).has("salt"); } catch (Exception e) { return false; }
    }

    /** Merges a settings file into this install. Returns a short summary. */
    static String importSettings(Context c, String json, String passphrase) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!"insula-settings".equals(o.optString("type"))) throw new Exception("This isn't an Insula settings file.");
        SecretKeySpec key = null;
        if (o.has("salt") && passphrase != null) {
            key = derive(passphrase, Base64.decode(o.getString("salt"), Base64.NO_WRAP));
            try { if (!"insula".equals(open(o.getString("check"), key))) throw new Exception(); }
            catch (Exception e) { throw new Exception("That passphrase doesn't match this file."); }
        }
        List<String> done = new ArrayList<>();
        JSONObject prefs = o.optJSONObject("prefs");
        if (prefs != null) {
            SharedPreferences.Editor ed = Ui.prefs(c).edit();
            for (String k : BOOL_PREFS) if (prefs.has(k)) ed.putBoolean(k, prefs.getBoolean(k));
            if (prefs.has("fps")) ed.putInt("fps", prefs.getInt("fps"));
            ed.apply();
            done.add("preferences");
        }
        JSONObject albums = o.optJSONObject("albums");
        int na = 0;
        if (albums != null) {
            Iterator<String> it = albums.keys();
            while (it.hasNext()) {
                String name = it.next();
                JSONArray a = albums.getJSONArray(name);
                Store.createAlbum(c, name);
                for (int i = 0; i < a.length(); i++) Store.addToAlbum(c, name, a.getString(i));
                na++;
            }
        }
        if (na > 0) done.add(na + " album" + (na == 1 ? "" : "s"));
        JSONObject src = o.optJSONObject("sources");
        if (src != null) { Iterator<String> it = src.keys(); while (it.hasNext()) { String k = it.next(); Store.setSource(c, k, src.getString(k)); } }
        JSONArray pacs = o.optJSONArray("pacs");
        int np = 0, secrets = 0;
        if (pacs != null) {
            List<Store.Profile> mine = Store.profiles(c);
            for (int i = 0; i < pacs.length(); i++) {
                JSONObject j = pacs.getJSONObject(i);
                Store.Profile p = null;
                for (Store.Profile q : mine) if (q.id.equals(j.optString("id"))) p = q;
                if (p == null) { p = new Store.Profile(); p.id = j.optString("id", p.id); mine.add(p); }
                p.name = j.optString("name"); p.institution = j.optString("inst"); p.url = j.optString("url");
                p.user = j.optString("user"); p.auth = j.optString("auth", "basic");
                if (key != null && j.has("secret")) { p.secret = open(j.getString("secret"), key); secrets++; }
                np++;
            }
            Store.saveProfiles(c, mine);
            String act = o.optString("pacsActive", "");
            if (!act.isEmpty()) Store.setActiveProfile(c, act);
        }
        if (np > 0) done.add(np + " PACS profile" + (np == 1 ? "" : "s") + (secrets > 0 ? " with sign-in details" : o.has("salt") ? "" : " (enter their passwords again)"));
        return done.isEmpty() ? "Nothing to import." : "Imported " + join(done) + ".";
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(i == l.size() - 1 ? " and " : ", "); sb.append(l.get(i)); }
        return sb.toString();
    }

    // ---------------- study sets ----------------
    /** Writes a ZIP with the studies' DICOM files plus a manifest of measurements, key images, and albums. */
    static int exportStudySet(Context c, List<Library.Study> studies, String name, boolean anon, File out, Library.Progress prog) throws Exception {
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
        Set<String> sops = new HashSet<>(), uids = new HashSet<>();
        JSONArray list = new JSONArray();
        int n = 0, total = 0;
        for (Library.Study s : studies) total += s.imageCount();
        try {
            int si = 0;
            for (Library.Study s : studies) {
                si++;
                uids.add(s.uid);
                JSONObject js = new JSONObject();
                js.put("uid", s.uid);
                js.put("date", s.date);
                js.put("description", s.desc);
                js.put("modalities", s.modalities());
                if (!anon) { js.put("patient", s.patientName); js.put("source", Store.source(c, s.uid)); }
                list.put(js);
                for (Library.Series se : s.series) {
                    for (Library.ImageInfo im : se.images) {
                        byte[] d = Library.readFile(im.file);
                        if (anon) {
                            Dicom.DataSet ds;
                            try { ds = Dicom.parse(d); } catch (Exception e) { continue; }
                            if (ds.deflated) continue;
                            Anonymizer.scrub(ds);
                        }
                        z.putNextEntry(new ZipEntry("dicom/study" + si + "/" + String.format(java.util.Locale.ROOT, "%05d", ++n) + ".dcm"));
                        z.write(d);
                        z.closeEntry();
                        sops.add(im.sopUid);
                        if (prog != null && n % 10 == 0) prog.update(n, total, "Packing");
                    }
                }
            }
            JSONObject m = AnnStore.toJson(sops);
            m.put("type", "insula-studyset");
            m.put("version", 1);
            m.put("name", name);
            m.put("created", System.currentTimeMillis());
            m.put("anonymized", anon);
            m.put("studies", list);
            JSONObject albums = new JSONObject();
            for (Map.Entry<String, List<String>> e : Store.albums(c).entrySet()) {
                JSONArray a = new JSONArray();
                for (String u : e.getValue()) if (uids.contains(u)) a.put(u);
                if (a.length() > 0) albums.put(e.getKey(), a);
            }
            m.put("albums", albums);
            z.putNextEntry(new ZipEntry(SET_MANIFEST));
            z.write(m.toString(2).getBytes("UTF-8"));
            z.closeEntry();
        } finally { z.close(); }
        return n;
    }

    /** Applies a study-set manifest after its DICOM files were imported. Returns a summary. */
    static String applyManifest(Context c, byte[] data) {
        try {
            JSONObject m = new JSONObject(new String(data, "UTF-8"));
            if (!"insula-studyset".equals(m.optString("type"))) return "";
            int added = AnnStore.merge(m, true);
            JSONObject albums = m.optJSONObject("albums");
            if (albums != null) {
                Iterator<String> it = albums.keys();
                while (it.hasNext()) {
                    String a = it.next();
                    JSONArray arr = albums.getJSONArray(a);
                    for (int i = 0; i < arr.length(); i++) Store.addToAlbum(c, a, arr.getString(i));
                }
            }
            JSONArray studies = m.optJSONArray("studies");
            String name = m.optString("name", "").trim();
            if (studies != null) {
                for (int i = 0; i < studies.length(); i++) {
                    JSONObject s = studies.getJSONObject(i);
                    if (!name.isEmpty()) Store.addToAlbum(c, name, s.optString("uid"));
                    if (!s.optString("source").isEmpty()) Store.setSource(c, s.optString("uid"), s.optString("source"));
                }
            }
            int keys = m.optJSONArray("keys") == null ? 0 : m.optJSONArray("keys").length();
            return "Study set \"" + name + "\": " + added + " measurement" + (added == 1 ? "" : "s") + ", " + keys + " key image" + (keys == 1 ? "" : "s")
                    + (name.isEmpty() ? "" : ", added to the album \"" + name + "\"");
        } catch (Exception e) {
            return "";
        }
    }
}
