#version 120

uniform sampler2D DiffuseSampler;
uniform sampler2D WorldColor;
uniform sampler2D WorldDepth;
uniform sampler2D LensFlow;
uniform mat4 InvViewProjection;
uniform mat4 ViewProjection;
uniform vec2 InSize;
uniform vec3 Center;
uniform float Progress;
uniform float MaxRadius;
varying vec2 texCoord;
varying vec3 lensCenter;

vec3 unproject(vec2 uv, float depth) {
    vec4 p = InvViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

uniform vec4 FilmGrade;
uniform float FilmTime;
uniform float InitialViewDistance;

// EN: Preserve the authored apparent-radius curve for a distant witness.
// 中文：以释放瞬间的距离校正投影，让远处观测者看到设定的半径变化曲线。
float worldFraction(float fraction) {
    if (InitialViewDistance <= MaxRadius) return fraction;
    float ratio = MaxRadius / InitialViewDistance;
    return fraction / sqrt((1.0 - ratio) * (1.0 + ratio) + ratio * ratio * fraction * fraction);
}

float surfaceDistance(vec2 uv) {
    float depth = texture2D(WorldDepth, uv).r;
    return depth >= 1.0 ? 1.e8 : length(unproject(uv, depth));
}

// EN: Measure the visible volume, not just a crossing of the far shell.
// 中文：计算视线在球内的可见长度，而不是要求它必须穿过球壳出口。
float thickness(vec2 uv, float radius) {
    vec3 ray = normalize(unproject(uv, .5));
    float along = dot(Center, ray);
    vec3 nearest = Center - along * ray;
    float chord2 = radius * radius - dot(nearest, nearest);
    if (chord2 <= 0.0) return 0.0;
    float halfChord = sqrt(chord2);
    return max(0.0, min(along + halfChord, surfaceDistance(uv)) - max(0.0, along - halfChord));
}

vec2 rawField(vec2 p, float index) {
    index = clamp(index, 0.0, 40.0);
    vec2 tile = vec2(mod(index, 8.0), floor(index / 8.0));
    vec2 cell = clamp((p * .5 + .5) * 80.0, vec2(.5), vec2(79.5));
    vec4 enc = texture2D(LensFlow, (tile * 80.0 + cell) / vec2(640.0,480.0));
    return (vec2(dot(enc.rg,vec2(256.0,1.0)),dot(enc.ba,vec2(256.0,1.0))) / 257.0 - .5) * 3.0;
}

// EN: The fitted displacement tiles can carry a small translation at their origin.
// Pin that one reference point without translating the outer ring.
// 中文：拟合位移图在原点可能有少量平移；只校正中心附近，不移动外圈。
vec2 fieldAt(vec2 p, float index) {
    float core = 1.0 - smoothstep(0.0, .18, length(p));
    return rawField(p, index) - core * rawField(vec2(0.0), index);
}

vec2 tangent(vec2 a, vec2 b) {
    return step(vec2(.00000001),a*b)*sign(a)*2.0*abs(a*b)/max(abs(a+b),vec2(.000001));
}

vec2 motion(vec2 p, float frame) {
    float k = floor(frame), t = fract(frame);
    vec2 a = fieldAt(p,k-1.0), b = fieldAt(p,k), c = fieldAt(p,k+1.0), d = fieldAt(p,k+2.0);
    vec2 left = tangent(b-a,c-b), right = tangent(c-b,d-c);
    return mix(b,c,t*t*(3.0-2.0*t)) + t*(1.0-t)*((1.0-t)*left-t*right);
}

// EN: Observer material preset approved in the standalone preview.
// 中文：独立预览中确认的旁观者球壳材质参数。
uniform float EnergySeconds;
const float energySpeed = 63.2;
const float energyDensity = 4.0;
const float surfaceOpacity = .28;
const vec3 surfaceGrays = vec3(0.0, 242.0, 120.0) / 255.0;
const float baseGray = 161.0 / 255.0;
const vec3 rimColor = vec3(1.0);
const float rimWidth = 4.0;
const float rimGain = .95;
const float returnBendScale = 4.8;
const float curl = 1.0;
const float softness = .10;
const float density = 1.30;
const float shine = .30;
#define shadowTint surfaceGrays.x
#define bodyTint surfaceGrays.y
#define crestTint surfaceGrays.z

// EN: Smooth noise carries the folds; no frame-random jitter or scrolling picture.
// 中文：用连续噪声推动褶皱，不使用逐帧随机数或平移图片。
float hash(vec2 p) {
    vec3 q = fract(p.xyx * vec3(.1031, .1030, .0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

// EN: Borrow the developed return lens in material space, not in scene UVs.
// 中文：在能量材质坐标内复用球体收缩时已成形的位移场，不二次扭曲场景。
float film(vec2 uv, float t, float backing, float focusWeight) {
    const float returnTile = 34.0;
    float distanceToFocus = length(uv);
    float reach = focusWeight;
    vec2 samplePoint = uv;
    if (reach > 0.0) {
        // EN: Map the infinite material plane smoothly into the tile interior.
        // The sphere mask, not the edge of the displacement tile, ends the effect.
        // 中文：把无限材质平面连续映入位移图内部。只由球体遮罩裁切，不在固定半径切换纹理。
        vec2 tilePoint = .96 * uv * inversesqrt(dot(uv, uv) + returnBendScale * returnBendScale);
        samplePoint += reach * returnBendScale * fieldAt(tilePoint, returnTile);
    }
    float r = length(samplePoint);
    vec2 dir = samplePoint / max(r, .001);

    // EN: The return field shapes the spokes. Measure phase on the undistorted
    // radius so each wavefront travels outward even where that field folds.
    // 中文：收缩位移场只塑造条纹方向；用未畸变半径推进波相，避免褶皱处的波峰倒行。
    float travel = 1.7 * log(1.0 + distanceToFocus) - .24 * t;
    vec2 drift = vec2(.16 * t, -.12 * t);
    float eddy = noise2(3.2 * uv + drift) - .5;
    eddy += .39 * (noise2(8.7 * uv - drift) - .5);
    vec2 ambient = .68 * uv + vec2(.19 * eddy, .06 * sin(t*.4 + uv.x));
    vec2 flow = mix(ambient, .82 * dir, reach);
    float ambientR = .58 + .34 * uv.y;
    vec2 adv = mix(vec2(1.8 * ambientR - .35 * t, .22 * t),
                   vec2(travel, .31 * travel), reach);
    r = mix(ambientR, travel, reach);
    float grain = noise2(4.4 * flow + adv);
    vec2 flow2 = rot(.23 * curl * (grain - .5) + .10 * sin(r * 8. - (1.0 - reach) * t)) * flow;
    // EN: Scale the advected coordinates, not the clock or lens radius.
    // 中文：缩放随波移动的纹理坐标；不调整能量时钟或透镜半径。1× 保留原纹理。
    vec2 broadUV = 3.1 * density * flow2 + .26 * adv;
    vec2 veinUV = 6.8 * density * flow2 + vec2(1.05 * r - (1.0 - reach) * .22 * t, -1.3);
    float broad = noise2(energyDensity * broadUV + vec2(2.8, 6.1));
    float vein = noise2(energyDensity * veinUV);
    vec2 plumeUV = mix(uv * vec2(13.0, 5.2) - drift,
                       5.2 * dir + vec2(2.1, .7) * travel, reach);
    float plume = noise2(plumeUV + 2.8 * flow2);
    float field = .66 * broad + .25 * vein + .09 * plume;
    float edgeWidth = .135 * softness;
    float ink = smoothstep(.495 - edgeWidth, .495 + edgeWidth, field);
    float edgeScale = sqrt(softness);
    float ridge = exp(-pow(abs(field - .435) / (.044 * edgeScale), 2.0));
    float filament = exp(-pow(abs(field - .485) / (.015 * edgeScale), 2.0));
    float pulse = .65 + .35 * noise2(3.5 * flow + vec2(3.3 * r - (1.0 - reach) * t, .4));
    float core = mix(1.0, smoothstep(.023, .20, distanceToFocus), reach);
    float dye = mix(bodyTint, shadowTint, ink);
    float color = mix(backing, dye, core * (.13 + .82 * ink) * pulse);
    color = mix(color, crestTint, .50 * shine * ridge * core * pulse);
    color += crestTint * .23 * shine * filament * core * pulse;
    color += bodyTint * .17 * (plume - .5) * ink * core;
    return clamp(color, 0.0, 1.0);
}

// EN: Backtrace into the ring so scenery appears to surge outwards, not collapse.
// The leading edge is steep; a broader wake releases the stretch behind it.
// 中文：反向采样球心一侧的场景，让像素向外冲。窄波头压紧轮廓，宽波尾逐渐释放拉伸。
vec2 openingImpulse(vec2 fromCenter, float shellPixels, float progress) {
    float radiusPx = max(shellPixels, 1.0);
    float radius = length(fromCenter) / radiusPx;
    float launch = 1.0 - exp(-8.0 * progress);
    float front = mix(.20, .93, launch);
    float lead = radius - front;
    float width = mix(.29, .065, smoothstep(-.07, .035, lead));
    float head = exp(-lead * lead / (width * width));
    float wakeDistance = (lead + .28) / .20;
    float wake = exp(-wakeDistance * wakeDistance);
    float envelope = smoothstep(0.0, .085, progress)
                   * (1.0 - smoothstep(.30, .57, progress));
    return -fromCenter * ((.62 * head + .14 * wake) * envelope);
}

void main() {
    vec2 uv = texCoord;
    vec3 inputColor = texture2D(DiffuseSampler, uv).rgb;
    vec3 original = texture2D(WorldColor, uv).rgb;
    const float apex = 26.0 / 53.0;
    const float fadeStart = 15.0 / 53.0;
    bool closing = Progress >= apex;
    float phase = closing ? (Progress - apex) / (1.0 - apex) : Progress / apex;
    float shell = closing ? 1.0 - phase : phase;
    float radius = max(.0001, MaxRadius * worldFraction(shell));
    float fade = clamp((Progress - fadeStart) / (apex - fadeStart), 0.0, 1.0);
    float openBend = .05 + .475 * (1.0 + cos(3.14159265 * fade));
    float u = clamp(phase / .48, 0.0, 1.0);
    float handoff = closing ? u*u*u*(10.0 + u*(6.0*u - 15.0)) : 0.0;
    float bend = closing ? mix(.05, 1.0, handoff) : openBend;
    float ring = Progress <= fadeStart ? shell : (fadeStart / apex) * (.22 + .78 * openBend);
    if (closing) ring *= shell;

    vec3 ray = normalize(unproject(uv, .5));
    float along = dot(Center, ray);
    float offset = length(Center - along * ray) - radius;
    float gradient = max(length(vec2(dFdx(offset), dFdy(offset))), .00001);
    float edgePx = offset / gradient;
    float radiusPx = radius / gradient;
    // EN: The post input can already contain a hand or another late overlay; do not refract it.
    // 中文：保留在世界快照之后才绘制的手部和其他覆盖层。
    if (any(greaterThan(abs(inputColor-original), vec3(1.5/255.)))) {
        // EN: Intermediate alpha marks coverage for the observer pulse; keep the overlay untouched.
        // 中文：中间缓冲的 alpha 标记脉冲覆盖范围，手部等覆盖层不参与后处理。
        gl_FragColor = vec4(inputColor, 0.25);
        return;
    }

    if (shell <= 0.0) {
        gl_FragColor = vec4(original, 0.5);
        return;
    }
    float path = thickness(uv, radius);
    bool visibleLimb = along > 0.0 && length(Center) > radius
            && (path > 0.0 || (edgePx >= 0.0 && along <= surfaceDistance(uv)));
    float widthPx = rimWidth * clamp(radiusPx * .010, 1.1, 3.6);
    float dist = abs(edgePx) / widthPx;
    float visibility = visibleLimb && dist < 5.0 ? smoothstep(1.0, 8.0, radiusPx) : 0.0;
    float core = visibility * exp(-dist * dist);
    float halo = visibility * exp(-.22 * dist * dist);
    vec3 emission = clamp(rimGain * rimColor * (core * vec3(.94, .94, .91)
                        + halo * vec3(.32, .16, .35)), 0.0, 1.0);
    if (path <= 0.0) {
        gl_FragColor = vec4(original + (1.0 - original) * emission, 0.5);
        return;
    }

    vec4 clip = ViewProjection * vec4(Center, 1.0);
    float focal = .5 * InSize.y * length(vec3(ViewProjection[0][1], ViewProjection[1][1], ViewProjection[2][1]));
    float z = max(clip.w, radius * 1.12);
    vec2 origin = lensCenter.xy * InSize;
    float scale = focal * radius / sqrt(z * z - radius * radius);


    // EN: From inside, use the maximum radius as a fixed ruler, not the growing radius itself.
    // 中文：球内用峰值半径作固定标尺，避免分子分母都随半径放大，导致内环停在原地。
    ring = max(.0001, MaxRadius * worldFraction(ring));
    float ringZ = max(clip.w, ring * 1.12);
    float nearScale = 1.9 * focal * ring / MaxRadius;
    float projectedRing = focal * ring / sqrt(ringZ * ringZ - ring * ring);
    float outerView = smoothstep(0.0, 1.0, max(0.0, clip.w) / MaxRadius);
    float openingScale = mix(nearScale, projectedRing, outerView);
    // EN: Each lens keeps its own sampling radius. Blend offsets in pixels, not their UV domains.
    // 中文：展开和收回各用自己的采样半径，只混合像素位移，不把小环突然放大到整球。
    vec2 fromCenter = uv * InSize - origin;
    vec2 p = fromCenter / openingScale;


    // EN: Pull the optical footprint inward; restore undistorted scene texels outside it.
    // 中文：向中心收拢折射区域，外围恢复未扭曲的场景采样，不叠加环形贴图。
    const float openingLens = 9.0;
    float separation = clamp(1.0 - ring / radius, 0.0, 1.0);
    float taper = 1.0 - smoothstep(.83, 1.08, length(p));
    vec2 displacement = fieldAt(p, openingLens)
                      * mix(1.0, taper, smoothstep(0.0, .15, separation));
    vec2 offsetPx = displacement * openingScale;
    if (!closing) {
        // EN: Only the opening refracts through the impulse. Keep the original
        // mask, optical centre, energy material and return path untouched.
        // 中文：只在展开时加入瞬态折射，不改球体遮罩、球心、能量材质和收缩路径。
        float innerShell = 1.9 * focal * radius / MaxRadius;
        float impactScale = min(mix(innerShell, scale, outerView), length(InSize));
        offsetPx += openingImpulse(fromCenter, impactScale, phase);
    }
    if (handoff > 0.0) {
        vec2 returning = motion(fromCenter / scale, clamp(FilmTime * 24.0, 0.0, 40.0));
        offsetPx = mix(offsetPx, returning * scale, handoff);
    }
    // EN: At zero bend, sample the exact original texel.
    // 中文：零折射直接采原像素，不影响变色和球边发光。
    vec2 margin = .5 / InSize;
    vec2 sampleUV = clamp(uv + lensCenter.z * bend * offsetPx / InSize,
                          margin, 1.0 - margin);
    vec3 tap = unproject(sampleUV, min(texture2D(WorldDepth, sampleUV).r, .999999));
    if (thickness(sampleUV, radius) <= 0.0 && (ViewProjection * vec4(tap, 1.0)).w < clip.w) {
        vec2 safe = uv, blocked = sampleUV;
        for (int i = 0; i < 6; ++i) {
            vec2 mid = .5 * (safe + blocked);
            if (thickness(mid, radius) > 0.0) safe = mid; else blocked = mid;
        }
        sampleUV = safe;
    }
    vec3 source = texture2D(WorldColor, sampleUV).rgb;
    float negative = 1.0 - dot(source, vec3(.2126, .7152, .0722));
    // EN: Retain both ends of the scene's tonal range. Apply film exposure without clipping the print.
    // 中文：底片保留夜景暗部与高光的层次；曝光改变色调，但不会截断明暗细节。
    vec3 light = sqrt(source);
    vec3 dark = sqrt(1.0 - source);
    vec3 channels = mix(dark / (light + dark), 1.0 - source, .35);
    channels /= channels + (1.0 - channels) * exp2(-4.0 * FilmGrade.a);
    float tone = dot(channels, vec3(.2126, .7152, .0722));
    vec3 printColor = mix(vec3(tone), channels, .70) * mix(FilmGrade.rgb, vec3(1.0), .60 * tone);
    vec3 corrected = mix(vec3(negative), printColor, smoothstep(.03, .19, FilmTime));
    float unit = 2.26 / min(InSize.x, InSize.y);
    float clock = 2.8 + EnergySeconds * energySpeed;
    float gray = film(fromCenter * unit, clock, baseGray, 1.0);
    if (lensCenter.z < 1.0) {
        vec2 ambientUV = (uv - .5) * InSize * unit;
        gray = mix(film(ambientUV, clock, baseGray, 0.0), gray, lensCenter.z);
    }
    vec3 graded = mix(corrected, vec3(gray), surfaceOpacity);
    gl_FragColor = vec4(graded + (1.0 - graded) * emission, 1.0);
}
