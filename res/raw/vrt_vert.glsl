#version 300 es
// Insula DICOM Viewer, GPL-3.0-or-later. Full-screen quad for ray casting and display.
in vec2 aPos;
out vec2 vNdc;
void main() {
    vNdc = aPos;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
