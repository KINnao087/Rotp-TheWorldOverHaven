#version 120

uniform sampler2D DiffuseSampler;
uniform sampler2D WorldColor;
uniform sampler2D WorldDepth;
uniform mat4 InvViewProjection;
uniform mat4 ViewProjection;
uniform vec2 InSize;
uniform vec3 Center;
uniform float Progress;
uniform float CoveredMode;
uniform vec3 BallTint;
uniform vec4 ShutterRadii;
uniform float MaxRadius;
uniform float ObserverPulse;
uniform float ShockwaveTravel;
varying vec2 texCoord;
varying vec3 lensCenter;

vec3 unproject(vec2 uv, float depth) {
    vec4 p = InvViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

struct SceneTap {
    vec3 rgb;
    float viewDepth;
    bool dyed;
};

SceneTap readScene(vec2 uv, float radius, float clockSec) {
    float depth = texture2D(WorldDepth, uv).r;
    vec3 src = texture2D(WorldColor, uv).rgb;
    bool sky = depth >= 1.0;
    vec3 surface = unproject(uv, min(depth, .999999));
    float z = max(.001, (ViewProjection * vec4(surface, 1.0)).w);
    // EN: Refract the observer's already-rendered lens, never a second copy of the caster's negative.
    // 中文：对观测者已有的内环图像做折射，不再重复套用释放者的底片变色。
    if (ObserverPulse > 0.5) {
        vec4 lens = texture2D(DiffuseSampler, uv);
        return SceneTap(lens.rgb, z, lens.a > 0.75);
    }
    SceneTap tap = SceneTap(src, z, false);

    // EN: Colour the outside seen through the exit, not a wall between the eye and the shell.
    // 中文：只有视线穿过球壳出口后看到的外界才变色；挡在出口前的墙保留原色。
    vec3 ray = normalize(surface);
    float along = dot(ray, Center);
    vec3 closest = Center - along * ray;
    float chordSquared = radius * radius - dot(closest, closest);
    if (chordSquared <= 0.0) return tap;
    float exitDistance = along + sqrt(chordSquared);
    // EN: One mask per cast: real surfaces inside, or exterior pixels behind the exit.
    // 中文：每次施放固定一种遮罩：球内实际表面，或透过出口看到的外界像素。
    bool covered = !sky && distance(surface, Center) <= radius;
    bool dyed = CoveredMode > .5 ? covered : (sky || length(surface) > exitDistance);
    if (exitDistance <= 0.0 || !dyed) return tap;

    // EN: A bounded negative with no clipping plateau keeps unlit block faces distinct.
    // 中文：底片曲线在黑端和白端都保留斜率，不把夜景中不同的方块面压成同色。
    vec3 light = sqrt(src);
    vec3 dark = sqrt(1.0 - src);
    vec3 negative = mix(dark / (light + dark), 1.0 - src, .35);
    vec3 swept = negative * BallTint;
    return SceneTap(swept, z, true);
}

// EN: Keep the radial warp, but never smear dyed exterior pixels onto an interior surface.
// 中文：保留径向畸变，但不允许染色后的外界像素被拖到球内建筑上。
vec3 guarded(vec2 sampleUV, float radius, float clockSec, SceneTap base) {
    vec2 margin = .5 / InSize;
    vec2 uv = clamp(sampleUV, margin, 1.0 - margin);
    SceneTap tap = readScene(uv, radius, clockSec);
    if (tap.dyed != base.dyed) return base.rgb;
    float error = abs(log(tap.viewDepth / base.viewDepth));
    return mix(tap.rgb, base.rgb, smoothstep(.20, .82, error));
}

// EN: Radii are measured in viewport heights. Only the emitter is projected from the world.
// 中文：仅将世界中的球心投影到屏幕；波半径以屏幕高度为单位，不受球面和场景深度裁剪。
vec3 compositeShockwaves(vec3 scene, vec2 uv) {
    float travel = ShockwaveTravel;
    if (travel <= 0.0 || travel >= 1.0) return scene;

    vec4 clip = ViewProjection * vec4(Center, 1.0);
    if (clip.w <= 0.0001) return scene;

    vec2 origin = 0.5 + 0.5 * clip.xy / clip.w;
    vec2 delta = (uv - origin) * InSize / InSize.y;
    float distanceFromOrigin = length(delta);
    float angle = atan(delta.y, delta.x + 0.000001);
    vec2 farCorner = max(abs(origin), abs(vec2(1.0) - origin)) * InSize / InSize.y;
    const float stagger = 0.085;
    // EN: Even the trailing edge of the fourth front clears the farthest screen corner.
    // 中文：最晚出发的第四道波连同渐隐尾部也能越过最远的屏幕角。
    float reach = 1.20 * length(farCorner) / (1.0 - 3.0 * stagger);
    float opacity = 0.0;
    for (int i = 0; i < 4; ++i) {
        float index = float(i);
        float age = travel - stagger * index;
        if (age <= 0.0) continue;

        float phase = 1.93 * index;
        float folds = 0.045 * sin(3.0 * angle + phase)
                    + 0.024 * sin(5.0 * angle - phase + 0.7)
                    + 0.013 * cos(9.0 * angle + phase);
        float front = reach * age * (1.0 + folds);
        float inset = front - distanceFromOrigin;
        float pixel = 1.0 / InSize.y;
        float lip = max(pixel, 0.012 * front);
        float tail = max(pixel, 0.09 * front);
        float band = smoothstep(-lip, 0.0, inset) * (1.0 - smoothstep(0.0, tail, inset));
        opacity = max(opacity, band * smoothstep(0.0, 0.045, age));
    }
    return mix(scene, vec3(0.99, 0.97, 0.94), 0.92 * opacity);
}

float hash3(vec3 p) {
    p = fract(p * vec3(.1031, .11369, .13787));
    p += dot(p, p.yxz + 19.19);
    return fract((p.x + p.y) * p.z);
}

float smoothNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f *= f * (3.0 - 2.0 * f);
    vec2 k = vec2(0.0, 1.0);
    return mix(mix(mix(hash3(i), hash3(i+k.yxx), f.x),
                   mix(hash3(i+k.xyx), hash3(i+k.yyx), f.x), f.y),
               mix(mix(hash3(i+k.xxy), hash3(i+k.yxy), f.x),
                   mix(hash3(i+k.xyy), hash3(i+k.yyy), f.x), f.y), f.z);
}

