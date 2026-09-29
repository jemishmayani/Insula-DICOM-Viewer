/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.io.ByteArrayInputStream;

import ucar.jpeg.jj2000.j2k.codestream.HeaderInfo;
import ucar.jpeg.jj2000.j2k.codestream.reader.BitstreamReaderAgent;
import ucar.jpeg.jj2000.j2k.codestream.reader.HeaderDecoder;
import ucar.jpeg.jj2000.j2k.decoder.DecoderSpecs;
import ucar.jpeg.jj2000.j2k.entropy.decoder.EntropyDecoder;
import ucar.jpeg.jj2000.j2k.fileformat.reader.FileFormatReader;
import ucar.jpeg.jj2000.j2k.image.BlkImgDataSrc;
import ucar.jpeg.jj2000.j2k.image.Coord;
import ucar.jpeg.jj2000.j2k.image.DataBlkInt;
import ucar.jpeg.jj2000.j2k.image.ImgDataConverter;
import ucar.jpeg.jj2000.j2k.image.invcomptransf.InvCompTransf;
import ucar.jpeg.jj2000.j2k.io.RandomAccessIO;
import ucar.jpeg.jj2000.j2k.quantization.dequantizer.Dequantizer;
import ucar.jpeg.jj2000.j2k.roi.ROIDeScaler;
import ucar.jpeg.jj2000.j2k.util.ISRandomAccessIO;
import ucar.jpeg.jj2000.j2k.util.ParameterList;
import ucar.jpeg.jj2000.j2k.wavelet.synthesis.InverseWT;

/**
 * Decodes one JPEG 2000 frame (lossless 5/3 or lossy 9/7, any tiling, with or without the
 * multi-component transform) using the JJ2000 reference decoder. See JJ2000-COPYRIGHT.txt.
 */
final class J2k {
    int width, height, ncomp, bits;
    boolean signed;

    static ParameterList defaults() {
        ParameterList d = new ParameterList();
        String[][][] all = {BitstreamReaderAgent.getParameterInfo(), EntropyDecoder.getParameterInfo(), ROIDeScaler.getParameterInfo(),
                Dequantizer.getParameterInfo(), InvCompTransf.getParameterInfo(), HeaderDecoder.getParameterInfo()};
        for (String[][] group : all) if (group != null) for (String[] p : group) if (p[3] != null) d.put(p[0], p[3]);
        String[][] own = {{"rate", "-1"}, {"nbytes", "-1"}, {"parsing", "on"}, {"ncb_quit", "-1"}, {"l_quit", "-1"}, {"m_quit", "-1"},
                {"poc_quit", "off"}, {"one_tp", "off"}, {"comp_transf", "on"}, {"debug", "off"}, {"cdstr_info", "off"},
                {"nocolorspace", "on"}, {"colorspace_debug", "off"}, {"verbose", "off"}, {"u", "off"}, {"v", "off"}};
        for (String[] p : own) d.put(p[0], p[1]);
        return d;
    }

    /** @return one int array per component (row-major), with unsigned data shifted back to its original range. */
    int[][] decode(byte[] data) throws Exception {
        try { return decodeRaw(data); }
        catch (Throwable e) {
            // Some encoders wrap the codestream in a JP2 container with boxes the reader doesn't know; decode the bare codestream.
            int soc = -1;
            for (int i = 0; i + 3 < data.length; i++)
                if ((data[i] & 255) == 0xFF && (data[i + 1] & 255) == 0x4F && (data[i + 2] & 255) == 0xFF && (data[i + 3] & 255) == 0x51) { soc = i; break; }
            if (soc <= 0) throw new Exception(e.getMessage() == null ? e.toString() : e.getMessage());
            byte[] cs = new byte[data.length - soc];
            System.arraycopy(data, soc, cs, 0, cs.length);
            return decodeRaw(cs);
        }
    }

    int[][] decodeRaw(byte[] data) throws Exception {
        ParameterList pl = new ParameterList(defaults());
        RandomAccessIO in = new ISRandomAccessIO(new ByteArrayInputStream(data), data.length, 1, data.length);
        FileFormatReader ff = new FileFormatReader(in);
        ff.readFileFormat();
        if (ff.JP2FFUsed) in.seek(ff.getFirstCodeStreamPos());
        HeaderInfo hi = new HeaderInfo();
        HeaderDecoder hd = new HeaderDecoder(in, pl, hi);
        int nc = hd.getNumComps();
        DecoderSpecs spec = hd.getDecoderSpecs();
        int[] depth = new int[nc];
        for (int i = 0; i < nc; i++) depth[i] = hd.getOriginalBitDepth(i);
        BitstreamReaderAgent br = BitstreamReaderAgent.createInstance(in, hd, pl, spec, false, hi);
        EntropyDecoder ed = hd.createEntropyDecoder(br, pl);
        ROIDeScaler roi = hd.createROIDeScaler(ed, pl, spec);
        Dequantizer dq = hd.createDequantizer(roi, depth, spec);
        InverseWT iw = InverseWT.createInstance(dq, spec);
        iw.setImgResLevel(br.getImgRes());
        ImgDataConverter conv = new ImgDataConverter(iw, 0);
        BlkImgDataSrc src = new InvCompTransf(conv, spec, depth, pl);

        width = src.getImgWidth();
        height = src.getImgHeight();
        ncomp = Math.min(nc, 3);
        bits = src.getNomRangeBits(0);
        signed = hd.isOriginalSigned(0);
        int[][] out = new int[ncomp][width * height];
        Coord nt = src.getNumTiles(null);
        DataBlkInt db = new DataBlkInt();
        for (int ty = 0; ty < nt.y; ty++) {
            for (int tx = 0; tx < nt.x; tx++) {
                src.setTile(tx, ty);
                int tIdx = src.getTileIdx();
                for (int c = 0; c < ncomp; c++) {
                    int fb = src.getFixedPoint(c);
                    boolean sg = hd.isOriginalSigned(c);
                    int nb = src.getNomRangeBits(c);
                    int shift = sg ? 0 : 1 << (nb - 1);
                    // Lossy (9/7) reconstruction can overshoot; clip to the component's nominal range like other decoders.
                    int vmin = sg ? -(1 << (nb - 1)) : 0, vmax = sg ? (1 << (nb - 1)) - 1 : (1 << nb) - 1;
                    int tw = src.getTileCompWidth(tIdx, c), th = src.getTileCompHeight(tIdx, c);
                    int ox = src.getCompULX(c) - (int) Math.ceil(src.getImgULX() / (double) src.getCompSubsX(c));
                    int oy = src.getCompULY(c) - (int) Math.ceil(src.getImgULY() / (double) src.getCompSubsY(c));
                    db.ulx = 0; db.uly = 0; db.w = tw; db.h = th;
                    db.data = null;
                    do { db = (DataBlkInt) src.getInternCompData(db, c); } while (db.progressive);
                    int[] o = out[c];
                    for (int y = 0; y < th; y++) {
                        int gy = oy + y;
                        if (gy < 0 || gy >= height) continue;
                        int si = db.offset + y * db.scanw, di = gy * width + ox;
                        for (int x = 0; x < tw; x++) {
                            int gx = ox + x;
                            if (gx < 0 || gx >= width) continue;
                            int v = (db.data[si + x] >> fb) + shift;
                            o[di + x] = v < vmin ? vmin : (v > vmax ? vmax : v);
                        }
                    }
                }
            }
        }
        return out;
    }
}
