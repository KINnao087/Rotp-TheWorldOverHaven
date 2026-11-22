#version 120
varying vec3 Local;
varying vec4 Rest;
varying vec4 Tint;

void main() {
    Local = gl_Vertex.xyz;
    Rest = gl_ModelViewProjectionMatrix * vec4(gl_MultiTexCoord0.xyz, 1.0);
    Tint = gl_Color;
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
