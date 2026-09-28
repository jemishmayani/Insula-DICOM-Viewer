package com.insula.dicomviewer;

import android.graphics.Bitmap;

/** One decoded frame: stored values (grayscale) or packed ARGB (color), plus calibration. */
public final class RawImage {
    public int w, h;
    public int[] pix;
    public boolean rgb, mono1;
    public double slope = 1, intercept = 0, rowSp, colSp, wc = Double.NaN, ww = Double.NaN;
    public int min, max;
    public String units = "";

    public void computeRange() {
        if (rgb || pix == null || pix.length == 0) { min = 0; max = 255; return; }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int v : pix) { if (v < lo) lo = v; if (v > hi) hi = v; }
        min = lo; max = hi;
    }

    public double modMin() { return Math.min(min * slope + intercept, max * slope + intercept); }
    public double modMax() { return Math.max(min * slope + intercept, max * slope + intercept); }

    public double[] defaultWindow() {
        if (rgb) return new double[]{127.5, 256};
        if (!Double.isNaN(wc) && !Double.isNaN(ww) && ww > 0) return new double[]{wc, ww};
        return autoWindow();
    }

    public double[] autoWindow() {
        double lo = modMin(), hi = modMax();
        return new double[]{(lo + hi) / 2, Math.max(1, hi - lo)};
    }

    public double value(int x, int y) { return pix[y * w + x] * slope + intercept; }
    public long bytes() { return (long) w * h * 4 + 64; }

    public void render(int[] out, double c, double width, boolean invert) {
        int n = w * h;
        if (rgb) {
            for (int i = 0; i < n; i++) { int p = pix[i]; out[i] = invert ? (0xFF000000 | (~p & 0xFFFFFF)) : (p | 0xFF000000); }
            return;
        }
        boolean inv = invert ^ mono1;
        double ww2 = Math.max(1, width);
        double lo = c - 0.5 - (ww2 - 1) / 2, hi = c - 0.5 + (ww2 - 1) / 2;
        long range = (long) max - min + 1;
        if (range > 0 && range <= (1 << 20)) {
            int[] lut = new int[(int) range];
            for (int i = 0; i < range; i++) lut[i] = gray((min + i) * slope + intercept, c, ww2, lo, hi, inv);
            for (int i = 0; i < n; i++) out[i] = lut[pix[i] - min];
        } else {
            for (int i = 0; i < n; i++) out[i] = gray(pix[i] * slope + intercept, c, ww2, lo, hi, inv);
        }
    }

    static int gray(double v, double c, double w, double lo, double hi, boolean inv) {
        int y;
        if (v <= lo) y = 0;
        else if (v > hi) y = 255;
        else { y = (int) (((v - (c - 0.5)) / (w - 1) + 0.5) * 255); if (y < 0) y = 0; if (y > 255) y = 255; }
        if (inv) y = 255 - y;
        return 0xFF000000 | (y << 16) | (y << 8) | y;
    }

    public Bitmap thumbnail(int size) {
        double[] d = defaultWindow();
        int[] full = new int[w * h];
        render(full, d[0], d[1], false);
        float s = Math.min(1f, (float) size / Math.max(w, h));
        int tw = Math.max(1, Math.round(w * s)), th = Math.max(1, Math.round(h * s));
        int[] t = new int[tw * th];
        for (int y = 0; y < th; y++) {
            int sy = Math.min(h - 1, (int) (y / s));
            for (int x = 0; x < tw; x++) t[y * tw + x] = full[sy * w + Math.min(w - 1, (int) (x / s))];
        }
        return Bitmap.createBitmap(t, tw, th, Bitmap.Config.ARGB_8888);
    }
}
