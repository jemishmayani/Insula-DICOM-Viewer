# Insula DICOM Viewer
# Copyright (C) 2026 Jemish Mayani
# SPDX-License-Identifier: GPL-3.0-or-later
"""Renders the VrtTest scenes with the app's real GLSL ES 3.00 shader (res/raw/vrt_frag.glsl) on an OpenGL context
and compares each image with the Java CPU renderer's output. Usage: vrt_gpu_check.py <work>/vrt <repo>/res/raw"""
import json, os, sys
import numpy as np

def main():
    d, raw = sys.argv[1], sys.argv[2]
    try:
        import moderngl
        ctx = moderngl.create_standalone_context(backend="egl")
    except Exception as e:
        print("SKIP GPU shader check: no OpenGL context available (%s)" % e)
        open(os.path.join(d, "gpu_result.txt"), "w").write("SKIPPED: %s\n" % e)
        return 0
    print("     GPU:", ctx.info["GL_RENDERER"])
    meta = json.load(open(os.path.join(d, "meta.json")))
    nx, ny, nz = meta["nx"], meta["ny"], meta["nz"]
    vol = np.fromfile(os.path.join(d, "vol.bin"), dtype="<i2").astype(np.float16)
    lab = np.fromfile(os.path.join(d, "lab.bin"), dtype=np.uint8)
    prog = ctx.program(vertex_shader=open(os.path.join(raw, "vrt_vert.glsl")).read(),
                       fragment_shader=open(os.path.join(raw, "vrt_frag.glsl")).read())
    tv = ctx.texture3d((nx, ny, nz), 1, vol.tobytes(), dtype="f2")
    tv.filter = (moderngl.LINEAR, moderngl.LINEAR); tv.repeat_x = tv.repeat_y = tv.repeat_z = False
    tl = ctx.texture3d((nx, ny, nz), 1, lab.tobytes(), dtype="f1")
    tl.filter = (moderngl.NEAREST, moderngl.NEAREST); tl.repeat_x = tl.repeat_y = tl.repeat_z = False
    quad = ctx.buffer(np.array([-1, -1, 1, -1, -1, 1, 1, 1], dtype="f4").tobytes())
    vao = ctx.vertex_array(prog, [(quad, "2f", "aPos")])
    fails = 0
    report = ["RAN on %s (%s)" % (ctx.info["GL_RENDERER"], ctx.info["GL_VERSION"])]
    for c in meta["cases"]:
        w, h = c["w"], c["h"]
        tf = ctx.texture((256, 8), 4, open(os.path.join(d, c["tf"]), "rb").read(), dtype="f1")
        tf.filter = (moderngl.LINEAR, moderngl.LINEAR); tf.repeat_x = tf.repeat_y = False
        tv.use(0); tl.use(1); tf.use(2)
        u = {"uVol": 0, "uLab": 1, "uTf": 2, "uToModel": tuple(c["toModel"]), "uHalf": tuple(meta["half"]),
             "uN": (float(nx), float(ny), float(nz)), "uClipLo": tuple(c["clipLo"]), "uClipHi": tuple(c["clipHi"]),
             "uPan": (c["panX"], c["panY"]), "uZoom": c["zoom"], "uAspect": w / h, "uStepScale": c["stepScale"],
             "uHuMin": float(meta["huMin"]), "uHuMax": float(meta["huMax"]), "uKa": c["ka"], "uKd": c["kd"], "uKs": c["ks"],
             "uShin": c["shin"], "uSurface": c["surface"], "uWin": (c["winC"], c["winW"]), "uBg": (0.0, 0.0, 0.0),
             "uMode": c["mode"], "uVis": c["vis"]}
        for k, val in u.items():
            if k in prog: prog[k].value = val
        fbo = ctx.simple_framebuffer((w, h), components=4)
        fbo.use(); fbo.clear(0, 0, 0, 1)
        vao.render(moderngl.TRIANGLE_STRIP)
        gpu = np.frombuffer(fbo.read(components=3), dtype=np.uint8).reshape(h, w, 3)[::-1].astype(int)   # GL rows start at the bottom
        argb = np.fromfile(os.path.join(d, "cpu_%s.bin" % c["name"]), dtype="<u4").reshape(h, w)
        cpu = np.stack([(argb >> 16) & 255, (argb >> 8) & 255, argb & 255], axis=-1).astype(int)
        diff = np.abs(gpu - cpu).max(axis=-1)
        mean, big = diff.mean(), (diff > 16).mean() * 100
        # Surface mode stops at a hard opacity threshold, so tiny float differences flip whole pixels at edges.
        lim_mean, lim_big = (3.0, 4.0) if c["mode"] == 3 else (2.0, 3.0)
        ok = mean < lim_mean and big < lim_big and gpu.max() > 0
        if not ok: fails += 1
        line = "%s GPU shader matches CPU reference: %-14s mean diff %.2f/255, %.2f%% of pixels differ by >16" % ("PASS" if ok else "FAIL", c["name"], mean, big)
        print(line)
        report.append(line)
        try:
            from PIL import Image
            Image.fromarray(np.concatenate([cpu, gpu], axis=1).astype(np.uint8)).save(os.path.join(d, "compare_%s.png" % c["name"]))
        except Exception:
            pass
    print("ALL PASSED" if fails == 0 else "%d FAILED" % fails)
    open(os.path.join(d, "gpu_result.txt"), "w").write("\n".join(report) + "\n")
    return 1 if fails else 0

if __name__ == "__main__":
    sys.exit(main())
