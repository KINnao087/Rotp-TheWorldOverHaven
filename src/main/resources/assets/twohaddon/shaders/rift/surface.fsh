#version 120
uniform sampler2D SceneB;
uniform vec3 Viewport;
uniform int Layer;
varying vec4 Rest;
varying vec4 Tint;

void main() {
    vec2 screen = gl_FragCoord.xy / Viewport.xy;
    if (Layer == 1) {
        // EN: Move the live source sample with the shard. No sky reflection or artificial highlight.
        // 中文：此岸采样跟随碎片原位移动，不添加天空反射或高光。
        vec2 restUV = .5 + .5*Rest.xy/Rest.w;
        vec2 shift = .006*vec2(cos(Tint.g*91.0),sin(Tint.g*91.0));
        vec2 uv = mix(screen,restUV+shift,Tint.r);
        gl_FragColor = vec4(texture2D(SceneB,clamp(uv,.001,.999)).rgb,1.0);
    } else {
        gl_FragColor = vec4(Tint.rgb,1.0);
    }
}