float ribbonDensity(vec3 path, vec3 tangent, float s, float clock) {
    // EN: Each filament runs along the intersection, with only a small transverse curl.
    // 中文：焰丝沿球面和墙面的交线前行，只允许轻微横向摆幅，不把亮点拉成径向尖刺。
    vec3 q = path * 2.2 - tangent * clock * 3.1;
    float eddy = smoothNoise(q * .82);
    float breaks = smoothstep(.18, .81, smoothNoise(q * 2.05 + vec3(3.2,1.4,5.1)));
    float bend = 1.15 * (eddy - .5);
    float mainBand = exp(-2.0 * pow((s - bend) / .88, 2.0));
    float threadA = exp(-2.0 * pow((s - bend - .60 - .20 * sin(dot(q,vec3(.8,1.2,.7)))) / .29, 2.0));
    float threadB = exp(-2.0 * pow((s - bend + .78 - .35 * eddy) / .34, 2.0));
    float folded = smoothNoise(q*1.45 + vec3(1.7,-2.2,.3));
    float tailAxis = bend - .35 - 1.75*folded;
    float taper = smoothstep(.23,.73,eddy + .31*breaks);
    float tail = exp(-1.65 * pow((s - tailAxis) / (.22+.31*taper), 2.0));
    return mainBand * (.29 + .59 * eddy) + breaks * (.78 * threadA + .60 * threadB)
           + .94 * taper * tail;
}

