#version 120
uniform mat4 SkyView;
uniform mat4 SkyProjection;
uniform int LateSky;
varying vec3 WorldRay;
void main(){
    WorldRay = gl_Vertex.xyz;
    vec4 camera = SkyView * vec4(WorldRay * 100.0, 1.0);
    gl_ClipVertex = camera;
    if (LateSky != 0) {
        gl_Position = SkyProjection * camera;
    } else {
        gl_Position = gl_ProjectionMatrix * camera;
    }
    gl_Position.z = gl_Position.w * 0.999999;
}
