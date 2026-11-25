#version 120
varying vec3 WorldRay;
void main() {
    vec3 d = normalize(WorldRay);
    gl_FragColor = vec4(voidBelow(otherSky(d), d), 1.0);
}
