package com.inza.twoh.client.render.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

/** EN: Camera-relative inputs for one world render, before the hand and HUD. 中文：手部和 HUD 绘制前，本次世界绘制共用的帧数据。 */
public final class EffectFrame {
    public final ClientWorld world;
    public final Vector3d eye;
    public final Vector3d forward;
    public final Vector3d right;
    public final Vector3d up;
    public final float partialTick;
    public final int width;
    public final int height;
    private final Matrix4f view;
    private final Matrix4f projection;

    EffectFrame(ClientWorld world, ActiveRenderInfo camera, float partialTick, Matrix4f view, Matrix4f projection) {
        this.world = world;
        this.eye = camera.getPosition();
        this.partialTick = partialTick;
        this.view = view.copy();
        this.projection = projection.copy();
        this.width = Minecraft.getInstance().getWindow().getWidth();
        this.height = Minecraft.getInstance().getWindow().getHeight();
        Vector3f f = camera.getLookVector();
        Vector3f u = camera.getUpVector();
        this.forward = new Vector3d(f.x(), f.y(), f.z());
        this.up = new Vector3d(u.x(), u.y(), u.z());
        float x = f.y() * u.z() - f.z() * u.y();
        float y = f.z() * u.x() - f.x() * u.z();
        float z = f.x() * u.y() - f.y() * u.x();
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        this.right = length <= 0.0001F ? new Vector3d(1, 0, 0) : new Vector3d(x / length, y / length, z / length);
    }

    public float seconds() {
        return EffectRenderer.seconds(world.getGameTime(), partialTick);
    }

    public Matrix4f viewMatrix() {
        return view.copy();
    }

    public Matrix4f projectionMatrix() {
        return projection.copy();
    }

    void applyView() {
        RenderSystem.multMatrix(view);
    }
}
