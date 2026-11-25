package com.inza.twoh.client.render.ascension;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps the already transformed real model and all its model layers. Never draws a collision box or calls a model twice. */
final class PassengerBuffers {
    private static final RenderType GROUND_SHADOW=RenderType.entityShadow(new ResourceLocation("textures/misc/shadow.png"));
    private final Map<RenderType,BufferBuilder> buffers=new LinkedHashMap<>();
    private boolean populated;

    IRenderTypeBuffer wrap(IRenderTypeBuffer original) {
        // Shadows belong on terrain. Leave them in the normal pass so they disappear with the ground.
        return type -> type==GROUND_SHADOW ? original.getBuffer(type) : append(type);
    }

    private IVertexBuilder append(RenderType type) {
        BufferBuilder b=buffers.computeIfAbsent(type,k->new BufferBuilder(Math.min(65536,k.bufferSize())));
        if(!b.building()) b.begin(type.mode(),type.format());
        populated=true;
        return b;
    }

    void flush() {
        if(!populated) return;
        populated=false;
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        try {
            buffers.forEach((type,b)->{if(b.building()) type.end(b,0,0,0);});
        } finally {
            Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        }
    }
}
