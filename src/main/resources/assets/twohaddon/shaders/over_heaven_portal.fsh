#version 120
uniform vec3 CameraForward, CameraRight, CameraUp;
uniform float FlowTime, MistRadius;
uniform vec3 MistOrigin;
uniform int MistPass;
// Median sRGB of the supplied lavender swatch, shared by the portal and the mist.
const vec3 Swatch = vec3(126.0,103.0,219.0)/255.0;
varying vec3 ViewDirection;
varying vec3 LocalPosition, BoxLow, BoxHigh;

varying float ExposedEdges;
const float SmokeTransparency=.60, MistDensity=.17, MistWindSpeed=2.;
float h3(vec3 p) {
    p = fract(p*vec3(.1031,.11369,.13787));
    p += dot(p,p.yzx+19.19);
    return fract((p.x+p.y)*p.z);
}
float n3(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    vec4 a=vec4(h3(i),h3(i+vec3(0,1,0)),h3(i+vec3(0,0,1)),h3(i+vec3(0,1,1)));
    i.x+=1.0;
    vec4 b=vec4(h3(i),h3(i+vec3(0,1,0)),h3(i+vec3(0,0,1)),h3(i+vec3(0,1,1)));
    vec4 c=mix(a,b,f.x);
    return mix(mix(c.x,c.y,f.y),mix(c.z,c.w,f.y),f.z);
}
vec3 octave(vec3 p) { return vec3(.8*p.x-.6*p.z,.6*p.x+.8*p.z,p.y)*2.04+vec3(3.3,9.1,4.6); }
float fb(vec3 p) {
    float d=.55*n3(p); p=octave(p); d+=.27*n3(p);
    p=octave(p); d+=.13*n3(p); return d+.05*n3(octave(p));
}
vec3 energy(vec3 ray) {
    vec3 p=ray*3.65 + vec3(3.2,4.5,1.3), drift=vec3(.14,-.21,.105)*FlowTime;
    vec3 a=p*.83+drift*.42;
    vec3 turn=vec3(n3(a),n3(a+13.4),n3(a-4.9))-.5;
    vec3 b=p*1.12+2.75*turn+drift;
    vec3 roll=vec3(n3(b+5.8),n3(b-11.3),n3(b))-.5;
    vec3 plume=p+3.4*turn+1.75*roll+drift;
    float density=fb(plume*1.35), back=fb(plume*.81+ray*.65+17.0);
    float body=smoothstep(.30,.76,density*.78+back*.22);
    float light=smoothstep(-.10,.18,density-fb(plume*1.35+vec3(-.19,.27,.15)));
    float warm=smoothstep(.29,.74,n3(a*.88+turn+7.1));
    // A slow cloud bank: the gaps between the main eddies are smoke, not a flat swatch.
    float t=FlowTime;
    vec3 farP=ray*2.5+vec3(-2.8,6.1,4.7)+vec3(-.032,.049,.023)*t;
    farP+=1.55*(vec3(n3(farP+3.),n3(farP-5.),n3(farP))-.5);
    float farD=fb(farP*1.48);
    // A sideways-drifting veil with its own deformation and a soft, broken edge.
    vec3 veilP=ray.yzx*4.9+vec3(4.3,-2.7,8.2)+vec3(.065,-.036,.041)*t;
    veilP+=2.35*(vec3(n3(veilP*.63+7.),n3(veilP*.63),n3(veilP*.63-3.))-.5);
    float veilD=fb(veilP*1.18+turn*.72);
    // A finer lilac wisp, sheared differently so it never duplicates the front curl.
    vec3 silkP=ray.zxy*vec3(7.2,4.3,5.7)+vec3(-3.,2.7,14.)+vec3(-.094,.075,.028)*t;
    float silk=fb(silkP+2.7*roll+1.6*vec3(veilD,farD,veilD));
    float shade=.56*smoothstep(.23,.75,farD)+.31*smoothstep(.28,.71,veilD)+.13*smoothstep(.38,.70,silk);
    vec3 under=mix(Swatch,vec3(172.,150.,250.)/255.,shade);
    float dye=clamp(.14+.32*warm,0.,1.);
    vec3 plum=mix(vec3(.35,.13,.43),vec3(.57,.13,.37),dye);
    vec3 rim=mix(vec3(.74,.47,.76),vec3(.90,.48,.68),dye);
    // Use a wider, gently warped volume for the plume envelope. Keep the
    // original curl inside it, but do not light a narrow density contour.
    vec3 cloud=(p+2.15*turn+.58*roll+drift)*1.13;
    vec3 axis=vec3(.29,-.19,.21);
    float mass=.50*fb(cloud)+.25*fb(cloud-axis)+.25*fb(cloud+axis);
    float cover=smoothstep(.18,.82,mix(mass,density,.18));
    float scatter=clamp(.15+.66*cover+.18*(back-.5)+.10*(light-.5),0.,1.);
    vec3 front=mix(plum,rim,scatter);
    float curl=mix(cover,cover*cover,.21);
    return mix(under,front,(curl*.91)*(1.-SmokeTransparency));
}
// Only soften physical ledges, never an internal chunk/proxy boundary.
float ledgeDistance(vec3 p) {
    vec4 mask=mod(floor(floor(ExposedEdges+.5)/vec4(1.,2.,4.,8.)),2.);
    vec4 dist=vec4(p.x-BoxLow.x,BoxHigh.x-p.x,p.z-BoxLow.z,BoxHigh.z-p.z);
    vec4 edges=mix(vec4(100.),dist,mask);
    return min(min(edges.x,edges.y),min(edges.z,edges.w));
}
vec4 groundMist() {
    vec3 dir=normalize(LocalPosition);
    vec3 inv=1./mix(vec3(.000001),dir,step(vec3(.000001),abs(dir)));
    vec3 a=BoxLow*inv,b=BoxHigh*inv,lo=min(a,b),hi=max(a,b);
    float first=max(0.,max(lo.x,max(lo.y,lo.z))),last=min(hi.x,min(hi.y,hi.z));
    float span=last-first;
    if(span<=.0001) discard;
    vec2 accum=vec2(0.);
    const int mistSteps=6;
    float stepSize=span/float(mistSteps);
    for(int i=0;i<mistSteps;++i){
        vec3 p=dir*(first+(float(i)+.5)*stepSize);
        float z=clamp((p.y-BoxLow.y)/(BoxHigh.y-BoxLow.y),0.,1.);
        vec2 pos=p.xz+MistOrigin.xz-vec2(.9578,.2874)*(FlowTime*MistWindSpeed);
        // Continuous mirrored domain across 8192-block origin rebases.
        pos=2048.-abs(mod(pos+2048.,8192.)-4096.);
        // Two overlapping broad fields; no density threshold or sharply cut cloud tops.
        vec2 g=vec2(.063*pos.x+.019*pos.y,-.036*pos.x+.121*pos.y);
        float bank=.70*n3(vec3(g,2.3+.12*z))+.30*n3(vec3(g.yx*1.51+vec2(8.7,-4.2),6.1));
        float prof=(1.-smoothstep(0.,1.,z))*mix(.88,1.12,bank);
        float mass=mix(.57,1.06,bank)*prof*smoothstep(0.,1.25,ledgeDistance(p));
        mass*=1.-smoothstep(.66*MistRadius,MistRadius,length(p.xz));
        accum+=mass*vec2(1.,.25+.55*bank+.12*z);
    }
    accum*=stepSize*MistDensity*1.65;
    vec3 tint=mix(Swatch,vec3(165.,146.,248.)/255.,accum.y/max(accum.x,.0001));
    return vec4(tint,1.-exp(-accum.x));
}
void main() {
    if (MistPass != 0) { gl_FragColor = groundMist(); return; }
    vec3 v = normalize(ViewDirection);
    vec3 ray = normalize(CameraRight*v.x + CameraUp*v.y - CameraForward*v.z);
    gl_FragColor = vec4(energy(ray),1.0);
}
