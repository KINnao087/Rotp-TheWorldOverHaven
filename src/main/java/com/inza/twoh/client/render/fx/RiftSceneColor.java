package com.inza.twoh.client.render.fx;


import com.qouteall.immersive_portals.render.context_management.PortalRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** EN: Capture this side before the recursive portal pass so shards cannot contain their own rift.
 * 中文：在递归渲染门户前复制此岸颜色，避免碎片上出现自己的裂隙。 */
public final class RiftSceneColor {
    private static final SceneColor color = new SceneColor();
    private static World owner, requested;
    private static long requestedTick;

    public static void request(World world) {
        requested = world;
        requestedTick = world.getGameTime();
    }
    private RiftSceneColor() {}

    public static void capture() {
        Minecraft mc = Minecraft.getInstance();
        if (PortalRendering.isRendering() || mc.level == null || mc.player == null) {
            return;
        }
        if (mc.level != requested || mc.level.getGameTime() - requestedTick > 1) {
            release(mc.level);
            return;
        }
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            color.bindCopy();
            owner = mc.level;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
            GL13.glActiveTexture(active);
        }
    }
    public static boolean bind(World world) {
        return owner == world && color.bindExisting();
    }
    public static void release(World world) {
        if (requested == world) {
            requested = null;
        }
        if (owner == world) {
            owner = null;
            color.close();
        }
    }
}
