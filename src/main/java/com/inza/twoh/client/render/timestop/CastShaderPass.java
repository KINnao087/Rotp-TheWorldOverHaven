package com.inza.twoh.client.render.timestop;

import com.github.standobyte.jojo.client.ClientTimeStopHandler;
import com.github.standobyte.jojo.client.render.world.shader.TimeStopShader;
import com.inza.twoh.AddonMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderInstance;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;

import java.io.IOException;

public final class CastShaderPass extends TimeStopShader {
    // EN: Eight chunks in blocks; this is a visual radius, not ROTP's freeze range.
    // 中文：视觉球半径为八个区块，换算成方块传给着色器，不改变 ROTP 的冻结范围。
    private static final float CASTER_RADIUS_BLOCKS = 8 * 16.0F;
    private static final float OBSERVER_RADIUS_BLOCKS = 10 * 16.0F;
    // EN: Sideflow's four shutter samples in Minecraft ticks (14, 34, 60 and 95 ms).
    // 中文：将 sideflow 的四个曝光采样时刻换算为游戏 tick。
    private static final float[] SHUTTER_TICKS = { .28F, .68F, 1.20F, 1.90F };
    private static final ResourceLocation FLOW = new ResourceLocation(AddonMain.MOD_ID, "textures/effect/cast_flow.png");
    private final IResourceManager resources;
    private final float duration;
    private Matrix4f ortho;
    private Shader casterPass;
    private Shader observerPass;
    private Shader observerPulse;
    private Framebuffer observerScene;
    private boolean failed;
    private boolean customFrame;

    public CastShaderPass(IResourceManager resources, String name, Framebuffer input,
            Framebuffer output, float duration) throws IOException {
        super(resources, name, input, output, duration);
        this.resources = resources;
        this.duration = duration;
        CastScene.nativeLength = duration;
    }

    @Override
    public void setOrthoMatrix(Matrix4f matrix) {
        super.setOrthoMatrix(matrix);
        ortho = matrix;
        if (casterPass != null) casterPass.setOrthoMatrix(matrix);
        if (observerPass != null) observerPass.setOrthoMatrix(matrix);
        if (observerPulse != null) observerPulse.setOrthoMatrix(matrix);
    }

    @Override
    public void process(float time) {
        if (CastScene.ready && CastScene.eligible()) {
            // EN: This is ROTP's fractional shader clock, including pauses; no wall-clock timer.
            // 中文：使用 ROTP 自身的 tick 与帧内小数，暂停和拖慢时不会另跑一只时钟。
            float ticks = ClientTimeStopHandler.getInstance().getTimeStopTicks() + MathHelper.frac(time * 20.0F);
            if (ticks > 0 && ticks < duration && renderActivation(time, ticks)) {
                if (!customFrame) {
                    AddonMain.LOGGER.debug("TWOH activation pass started at {} / {} ticks ({})",
                            ticks, duration, CastScene.casterView ? "caster" : "observer");
                }
                customFrame = true;
                return;
            }
        }
        // EN: All other stands, the sustained grade, and the fade at resume use the original pass.
        // 中文：其他替身、持续灰屏和解除时停的淡出均执行原版着色通道。
        super.process(time);
        if (customFrame) {
            customFrame = false;
            AddonMain.LOGGER.debug("Returned to native ROTP shader at tick {}",
                    ClientTimeStopHandler.getInstance().getTimeStopTicks());
        }
    }

