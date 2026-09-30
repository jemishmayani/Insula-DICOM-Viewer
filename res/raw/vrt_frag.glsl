#version 300 es
// Insula DICOM Viewer, GPL-3.0-or-later.
// GPU ray casting of a labelled volume. CpuVrt.java implements the same steps for testing and fallback.
precision highp float;
precision highp int;
precision highp sampler3D;
precision highp sampler2D;

uniform sampler3D uVol;       // modality values (HU), R16F, linear filtering
uniform sampler3D uLab;       // tissue class per voxel, R8, nearest; values >= 128 were removed by the user
uniform sampler2D uTf;        // 256 x 8 RGBA transfer functions, one row per class
uniform mat3 uToModel;        // columns: camera right, up, back in model space
uniform vec3 uHalf;           // box half extents in model space
uniform vec3 uN;              // volume dimensions in voxels
uniform vec3 uClipLo;         // clip box in texture space
uniform vec3 uClipHi;
uniform vec2 uPan;
uniform float uZoom;
uniform float uAspect;
uniform float uStepScale;     // 1 = one voxel per step; larger while the user drags
uniform float uHuMin;
uniform float uHuMax;
uniform float uKa;
uniform float uKd;
uniform float uKs;
uniform float uShin;
uniform float uSurface;       // opacity at which Surface mode stops
uniform vec2 uWin;            // window centre and width for MIP and MinIP
uniform vec3 uBg;
uniform int uMode;            // 0 VRT, 1 MIP, 2 MinIP, 3 Surface
uniform int uVis;             // bit c set if class c is visible

in vec2 vNdc;
out vec4 fragColor;

float hu(vec3 q) { return texture(uVol, q).r; }

vec3 safeDir(vec3 d) {
    return vec3(abs(d.x) < 1e-6 ? 1e-6 : d.x, abs(d.y) < 1e-6 ? 1e-6 : d.y, abs(d.z) < 1e-6 ? 1e-6 : d.z);
}

vec3 shade(vec3 base, vec3 q, vec3 rd, vec3 vox) {
    vec3 d = 1.0 / uN;
    vec3 g = vec3(hu(q + vec3(d.x, 0.0, 0.0)) - hu(q - vec3(d.x, 0.0, 0.0)),
                  hu(q + vec3(0.0, d.y, 0.0)) - hu(q - vec3(0.0, d.y, 0.0)),
                  hu(q + vec3(0.0, 0.0, d.z)) - hu(q - vec3(0.0, 0.0, d.z))) / vox;
    float gl = length(g);
    float diff = 1.0;
    float spec = 0.0;
    if (gl > 1e-3) {
        float ndl = abs(dot(g / gl, -rd));
        diff = ndl;
        spec = pow(ndl, uShin);
    }
    return base * (uKa + uKd * diff) + vec3(uKs * spec);
}

void main() {
    vec3 cam = vec3(vNdc.x * uAspect / uZoom - uPan.x, vNdc.y / uZoom - uPan.y, 3.0);
    vec3 ro = uToModel * cam;
    vec3 rd = -uToModel[2];
    vec3 bmin = -uHalf + uClipLo * 2.0 * uHalf;
    vec3 bmax = -uHalf + uClipHi * 2.0 * uHalf;
    vec3 inv = 1.0 / safeDir(rd);
    vec3 ta = (bmin - ro) * inv;
    vec3 tb = (bmax - ro) * inv;
    vec3 tmin = min(ta, tb);
    vec3 tmax = max(ta, tb);
    float tnear = max(max(max(tmin.x, tmin.y), tmin.z), 0.0);
    float tfar = min(min(tmax.x, tmax.y), tmax.z);
    if (tfar <= tnear) { fragColor = vec4(uBg, 1.0); return; }

    vec3 vox = 2.0 * uHalf / uN;
    float step = min(vox.x, min(vox.y, vox.z)) * uStepScale;
    vec4 acc = vec4(0.0);
    float m = uMode == 2 ? 1e9 : -1e9;
    bool found = false;
    for (int i = 0; i < 4096; i++) {
        float t = tnear + (float(i) + 0.5) * step;
        if (t > tfar) break;
        vec3 q = (ro + rd * t + uHalf) / (2.0 * uHalf);
        int lab = int(texture(uLab, q).r * 255.0 + 0.5);
        if (lab >= 128) continue;
        if (((uVis >> lab) & 1) == 0) continue;
        float h = hu(q);
        if (uMode == 1) { m = max(m, h); found = true; continue; }
        if (uMode == 2) { m = min(m, h); found = true; continue; }
        float u = clamp((h - uHuMin) / (uHuMax - uHuMin), 0.0, 1.0);
        vec4 tf = texture(uTf, vec2(u, (float(lab) + 0.5) / 8.0));
        if (uMode == 3) {
            if (tf.a >= uSurface) { fragColor = vec4(shade(tf.rgb, q, rd, vox), 1.0); return; }
            continue;
        }
        if (tf.a > 0.002) {
            float a = 1.0 - pow(1.0 - tf.a, uStepScale);
            vec3 col = shade(tf.rgb, q, rd, vox);
            acc.rgb += (1.0 - acc.a) * a * col;
            acc.a += (1.0 - acc.a) * a;
            if (acc.a > 0.97) break;
        }
    }
    if (uMode == 1 || uMode == 2) {
        if (!found) { fragColor = vec4(uBg, 1.0); return; }
        float gv = clamp((m - (uWin.x - uWin.y * 0.5)) / uWin.y, 0.0, 1.0);
        fragColor = vec4(vec3(gv), 1.0);
        return;
    }
    if (uMode == 3) { fragColor = vec4(uBg, 1.0); return; }
    fragColor = vec4(acc.rgb + (1.0 - acc.a) * uBg, 1.0);
}
