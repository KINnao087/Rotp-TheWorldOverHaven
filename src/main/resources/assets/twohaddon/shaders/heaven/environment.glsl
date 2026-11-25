// Shared by permanent Over Heaven and its travel transition; GLSL 1.20 fragment prelude.
uniform samplerCube heavenSky;
uniform sampler2D grain;
uniform vec3 heavenEye;
uniform float heavenTime;
vec3 distantSky(vec3 d){return textureCube(heavenSky,d).rgb;}
float noise2(vec2 p){ vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
    return texture2D(grain,(i+f+.5)/256.).r; }
float ns(vec3 p){ float k=floor(p.z),v=fract(p.z); v=v*v*(3.-2.*v);
    return mix(noise2(p.xy+k*vec2(37.,71.)),noise2(p.xy+(k+1.)*vec2(37.,71.)),v);}
vec2 rot(vec2 a){return mat2(.8,.6,-.6,.8)*a;}
float fb(vec2 q){float n=.52*noise2(q);q=rot(q)*2.13+13.1;n+=.27*noise2(q);
    q=rot(q)*2.05+16.7;n+=.14*noise2(q);return n+.07*noise2(rot(q)*2.09);}
float fb3(vec3 p){return .52*ns(p)+.27*ns(p.zxy*2.03+11.)+.14*ns(p.yzx*4.17-14.)+.07*ns(p*8.43+26.);}
float stars(vec2 p, float key){
    vec2 cell=floor(p),j=vec2(noise2(cell+key),noise2(cell+53.+key));
    vec2 q=fract(p)-(.17+.66*j);
    float r=mix(.019,.080,pow(j.y,3.));
    float aa=clamp(length(fwidth(p))*.42,.004,.080);
    float core=1.-smoothstep(r*.35,r+aa,length(q));
    return step(.62,j.x)*core*(.28+.72*j.y);
}
// Three differently deep layers below the missing ground; all coordinates are world anchored.
vec3 voidBelow(vec3 base,vec3 d){
    if(d.y>=-.002)return base;
    float horizon=smoothstep(.009,.17,-d.y);
    vec2 farP=heavenEye.xz+d.xz*((-19.-heavenEye.y)/d.y);
    vec2 midP=heavenEye.xz+d.xz*((-8.5-heavenEye.y)/d.y);
    vec2 nearP=heavenEye.xz+d.xz*((-3.2-heavenEye.y)/d.y);
    vec2 p=rot(midP*.13)+vec2(-.3,1.5);
    vec2 flow=vec2(fb(p*.78+vec2(.032*heavenTime,3.)),fb(p*.76+vec2(13.,-.023*heavenTime)))-.5;
    vec2 q=p+2.8*flow;
    float fold=fb(q*1.72+vec2(-.026*heavenTime,.042*heavenTime));
    float ridge=abs(q.y-.44*q.x-1.25*sin(q.x*1.16+.27)-.30);
    float band=exp(-ridge*ridge*.65);
    float lanes=.54+.46*cos(7.5*q.y+5.2*fold+1.7*q.x);
    float cloud=band*smoothstep(.21,.77,fold)*(.55+.45*lanes);
    float wisps=exp(-abs(1.35*fold+.18*ridge-.80)*16.)*band;
    float deep=fb(farP*.105+vec2(3.,.018*heavenTime));
    vec3 c=mix(vec3(.035,.034,.093),vec3(.16,.096,.275),deep);
    vec3 hue=mix(vec3(.24,.34,.68),vec3(.73,.32,.60),smoothstep(-2.,4.,q.x+fold*3.));
    c+=hue*cloud*1.48+vec3(.34,.24,.39)*wisps*.58;
    float foreground=fb(nearP*.19+vec2(17.,-.023*heavenTime));
    c*=1.-.29*smoothstep(.54,.78,foreground);
    c+=vec3(.70,.77,1.)*stars(farP*.86,23.)*.84+vec3(.98,.82,.76)*stars(midP*.46,103.)*.59;
    return mix(base,c,horizon);
}