// EN: World-depth unprojection already returns camera-relative world axes in the mod.
 // 中文：游戏的逆视投影矩阵直接还原相对摄像机的世界坐标，无需 Demo 的视空间轴变换。
vec3 flameFront(vec2 uv, vec3 base, float radius, float clockSec, SceneTap tap, vec3 rel, vec2 grad, vec3 face) {
    float depth = texture2D(WorldDepth, uv).r;
    float dist = length(rel);
    face /= max(length(face), .000001);
    float shellWidth = clamp(.17 + .038 * radius, .19, 1.15);
    float s = (dist - radius) / shellWidth;
    vec4 past = ShutterRadii;
    float lo = min(radius,min(min(past.x,past.y), min(past.z,past.w)));
    float hi = max(radius,max(max(past.x,past.y), max(past.z,past.w)));
    if (dist < lo-4.0*shellWidth || dist > hi+4.0*shellWidth) return base;

    // EN: The circular intersection gives a continuous tangential coordinate on each face.
    // 中文：用球与可见表面的交线确定焰丝走向；不是贴在屏幕上的水平条纹。
    vec3 foot = face * dot(rel,face);
    vec3 inPlane = rel - foot;
    vec3 radial = inPlane / max(length(inPlane), .00001);
    float arcRadius = sqrt(max(0.0, radius*radius - dot(foot,foot)));
    vec3 onArc = foot + radial * arcRadius;
    vec3 along = cross(face,radial);
    vec3 path = onArc;
    vec3 tangent = along;
    // Project onto a gently changing world-space pattern. The broad scale preserves long wisps.
    path /= max(1.0, radius*.23);
    float orientation = radius >= past.x ? 1.0 : -1.0;
    float core = ribbonDensity(path, tangent, s * orientation, clockSec);
    vec4 offsets = (vec4(dist)-past) / shellWidth;
    float streak = 0.0;
    for (int i=0; i<4; ++i) {
        float n = float(i)+1.0;
        float older = ribbonDensity(path - tangent*n*.13, tangent, offsets[i]*orientation, clockSec-n*.015);
        streak += older * (1.0 - .135*n);
    }
    streak /= 2.65;
    // EN: A soft exposure envelope fills between taps, especially over a fast-moving floor.
    // 中文：柔和的曝光包络填平高速扫过地面时离散采样间的空隙。
    float back = (radius - past.w) / shellWidth;
    float wakeCoord = clamp(-s * sign(back) / max(abs(back),.01), 0.0, 1.0);
    float radialGap = abs(s + wakeCoord * back);
    float wake = exp(-.58 * radialGap*radialGap - 1.85 * wakeCoord)
                 * smoothstep(.05,1.0,abs(back));
    float amplitude = smoothstep(.015,.26,radius);
    vec2 axis = grad / max(length(grad),.0001);
    vec2 alongPx = vec2(-axis.y,axis.x);
    float px = clamp(abs(radius-past.w)/max(length(grad),.0001),0.0,22.0);
    vec2 shutter = (axis*sign(back)*px + alongPx*4.5) / InSize;
    vec3 smear = .56*guarded(uv+shutter*.31,radius,clockSec,tap)
               + .44*guarded(uv+shutter*.74,radius,clockSec,tap);
    vec3 lit = mix(base,smear,clamp(amplitude*wake*.46,0.0,.46));
    float soft = amplitude * (.20*exp(-.36*s*s) + .49*streak + .28*wake);
    lit += (1.0-lit) * vec3(.93,.80,1.0) * (1.0-exp(-1.7*soft));
    float white = 1.0-exp(-amplitude*(1.98*core + 1.06*streak));
    return mix(lit, vec3(1.0,.985,1.0), white);
}



