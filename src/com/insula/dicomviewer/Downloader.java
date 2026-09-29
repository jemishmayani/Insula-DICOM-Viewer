/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads a study over DICOMweb (WADO-RS) quickly:
 * one request per series with the multipart response parsed as it streams in, several series in parallel,
 * and HTTP keep-alive so HTTPS handshakes aren't repeated. Falls back to parallel per-instance requests.
 */
final class Downloader {
    static final int SERIES_THREADS = 3, INSTANCE_THREADS = 4;
    static final String ACCEPT_ANY = "multipart/related; type=\"application/dicom\"; transfer-syntax=*";
    static final String ACCEPT_DEFAULT = "multipart/related; type=\"application/dicom\"";

    interface Listener { void progress(int done, int total, long bytes); }

    final Store.Profile p;
    final String studyUid;
    final int[] stats = new int[3];
    final AtomicInteger done = new AtomicInteger();
    final AtomicLong bytes = new AtomicLong();
    volatile boolean cancel;
    volatile int total;
    volatile boolean usedFallback;

    Downloader(Store.Profile p, String studyUid) { this.p = p; this.studyUid = studyUid; }

    interface Sink { void part(byte[] data) throws IOException; }

    /** Opens a GET and throws PacsActivity.HttpErr for error statuses. Caller must close the stream (not disconnect) to keep the connection alive. */
    static HttpURLConnection open(Store.Profile p, String url, String accept, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(Math.max(timeout, 90000));
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("Accept-Encoding", "identity");
        if ("basic".equals(p.auth) && !p.user.isEmpty()) {
            String tok = android.util.Base64.encodeToString((p.user + ":" + p.secret).getBytes("UTF-8"), android.util.Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + tok);
        } else if ("bearer".equals(p.auth) && !p.secret.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + p.secret.trim());
        }
        int code = c.getResponseCode();
        if (code >= 400) {
            try { InputStream e = c.getErrorStream(); if (e != null) { Library.readAll(e); e.close(); } } catch (Exception ignored) { }
            throw new PacsActivity.HttpErr(code, PacsActivity.explain(code));
        }
        return c;
    }

    static String boundary(String ct) {
        if (ct == null) return null;
        for (String s : ct.split(";")) {
            s = s.trim();
            if (s.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                String b = s.substring(9).trim();
                if (b.startsWith("\"") && b.endsWith("\"") && b.length() >= 2) b = b.substring(1, b.length() - 1);
                return b;
            }
        }
        return null;
    }

    /** Streams a multipart/related body, handing each part's content to the sink as soon as it is complete. */
    static int streamParts(InputStream in, String boundary, Sink sink, AtomicLong counter, Downloader owner) throws IOException {
        byte[] delim = ("\r\n--" + boundary).getBytes("ISO-8859-1");
        byte[] crlf2 = {'\r', '\n', '\r', '\n'};
        byte[] buf = new byte[1 << 20];
        buf[0] = '\r'; buf[1] = '\n';             // lets the first "--boundary" match the same delimiter
        int len = 2, searchFrom = 0, partStart = -1, parts = 0;
        byte[] chunk = new byte[1 << 16];
        boolean eof = false;
        while (true) {
            // Look for every delimiter available in the buffer.
            while (true) {
                int idx = indexOf(buf, len, delim, searchFrom);
                if (idx < 0) { searchFrom = Math.max(0, len - delim.length); break; }
                int after = idx + delim.length;
                if (after + 2 > len) { searchFrom = idx; break; }      // need to see what follows
                boolean closing = buf[after] == '-' && buf[after + 1] == '-';
                int start = -1;
                if (!closing) {
                    int nl = after;
                    while (nl + 1 < len && !(buf[nl] == '\r' && buf[nl + 1] == '\n')) nl++;
                    if (nl + 1 >= len) { searchFrom = idx; break; }    // boundary line not complete yet
                    start = nl + 2;
                }
                if (partStart >= 0) {
                    int he = indexOf(buf, idx, crlf2, partStart);
                    if (he >= 0) {
                        byte[] part = new byte[idx - (he + 4)];
                        System.arraycopy(buf, he + 4, part, 0, part.length);
                        sink.part(part);
                        parts++;
                    }
                }
                if (closing) return parts;
                System.arraycopy(buf, start, buf, 0, len - start);
                len -= start;
                partStart = 0;
                searchFrom = 0;
            }
            if (eof) return parts;
            if (owner != null && owner.cancel) return parts;
            int n = in.read(chunk);
            if (n < 0) { eof = true; continue; }
            if (counter != null) counter.addAndGet(n);
            if (len + n > buf.length) {
                byte[] nb = new byte[Math.max(buf.length * 2, len + n)];
                System.arraycopy(buf, 0, nb, 0, len);
                buf = nb;
            }
            System.arraycopy(chunk, 0, buf, len, n);
            len += n;
        }
    }

    static int indexOf(byte[] a, int limit, byte[] pat, int from) {
        int last = limit - pat.length;
        byte f = pat[0];
        outer:
        for (int i = Math.max(0, from); i <= last; i++) {
            if (a[i] != f) continue;
            for (int j = 1; j < pat.length; j++) if (a[i + j] != pat[j]) continue outer;
            return i;
        }
        return -1;
    }

    void importPart(byte[] data) {
        int[] local = new int[3];
        Library.importBytes(data, local);
        synchronized (stats) { stats[0] += local[0]; stats[1] += local[1]; stats[2] += local[2]; }
        done.incrementAndGet();
    }

    /** Retrieves one whole series in a single streamed request. */
    void fetchSeries(String seriesUid) throws Exception {
        String url = p.base() + "/studies/" + studyUid + "/series/" + seriesUid;
        HttpURLConnection c;
        try { c = open(p, url, ACCEPT_ANY, 20000); }
        catch (PacsActivity.HttpErr e) {
            if (e.code == 406 || e.code == 400 || e.code == 415) c = open(p, url, ACCEPT_DEFAULT, 20000);
            else throw e;
        }
        InputStream in = c.getInputStream();
        try {
            String b = boundary(c.getContentType());
            if (b == null) {
                byte[] d = Library.readAll(in);
                bytes.addAndGet(d.length);
                importPart(d);
            } else {
                streamParts(new java.io.BufferedInputStream(in, 1 << 16), b, new Sink() { public void part(byte[] d) { importPart(d); } }, bytes, this);
            }
        } finally { in.close(); }
    }

    /** Fallback for servers without series-level retrieval: fetch instances, several at a time. */
    void fetchInstances(String seriesUid, List<String> sops) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(INSTANCE_THREADS, Library.daemon("insula-dl-instance"));
        final String base = p.base() + "/studies/" + studyUid + "/series/" + seriesUid + "/instances/";
        final String[] accept = {ACCEPT_ANY};
        List<Future<Void>> fs = new ArrayList<>();
        for (final String sop : sops) {
            fs.add(pool.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    if (cancel) return null;
                    HttpURLConnection c;
                    try { c = open(p, base + sop, accept[0], 20000); }
                    catch (PacsActivity.HttpErr e) {
                        if (e.code == 406 || e.code == 400 || e.code == 415) { accept[0] = ACCEPT_DEFAULT; c = open(p, base + sop, accept[0], 20000); }
                        else throw e;
                    }
                    InputStream in = c.getInputStream();
                    try {
                        String b = boundary(c.getContentType());
                        if (b == null) { byte[] d = Library.readAll(in); bytes.addAndGet(d.length); importPart(d); }
                        else streamParts(new java.io.BufferedInputStream(in, 1 << 16), b, new Sink() { public void part(byte[] d) { importPart(d); } }, bytes, Downloader.this);
                    } finally { in.close(); }
                    return null;
                }
            }));
        }
        pool.shutdown();
        Exception first = null;
        for (Future<Void> f : fs) {
            try { f.get(); } catch (java.util.concurrent.ExecutionException e) { if (first == null) first = (Exception) (e.getCause() instanceof Exception ? e.getCause() : e); }
        }
        if (first != null) throw first;
    }

    /** Runs the whole download. Returns null on success or an error message. */
    String run(final Listener l) {
        final Thread ticker = new Thread("insula-dl-progress") {
            public void run() {
                while (!isInterrupted()) {
                    l.progress(done.get(), total, bytes.get());
                    try { Thread.sleep(400); } catch (InterruptedException e) { return; }
                }
            }
        };
        ticker.setDaemon(true);
        try {
            String sb = p.base() + "/studies/" + studyUid;
            JSONArray series = new JSONArray(new String(PacsActivity.json(p, sb + "/series"), "UTF-8"));
            final List<String> seriesUids = new ArrayList<>();
            final List<List<String>> sops = new ArrayList<>();
            int count = 0;
            for (int i = 0; i < series.length(); i++) {
                String se = PacsActivity.jv(series.getJSONObject(i), "0020000E");
                if (se.isEmpty()) continue;
                seriesUids.add(se);
                List<String> l2 = new ArrayList<>();
                try {
                    byte[] d = PacsActivity.json(p, sb + "/series/" + se + "/instances");
                    JSONArray a = d.length == 0 ? new JSONArray() : new JSONArray(new String(d, "UTF-8"));
                    for (int k = 0; k < a.length(); k++) l2.add(PacsActivity.jv(a.getJSONObject(k), "00080018"));
                } catch (Exception ignored) { }
                sops.add(l2);
                count += l2.size();
            }
            total = count;
            ticker.start();
            ExecutorService pool = Executors.newFixedThreadPool(SERIES_THREADS, Library.daemon("insula-dl-series"));
            List<Future<Void>> fs = new ArrayList<>();
            for (int i = 0; i < seriesUids.size(); i++) {
                final String se = seriesUids.get(i);
                final List<String> list = sops.get(i);
                fs.add(pool.submit(new Callable<Void>() {
                    public Void call() throws Exception {
                        if (cancel) return null;
                        try { fetchSeries(se); }
                        catch (PacsActivity.HttpErr e) {
                            if (e.code == 401 || e.code == 403 || list.isEmpty()) throw e;
                            usedFallback = true;
                            fetchInstances(se, list);
                        } catch (IOException e) {
                            if (list.isEmpty() || cancel) throw e;
                            usedFallback = true;
                            fetchInstances(se, list);
                        }
                        return null;
                    }
                }));
            }
            pool.shutdown();
            String err = null;
            for (Future<Void> f : fs) {
                try { f.get(); } catch (java.util.concurrent.ExecutionException e) { if (err == null) err = e.getCause() == null ? e.toString() : e.getCause().getMessage(); }
            }
            return err;
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            ticker.interrupt();
            l.progress(done.get(), total, bytes.get());
        }
    }
}