// Shared direction-space sky for the permanent dimension and the ascension transition.
// Direction-space details for the heaven sky. Both passes return additive radiance.
float ssHash(vec3 p) {
    p = fract(p * vec3(.1031, .1030, .0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 skyStars(vec3 d, float time) {
    vec3 a = abs(d);
    vec2 st;
    float face;
    if (a.x >= a.y && a.x >= a.z) { st = d.zy / a.x; face = d.x > 0. ? 1. : 2.; }
    else if (a.y >= a.z)          { st = d.xz / a.y; face = d.y > 0. ? 3. : 4.; }
    else                          { st = d.xy / a.z; face = d.z > 0. ? 5. : 6.; }

    // Leave a margin around each cell (including the cube edges). No texture
    // seam, crescent-shaped cut-off stars, or positions that crawl as the camera turns.
    vec2 grid = (st * .5 + .5) * 60.;
    vec3 cell = vec3(floor(grid), face);
    float seed = ssHash(cell + 17.1);
    float pixel = clamp(30. * max(length(dFdx(d)), length(dFdy(d))) /
                        max(a.x, max(a.y, a.z)), .008, .21);
    if (seed < .82 || d.y <= -.01) return vec3(0.);
    vec2 center = .20 + .60 * vec2(ssHash(cell + 5.7), ssHash(cell + 9.1));
    vec2 q = fract(grid) - center;
    float sizeSeed = ssHash(cell + 21.);
    float r = mix(.026, .079, sizeSeed*sizeSeed);
    float r2 = r*r + .23*pixel*pixel;
    float radsq = dot(q, q);
    float core = exp(-radsq/r2) * (r*r/r2);
    float glow = .105 * exp(-radsq/(4.*r2));
    float phase = 6.2831853 * ssHash(cell + 29.);
    float rate = mix(.77, 2.65, ssHash(cell + 63.));
    // Two non-commensurate oscillators per star: the field never strobes together.
    float pulse = .55 + .31*sin(time*rate + phase) + .14*sin(time*(rate*.43+.32) + 2.1*phase);
    vec3 tint = mix(vec3(.67,.76,1.), vec3(1.,.82,.94), ssHash(cell + 1.8));
    float horizon = smoothstep(-.01, .13, d.y);
    return tint * (core+glow) * mix(.32,1.12,sizeSeed) * (.23 + 1.05*pulse) * horizon;
}

vec3 ssMeteor(vec3 d, float time, float track, vec3 aim, float footprint) {
    float period = 8.1 + .73*track;
    float cycle = floor(time/period);
    float salt = 14. + track*5.;
    float jitter = ssHash(vec3(cycle, salt, 7.2));
    float debut = .24 + mod(track*3.1,4.9) + jitter*1.25;
    // The first trail crosses the black-hole sightline; later showers vary in speed and heading.
    if (track < .5 && cycle == 0.) debut = .24;
    float duration = 1.08 + .31*ssHash(vec3(cycle,salt,4.));
    float f = (mod(time,period)-debut)/duration;
    float den = dot(d,aim);
    if (f <= 0. || f >= 1. || den < .62 || d.y < .05) return vec3(0.);

    vec3 right = normalize(cross(aim,vec3(0.,1.,0.)));
    vec3 up = cross(right,aim);
    vec2 p = vec2(dot(d,right),dot(d,up)) / den;
    float side = mod(track,2.) < 1. ? 1. : -1.;
    vec2 start = vec2(side*(.49+.20*jitter), .25+.12*ssHash(vec3(cycle,salt,11.)));
    vec2 travel = vec2(-side*(.31+.13*jitter), -.14-.10*ssHash(vec3(cycle,salt,20.)));
    if (track < .5 && cycle == 0.) { start = vec2(.62,.34); travel = vec2(-.39,-.17); }
    vec2 tangent = normalize(travel);
    vec2 off = p - (start + travel*f);
    float behind = -dot(off,tangent);
    float across = abs(dot(off,vec2(-tangent.y,tangent.x)));
    float tail = mix(.13,.21,jitter);
    float k = clamp(behind/tail,0.,1.);
    float age = f - max(behind,0.) / length(travel);
    float emission = smoothstep(0.,.12,age) * (1.-smoothstep(.67,1.,f));
    if (emission <= 0.) return vec3(0.);
    // Angular, not pixel widths. Integration keeps the taper legible without
    // screen-fixed size, a hard end cap, or a glowing line ahead of the head.
    float px = .7 * footprint / den + .00004;
    float w = mix(.00155,.00048,k);
    float width = sqrt(w*w + px*px*.36);
    float along = smoothstep(-width, width, behind) * (1.-smoothstep(.04,1.,k));
    float ribbon = exp(-across*across/(width*width)) * (w/width) * along;
    float mistWidth = .005+.004*k;
    float mist = exp(-across*across/(mistWidth*mistWidth)) * exp(-3.8*k) * along;
    float headW = sqrt(.0024*.0024 + px*px*.36);
    float head = exp(-dot(off,off)/(headW*headW)) * (.0024*.0024/(headW*headW));
    float bloom = exp(-dot(off,off)/(.007*.007));
    return emission*smoothstep(.05,.13,d.y) *
        (vec3(.62,.66,1.)*ribbon*.95 + vec3(.43,.21,.68)*mist*.36 +
         vec3(1.,.92,1.)*head*1.55 + vec3(.76,.61,.95)*bloom*.20);
}

vec3 skyMeteors(vec3 d, float time) {
    float footprint = max(length(dFdx(d)), length(dFdy(d)));
    vec3 north = normalize(vec3(-.18,.48,-1.));
    vec3 sum = ssMeteor(d,time,0.,north,footprint) + ssMeteor(d,time,1.,north,footprint);
    sum += ssMeteor(d,time,2.,normalize(vec3(.88,.59,.45)),footprint);
    sum += ssMeteor(d,time,3.,normalize(vec3(-.85,.66,.56)),footprint);
    return sum;
}

// Emitters live in comoving *volume* coordinates (one unit = one shadow radius).
// Integrate a compact quartic ball exactly along the finite ray segment. The
// full on-axis integral is 1, irrespective of how the march subdivides it.
float bhStarBall(float perp2, float s0, float s1, float radius) {
    float w=1.-perp2/(radius*radius);
    if(w<=0.) return 0.;
    float h=radius*sqrt(w);
    vec2 x=clamp(vec2(s0,s1)/h,-1.,1.);
    vec2 f=x*(1.+x*x*(-.6666667+.2*x*x));
    return .9375*w*w*sqrt(w)*(f.y-f.x);
}

vec3 bhStarsInGas(vec3 rest, vec3 restRay, float stepLen, float time) {
    const float cellSize=.54;
    const float occupancy=.56;
    vec3 start=rest-.5*stepLen*restRay;
    vec3 grid=start/cellSize;
    vec3 cell=floor(grid);
    vec3 stride=sign(restRay);
    vec3 ticks=cellSize/max(abs(restRay),vec3(.000001));
    vec3 toFace=(step(vec3(0.),restRay)-fract(grid))*stride;
    // An exactly parallel axis must never be stepped (also at a cell boundary).
    vec3 nextFace=mix(vec3(1.e6),toFace*ticks,abs(stride));
    float t0=0.;
    vec3 sum=vec3(0.);
    for(int i=0;i<7;i++){
        float t1=min(stepLen,min(nextFace.x,min(nextFace.y,nextFace.z)));
        float seed=ssHash(cell+vec3(8.,41.,17.));
        if(seed<occupancy) {
            vec3 j=vec3(ssHash(cell+43.7),ssHash(cell+18.3),ssHash(cell+73.6));
            // Halo stays inside the emitter's own cell. Check only crossed
            // cells, not a 27-neighbour box per march step.
            vec3 centre=cellSize*(cell+.18+.64*j);
            vec3 delta=centre-start;
            float at=dot(delta,restRay);
            vec3 perp=delta-at*restRay;
            float d2=dot(perp,perp);
            float rare=ssHash(cell+95.3);
            float r=.0145+.012*rare*rare*rare;
            if(d2<7.84*r*r) {
                float core=bhStarBall(d2,t0-at,t1-at,r);
                float halo=bhStarBall(d2,t0-at,t1-at,2.8*r);
                vec3 tint=mix(vec3(.65,.81,1.),vec3(1.,.77,.68),smoothstep(.56,.95,j.y));
                float breath=.93+.07*sin(.74*time+6.283185*j.z);
                sum+=tint*breath*(1.25+3.75*rare*rare*rare)*(core+.095*halo);
            }
        }
        if(t1>=stepLen) break;
        vec3 hit=step(nextFace,vec3(t1+.000001));
        cell+=hit*stride; nextFace+=hit*ticks; t0=t1;
    }
    return sum;
}

// Coordinates in the orbiting disk: x/y equatorial, z vertical; one unit is the shadow radius.
const float bhEmbeddedStars=1.0;
const float gasOuter=4.47, gasHeight=.69;

vec3 bhGasRest(vec3 pos,float time) {
    float r=length(pos.xy);
    // Advection plus bounded differential shear; unlike angle += t / r, the cloud
    // does not wind into threads after leaving the viewer running for a few minutes.
    float a=-.15*time+.46*log(max(.9,r))+.31*sin(1.4*r-.34*time);
    float c=cos(a),s=sin(a);
    return vec3(mat2(c,s,-s,c)*pos.xy,pos.z);
}

// A bright annulus with a diffuse outer skirt, specified in disk-space radii.
float bhEmittingBelt(float r) {
    return 1.-smoothstep(0.36,1.04,abs(r-2.39));
}
float bhGasEnvelope(vec3 v) {
    float r=length(v.xy);
    float z0=.085*sin(1.31*v.x+.53*v.y)+.056*cos(1.60*v.y-.32*v.x);
    float outer=3.95+.29*sin(1.52*v.x+.38*v.y)+.20*sin(1.2*v.y);
    float width=.38+.11*sin(.85*r)+.08*cos(1.4*v.y-.52*v.x);
    // Constrain the emitting gas in disk coordinates, not in the sky image.
    float belt=bhEmittingBelt(r);
    float heightScale=mix(0.67,0.78,belt);
    z0*=heightScale;
    width*=heightScale;
    float columnDensity=mix(0.60,1.22,belt);
    return columnDensity*smoothstep(1.12,1.58,r)*(1.-smoothstep(outer-.65,outer+.25,r))*
           (1.-smoothstep(width*.20,width,abs(v.z-z0)));
}

vec3 bhGasCoord(vec3 v) {
    vec3 q=v*vec3(1.82,1.82,3.52);
    q.xy+=.39*vec2(sin(.81*q.y+.62*q.z),cos(1.12*q.x-.38*q.z));
    return q+vec3(8.4,36.2,15.7);
}
float bhMacroGas(vec3 v) {
    vec3 q=bhGasCoord(v);
    return bhGasEnvelope(v)*smoothstep(.28,.69,.64*ns(q)+.36*ns(q.yzx*2.05+11.));
}
float bhGasDensity(vec3 v,out float detail) {
    float envelope=bhGasEnvelope(v);
    detail=0.;
    if(envelope<.001) return 0.;
    vec3 q=bhGasCoord(v);
    float coarse=ns(q);
    float mid=ns(q.yzx*2.05+11.);
    vec3 fold=q.zxy*2.91+vec3(8.1,2.8,4.7)+vec3(mid*1.86,coarse*1.82,0.);
    float fine=ns(fold*1.47);
    detail=ns(fold.yzx*2.90+vec3(9.3,fine*2.05,1.5));
    float structure=.43*coarse+.30*mid+.19*fine+.08*detail;
    // Sculpt big, irregular cloud banks with gaps; keep the outer haze translucent.
    return envelope*(.008+.992*smoothstep(.335,.647,structure))*(.58+.79*fine);
}

// Integrate one short curved-path chord into the SAME front-to-back accumulator.
void bhIntegrateArc(vec3 a,vec3 b,float time,inout vec4 gas) {
    if(gas.a<.009 || min(a.z,b.z)>gasHeight || max(a.z,b.z)<-gasHeight ||
       max(length(a.xy),length(b.xy))<1.12)return;
    vec3 line=b-a;
    float lo=0.,hi=1.;
    if(abs(line.z)>.00001){
        vec2 cut=(vec2(-gasHeight,gasHeight)-a.z)/line.z;
        lo=max(0.,min(cut.x,cut.y)); hi=min(1.,max(cut.x,cut.y));
    }
    if(hi<=lo)return;
    vec3 pos=a+lo*line;
    float len=length(line)*(hi-lo);
    if(len<.00001)return;
    int steps=int(clamp(ceil(len/.080),1.,120.));
    float ds=len/float(steps);
    vec3 stepVec=line*((hi-lo)/float(steps));
    vec3 mid=a+(.5*(lo+hi))*line;
    float radius=max(1.0,length(mid)); // gas is absent at smaller radii
    float radial=dot(mid/radius,normalize(line));
    float rs=.3849001795;
    float properDs=ds*sqrt(1.+rs/(radius-rs)*radial*radial);
    vec3 rest0=bhGasRest(pos,time),emission=gas.rgb;
    float T=gas.a;
    for(int i=0;i<120;i++){
        if(i>=steps)break;
        pos+=stepVec;
        vec3 rest1=bhGasRest(pos,time);
        vec3 v=.5*(rest0+rest1), delta=rest1-rest0;
        rest0=rest1;
        float detail;
        float density=bhGasDensity(v,detail);
        if(density<.0015)continue;
        float radius=length(v.xy);
        vec3 lightDir=normalize(vec3(-v.x*.72-.8,-v.y*.72+.30,1.95));
        float ahead=bhMacroGas(v+lightDir*.22);
        float blocking=ahead*.67+bhMacroGas(v+lightDir*.61)*1.18;
        float lit=exp(-2.85*blocking);
        float shoulder=clamp(.48+1.65*(density-ahead),.08,1.);
        float heat=exp(-.44*max(0.,radius-1.35));
        float hue=.5+.5*sin(.95*v.x-.77*v.y+.55);
        vec3 shadow=mix(vec3(.047,.033,.127),vec3(.109,.035,.124),hue);
        vec3 mantle=mix(vec3(.46,.49,.97),vec3(.94,.42,.77),hue);
        float crest=clamp(.20+heat*.39+detail*.36,0.,1.);
        vec3 light=mix(mantle,vec3(1.,.86,.98),crest*.73);
        vec3 scatter=shadow+light*(.09+1.30*lit)*(.48+.79*heat)*(.73+.58*detail)*(.50+.83*shoulder);
        // Dustier banks still extinguish what lies behind them but emit less:
        // their irregular dark lanes separate the illuminated cloud shoulders.
        float dust=smoothstep(.40,.72,ns(v*1.24+vec3(18.3,-5.7,23.1)))*(.82-.29*lit);
        scatter*=mix(vec3(1.),vec3(.31,.23,.47),dust);
        scatter/=1.+.46*max(scatter.r,max(scatter.g,scatter.b));
        scatter*=mix(0.58,1.06,bhEmittingBelt(radius));
        float opticalDepth=2.95*density*properDs;
        float alpha=1.-exp(-opticalDepth);
        emission+=T*alpha*scatter;
        if(bhEmbeddedStars>.5) {
            float stepRest=length(delta);
            float inCloud=smoothstep(.016,.21,density);
            emission+=T*exp(-.5*opticalDepth)*inCloud*
                bhStarsInGas(v,delta/max(.00001,stepRest),stepRest,time);
        }
        T*=1.-alpha;
        if(T<.013)break;
    }
    gas=vec4(emission,T);
}

// Schwarzschild null rays in their orbital plane, traced backward from a distant
// observer. G=c=1, lengths in critical impact parameters (3 sqrt(3) M = 1).
const float bhM=.19245008972987526, bhShadowScale=.122*2.0, bhExtent=6.25;
const vec3 bhAxis=vec3(-.18,.48,-1.);
const float bhInclination=.236, bhPi=3.141592653589793;

vec2 bhODE(vec2 s){return vec2(s.y,-s.x+3.*bhM*s.x*s.x);}
vec2 bhOrbitStep(vec2 state,float h){
    vec2 k1=bhODE(state),k2=bhODE(state+.5*h*k1);
    vec2 k3=bhODE(state+.5*h*k2),k4=bhODE(state+h*k3);
    return state+h/6.*(k1+2.*k2+2.*k3+k4);
}

// Hermite event location between two RK steps (sphere entry, escape or capture).
float bhCrossing(vec2 a,vec2 b,float h,float level){
    float t=clamp((level-a.x)/(b.x-a.x),0.,1.);
    float A=2.*(a.x-b.x)+h*(a.y+b.y),B=3.*(b.x-a.x)-h*(2.*a.y+b.y);
    for(int i=0;i<2;i++){
        float err=((A*t+B)*t+h*a.y)*t+a.x-level,der=(3.*A*t+2.*B)*t+h*a.y;
        if(abs(der)>.0000001)t=clamp(t-err/der,0.,1.);
    }
    return t;
}

void bhTrace(vec2 p,float time,out vec4 gas,out float exitPhi,out bool captured){
    float impact=length(p),ci=sqrt(1.-bhInclination*bhInclination);
    vec3 obs=vec3(0.,-ci,bhInclination);
    const float bound=4.57, uMin=1./bound, uH=1./(2.*bhM);
    gas=vec4(0.,0.,0.,1.);exitPhi=bhPi;captured=(impact<=1.);
    if(impact<.0001){
        bhIntegrateArc(obs*bound,obs*(2.*bhM),time,gas);
        return;
    }
    vec2 axis=p/impact;
    vec3 lat=vec3(axis.x,bhInclination*axis.y,ci*axis.y);
    vec2 state=vec2(0.,1./impact);  // u=1/r at infinity, du/dphi=1/b.
    float phi=0.,h=.065*min(1.,impact);
    for(int i=0;i<352;i++){
        vec2 nextState=bhOrbitStep(state,h);
        float ua=state.x,ub=nextState.x;
        // March gas only along the portion of the trajectory that can contain it.
        if(gas.a>.009 && max(ua,ub)>=uMin && min(ua,ub)<uH){
            float ta=0.,tb=1.,u0=ua,u1=ub;
            if(ua<uMin){ta=bhCrossing(state,nextState,h,uMin);u0=uMin;}
            if(ub<uMin){tb=bhCrossing(state,nextState,h,uMin);u1=uMin;}
            if(ub>=uH){tb=bhCrossing(state,nextState,h,uH);u1=uH;}
            float a=phi+ta*h,b=phi+tb*h;
            vec3 pa=(obs*cos(a)+lat*sin(a))/u0,pb=(obs*cos(b)+lat*sin(b))/u1;
            bhIntegrateArc(pa,pb,time,gas);
        }
        if(ub>=uH){
            captured=true;exitPhi=phi+h*bhCrossing(state,nextState,h,uH);return;
        }
        if(ub<=0. && nextState.y<0.){
            captured=false;exitPhi=phi+h*bhCrossing(state,nextState,h,0.);return;
        }
        state=nextState;phi+=h;
    }
    // Only relevant extremely close to the critical orbit. Its fitted disk cavity is empty.
    exitPhi=phi;
}

vec3 bhPlainBackground(vec3 ray,vec3 source,float time){
    vec3 sky=distantSky(source);
    if(ray.y>0.){
        sky+=skyStars(source,time);
        sky+=skyMeteors(ray,time);
    }
    return sky;
}

vec3 blackHoleSky(vec3 ray,float time){
    vec3 centre=normalize(bhAxis);float axial=dot(centre,ray);
    if(ray.y<=0. || axial<=inversesqrt(1.+bhShadowScale*bhShadowScale*bhExtent*bhExtent))
        return bhPlainBackground(ray,ray,time);
    vec3 east=normalize(cross(centre,vec3(0.,1.,0.))),north=cross(east,centre);
    vec2 uv=vec2(dot(ray,east),dot(ray,north))/(axial*bhShadowScale);
    vec2 p=vec2(dot(uv,vec2(.9703,.2419)),dot(uv,vec2(-.2419,.9703)));
    // Normalise the roll basis precisely to keep the critical impact radius equal to 1.
    p*=inversesqrt(.9703*.9703+.2419*.2419);
    float impact=length(p);bool captured=false;
    vec4 gas=vec4(0.,0.,0.,1.);vec3 source=ray;
    float escapeAngle;bhTrace(p,time,gas,escapeAngle,captured);
    if(!captured){
        float theta=atan(length(uv)*bhShadowScale);
        // Distant-disk patch is embedded in the Heaven sky. Only outside the
        // disk support fade the far-field sky deflection, never the disk geometry.
        float weight=(1.-smoothstep(4.92,bhExtent,impact))*smoothstep(0.,.10,ray.y);
        float beta=theta-(escapeAngle-bhPi)*weight;
        vec3 side=normalize(uv.x*east+uv.y*north);
        source=normalize(centre*cos(beta)+side*sin(beta));
    }
    vec3 background=captured?vec3(0.):bhPlainBackground(ray,source,time);
    return gas.rgb+gas.a*background;
}

// Rays in one pixel can straddle the capture boundary or resolve successive
// winding images. Integrate four subpixel ray directions there instead of
// outlining the mathematical shadow. All samples see the same 3-D gas.
vec3 bhSkyPixel(vec3 ray,float time){
    vec3 dx=dFdx(ray),dy=dFdy(ray);
    float c=dot(ray,normalize(bhAxis));
    bool critical=abs(c-inversesqrt(1.+bhShadowScale*bhShadowScale))<.0018+2.*fwidth(c);
    int count=critical?4:1;
    vec3 colour=vec3(0.);
    for(int i=0;i<4;i++){
        if(i>=count)break;
        vec2 off=i==0?vec2(-.375,-.125):i==1?vec2(.125,-.375):
                 i==2?vec2(.375,.125):vec2(-.125,.375);
        colour+=blackHoleSky(count==1?ray:normalize(ray+off.x*dx+off.y*dy),time);
    }
    return colour/float(count);
}

vec3 otherSky(vec3 d) { return bhSkyPixel(d, heavenTime); }
