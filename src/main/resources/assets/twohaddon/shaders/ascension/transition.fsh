#version 120
// The environment prelude supplies the same oriented cubemap, noise and layered void as the dimension.
uniform sampler2D SceneColor, SceneDepth;
uniform mat4 invertedVP, vp;
uniform vec3 centerRelative;
uniform float t;
uniform int destination; // Whether the current frame is already rendering the destination world.
uniform int returning;   // A non-Heaven destination must supply its own final sky pixels.
varying vec2 uv;

// Selected on the native preview (4.00x). These are sRGB palette values as in the demo.
const float pixelDensity=512.;
const vec3 burnEdgeColor=vec3(255.,240.,51.)/255.;
const vec3 emberColor=vec3(204.,48.,226.)/255.;
float burnField(vec3 d){
    float islands=min(length(d-normalize(vec3(-.61,.34,-1.))),.17+length(d-normalize(vec3(.46,.50,-1.))));
    islands=min(islands,.31+length(d-normalize(vec3(.18,.83,.68))));
    islands=min(islands,.43+length(d-normalize(vec3(-.91,.12,.42))));
    vec3 warp=d*4.+vec3(3.,8.,17.);
    vec3 fold=vec3(ns(warp),ns(warp.yzx+9.),ns(warp.zxy-6.))-.5;
    return islands+.54*(fb3(d*8.4+fold*2.)-.5)+.040*(ns(d*144.)-.5);
}
// One RGB control drives the halo, flame and hot core, not the charcoal or detached sparks.
// The two knots are the blue and green channels of the original amber swatch (#FFA833).
vec3 rimTone(vec3 tint, vec3 stops){
    const float b=51./255., g=168./255.;
    vec3 value=vec3(stops.x)*clamp(tint/b,0.,1.);
    value=mix(value,vec3(stops.y),clamp((tint-b)/(g-b),0.,1.));
    return mix(value,vec3(stops.z),clamp((tint-g)/(1.-g),0.,1.));
}
// A square grid on each cubemap face, independent of viewport size, camera and frame.
void skyChart(vec3 d,out vec3 n,out vec3 x,out vec3 y,out vec2 uv){
    vec3 a=abs(d);
    if(a.x>=a.y && a.x>=a.z){n=vec3(sign(d.x),0.,0.);x=vec3(0.,0.,1.);y=vec3(0.,1.,0.);}
    else if(a.y>=a.z){n=vec3(0.,sign(d.y),0.);x=vec3(1.,0.,0.);y=vec3(0.,0.,1.);}
    else{n=vec3(0.,0.,sign(d.z));x=vec3(1.,0.,0.);y=vec3(0.,1.,0.);}
    uv=vec2(dot(d,x),dot(d,y))/dot(d,n);
}
vec3 cellCenter(vec3 n,vec3 x,vec3 y,vec2 uv,float cells){
    vec2 index=clamp(floor((uv*.5+.5)*cells),0.,cells-1.);
    vec2 center=(index+.5)*(2./cells)-1.;
    return n+x*center.x+y*center.y;
}
vec3 burningSky(vec3 d,vec3 sourceSky,vec3 other,float elapsed){
    if(elapsed<=0.)return sourceSky;
    if(elapsed>=2.85)return other;
    float u=clamp(elapsed/2.85,0.,1.);
    float front=mix(-.29,2.32,pow(u,1.32));
    float cells=pixelDensity;
    float cellScale=128./cells;
    float guardBand=.38*max(1.,cellScale);
    float rough=burnField(d)-front;
    if(rough>guardBand)return sourceSky;
    if(rough< -guardBand)return other;
    vec3 n,x,y;vec2 uv;skyChart(d,n,x,y,uv);
    vec3 tile=cellCenter(n,x,y,uv,cells);
    vec3 dx=x/cells,dy=y/cells;
    // Adjacent cells, including cells across cube seams, evaluate the same corner directions.
    vec4 corners=vec4(burnField(normalize(tile-dx-dy)),burnField(normalize(tile+dx-dy)),
                      burnField(normalize(tile-dx+dy)),burnField(normalize(tile+dx+dy)))-front;
    float lo=min(min(corners.x,corners.y),min(corners.z,corners.w));
    float hi=max(max(corners.x,corners.y),max(corners.z,corners.w));
    float span=clamp(hi-lo,.012*cellScale,.064*cellScale);
    vec3 flame=rimTone(burnEdgeColor,vec3(.20,.66,1.));
    vec3 darkFlame=rimTone(burnEdgeColor,vec3(.020,.133,.54));
    if(lo<=0. && hi>=0.){
        float peak=max(burnEdgeColor.r,max(burnEdgeColor.g,burnEdgeColor.b));
        vec3 hot=max(rimTone(burnEdgeColor,vec3(.69,.94,1.)),vec3(.34*peak));
        return dot(corners,vec4(.25))<0. || ns(normalize(tile)*46.)>.61 ? hot : flame;
    }
    if(lo>0.){
        if(lo<.42*span)return darkFlame;
        if(lo<1.36*span)return vec3(.1137,.0745,.0980);
        return sourceSky;
    }
    if(-hi<.31*span)return flame;
    if(-hi<.72*span)return darkFlame;
    // Smaller independent ember sprites behind the rim; three palette entries, never a sky tint.
    vec3 sparkRay=normalize(cellCenter(n,x,y,uv,5.*cells));
    float e=burnField(sparkRay)-front;
    float grainLevel=smoothstep(.79,.89,ns(sparkRay*260.+vec3(t*3.2,-t*7.,0.)));
    float spark=grainLevel*smoothstep(-.23,-.12,e)*(1.-smoothstep(-.10,-.012,e));
    if(spark>.15)return emberColor*(spark>.78?1.:spark>.42?.65:.36);
    return other;
}

