#version 120
varying vec3 ViewDirection;
varying vec3 LocalPosition, BoxLow, BoxHigh;
varying float ExposedEdges;
void main() {
    // EN: IP 0.17 uses the compatibility clip plane for individual portal views.
    // 中文：IP 单门视图使用兼容管线裁剪面；自定义顶点着色器也须提供眼空间坐标。
    gl_ClipVertex = gl_ModelViewMatrix * gl_Vertex;
    ViewDirection = gl_ClipVertex.xyz;
    LocalPosition = gl_Vertex.xyz;
    ExposedEdges = gl_MultiTexCoord0.w;
    BoxLow = gl_MultiTexCoord0.xyz; BoxHigh = gl_MultiTexCoord1.xyz;
    gl_Position = ftransform();
}
