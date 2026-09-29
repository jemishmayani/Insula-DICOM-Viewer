/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class PixelDecoder {

    public static RawImage decode(Dicom.DataSet ds, int frame) throws Exception {
        int rows = ds.getInt(0x00280010, 0), cols = ds.getInt(0x00280011, 0);
        if (rows <= 0 || cols <= 0) throw new Exception("This object has no image (rows/columns missing).");
        int spp = ds.getInt(0x00280002, 1), ba = ds.getInt(0x00280100, 16), bs = ds.getInt(0x00280101, ba);
        int pr = ds.getInt(0x00280103, 0), planar = ds.getInt(0x00280006, 0);
        String photo = ds.getString(0x00280004);
        if (photo == null || photo.isEmpty()) photo = spp == 3 ? "RGB" : "MONOCHROME2";
        photo = photo.toUpperCase();
        int nFrames = Math.max(1, ds.getInt(0x00280008, 1));
        if (frame >= nFrames) frame = nFrames - 1;
        Dicom.Element pe = ds.get(Dicom.PIXEL_DATA);
        if (pe == null) throw new Exception("No pixel data. Open Tags to see this object's contents.");
        RawImage r = new RawImage();
        r.w = cols; r.h = rows;
        r.mono1 = photo.equals("MONOCHROME1");
        int n = rows * cols;
        int[][] comps;
        boolean fromJpegRgb = false;

        if (pe.fragments != null) {
            byte[] comp = frameBytes(ds, pe, frame, nFrames);
            String ts = ds.transferSyntax;
            if (ts.equals(Dicom.TS_RLE)) {
                comps = Rle.decode(comp, n, spp, ba);
            } else if (ts.equals("1.2.840.10008.1.2.4.57") || ts.equals("1.2.840.10008.1.2.4.70")) {
                JpegLossless j = new JpegLossless(comp);
                comps = j.decode();
                r.w = j.width; r.h = j.height; n = r.w * r.h;
                if (j.ncomp != spp) spp = j.ncomp;
            } else if (ts.startsWith("1.2.840.10008.1.2.4.9")) {
                J2k j = new J2k();
                try { comps = j.decode(comp); }
                catch (Throwable e) { throw new Exception("Couldn't decode this JPEG 2000 image: " + e.getMessage()); }
                r.w = j.width; r.h = j.height; n = r.w * r.h;
                spp = j.ncomp;
                if (spp >= 3 && j.bits > 8) for (int[] cc : comps) for (int i = 0; i < cc.length; i++) cc[i] = clamp8(cc[i] >> (j.bits - 8));
            } else if (ts.startsWith("1.2.840.10008.1.2.4.8") || ts.startsWith("1.2.840.10008.1.2.4.20")
                    || ts.startsWith("1.2.840.10008.1.2.4.10")) {
                throw new Exception("Compression not supported yet: " + Dicom.tsName(ts));
            } else {
                Bitmap bm = BitmapFactory.decodeByteArray(comp, 0, comp.length);
                if (bm == null) throw new Exception("Compression not supported yet: " + Dicom.tsName(ts));
                r.w = bm.getWidth(); r.h = bm.getHeight(); n = r.w * r.h;
                int[] px = new int[n];
                bm.getPixels(px, 0, r.w, 0, 0, r.w, r.h);
                bm.recycle();
                if (spp == 1 && !photo.startsWith("PALETTE")) {
                    for (int i = 0; i < n; i++) px[i] = (px[i] >> 16) & 255;
                    comps = new int[][]{px};
                } else if (spp == 1) {
                    for (int i = 0; i < n; i++) px[i] = (px[i] >> 16) & 255;
                    comps = new int[][]{px};
                } else {
                    r.rgb = true; r.pix = px; fromJpegRgb = true; comps = null;
                }
                bs = Math.min(bs, 8); pr = 0;
            }
        } else {
            comps = decodeNative(ds, pe, frame, n, spp, ba, planar, photo);
        }

        if (fromJpegRgb) { r.computeRange(); return r; }

        if (spp == 1) {
            int[] v = comps[0];
            if (pe.fragments != null && pr == 1 && bs > 0 && bs < 32) {
                int sign = 1 << (bs - 1), full = 1 << bs;
                for (int i = 0; i < v.length; i++) if ((v[i] & sign) != 0) v[i] = (v[i] & (full - 1)) - full;
            }
            if (photo.startsWith("PALETTE")) {
                int[] rl = paletteLut(ds, 0x00281101, 0x00281201), gl = paletteLut(ds, 0x00281102, 0x00281202), bl = paletteLut(ds, 0x00281103, 0x00281203);
                if (rl != null && gl != null && bl != null) {
                    int first = palFirst(ds);
                    int[] out = new int[v.length];
                    for (int i = 0; i < v.length; i++) {
                        int idx = v[i] - first;
                        int ri = clampIdx(idx, rl.length), gi = clampIdx(idx, gl.length), bi = clampIdx(idx, bl.length);
                        out[i] = 0xFF000000 | (rl[ri] << 16) | (gl[gi] << 8) | bl[bi];
                    }
                    r.rgb = true; r.pix = out; r.computeRange();
                    return r;
                }
            }
            r.pix = v;
        } else {
            int[] out = new int[n];
            int shift = (pe.fragments != null && ds.transferSyntax.equals(Dicom.TS_RLE)) ? Math.max(0, ba - 8) : 0;
            if (shift > 0) for (int[] cc : comps) for (int i = 0; i < cc.length; i++) cc[i] = (cc[i] >>> shift) & 255;
            int[] c0 = comps[0], c1 = comps[1], c2 = comps[2];
            boolean ybr = photo.startsWith("YBR") && pe.fragments == null || (photo.startsWith("YBR") && ds.transferSyntax.equals(Dicom.TS_RLE));
            for (int i = 0; i < n; i++) {
                int a = c0[i], b = c1[i], c = c2[i];
                if (ybr) {
                    double y = a, cb = b - 128, cr = c - 128;
                    a = clamp8((int) Math.round(y + 1.402 * cr));
                    b = clamp8((int) Math.round(y - 0.344136 * cb - 0.714136 * cr));
                    c = clamp8((int) Math.round(y + 1.772 * cb));
                }
                out[i] = 0xFF000000 | (clamp8(a) << 16) | (clamp8(b) << 8) | clamp8(c);
            }
            r.rgb = true; r.pix = out;
        }
        r.computeRange();
        return r;
    }

    static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
    static int clampIdx(int i, int n) { return i < 0 ? 0 : (i >= n ? n - 1 : i); }

    static int[][] decodeNative(Dicom.DataSet ds, Dicom.Element pe, int frame, int n, int spp, int ba, int planar, String photo) throws Exception {
        byte[] b = ds.buf;
        int bs = ds.getInt(0x00280101, ba), pr = ds.getInt(0x00280103, 0);
        if (photo.equals("YBR_FULL_422") && spp == 3 && ba == 8) {
            long fb = (long) n * 2;
            int start = (int) (pe.offset + frame * fb);
            if (start + fb > (long) pe.offset + pe.length) throw new Exception("Pixel data is truncated.");
            int[][] c = new int[3][n];
            for (int i = 0; i + 1 < n; i += 2) {
                int o = start + i * 2;
                c[0][i] = b[o] & 255; c[0][i + 1] = b[o + 1] & 255;
                c[1][i] = c[1][i + 1] = b[o + 2] & 255;
                c[2][i] = c[2][i + 1] = b[o + 3] & 255;
            }
            return c;
        }
        if (ba == 1) {
            long bitStart = (long) frame * n;
            int[] v = new int[n];
            for (int i = 0; i < n; i++) {
                long bit = bitStart + i;
                int o = (int) (pe.offset + bit / 8);
                if (o >= b.length) break;
                v[i] = (b[o] >> (int) (bit % 8)) & 1;
            }
            return new int[][]{v};
        }
        int bp = Math.max(1, ba / 8);
        long frameBytes = (long) n * spp * bp;
        long start = pe.offset + frame * frameBytes;
        if (start + frameBytes > (long) pe.offset + pe.length) {
            if (frame == 0 && pe.length >= (long) n * spp * bp * 0.5) { /* tolerate slight truncation */ } else throw new Exception("Pixel data is truncated.");
        }
        int s = (int) start, lim = pe.offset + pe.length;
        int sw = (ds.bigEndian && bp == 1 && "OW".equals(pe.vr)) ? 1 : 0; // 8-bit samples in a byte-swapped OW value
        if (spp == 1) {
            int[] v = new int[n];
            if (bp == 1) {
                for (int i = 0; i < n && s + i < lim; i++) { int o = pe.offset + ((s - pe.offset + i) ^ sw); if (o >= lim) continue; v[i] = pr == 1 ? b[o] : (b[o] & 255); }
            } else if (bp == 2) {
                for (int i = 0; i < n && s + 2 * i + 1 < lim; i++) v[i] = ds.u16(s + 2 * i);
                if (bs < 16 && bs > 0) {
                    int mask = (1 << bs) - 1, sign = 1 << (bs - 1);
                    for (int i = 0; i < n; i++) { int x = v[i] & mask; if (pr == 1 && (x & sign) != 0) x -= (1 << bs); v[i] = x; }
                } else if (pr == 1) {
                    for (int i = 0; i < n; i++) v[i] = (short) v[i];
                }
            } else {
                for (int i = 0; i < n && s + 4 * i + 3 < lim; i++) v[i] = ds.s32(s + 4 * i);
            }
            return new int[][]{v};
        }
        int[][] c = new int[3][n];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 3; k++) {
                int o = planar == 0 ? s + (i * spp + k) * bp : s + (k * n + i) * bp;
                if (sw == 1) o = pe.offset + ((o - pe.offset) ^ 1);
                if (o + bp > lim) continue;
                c[k][i] = bp == 1 ? (b[o] & 255) : (ds.u16(o) >> 8);
            }
        }
        return c;
    }

    static int palFirst(Dicom.DataSet ds) {
        int[] d = ds.getIntArray(0x00281101);
        return d != null && d.length >= 2 ? d[1] : 0;
    }

    static int[] paletteLut(Dicom.DataSet ds, int descTag, int dataTag) {
        int[] d = ds.getIntArray(descTag);
        Dicom.Element e = ds.get(dataTag);
        if (d == null || d.length < 3 || e == null) return null;
        int entries = d[0] == 0 ? 65536 : d[0];
        int bits = d[2];
        int[] lut = new int[entries];
        boolean packed8 = e.length == entries;
        for (int i = 0; i < entries; i++) {
            int v;
            if (packed8) v = ds.buf[e.offset + i] & 255;
            else { int o = e.offset + 2 * i; if (o + 1 >= e.offset + e.length) break; v = ds.u16(o); if (bits > 8) v >>= 8; else v &= 255; }
            lut[i] = v;
        }
        return lut;
    }

    static byte[] frameBytes(Dicom.DataSet ds, Dicom.Element pe, int frame, int nFrames) throws Exception {
        List<int[]> fr = pe.fragments;
        if (fr.size() < 2) throw new Exception("Compressed pixel data is empty.");
        byte[] b = ds.buf;
        List<int[]> data = fr.subList(1, fr.size());
        if (nFrames <= 1) return concat(b, data);
        if (data.size() == nFrames) return copy(b, data.get(frame));
        int[] bot = fr.get(0);
        if (bot[1] >= 4 * nFrames) {
            int base = data.get(0)[0] - 8;
            long startOff = ds.s32(bot[0] + 4 * frame) & 0xFFFFFFFFL;
            long endOff = frame + 1 < nFrames ? (ds.s32(bot[0] + 4 * (frame + 1)) & 0xFFFFFFFFL) : Long.MAX_VALUE;
            List<int[]> sel = new ArrayList<>();
            for (int[] f : data) { long rel = f[0] - 8 - base; if (rel >= startOff && rel < endOff) sel.add(f); }
            if (!sel.isEmpty()) return concat(b, sel);
        }
        // Fall back: split on JPEG start-of-image markers.
        List<List<int[]>> frames = new ArrayList<>();
        for (int[] f : data) {
            boolean soi = f[1] >= 2 && (b[f[0]] & 255) == 0xFF && (b[f[0] + 1] & 255) == 0xD8;
            if (soi || frames.isEmpty()) frames.add(new ArrayList<int[]>());
            frames.get(frames.size() - 1).add(f);
        }
        if (frame < frames.size()) return concat(b, frames.get(frame));
        throw new Exception("Could not locate frame " + (frame + 1) + ".");
    }

    static byte[] copy(byte[] b, int[] f) { byte[] o = new byte[f[1]]; System.arraycopy(b, f[0], o, 0, f[1]); return o; }
    static byte[] concat(byte[] b, List<int[]> l) {
        if (l.size() == 1) return copy(b, l.get(0));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        for (int[] f : l) bo.write(b, f[0], f[1]);
        return bo.toByteArray();
    }

    /** DICOM RLE Lossless (PackBits segments). */
    static final class Rle {
        static int le32(byte[] d, int o) { return (d[o] & 255) | ((d[o + 1] & 255) << 8) | ((d[o + 2] & 255) << 16) | ((d[o + 3] & 255) << 24); }
        static int[][] decode(byte[] d, int n, int spp, int ba) throws Exception {
            int bpp = Math.max(1, ba / 8);
            int segs = le32(d, 0);
            if (segs < spp * bpp || segs > 15) throw new Exception("Invalid RLE header.");
            int[][] out = new int[spp][n];
            for (int s = 0; s < spp; s++) {
                for (int bi = 0; bi < bpp; bi++) {
                    int seg = s * bpp + bi;
                    int off = le32(d, 4 + 4 * seg);
                    int end = seg + 1 < segs ? le32(d, 4 + 4 * (seg + 1)) : d.length;
                    byte[] plane = packbits(d, off, Math.min(end, d.length), n);
                    int shift = 8 * (bpp - 1 - bi);
                    int[] o = out[s];
                    for (int i = 0; i < n; i++) o[i] |= (plane[i] & 255) << shift;
                }
            }
            return out;
        }
        static byte[] packbits(byte[] d, int p, int end, int n) {
            byte[] out = new byte[n];
            int o = 0;
            while (p < end && o < n) {
                int c = d[p++];
                if (c >= 0) { int cnt = c + 1; for (int i = 0; i < cnt && p < end && o < n; i++) out[o++] = d[p++]; }
                else if (c != -128) { int cnt = 1 - c; byte v = p < end ? d[p++] : 0; for (int i = 0; i < cnt && o < n; i++) out[o++] = v; }
            }
            return out;
        }
    }

    /** JPEG Lossless (process 14, predictors 1-7) decoder, as used on most CT/MR CDs. */
    static final class JpegLossless {
        final byte[] d;
        int pos, width, height, precision, ncomp, restart, predictor, pt;
        int[] compId = new int[4], compTable = new int[4];
        final int[][] maxcode = new int[4][], valptr = new int[4][], mincode = new int[4][], vals = new int[4][];
        int bitBuf, bitCnt;
        boolean marker;

        JpegLossless(byte[] d) { this.d = d; }
        int u16(int o) { return ((d[o] & 255) << 8) | (d[o + 1] & 255); }

        int[][] decode() throws Exception {
            if (d.length < 4 || (d[0] & 255) != 0xFF || (d[1] & 255) != 0xD8) throw new Exception("Not a JPEG stream.");
            pos = 2;
            while (pos + 4 <= d.length) {
                if ((d[pos] & 255) != 0xFF) { pos++; continue; }
                int m = d[pos + 1] & 255;
                pos += 2;
                if (m == 0xD8 || m == 0x01 || (m >= 0xD0 && m <= 0xD7) || m == 0xFF) { if (m == 0xFF) pos--; continue; }
                int len = u16(pos);
                int seg = pos + 2, next = pos + len;
                switch (m) {
                    case 0xC3: case 0xC7: case 0xCB: case 0xCF:
                        precision = d[seg] & 255; height = u16(seg + 1); width = u16(seg + 3); ncomp = d[seg + 5] & 255;
                        if (ncomp < 1 || ncomp > 4) throw new Exception("Unsupported JPEG component count.");
                        for (int i = 0; i < ncomp; i++) compId[i] = d[seg + 6 + i * 3] & 255;
                        break;
                    case 0xC0: case 0xC1: case 0xC2:
                        throw new Exception("Lossy JPEG inside a lossless transfer syntax.");
                    case 0xC4: {
                        int p = seg;
                        while (p < next) {
                            int tc = (d[p] & 255) >> 4, th = d[p] & 15;
                            p++;
                            int[] bits = new int[17];
                            int total = 0;
                            for (int i = 1; i <= 16; i++) { bits[i] = d[p++] & 255; total += bits[i]; }
                            int[] v = new int[total];
                            for (int i = 0; i < total; i++) v[i] = d[p++] & 255;
                            if (tc == 0 && th < 4) build(th, bits, v);
                        }
                        break;
                    }
                    case 0xDD: restart = u16(seg); break;
                    case 0xDA: {
                        int ns = d[seg] & 255;
                        for (int i = 0; i < ns; i++) {
                            int id = d[seg + 1 + i * 2] & 255, t = (d[seg + 2 + i * 2] & 255) >> 4;
                            for (int k = 0; k < ncomp; k++) if (compId[k] == id) compTable[k] = t;
                        }
                        int p = seg + 1 + ns * 2;
                        predictor = d[p] & 255;
                        pt = d[p + 2] & 15;
                        pos = next;
                        return scan();
                    }
                    default: break;
                }
                pos = next;
            }
            throw new Exception("JPEG scan not found.");
        }

        void build(int t, int[] bits, int[] v) {
            int[] huffcode = new int[v.length];
            int code = 0, k = 0;
            for (int l = 1; l <= 16; l++) { for (int i = 0; i < bits[l]; i++) huffcode[k++] = code++; code <<= 1; }
            int[] mx = new int[18], vp = new int[17], mn = new int[17];
            int j = 0;
            for (int l = 1; l <= 16; l++) {
                if (bits[l] == 0) { mx[l] = -1; continue; }
                vp[l] = j; mn[l] = huffcode[j]; j += bits[l]; mx[l] = huffcode[j - 1];
            }
            mx[17] = Integer.MAX_VALUE;
            maxcode[t] = mx; valptr[t] = vp; mincode[t] = mn; vals[t] = v;
        }

        void fill() {
            int v;
            if (marker || pos >= d.length) v = 0;
            else {
                v = d[pos] & 255;
                if (v == 0xFF) {
                    int nx = pos + 1 < d.length ? d[pos + 1] & 255 : 0;
                    if (nx == 0) pos += 2; else { marker = true; v = 0; }
                } else pos++;
            }
            bitBuf = v; bitCnt = 8;
        }
        int bit() { if (bitCnt == 0) fill(); bitCnt--; return (bitBuf >> bitCnt) & 1; }
        int bits(int n) { int v = 0; for (int i = 0; i < n; i++) v = (v << 1) | bit(); return v; }

        int huff(int t) {
            int[] mx = maxcode[t];
            if (mx == null) return 0;
            int code = bit(), l = 1;
            while (l <= 16 && code > mx[l]) { code = (code << 1) | bit(); l++; }
            if (l > 16) return 0;
            return vals[t][valptr[t][l] + code - mincode[t][l]];
        }

        int diff(int t) {
            int s = huff(t);
            if (s == 0) return 0;
            if (s == 16) return 32768;
            int v = bits(s);
            if (v < (1 << (s - 1))) v -= (1 << s) - 1;
            return v;
        }

        void doRestart() {
            bitCnt = 0; marker = false;
            while (pos + 1 < d.length) {
                if ((d[pos] & 255) == 0xFF && ((d[pos + 1] & 255) & 0xF8) == 0xD0) { pos += 2; return; }
                pos++;
            }
        }

        int[][] scan() {
            int n = width * height;
            int[][] out = new int[ncomp][n];
            int mask = precision >= 32 ? -1 : (1 << precision) - 1;
            int dflt = 1 << (precision - pt - 1);
            int mcu = 0;
            boolean reset = true;
            int resetRow = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (restart > 0 && mcu > 0 && mcu % restart == 0) { doRestart(); reset = true; }
                    boolean firstOfInterval = reset;
                    if (reset) { resetRow = y; reset = false; }
                    int idx = y * width + x;
                    for (int c = 0; c < ncomp; c++) {
                        int[] o = out[c];
                        int pred;
                        if (firstOfInterval) pred = dflt;
                        else if (y == resetRow) pred = o[idx - 1];
                        else if (x == 0) pred = o[idx - width];
                        else {
                            int ra = o[idx - 1], rb = o[idx - width], rc = o[idx - width - 1];
                            switch (predictor) {
                                case 1: pred = ra; break;
                                case 2: pred = rb; break;
                                case 3: pred = rc; break;
                                case 4: pred = ra + rb - rc; break;
                                case 5: pred = ra + ((rb - rc) >> 1); break;
                                case 6: pred = rb + ((ra - rc) >> 1); break;
                                case 7: pred = (ra + rb) >> 1; break;
                                default: pred = ra;
                            }
                        }
                        o[idx] = (pred + diff(compTable[c])) & mask;
                    }
                    mcu++;
                }
            }
            if (pt > 0) for (int[] o : out) for (int i = 0; i < n; i++) o[i] <<= pt;
            return out;
        }
    }
}
