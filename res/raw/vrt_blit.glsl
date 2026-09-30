#version 300 es
// Insula DICOM Viewer, GPL-3.0-or-later. Draws the ray-cast image (rendered at reduced resolution) to the screen.
precision mediump float;
uniform sampler2D uTex;
in vec2 vNdc;
out vec4 fragColor;
void main() { fragColor = texture(uTex, vNdc * 0.5 + 0.5); }
