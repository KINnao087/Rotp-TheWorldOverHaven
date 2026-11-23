#version 120

attribute vec4 Position;
uniform mat4 ProjMat;
uniform mat4 ViewProjection;
uniform vec2 OutSize;
uniform vec3 Center;
varying vec2 texCoord;
varying vec3 lensCenter;

// EN: Project the emitter itself, even when the sphere fills the frame.
// 中文：始终投影世界球心；球壳大小不能把纹理中心拉向屏幕中心。
vec3 projectedFocus() {
    vec4 clip = ViewProjection * vec4(Center, 1.0);
    float facing = clip.w / max(length(Center), .000001);
    if (facing <= .05) return vec3(.5, .5, 0.0);
    return vec3(.5 + .5 * clip.xy / clip.w, smoothstep(.05, .12, facing));
}

void main() {
    vec4 pos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(pos.xy, .2, 1.0);
    texCoord = Position.xy / OutSize;
    lensCenter = projectedFocus();
}

