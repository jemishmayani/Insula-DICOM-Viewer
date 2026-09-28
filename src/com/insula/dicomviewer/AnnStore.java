package com.insula.dicomviewer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Measurements, annotations, and key-image marks, keyed by "SOPInstanceUID#frame".
 * Keys stay valid when a study is re-imported, exported in a study set, or anonymized (UIDs are kept).
 */
final class AnnStore {
    static File file;
    static final Map<String, List<DicomView.Ann>> map = new HashMap<>();
    static final Set<String> keys = new HashSet<>();
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static synchronized void init(File dir) {
        file = new File(dir, "annotations.json");
        try { if (file.exists()) merge(new JSONObject(new String(Library.readFile(file), "UTF-8")), false); } catch (Exception ignored) { }
    }

    static String key(Library.SliceRef r) { return r.info.sopUid + "#" + r.frame; }
    static String sopOf(String key) { int i = key.lastIndexOf('#'); return i < 0 ? key : key.substring(0, i); }

    static synchronized List<DicomView.Ann> list(String k) {
        List<DicomView.Ann> l = map.get(k);
        if (l == null) { l = new ArrayList<>(); map.put(k, l); }
        return l;
    }

    static synchronized boolean isKey(String k) { return keys.contains(k); }

    static boolean setKey(String k, boolean on) {
        boolean ch;
        synchronized (AnnStore.class) { ch = on ? keys.add(k) : keys.remove(k); }
        if (ch) changed();
        return ch;
    }

    static synchronized int count() {
        int n = 0;
        for (List<DicomView.Ann> l : map.values()) for (DicomView.Ann a : l) if (a.done) n++;
        return n;
    }

    /** Writes the store in the background. */
    static void changed() {
        final String json;
        synchronized (AnnStore.class) { json = toJson(null).toString(); }
        if (file == null) return;
        IO.submit(new Runnable() {
            public void run() {
                try {
                    File tmp = new File(file.getPath() + ".tmp");
                    FileOutputStream fo = new FileOutputStream(tmp);
                    fo.write(json.getBytes("UTF-8"));
                    fo.close();
                    tmp.renameTo(file);
                } catch (Exception ignored) { }
            }
        });
    }

    static boolean valid(DicomView.Ann a) {
        if (!a.done) return false;
        for (float f : a.p) if (Float.isNaN(f) || Float.isInfinite(f)) return false;
        return true;
    }

    /** @param sops restrict to these SOP Instance UIDs, or null for everything. */
    static synchronized JSONObject toJson(Set<String> sops) {
        JSONObject o = new JSONObject();
        try {
            JSONObject a = new JSONObject();
            for (Map.Entry<String, List<DicomView.Ann>> e : map.entrySet()) {
                if (sops != null && !sops.contains(sopOf(e.getKey()))) continue;
                JSONArray arr = new JSONArray();
                for (DicomView.Ann ann : e.getValue()) {
                    if (!valid(ann)) continue;
                    JSONObject j = new JSONObject();
                    j.put("t", ann.type);
                    JSONArray p = new JSONArray();
                    for (float f : ann.p) p.put((double) f);
                    j.put("p", p);
                    if (!ann.text.isEmpty()) j.put("x", ann.text);
                    arr.put(j);
                }
                if (arr.length() > 0) a.put(e.getKey(), arr);
            }
            o.put("annotations", a);
            JSONArray k = new JSONArray();
            for (String key : keys) if (sops == null || sops.contains(sopOf(key))) k.put(key);
            o.put("keys", k);
        } catch (Exception ignored) { }
        return o;
    }

    /** Adds annotations and key marks from JSON, skipping exact duplicates. Returns the number of annotations added. */
    static int merge(JSONObject o, boolean save) {
        int added = 0;
        synchronized (AnnStore.class) {
            JSONObject a = o.optJSONObject("annotations");
            if (a != null) {
                Iterator<String> it = a.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray arr = a.optJSONArray(k);
                    if (arr == null) continue;
                    List<DicomView.Ann> l = list(k);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject j = arr.optJSONObject(i);
                        if (j == null) continue;
                        JSONArray p = j.optJSONArray("p");
                        if (p == null) continue;
                        float[] pts = new float[p.length()];
                        for (int q = 0; q < pts.length; q++) pts[q] = (float) p.optDouble(q);
                        DicomView.Ann ann = new DicomView.Ann(j.optInt("t"), pts);
                        ann.text = j.optString("x", "");
                        ann.done = true;
                        boolean dup = false;
                        for (DicomView.Ann e : l) if (e.type == ann.type && java.util.Arrays.equals(e.p, ann.p) && e.text.equals(ann.text)) dup = true;
                        if (!dup) { l.add(ann); added++; }
                    }
                }
            }
            JSONArray k = o.optJSONArray("keys");
            if (k != null) for (int i = 0; i < k.length(); i++) keys.add(k.optString(i));
        }
        if (save) changed();
        return added;
    }

    static void removeSop(String sop) {
        synchronized (AnnStore.class) {
            String pre = sop + "#";
            Iterator<String> it = map.keySet().iterator();
            while (it.hasNext()) if (it.next().startsWith(pre)) it.remove();
            Iterator<String> ki = keys.iterator();
            while (ki.hasNext()) if (ki.next().startsWith(pre)) ki.remove();
        }
        changed();
    }
}
