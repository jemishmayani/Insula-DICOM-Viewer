package com.insula.dicomviewer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Outline icons drawn on a 24-unit grid, so the app needs no image assets. */
public final class Icons extends Drawable {
    final String name;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    int color;

    public Icons(String name, int color) {
        this.name = name;
        this.color = color;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.7f);
        switch (name) {
            case "back": line(c, 20, 12, 4, 12); open(c, 10, 6, 4, 12, 10, 18); break;
            case "close": line(c, 6, 6, 18, 18); line(c, 18, 6, 6, 18); break;
            case "menu": line(c, 4, 6, 20, 6); line(c, 4, 12, 20, 12); line(c, 4, 18, 20, 18); break;
            case "more": fill(); c.drawCircle(12, 5, 1.8f, p); c.drawCircle(12, 12, 1.8f, p); c.drawCircle(12, 19, 1.8f, p); break;
            case "search": c.drawCircle(10.5f, 10.5f, 6.5f, p); line(c, 15.5f, 15.5f, 21, 21); break;
            case "plus": p.setStrokeWidth(2.2f); line(c, 12, 5, 12, 19); line(c, 5, 12, 19, 12); break;
            case "chevron": open(c, 6, 9, 12, 15, 18, 9); break;
            case "chevronr": open(c, 9, 6, 15, 12, 9, 18); break;
            case "speed": c.drawArc(new RectF(3, 5, 21, 23), 180, 180, false, p); line(c, 12, 14, 16.5f, 8.5f); fill(); c.drawCircle(12, 14, 1.8f, p); break;
            case "share":
                c.drawCircle(18, 5, 2.6f, p); c.drawCircle(6, 12, 2.6f, p); c.drawCircle(18, 19, 2.6f, p);
                line(c, 8.3f, 10.7f, 15.7f, 6.3f); line(c, 8.3f, 13.3f, 15.7f, 17.7f); break;
            case "report":
                c.drawRoundRect(new RectF(4, 3, 17, 21), 2, 2, p);
                line(c, 7.5f, 8, 13.5f, 8); line(c, 7.5f, 12, 13.5f, 12); line(c, 7.5f, 16, 10.5f, 16);
                open(c, 13, 20, 13.5f, 17.5f, 20, 11, 22, 13, 15.5f, 19.5f, 13, 20); break;
            case "pencil":
                closed(c, 4, 20, 5, 15.5f, 16, 4.5f, 19.5f, 8, 8.5f, 19); line(c, 13.5f, 7, 17, 10.5f); break;
            case "layers":
                closed(c, 12, 3, 21, 8, 12, 13, 3, 8); open(c, 3, 12, 12, 17, 21, 12); open(c, 3, 16, 12, 21, 21, 16); break;
            case "brightness": {
                c.drawCircle(12, 12, 4.5f, p);
                Path half = new Path(); half.addArc(new RectF(7.5f, 7.5f, 16.5f, 16.5f), 90, 180); half.close();
                fill(); c.drawPath(half, p); stroke();
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    line(c, (float) (12 + 7 * Math.cos(a)), (float) (12 + 7 * Math.sin(a)), (float) (12 + 9.3 * Math.cos(a)), (float) (12 + 9.3 * Math.sin(a)));
                }
                break;
            }
            case "ruler":
                c.drawRoundRect(new RectF(8, 2.5f, 16, 21.5f), 1.5f, 1.5f, p);
                for (int i = 0; i < 6; i++) { float y = 5.5f + i * 2.8f; line(c, 8, y, i % 2 == 0 ? 12.5f : 11, y); }
                break;
            case "link":
                c.save(); c.rotate(-45, 12, 12);
                c.drawRoundRect(new RectF(2.5f, 8.5f, 13, 15.5f), 3.5f, 3.5f, p);
                c.drawRoundRect(new RectF(11, 8.5f, 21.5f, 15.5f), 3.5f, 3.5f, p);
                c.restore(); break;
            case "loop":
                c.drawArc(new RectF(4, 5, 20, 19), 200, 140, false, p);
                c.drawArc(new RectF(4, 5, 20, 19), 20, 140, false, p);
                open(c, 17, 4, 18.5f, 7.5f, 15, 8.5f); open(c, 7, 20, 5.5f, 16.5f, 9, 15.5f); break;
            case "annotations":
                c.drawRoundRect(new RectF(8, 8, 20, 20), 2, 2, p); open(c, 4, 16, 4, 4, 16, 4); break;
            case "crossref":
                line(c, 12, 3, 12, 9); line(c, 12, 15, 12, 21); line(c, 3, 12, 9, 12); line(c, 15, 12, 21, 12); break;
            case "teacher":
                closed(c, 2, 9, 12, 4, 22, 9, 12, 14); open(c, 6, 11, 6, 16, 12, 19, 18, 16, 18, 11); line(c, 22, 9, 22, 15); break;
            case "file":
                open(c, 14, 3, 6, 3, 6, 21, 18, 21, 18, 7, 14, 3, 14, 7, 18, 7); line(c, 9, 12, 15, 12); line(c, 9, 16, 15, 16); break;
            case "folder": closed(c, 3, 6, 9, 6, 11, 8, 21, 8, 21, 19, 3, 19); break;
            case "download": line(c, 12, 3, 12, 15); open(c, 7, 10, 12, 15, 17, 10); line(c, 4, 20, 20, 20); break;
            case "server":
                c.drawRoundRect(new RectF(4, 3, 20, 10), 1.5f, 1.5f, p); c.drawRoundRect(new RectF(4, 12, 20, 19), 1.5f, 1.5f, p);
                fill(); c.drawCircle(7.5f, 6.5f, 1, p); c.drawCircle(7.5f, 15.5f, 1, p); stroke(); line(c, 12, 19, 12, 22); break;
            case "cube":
                closed(c, 12, 2.5f, 20.5f, 7, 20.5f, 17, 12, 21.5f, 3.5f, 17, 3.5f, 7); open(c, 3.5f, 7, 12, 11.5f, 20.5f, 7); line(c, 12, 11.5f, 12, 21.5f); break;
            case "tags": line(c, 5, 6, 19, 6); line(c, 5, 10, 19, 10); line(c, 5, 14, 15, 14); line(c, 5, 18, 12, 18); break;
            case "help": c.drawCircle(12, 12, 9, p); open(c, 9.3f, 9.5f, 9.8f, 7.8f, 12, 7, 14.3f, 7.8f, 14.7f, 10, 12, 12, 12, 14); fill(); c.drawCircle(12, 17, 1.1f, p); break;
            case "reset":
                c.drawArc(new RectF(4, 4, 20, 20), 300, 300, false, p); open(c, 17, 3, 17.5f, 6.5f, 14, 7.2f);
                line(c, 9.5f, 9.5f, 14.5f, 14.5f); line(c, 14.5f, 9.5f, 9.5f, 14.5f); break;
            case "trash": line(c, 4, 6, 20, 6); open(c, 9, 6, 9, 3.5f, 15, 3.5f, 15, 6); open(c, 6, 6, 7, 21, 17, 21, 18, 6); break;
            case "album": c.drawRoundRect(new RectF(3, 6, 18, 20), 2, 2, p); open(c, 6, 3, 21, 3, 21, 17); break;
            case "settings": c.drawCircle(12, 12, 3.2f, p);
                for (int i = 0; i < 8; i++) { double a = Math.PI * i / 4; line(c, (float) (12 + 6 * Math.cos(a)), (float) (12 + 6 * Math.sin(a)), (float) (12 + 8.8 * Math.cos(a)), (float) (12 + 8.8 * Math.sin(a))); }
                c.drawCircle(12, 12, 6, p); break;
            case "info": c.drawCircle(12, 12, 9, p); line(c, 12, 11, 12, 17); fill(); c.drawCircle(12, 7.5f, 1.1f, p); break;
            case "select": closed(c, 6, 3, 6, 19, 10.2f, 15, 13.2f, 21.5f, 15.8f, 20.3f, 12.8f, 14, 18.5f, 14); break;
            case "length": line(c, 5, 19, 19, 5); c.drawCircle(5, 19, 2, p); c.drawCircle(19, 5, 2, p); line(c, 9, 13, 11, 15); line(c, 13, 9, 15, 11); break;
            case "angle": line(c, 4, 20, 21, 20); line(c, 4, 20, 15, 5); c.drawArc(new RectF(-4, 12, 12, 28), -54, 54, false, p); break;
            case "cobb": line(c, 4, 4, 20, 8.5f); line(c, 4, 20, 20, 15.5f); line(c, 11.5f, 6, 9, 12); line(c, 11.5f, 18, 9, 12); c.drawCircle(9, 12, 1.3f, p); break;
            case "ellipse": c.drawOval(new RectF(3, 6, 21, 18), p); break;
            case "rect": c.drawRect(new RectF(4, 6, 20, 18), p); break;
            case "probe": c.drawCircle(12, 12, 6, p); line(c, 12, 2, 12, 6); line(c, 12, 18, 12, 22); line(c, 2, 12, 6, 12); line(c, 18, 12, 22, 12); fill(); c.drawCircle(12, 12, 1.6f, p); break;
            case "arrow": line(c, 19, 19, 6, 6); open(c, 6, 13, 6, 6, 13, 6); break;
            case "eraser": closed(c, 3.5f, 14.5f, 13, 5, 20, 12, 11.5f, 20.5f, 7, 20.5f); line(c, 8.5f, 9.5f, 15.5f, 16.5f); line(c, 11.5f, 20.5f, 20.5f, 20.5f); break;
            case "undo": open(c, 9, 5, 4, 10, 9, 15); open(c, 4, 10, 14, 10); c.drawArc(new RectF(9, 10, 20, 21), -90, 180, false, p); line(c, 14.5f, 21, 8, 21); break;
            case "star": closed(c, star()); break;
            case "starfill": fill(); closed(c, star()); break;
            case "check": p.setStrokeWidth(2.2f); open(c, 5, 12.5f, 10, 17.5f, 19, 7); break;
            case "move": line(c, 12, 3, 12, 21); line(c, 3, 12, 21, 12); open(c, 9, 6, 12, 3, 15, 6); open(c, 9, 18, 12, 21, 15, 18); open(c, 6, 9, 3, 12, 6, 15); open(c, 18, 9, 21, 12, 18, 15); break;
            case "target": c.drawCircle(12, 12, 6, p); line(c, 12, 2, 12, 8); line(c, 12, 16, 12, 22); line(c, 2, 12, 8, 12); line(c, 16, 12, 22, 12); break;
            case "curve": open(c, 3.5f, 18, 8, 9, 13.5f, 14.5f, 20.5f, 5.5f); fill(); c.drawCircle(3.5f, 18, 1.8f, p); c.drawCircle(8, 9, 1.8f, p); c.drawCircle(13.5f, 14.5f, 1.8f, p); c.drawCircle(20.5f, 5.5f, 1.8f, p); break;
            case "export": open(c, 8, 7.5f, 12, 3.5f, 16, 7.5f); line(c, 12, 3.5f, 12, 15); open(c, 5, 12, 5, 20.5f, 19, 20.5f, 19, 12); break;
            case "import": line(c, 12, 3.5f, 12, 15); open(c, 8, 11, 12, 15, 16, 11); open(c, 5, 12, 5, 20.5f, 19, 20.5f, 19, 12); break;
            case "book": closed(c, 12, 6.5f, 3.5f, 4.5f, 3.5f, 18.5f, 12, 20.5f); closed(c, 12, 6.5f, 20.5f, 4.5f, 20.5f, 18.5f, 12, 20.5f); break;
            case "lock": c.drawRoundRect(new RectF(5.5f, 10.5f, 18.5f, 20.5f), 2, 2, p); c.drawArc(new RectF(8, 4, 16, 14), 180, 180, false, p); line(c, 8, 9, 8, 10.5f); line(c, 16, 9, 16, 10.5f); break;
            case "shield": closed(c, 12, 3, 19.5f, 6, 19, 13, 12, 21, 5, 13, 4.5f, 6); break;
            case "eye": c.drawArc(new RectF(2, 5, 22, 29), 218, 104, false, p); c.drawArc(new RectF(2, -5, 22, 19), 38, 104, false, p); c.drawCircle(12, 12, 3, p); break;
            case "contrast": c.drawCircle(12, 12, 8.5f, p); { Path hp = new Path(); hp.addArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), -90, 180); hp.close(); fill(); c.drawPath(hp, p); } break;
            case "rotate": c.drawArc(new RectF(4, 4, 20, 20), 300, 270, false, p); open(c, 16.5f, 3, 17.5f, 6.7f, 13.8f, 7.5f); break;
            case "fliph": closed(c, 9.5f, 6, 9.5f, 18, 3, 12); closed(c, 14.5f, 6, 14.5f, 18, 21, 12); line(c, 12, 3, 12, 5); line(c, 12, 8, 12, 10); line(c, 12, 14, 12, 16); line(c, 12, 19, 12, 21); break;
            case "flipv": closed(c, 6, 9.5f, 18, 9.5f, 12, 3); closed(c, 6, 14.5f, 18, 14.5f, 12, 21); line(c, 3, 12, 5, 12); line(c, 8, 12, 10, 12); line(c, 14, 12, 16, 12); line(c, 19, 12, 21, 12); break;
            case "sliders": line(c, 4, 7, 20, 7); line(c, 4, 17, 20, 17); fill(); c.drawCircle(9, 7, 2.5f, p); c.drawCircle(15, 17, 2.5f, p); break;
            case "label": closed(c, 3, 12, 11, 4, 20, 4, 20, 13, 12, 21); c.drawCircle(15.5f, 8.5f, 1.5f, p); break;
            case "copy": c.drawRoundRect(new RectF(8, 8, 20, 20), 2, 2, p); open(c, 4, 16, 4, 4, 16, 4); break;
            case "maximize": open(c, 4, 9, 4, 4, 9, 4); open(c, 15, 4, 20, 4, 20, 9); open(c, 20, 15, 20, 20, 15, 20); open(c, 9, 20, 4, 20, 4, 15); break;
            case "clearimg": c.drawRoundRect(new RectF(3.5f, 4.5f, 20.5f, 19.5f), 2, 2, p); line(c, 9, 9, 15, 15); line(c, 15, 9, 9, 15); break;
            case "hand": open(c, 8, 13, 8, 5.5f, 10, 4, 12, 5.5f, 12, 11); open(c, 12, 5.5f, 14, 4, 16, 5.5f, 16, 12); open(c, 16, 7.5f, 18, 6.5f, 19.5f, 7.5f, 19.5f, 15, 16, 21, 10, 21, 5, 15, 4.5f, 12.5f, 6, 11.5f, 8, 13); break;
            // Layout glyphs
            case "lay1": frame(c); fill(); c.drawCircle(12, 12, 1.3f, p); break;
            case "lay2v": frame(c); line(c, 4, 12, 20, 12); fill(); c.drawCircle(12, 8, 1.2f, p); c.drawCircle(12, 16, 1.2f, p); break;
            case "lay2h": frame(c); line(c, 12, 4, 12, 20); fill(); c.drawCircle(8, 12, 1.2f, p); c.drawCircle(16, 12, 1.2f, p); break;
            case "lay3v": frame(c); line(c, 4, 9.33f, 20, 9.33f); line(c, 4, 14.66f, 20, 14.66f); break;
            case "lay3h": frame(c); line(c, 9.33f, 4, 9.33f, 20); line(c, 14.66f, 4, 14.66f, 20); break;
            case "lay4": frame(c); line(c, 12, 4, 12, 20); line(c, 4, 12, 20, 12); break;
            default: c.drawCircle(12, 12, 8, p);
        }
        c.restore();
    }

    static float[] star() {
        float[] v = new float[20];
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, r = i % 2 == 0 ? 9.5 : 4.2;
            v[2 * i] = (float) (12 + r * Math.cos(a));
            v[2 * i + 1] = (float) (12.8 + r * Math.sin(a));
        }
        return v;
    }

    void fill() { p.setStyle(Paint.Style.FILL); }
    void stroke() { p.setStyle(Paint.Style.STROKE); }
    void frame(Canvas c) { c.drawRoundRect(new RectF(4, 4, 20, 20), 2, 2, p); }
    void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, p); }
    void open(Canvas c, float... v) { c.drawPath(path(v, false), p); }
    void closed(Canvas c, float... v) { c.drawPath(path(v, true), p); }
    static Path path(float[] v, boolean close) {
        Path pa = new Path();
        pa.moveTo(v[0], v[1]);
        for (int i = 2; i + 1 < v.length; i += 2) pa.lineTo(v[i], v[i + 1]);
        if (close) pa.close();
        return pa;
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
