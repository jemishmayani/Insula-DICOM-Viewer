/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GPU volume rendering with OpenGL ES 3.0: the volume is a half-float 3D texture, tissue classes an 8-bit 3D texture,
 * and res/raw/vrt_frag.glsl ray-casts them into an offscreen buffer at reduced resolution, which is then scaled to the
 * screen. The shader is tested against CpuVrt in tests/python/vrt_gpu_check.py.
 */
final class GlVrt extends GLSurfaceView implements GLSurfaceView.Renderer {
    interface Host {
        /** Called on the UI thread if the GPU can't render this volume (for example, out of memory). */
        void onGpuFailure(String why);
        void onCaptured(int[] argb, int w, int h, Object tag);
    }

    final Host host;
    volatile Vol3D vol;
    volatile VrtState st;
    volatile boolean volDirty, labDirty, tfDirty, interactive;
    /** Fraction of screen resolution for ray casting: at rest and while dragging. */
    volatile float restScale = 0.6f, dragScale = 0.33f;
    volatile float restStep = 1f, dragStep = 2f;
    // capture request (read on the GL thread)
    volatile int capW, capH;
    volatile Object capTag;

    int prog, blit, vbo, texVol, texLab, texTf, fbo, fboTex, fboW, fboH, viewW, viewH;
    boolean failed;
    final String vert, frag, blitFrag;

