#version 120
varying vec3 ViewDirection;
varying vec3 LocalPosition, BoxLow, BoxHigh;
varying float ExposedEdges;
void main() {
    ViewDirection = (gl_ModelViewMatrix * gl_Vertex).xyz;
    LocalPosition = gl_Vertex.xyz;
    ExposedEdges = gl_MultiTexCoord0.w;
    BoxLow = gl_MultiTexCoord0.xyz; BoxHigh = gl_MultiTexCoord1.xyz;
    gl_Position = ftransform();
}