    private boolean renderActivation(float time, float ticks) {
        if (failed || ortho == null) return false;
        boolean caster = CastScene.casterView;
        if (!caster) {
            // EN: RGB holds the existing lens; alpha carries its depth-tested coverage into the shared pulse.
            // 中文：RGB 保存原有内环，alpha 将经过深度判断的球体覆盖范围传给共用脉冲。
            if (observerScene == null) {
                observerScene = new Framebuffer(inTarget.width, inTarget.height, false, Minecraft.ON_OSX);
            } else if (observerScene.width != inTarget.width || observerScene.height != inTarget.height) {
                observerScene.resize(inTarget.width, inTarget.height, Minecraft.ON_OSX);
            }
        }
        Shader activation = caster ? casterPass : observerPass;
        if (activation == null) {
            try {
                String program = caster ? ":twoh_caster" : ":twoh_cast";
                activation = new Shader(resources, AddonMain.MOD_ID + program, inTarget,
                        caster ? outTarget : observerScene);
                activation.setOrthoMatrix(ortho);
                ShaderInstance effect = activation.getEffect();
                effect.setSampler("WorldColor", () -> CastScene.snapshot.getColorTextureId());
                effect.setSampler("WorldDepth", () -> CastScene.snapshot.getDepthTextureId());
                if (caster) {
                    casterPass = activation;
                } else {
                    TextureManager textures = Minecraft.getInstance().getTextureManager();
                    if (textures.getTexture(FLOW) == null) textures.register(FLOW, new SimpleTexture(FLOW));
                    effect.setSampler("LensFlow", () -> textures.getTexture(FLOW).getId());
                    observerPass = activation;
                    observerPulse = new Shader(resources, AddonMain.MOD_ID + ":twoh_caster", observerScene, outTarget);
                    observerPulse.setOrthoMatrix(ortho);
                    observerPulse.getEffect().setSampler("WorldColor", () -> CastScene.snapshot.getColorTextureId());
                    observerPulse.getEffect().setSampler("WorldDepth", () -> CastScene.snapshot.getDepthTextureId());
                }
            } catch (IOException ex) {
                failed = true;
                AddonMain.LOGGER.error("Cannot load TWOH activation shader; retaining ROTP visuals", ex);
                return false;
            }
        }
        ShaderInstance effect = activation.getEffect();
        float progress = caster ? CastTiming.lensProgress(ticks, duration)
                : CastTiming.observerProgress(ticks, duration);
        float visualRadius = caster ? CASTER_RADIUS_BLOCKS : OBSERVER_RADIUS_BLOCKS;
        setSceneUniforms(effect, progress, visualRadius);
        if (!caster) {
            effect.safeGetUniform("InitialViewDistance").set(CastScene.initialViewDistance);
            effect.safeGetUniform("EnergySeconds").set(ticks / 20.0F);
            CastFilmGrade.apply(effect, progress, CastScene.appearance.tint(progress));
        } else {
            effect.safeGetUniform("CoveredMode").set(CastScene.appearance.colorCovered ? 1.0F : 0.0F);
            effect.safeGetUniform("ShutterRadii").set(shutterRadius(ticks, 0), shutterRadius(ticks, 1),
                    shutterRadius(ticks, 2), shutterRadius(ticks, 3));
        }
        activation.process(time);
        if (!caster) {
            ShaderInstance pulse = observerPulse.getEffect();
            setSceneUniforms(pulse, progress, visualRadius);
            pulse.safeGetUniform("ObserverPulse").set(1.0F);
            pulse.safeGetUniform("ShockwaveTravel").set(CastTiming.waveTravel(ticks, duration));
            observerPulse.process(time);
        }
        return true;
    }

    private static void setSceneUniforms(ShaderInstance effect, float progress, float visualRadius) {
        int tint = CastScene.appearance.tint(progress);
        if (effect.getUniform("BallTint") != null) {
            effect.safeGetUniform("BallTint").set(((tint >> 16) & 255) / 255.0F,
                    ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F);
        }
        effect.safeGetUniform("InvViewProjection").set(CastScene.inverse);
        effect.safeGetUniform("ViewProjection").set(CastScene.viewProjection);
        effect.safeGetUniform("Center").set((float) CastScene.center.x, (float) CastScene.center.y,
                (float) CastScene.center.z);
        effect.safeGetUniform("Progress").set(progress);
        effect.safeGetUniform("MaxRadius").set(visualRadius);
    }

    private float shutterRadius(float ticks, int sample) {
        return CASTER_RADIUS_BLOCKS * CastTiming.radiusFraction(ticks - SHUTTER_TICKS[sample], duration);
    }

    @Override
    public void close() {
        if (casterPass != null) casterPass.close();
        if (observerPass != null) observerPass.close();
        if (observerPulse != null) observerPulse.close();
        if (observerScene != null) observerScene.destroyBuffers();
        super.close();
    }
}
