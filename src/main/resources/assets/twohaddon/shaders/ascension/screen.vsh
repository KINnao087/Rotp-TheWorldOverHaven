#version 120
varying vec2 uv;
void main(){
    gl_Position=gl_Vertex;
    uv=.5+.5*gl_Vertex.xy;
}