void main() {
    vec2 uv = texCoord;
    vec4 inputPixel = texture2D(DiffuseSampler, uv);
    vec3 inputColor = inputPixel.rgb;
    vec3 original = texture2D(WorldColor, uv).rgb;
    // EN: The post input can already contain a hand or another late overlay; do not refract it.
    // 中文：保留在世界快照之后才绘制的手部和其他覆盖层。
    bool skipPulse = ObserverPulse > 0.5
            ? inputPixel.a <= 0.75
            : any(greaterThan(abs(inputColor-original), vec3(1.5/255.)));
    vec3 rel = unproject(uv, min(texture2D(WorldDepth,uv).r,.999999)) - Center;
    float surfaceRadius = length(rel);
    vec2 grad = vec2(dFdx(surfaceRadius), dFdy(surfaceRadius));
    vec3 face = cross(dFdx(rel), dFdy(rel));
    if (skipPulse) {
        // EN: Outside the lens, apply only the screen wave. Late overlays retain their own mask.
        // 中文：透镜外只合成屏幕白波；手部等后绘制内容有独立标记，不受影响。
        vec3 color = inputColor;
        if (ObserverPulse > 0.5 && inputPixel.a > 0.375) {
            color = compositeShockwaves(color, uv);
        }
        gl_FragColor = vec4(color, 1.0);
        return;
    }

    const float apex = 26.0 / 53.0;
    float radius = MaxRadius * min(Progress / apex, (1.0 - Progress) / (1.0 - apex));
    float f = 1.5 * Progress;
    SceneTap base = readScene(uv, radius, f);
    float attack = smoothstep(.005, .08, f) * (1.0 - smoothstep(.14, .36, f));
    float release = smoothstep(.94, 1.19, f) * (1.0 - smoothstep(1.32, 1.50, f));
    float body = smoothstep(.04, .25, f) * (1.0 - smoothstep(1.07, 1.48, f));
    vec2 focus = vec2(.5, .43 + .27 * smoothstep(.48, 1.15, f));
    if (ObserverPulse > .5) {
        focus = lensCenter.xy;
    }
    vec2 d = uv - focus;
    float r = length(d * vec2(InSize.x / InSize.y, 1.0));
    float depthGain = mix(.27, 1.0, smoothstep(.6, 6.0, base.viewDepth));
    float wave = sin(14.0 * r - 9.0 * f) * exp(-2.0 * r * r);
    float shift = (.40 * attack - .26 * release + .11 * body * wave) * depthGain;
    if (ObserverPulse > .5) shift *= lensCenter.z;
    vec2 warp = uv - d * shift;
    vec2 spread = d * (.22 * attack + .16 * release) * depthGain;
    if (ObserverPulse > .5) spread *= lensCenter.z;
    vec3 rgb = guarded(warp, radius, f, base) * .40;
    rgb += guarded(warp - spread * .25, radius, f, base) * .24;
    rgb += guarded(warp - spread * .52, radius, f, base) * .18;
    rgb += guarded(warp - spread * .76, radius, f, base) * .12;
    rgb += guarded(warp - spread, radius, f, base) * .06;
    if (!base.dyed) {
        gl_FragColor = vec4(ObserverPulse > .5 ? rgb : flameFront(uv, rgb, radius, f, base, rel, grad, face), 1.0);
        return;
    }

    // EN: Keep the foreground legible while the two optical impulses pass across the entire view.
    // 中文：两次径向脉冲经过整个视野，近景仍能辨认，颜色来自当帧场景而非视频贴图。
    float y = dot(rgb, vec3(.2126,.7152,.0722));
    vec3 after = (1.0 - rgb) * mix(vec3(1.0), BallTint, .36);
    float edge = 1.0 - smoothstep(.25, .94, r);
    vec3 graded = mix(rgb, after, clamp(body * (.07 + .23 * edge), 0.0, .5));
    graded += (attack + release) * .11 * y;
    if (ObserverPulse > 0.5) {
        graded = compositeShockwaves(graded, uv);
    } else {
        graded = flameFront(uv, graded, radius, f, base, rel, grad, face);
    }
    gl_FragColor = vec4(graded, 1.0);
}