vec3 viewPoint(vec2 coord,float depth){
    vec4 p=invertedVP*vec4(coord*2.-1.,depth*2.-1.,1.);
    return p.xyz/p.w;
}
float tileArrival(vec3 p){
    vec2 tile=floor(p.xz);
    float radial=smoothstep(0.,1.,length(tile)/34.);
    return clamp(.77*radial+.16*noise2(tile+51.)+.07*(.5+.5*sin(dot(tile,vec2(.48,.30)))),0.,1.);
}
float tileProgress(vec3 p) {
    float a=tileArrival(p);
    return destination==1 ? clamp((t-6.08-a*1.78)/.83,0.,1.) : clamp((t-3.28-a*1.77)/.56,0.,1.);
}
// Lift the sampling position into the original, undisplaced scene, rather than tinting its pixels.
// A second projected sample follows adjacent blocks. Reject silhouettes that expose unknown geometry.
vec2 liftedUV(vec3 p,float drop) {
    vec4 clip=vp*vec4(p+vec3(0.,drop,0.),1.);
    return .5+.5*clip.xy/max(clip.w,.0001);
}
void main(){
    float depth=texture2D(SceneDepth,uv).r;
    vec3 scene=texture2D(SceneColor,uv).rgb;
    vec3 ray=normalize(viewPoint(uv,.995));
    bool sky=depth>=1.-0.0000001;
    if(sky && returning!=0){
        // Before the swap SceneColor still belongs to Heaven: don't burn back to Heaven.
        // Once the destination is live it includes its own day, weather, fog and custom sky renderer.
        if(destination==0 || t>=8.65) gl_FragColor=vec4(scene,1.);
        else gl_FragColor=vec4(burningSky(ray,voidBelow(otherSky(ray),ray),scene,t-5.80),1.);
        return;
    }
    if(t<3.28){
        // Do not burn through solid foreground geometry just because the player is looking up.
        gl_FragColor=vec4(sky ? burningSky(ray,scene,otherSky(ray),t-.45) : scene,1.);
        return;
    }
    vec3 nextSky=otherSky(ray);
    vec3 voidColor=voidBelow(nextSky,ray);
    if(sky) {
        gl_FragColor=vec4(destination==0 ? voidColor : mix(voidColor,scene,smoothstep(7.80,8.70,t)),1.);
        return;
    }
    if((destination==0 && t>=5.63)||(destination!=0 && t<=6.08)){
        gl_FragColor=vec4(voidColor,1.);
        return;
    }
    vec3 point=viewPoint(uv,depth);
    vec3 local=point-centerRelative;
    float phase=tileProgress(local);
    float drop=destination==0 ? phase*phase*.85 : (1.-phase)*(1.-phase)*(1.65+.32*tileArrival(local));
    vec2 tap=liftedUV(point,drop);
    if(drop>.001 && all(greaterThan(tap,vec2(0.))) && all(lessThan(tap,vec2(1.)))){
        float displacedDepth=texture2D(SceneDepth,tap).r;
        vec3 samplePoint=viewPoint(tap,min(.9999999,displacedDepth));
        // Do not smear distant sky or a background cliff across an unoccluded foreground edge.
        if(displacedDepth<1. && distance(samplePoint,point)<max(2.5,length(point)*.08)){
            local=samplePoint-centerRelative;
            phase=tileProgress(local);
            scene=texture2D(SceneColor,tap).rgb;
        }
    }
    vec3 grid=floor(local*16.)/16.;
    float erosion=.48*ns(grid*1.66+vec3(17.,0.,33.))+.33*ns(grid*4.1+vec3(-7.,0.,19.))
                  +.14*ns(floor(grid*5.2)+23.)+.05*ns(floor(grid*16.)+17.);
    if(destination==0){
        if(phase>=1. || (phase>0. && erosion<phase*.99)) scene=voidColor;
        else scene=mix(scene,vec3(.69,.59,.82),.72*(1.-smoothstep(0.,.13,erosion-phase*.99))*smoothstep(.03,.30,phase));
    } else {
        // Real destination terrain emerges tile-by-tile, and is completely unmodified at rest.
        if(phase<=0. || (phase<1. && erosion>phase)) scene=voidColor;
        else {
            vec2 edge=abs(fract(local.xz)-.5);
            scene=mix(scene,vec3(.90,.83,1.),(1.-phase)*(.19+.51*smoothstep(.456,.497,max(edge.x,edge.y))));
        }
    }
    gl_FragColor=vec4(scene,1.);
}