    GlVrt(Context c, Host host) {
        super(c);
        this.host = host;
        vert = raw(c, R.raw.vrt_vert);
        frag = raw(c, R.raw.vrt_frag);
        blitFrag = raw(c, R.raw.vrt_blit);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    static String raw(Context c, int id) {
        try {
            InputStream in = c.getResources().openRawResource(id);
            String s = new String(Library.readAll(in), "UTF-8");
            in.close();
            return s;
        } catch (Exception e) { return ""; }
    }

    void setScene(Vol3D v, VrtState s) { vol = v; st = s; volDirty = labDirty = tfDirty = true; requestRender(); }
    void labelsChanged() { labDirty = true; requestRender(); }
    void tfChanged() { tfDirty = true; requestRender(); }
    void redraw(boolean dragging) { interactive = dragging; requestRender(); }
    void capture(int w, int h, Object tag) { capW = w; capH = h; capTag = tag; requestRender(); }

    // ---------------- GL thread ----------------
    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try {
            prog = program(vert, frag);
            blit = program(vert, blitFrag);
            int[] b = new int[1];
            GLES20.glGenBuffers(1, b, 0);
            vbo = b[0];
            FloatBuffer q = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            q.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, q, GLES20.GL_STATIC_DRAW);
            int[] t = new int[5];
            GLES20.glGenTextures(3, t, 0);
            texVol = t[0]; texLab = t[1]; texTf = t[2];
            texVol = t[0];
            fbo = 0; fboTex = 0;
            volDirty = labDirty = tfDirty = true;
            failed = false;
        } catch (final Exception e) { fail(e.getMessage()); }
    }

    @Override public void onSurfaceChanged(GL10 unused, int w, int h) { viewW = w; viewH = h; }

    @Override public void onDrawFrame(GL10 unused) {
        Vol3D v = vol;
        VrtState s = st;
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (failed || v == null || s == null || prog == 0) return;
        try {
            upload(v, s);
            if (failed) return;
            Object tag = capTag;
            if (tag != null) {
                int cw = capW, ch = capH;
                capTag = null;
                int[] px = renderTo(v, s, cw, ch, restStep * 0.75f, true);
                if (px != null) post(tag, px, cw, ch);
            }
            float scale = interactive ? dragScale : restScale;
            int rw = Math.max(64, Math.round(viewW * scale)), rh = Math.max(64, Math.round(viewH * scale));
            renderTo(v, s, rw, rh, interactive ? dragStep : restStep, false);
            // draw the offscreen image to the screen
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, viewW, viewH);
            GLES20.glUseProgram(blit);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(blit, "uTex"), 0);
            quad(blit);
        } catch (Exception e) { fail(e.getMessage()); }
    }

    void post(final Object tag, final int[] px, final int w, final int h) {
        post(new Runnable() { public void run() { host.onCaptured(px, w, h, tag); } });
    }

    void fail(final String why) {
        failed = true;
        post(new Runnable() { public void run() { host.onGpuFailure(why == null ? "GPU error" : why); } });
    }

    /** Uploads whatever changed. Detects out-of-memory so the app can fall back to a smaller volume. */
    void upload(Vol3D v, VrtState s) {
        if (volDirty) {
            volDirty = false;
            ShortBuffer sb = ByteBuffer.allocateDirect(v.n() * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
            short[] row = new short[v.nx];
            for (int o = 0; o < v.n(); o += v.nx) {
                for (int i = 0; i < v.nx; i++) row[i] = Vol3D.toHalf(v.hu[o + i]);
                sb.put(row);
            }
            sb.position(0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, texVol);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2);
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R16F, v.nx, v.ny, v.nz, 0, GLES30.GL_RED, GLES30.GL_HALF_FLOAT, sb);
            params3d(GLES20.GL_LINEAR);
            if (checkMemory("volume")) return;
        }
        if (labDirty) {
            labDirty = false;
            ByteBuffer lb = ByteBuffer.allocateDirect(v.n()).order(ByteOrder.nativeOrder());
            lb.put(v.labels).position(0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, texLab);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8, v.nx, v.ny, v.nz, 0, GLES30.GL_RED, GLES20.GL_UNSIGNED_BYTE, lb);
            params3d(GLES20.GL_NEAREST);
            if (checkMemory("tissue map")) return;
        }
        if (tfDirty) {
            tfDirty = false;
            byte[] t = s.table(v.huMin, v.huMax);
            ByteBuffer tb = ByteBuffer.allocateDirect(t.length).order(ByteOrder.nativeOrder());
            tb.put(t).position(0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texTf);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 8, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, tb);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }
    }

    boolean checkMemory(String what) {
        int e = GLES20.glGetError();
        if (e == GLES20.GL_OUT_OF_MEMORY) { fail("The GPU ran out of memory for the " + what + "."); return true; }
        if (e != GLES20.GL_NO_ERROR) { fail("The GPU couldn't load the " + what + " (error " + e + ")."); return true; }
        return false;
    }

    static void params3d(int filter) {
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES20.GL_CLAMP_TO_EDGE);
    }

    /** Ray-casts into the offscreen buffer at w x h. If read, returns the pixels as ARGB with row 0 at the top. */
    int[] renderTo(Vol3D v, VrtState s, int w, int h, float step, boolean read) {
        ensureFbo(w, h);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(prog);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, texVol);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, texLab);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texTf);
        u1i("uVol", 0); u1i("uLab", 1); u1i("uTf", 2);
        GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(prog, "uToModel"), 1, false, s.cam.toModel(), 0);
        double[] hf = v.half();
        u3f("uHalf", (float) hf[0], (float) hf[1], (float) hf[2]);
        u3f("uN", v.nx, v.ny, v.nz);
        u3f("uClipLo", s.clipLo[0], s.clipLo[1], s.clipLo[2]);
        u3f("uClipHi", s.clipHi[0], s.clipHi[1], s.clipHi[2]);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "uPan"), (float) s.cam.panX, (float) s.cam.panY);
        u1f("uZoom", (float) s.cam.zoom); u1f("uAspect", w / (float) h); u1f("uStepScale", step);
        u1f("uHuMin", v.huMin); u1f("uHuMax", v.huMax);
        u1f("uKa", s.ka); u1f("uKd", s.kd); u1f("uKs", s.ks); u1f("uShin", s.shin); u1f("uSurface", s.surface);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "uWin"), (float) s.winC, (float) s.winW);
        u3f("uBg", ((s.bg >> 16) & 255) / 255f, ((s.bg >> 8) & 255) / 255f, (s.bg & 255) / 255f);
        u1i("uMode", s.mode); u1i("uVis", s.visMask());
        quad(prog);
        if (!read) return null;
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int o = ((h - 1 - y) * w + x) * 4;       // GL rows start at the bottom
            out[y * w + x] = 0xFF000000 | ((bb.get(o) & 255) << 16) | ((bb.get(o + 1) & 255) << 8) | (bb.get(o + 2) & 255);
        }
        return out;
    }

    void ensureFbo(int w, int h) {
        if (fbo != 0 && fboW == w && fboH == h) return;
        int[] t = new int[1];
        if (fbo != 0) { t[0] = fbo; GLES20.glDeleteFramebuffers(1, t, 0); t[0] = fboTex; GLES20.glDeleteTextures(1, t, 0); }
        GLES20.glGenTextures(1, t, 0);
        fboTex = t[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glGenFramebuffers(1, t, 0);
        fbo = t[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0);
        fboW = w; fboH = h;
    }

    void quad(int p) {
        int loc = GLES20.glGetAttribLocation(p, "aPos");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(loc);
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    void u1i(String n, int v) { GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, n), v); }
    void u1f(String n, float v) { GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, n), v); }
    void u3f(String n, float a, float b, float c) { GLES20.glUniform3f(GLES20.glGetUniformLocation(prog, n), a, b, c); }

    static int program(String vs, String fs) throws Exception {
        int v = shader(GLES20.GL_VERTEX_SHADER, vs), f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new Exception("GPU program failed to link: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }

    static int shader(int type, String src) throws Exception {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new Exception("GPU shader failed to compile: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }
}
