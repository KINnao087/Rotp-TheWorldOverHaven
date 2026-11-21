package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;
import com.inza.twoh.init.InitDimension;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL20;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class OverHeavenPortalShader {
    private static final String VERTEX_SHADER =
            "#version 120\n" +
            "varying vec3 ViewDirection;\n" +
            "varying vec3 LocalPosition, BoxLow, BoxHigh;\n" +
            "varying float ExposedEdges;\n" +
            "void main() {\n" +
            "    ViewDirection = (gl_ModelViewMatrix * gl_Vertex).xyz;\n" +
            "    LocalPosition = gl_Vertex.xyz;\n" +
            "    ExposedEdges = gl_MultiTexCoord0.w;\n" +
            "    BoxLow = gl_MultiTexCoord0.xyz; BoxHigh = gl_MultiTexCoord1.xyz;\n" +
            "    gl_Position = ftransform();\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 120\n" +
            "uniform vec3 CameraForward, CameraRight, CameraUp;\n" +
            "uniform float FlowTime, MistRadius;\n" +
            "uniform vec3 MistOrigin;\n" +
            "uniform int MistPass;\n" +
            "// Median sRGB of the supplied lavender swatch, shared by the portal and the mist.\n" +
            "const vec3 Swatch = vec3(126.0,103.0,219.0)/255.0;\n" +
            "varying vec3 ViewDirection;\n" +
            "varying vec3 LocalPosition, BoxLow, BoxHigh;\n" +
            "\n" +
            "varying float ExposedEdges;\n" +
            "const float SmokeTransparency=.60, MistDensity=.17, MistWindSpeed=2.;\n" +
            "float h3(vec3 p) {\n" +
            "    p = fract(p*vec3(.1031,.11369,.13787));\n" +
            "    p += dot(p,p.yzx+19.19);\n" +
            "    return fract((p.x+p.y)*p.z);\n" +
            "}\n" +
            "float n3(vec3 p) {\n" +
            "    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);\n" +
            "    vec4 a=vec4(h3(i),h3(i+vec3(0,1,0)),h3(i+vec3(0,0,1)),h3(i+vec3(0,1,1)));\n" +
            "    i.x+=1.0;\n" +
            "    vec4 b=vec4(h3(i),h3(i+vec3(0,1,0)),h3(i+vec3(0,0,1)),h3(i+vec3(0,1,1)));\n" +
            "    vec4 c=mix(a,b,f.x);\n" +
            "    return mix(mix(c.x,c.y,f.y),mix(c.z,c.w,f.y),f.z);\n" +
            "}\n" +
            "vec3 octave(vec3 p) { return vec3(.8*p.x-.6*p.z,.6*p.x+.8*p.z,p.y)*2.04+vec3(3.3,9.1,4.6); }\n" +
            "float fb(vec3 p) {\n" +
            "    float d=.55*n3(p); p=octave(p); d+=.27*n3(p);\n" +
            "    p=octave(p); d+=.13*n3(p); return d+.05*n3(octave(p));\n" +
            "}\n" +
            "vec3 energy(vec3 ray) {\n" +
            "    vec3 p=ray*3.65 + vec3(3.2,4.5,1.3), drift=vec3(.14,-.21,.105)*FlowTime;\n" +
            "    vec3 a=p*.83+drift*.42;\n" +
            "    vec3 turn=vec3(n3(a),n3(a+13.4),n3(a-4.9))-.5;\n" +
            "    vec3 b=p*1.12+2.75*turn+drift;\n" +
            "    vec3 roll=vec3(n3(b+5.8),n3(b-11.3),n3(b))-.5;\n" +
            "    vec3 plume=p+3.4*turn+1.75*roll+drift;\n" +
            "    float density=fb(plume*1.35), back=fb(plume*.81+ray*.65+17.0);\n" +
            "    float body=smoothstep(.30,.76,density*.78+back*.22);\n" +
            "    float light=smoothstep(-.10,.18,density-fb(plume*1.35+vec3(-.19,.27,.15)));\n" +
            "    float warm=smoothstep(.29,.74,n3(a*.88+turn+7.1));\n" +
            "    // A slow cloud bank: the gaps between the main eddies are smoke, not a flat swatch.\n" +
            "    float t=FlowTime;\n" +
            "    vec3 farP=ray*2.5+vec3(-2.8,6.1,4.7)+vec3(-.032,.049,.023)*t;\n" +
            "    farP+=1.55*(vec3(n3(farP+3.),n3(farP-5.),n3(farP))-.5);\n" +
            "    float farD=fb(farP*1.48);\n" +
            "    // A sideways-drifting veil with its own deformation and a soft, broken edge.\n" +
            "    vec3 veilP=ray.yzx*4.9+vec3(4.3,-2.7,8.2)+vec3(.065,-.036,.041)*t;\n" +
            "    veilP+=2.35*(vec3(n3(veilP*.63+7.),n3(veilP*.63),n3(veilP*.63-3.))-.5);\n" +
            "    float veilD=fb(veilP*1.18+turn*.72);\n" +
            "    // A finer lilac wisp, sheared differently so it never duplicates the front curl.\n" +
            "    vec3 silkP=ray.zxy*vec3(7.2,4.3,5.7)+vec3(-3.,2.7,14.)+vec3(-.094,.075,.028)*t;\n" +
            "    float silk=fb(silkP+2.7*roll+1.6*vec3(veilD,farD,veilD));\n" +
            "    float shade=.56*smoothstep(.23,.75,farD)+.31*smoothstep(.28,.71,veilD)+.13*smoothstep(.38,.70,silk);\n" +
            "    vec3 under=mix(Swatch,vec3(172.,150.,250.)/255.,shade);\n" +
            "    float dye=clamp(.14+.32*warm,0.,1.);\n" +
            "    vec3 plum=mix(vec3(.35,.13,.43),vec3(.57,.13,.37),dye);\n" +
            "    vec3 rim=mix(vec3(.74,.47,.76),vec3(.90,.48,.68),dye);\n" +
            "    // Use a wider, gently warped volume for the plume envelope. Keep the\n" +
            "    // original curl inside it, but do not light a narrow density contour.\n" +
            "    vec3 cloud=(p+2.15*turn+.58*roll+drift)*1.13;\n" +
            "    vec3 axis=vec3(.29,-.19,.21);\n" +
            "    float mass=.50*fb(cloud)+.25*fb(cloud-axis)+.25*fb(cloud+axis);\n" +
            "    float cover=smoothstep(.18,.82,mix(mass,density,.18));\n" +
            "    float scatter=clamp(.15+.66*cover+.18*(back-.5)+.10*(light-.5),0.,1.);\n" +
            "    vec3 front=mix(plum,rim,scatter);\n" +
            "    float curl=mix(cover,cover*cover,.21);\n" +
            "    return mix(under,front,(curl*.91)*(1.-SmokeTransparency));\n" +
            "}\n" +
            "// Only soften physical ledges, never an internal chunk/proxy boundary.\n" +
            "float ledgeDistance(vec3 p) {\n" +
            "    vec4 mask=mod(floor(floor(ExposedEdges+.5)/vec4(1.,2.,4.,8.)),2.);\n" +
            "    vec4 dist=vec4(p.x-BoxLow.x,BoxHigh.x-p.x,p.z-BoxLow.z,BoxHigh.z-p.z);\n" +
            "    vec4 edges=mix(vec4(100.),dist,mask);\n" +
            "    return min(min(edges.x,edges.y),min(edges.z,edges.w));\n" +
            "}\n" +
            "vec4 groundMist() {\n" +
            "    vec3 dir=normalize(LocalPosition);\n" +
            "    vec3 inv=1./mix(vec3(.000001),dir,step(vec3(.000001),abs(dir)));\n" +
            "    vec3 a=BoxLow*inv,b=BoxHigh*inv,lo=min(a,b),hi=max(a,b);\n" +
            "    float first=max(0.,max(lo.x,max(lo.y,lo.z))),last=min(hi.x,min(hi.y,hi.z));\n" +
            "    float span=last-first;\n" +
            "    if(span<=.0001) discard;\n" +
            "    vec2 accum=vec2(0.);\n" +
            "    const int mistSteps=6;\n" +
            "    float stepSize=span/float(mistSteps);\n" +
            "    for(int i=0;i<mistSteps;++i){\n" +
            "        vec3 p=dir*(first+(float(i)+.5)*stepSize);\n" +
            "        float z=clamp((p.y-BoxLow.y)/(BoxHigh.y-BoxLow.y),0.,1.);\n" +
            "        vec2 pos=p.xz+MistOrigin.xz-vec2(.9578,.2874)*(FlowTime*MistWindSpeed);\n" +
            "        // Continuous mirrored domain across 8192-block origin rebases.\n" +
            "        pos=2048.-abs(mod(pos+2048.,8192.)-4096.);\n" +
            "        // Two overlapping broad fields; no density threshold or sharply cut cloud tops.\n" +
            "        vec2 g=vec2(.063*pos.x+.019*pos.y,-.036*pos.x+.121*pos.y);\n" +
            "        float bank=.70*n3(vec3(g,2.3+.12*z))+.30*n3(vec3(g.yx*1.51+vec2(8.7,-4.2),6.1));\n" +
            "        float prof=(1.-smoothstep(0.,1.,z))*mix(.88,1.12,bank);\n" +
            "        float mass=mix(.57,1.06,bank)*prof*smoothstep(0.,1.25,ledgeDistance(p));\n" +
            "        mass*=1.-smoothstep(.66*MistRadius,MistRadius,length(p.xz));\n" +
            "        accum+=mass*vec2(1.,.25+.55*bank+.12*z);\n" +
            "    }\n" +
            "    accum*=stepSize*MistDensity*1.65;\n" +
            "    vec3 tint=mix(Swatch,vec3(165.,146.,248.)/255.,accum.y/max(accum.x,.0001));\n" +
            "    return vec4(tint,1.-exp(-accum.x));\n" +
            "}\n" +
            "void main() {\n" +
            "    if (MistPass != 0) { gl_FragColor = groundMist(); return; }\n" +
            "    vec3 v = normalize(ViewDirection);\n" +
            "    vec3 ray = normalize(CameraRight*v.x + CameraUp*v.y - CameraForward*v.z);\n" +
            "    gl_FragColor = vec4(energy(ray),1.0);\n" +
            "}\n";

    private static float forwardX = 0.0F;
    private static float forwardY = 0.0F;
    private static float forwardZ = -1.0F;
    private static float rightX = 1.0F;
    private static float rightY = 0.0F;
    private static float rightZ = 0.0F;
    private static float upX = 0.0F;
    private static float upY = 1.0F;
    private static float upZ = 0.0F;

    private static int program;
    private static boolean failed;
    private static int uForward, uRight, uUp, uTime, uPass, uOrigin, uRadius;
    private static int previousFloorProgram;
    private static long startTick = Long.MIN_VALUE;
    // Merge adjacent height-map columns so an ordinary flat chunk costs one fog box.
    private static final java.util.List<int[]> banks = new java.util.ArrayList<>();
    private static java.lang.ref.WeakReference<net.minecraft.client.world.ClientWorld> bankWorld = new java.lang.ref.WeakReference<>(null);
    private static int lastCX, lastCZ, lastRange;
    private static long nextScan;
    private static void refreshBanks(net.minecraft.client.world.ClientWorld world, int cx, int cz, int range) {
        long now = world.getGameTime();
        if (world == bankWorld.get() && cx == lastCX && cz == lastCZ && range == lastRange && now < nextScan && now >= nextScan-15) return;
        bankWorld = new java.lang.ref.WeakReference<>(world); lastCX=cx; lastCZ=cz; lastRange=range; nextScan=now+15;
        banks.clear();
        int[] tops = new int[256];
        for (int zc=cz-range; zc<=cz+range; ++zc) for (int xc=cx-range; xc<=cx+range; ++xc) {
            net.minecraft.world.chunk.Chunk chunk=world.getChunkSource().getChunkNow(xc,zc);
            if (chunk==null) continue;
            for (int k=0;k<256;++k) tops[k]=1+chunk.getHeight(net.minecraft.world.gen.Heightmap.Type.WORLD_SURFACE,k&15,k>>4);
            for (int z=0;z<16;++z) for (int x=0;x<16;++x) {
                int top=tops[z*16+x]; if (top<=0) continue;
                int x1=x+1, z1=z+1;
                while (x1<16 && tops[z*16+x1]==top) ++x1;
                outer: while(z1<16) {
                    for(int i=x;i<x1;++i) if(tops[z1*16+i]!=top) break outer;
                    ++z1;
                }
                int wx=xc*16+x, wz=zc*16+z, wx1=xc*16+x1, wz1=zc*16+z1;
                banks.add(new int[]{wx,top,wz,wx1,wz1,exposedEdges(world,wx,wz,wx1,wz1,top)});
                for(int j=z;j<z1;++j) java.util.Arrays.fill(tops,j*16+x,j*16+x1,0);
            }
        }
    }

    // A rectangle side is exposed only if every neighbouring column is lower.
    private static boolean lowerSurface(net.minecraft.client.world.ClientWorld world, int x, int z, int y) {
        net.minecraft.world.chunk.Chunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk != null && chunk.getHeight(net.minecraft.world.gen.Heightmap.Type.WORLD_SURFACE, x & 15, z & 15) + 1 < y;
    }
    private static int exposedEdges(net.minecraft.client.world.ClientWorld world, int x, int z, int X, int Z, int y) {
        int mask=15;
        for (int j=z;j<Z && (mask&3)!=0;j++) {
            if (!lowerSurface(world,x-1,j,y)) mask &= ~1;
            if (!lowerSurface(world,X,j,y)) mask &= ~2;
        }
        for (int i=x;i<X && (mask&12)!=0;i++) {
            if (!lowerSurface(world,i,z-1,y)) mask &= ~4;
            if (!lowerSurface(world,i,Z,y)) mask &= ~8;
        }
        return mask;
    }
    private static final float MIST_HEIGHT = 1.0F;

    private static final int[][] BOX_FACES = {{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
    private static void fogBox(float x, float y, float z, float X, float Z, int exposed) {
        float Y=y+MIST_HEIGHT;
        y+=.002F; // Keep the exit face just clear of the solid surface.
        org.lwjgl.opengl.GL13.glMultiTexCoord4f(org.lwjgl.opengl.GL13.GL_TEXTURE0,x,y,z,exposed);
        org.lwjgl.opengl.GL13.glMultiTexCoord3f(org.lwjgl.opengl.GL13.GL_TEXTURE1,X,Y,Z);
        for (int[] face:BOX_FACES) for(int v:face)
            GL11.glVertex3f((v&1)==0?x:X,(v&2)==0?y:Y,(v&4)==0?z:Z);
    }


    private OverHeavenPortalShader() {
    }

    public static void setCamera(ActiveRenderInfo camera) {
        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();

        forwardX = forward.x();
        forwardY = forward.y();
        forwardZ = forward.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();

        rightX = forwardY * upZ - forwardZ * upY;
        rightY = forwardZ * upX - forwardX * upZ;
        rightZ = forwardX * upY - forwardY * upX;
        normalizeRightVector();
    }

    public static void setup(ResourceLocation texture) {
        previousFloorProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        ensureProgram();
        if (program == 0) {
            // Keep the old material visible on drivers without compatibility shaders.
            Minecraft.getInstance().getTextureManager().bind(texture);
            return;
        }
        GL20.glUseProgram(program);
        GL20.glUniform1i(uPass, 0);
        GL20.glUniform1f(uTime, seconds(Minecraft.getInstance().getFrameTime()));
        GL20.glUniform3f(uForward, forwardX, forwardY, forwardZ);
        GL20.glUniform3f(uRight, rightX, rightY, rightZ);
        GL20.glUniform3f(uUp, upX, upY, upZ);
    }

    public static void clear() {
        GL20.glUseProgram(previousFloorProgram);
    }

    private static float seconds(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        long tick = mc.level.getGameTime();
        if (startTick == Long.MIN_VALUE || tick < startTick) startTick = tick;
        return (tick - startTick + partialTick) * .05F;
    }

    @SubscribeEvent
    public static void renderGroundMist(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !InitDimension.OVER_HEAVEN.equals(mc.level.dimension())) return;

        Vector3d eye = mc.gameRenderer.getMainCamera().getPosition();
        float radius = Math.min(64F, mc.options.renderDistance * 16F);
        refreshBanks(mc.level, ((int)Math.floor(eye.x)) >> 4, ((int)Math.floor(eye.z)) >> 4, (int)Math.ceil(radius / 16F));
        if (banks.isEmpty()) return;
        ensureProgram();
        if (program == 0) return;

        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        // Isolate raw compatibility-GL state; do not invalidate Minecraft's state cache.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT | GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        RenderSystem.pushMatrix();
        try {
            RenderSystem.multMatrix(event.getMatrixStack().last().pose());
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_FRONT);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL20.glUseProgram(program);
            GL20.glUniform1i(uPass, 1);
            GL20.glUniform1f(uTime, seconds(event.getPartialTicks()));
            GL20.glUniform3f(uOrigin, wrapPosition(eye.x), 0, wrapPosition(eye.z));
            GL20.glUniform1f(uRadius, radius);
            // Draw exit faces only; apply Beer-Lambert absorption once per intersected bank.
            GL11.glBegin(GL11.GL_QUADS);
            for (int[] b : banks) {
                if (Math.abs(b[1] - eye.y) > radius+1) continue;
                fogBox((float)(b[0]-eye.x), (float)(b[1]-eye.y), (float)(b[2]-eye.z),
                        (float)(b[3]-eye.x), (float)(b[4]-eye.z), b[5]);
            }
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(previous);
            RenderSystem.popMatrix();
            GL11.glPopAttrib();
        }
    }

    private static float wrapPosition(double coordinate) {
        return (float) (coordinate - 8192.0 * Math.floor(coordinate / 8192.0));
    }

    private static void normalizeRightVector() {
        float length = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (length <= 0.0001F) {
            rightX = 1.0F;
            rightY = 0.0F;
            rightZ = 0.0F;
            return;
        }

        rightX /= length;
        rightY /= length;
        rightZ /= length;
    }

    private static void ensureProgram() {
        if (program != 0 || failed) {
            return;
        }

        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GL20.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GL20.glDeleteShader(fragmentShader);
            failed = true;
            return;
        }

        int linkedProgram = GL20.glCreateProgram();
        GL20.glAttachShader(linkedProgram, vertexShader);
        GL20.glAttachShader(linkedProgram, fragmentShader);
        GL20.glLinkProgram(linkedProgram);

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        if (GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            AddonMain.LOGGER.error("Failed to link Over Heaven floor portal shader: {}",
                    GL20.glGetProgramInfoLog(linkedProgram));
            GL20.glDeleteProgram(linkedProgram);
            failed = true;
            return;
        }

        program = linkedProgram;
        uForward = GL20.glGetUniformLocation(program, "CameraForward");
        uRight = GL20.glGetUniformLocation(program, "CameraRight");
        uUp = GL20.glGetUniformLocation(program, "CameraUp");
        uTime = GL20.glGetUniformLocation(program, "FlowTime");
        uPass = GL20.glGetUniformLocation(program, "MistPass");
        uOrigin = GL20.glGetUniformLocation(program, "MistOrigin");
        uRadius = GL20.glGetUniformLocation(program, "MistRadius");
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            AddonMain.LOGGER.error("Failed to compile Over Heaven floor portal shader: {}",
                    GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}

