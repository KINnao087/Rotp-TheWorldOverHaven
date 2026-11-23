// Modified for TWOH from Immersive Portals Forge, copyright 2020 qouteall, Apache-2.0.
package com.inza.twoh.portalcore.immersive_portals;

import com.inza.twoh.portalcore.hiding_in_the_bushes.MyNetworkClient;
import com.inza.twoh.portalcore.hiding_in_the_bushes.O_O;
import com.inza.twoh.portalcore.immersive_portals.miscellaneous.GcMonitor;
import com.inza.twoh.portalcore.immersive_portals.my_util.MyTaskList;
import com.inza.twoh.portalcore.immersive_portals.optifine_compatibility.OFBuiltChunkStorageFix;
import com.inza.twoh.portalcore.immersive_portals.optifine_compatibility.OFGlobal;
import com.inza.twoh.portalcore.immersive_portals.optifine_compatibility.OFInterfaceInitializer;
import com.inza.twoh.portalcore.immersive_portals.render.CrossPortalEntityRenderer;
import com.inza.twoh.portalcore.immersive_portals.render.PortalRenderInfo;
import com.inza.twoh.portalcore.immersive_portals.render.PortalRenderer;
import com.inza.twoh.portalcore.immersive_portals.render.RendererUsingFrameBuffer;
import com.inza.twoh.portalcore.immersive_portals.render.RendererUsingStencil;
import com.inza.twoh.portalcore.immersive_portals.render.ShaderManager;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.CloudContext;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.PortalRendering;
import com.inza.twoh.portalcore.immersive_portals.render.lag_spike_fix.GlBufferCache;
import com.inza.twoh.portalcore.immersive_portals.teleportation.ClientTeleportationManager;
import com.inza.twoh.portalcore.immersive_portals.teleportation.CollisionHelper;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TranslationTextComponent;

public class ModMainClient {
    
    public static void switchToCorrectRenderer() {
        if (PortalRendering.isRendering()) {
            //do not switch when rendering
            return;
        }
        if (OFInterface.isShaders.getAsBoolean()) {
            switch (Global.renderMode) {
                case normal:
                    switchRenderer(OFGlobal.rendererMixed);
                    break;
                case compatibility:
                    switchRenderer(OFGlobal.rendererDeferred);
                    break;
                case debug:
                    switchRenderer(OFGlobal.rendererDebugWithShader);
                    break;
                case none:
                    switchRenderer(CGlobal.rendererDummy);
                    break;
            }
        }
        else {
            switch (Global.renderMode) {
                case normal:
                    switchRenderer(CGlobal.rendererUsingStencil);
                    break;
                case compatibility:
                    switchRenderer(CGlobal.rendererUsingFrameBuffer);
                    break;
                case debug:
                    switchRenderer(CGlobal.rendererDebug);
                    break;
                case none:
                    switchRenderer(CGlobal.rendererDummy);
                    break;
            }
        }
    }
    
    private static void switchRenderer(PortalRenderer renderer) {
        if (CGlobal.renderer != renderer) {
            Helper.log("switched to renderer " + renderer.getClass());
            CGlobal.renderer = renderer;
        }
    }
    
    private static void showOptiFineWarning() {
        ModMain.clientTaskList.addTask(MyTaskList.withDelayCondition(
            () -> Minecraft.getInstance().level == null,
            MyTaskList.oneShotTask(() -> {
                Minecraft.getInstance().gui.handleChat(
                    ChatType.CHAT,
                    new TranslationTextComponent("imm_ptl.optifine_warning"),
                    UUID.randomUUID()
                );
            })
        ));
    }
    
    public static void init() {
        MyNetworkClient.init();
        
        ClientWorldLoader.init();
        
        Minecraft.getInstance().execute(() -> {
            CGlobal.rendererUsingStencil = new RendererUsingStencil();
            CGlobal.rendererUsingFrameBuffer = new RendererUsingFrameBuffer();
            
            CGlobal.renderer = CGlobal.rendererUsingStencil;
            CGlobal.clientTeleportationManager = new ClientTeleportationManager();
            
            if (CGlobal.shaderManager == null) {
                CGlobal.shaderManager = new ShaderManager();
            }
        });
        
        O_O.loadConfigFabric();
        
        DubiousThings.init();
        
        CrossPortalEntityRenderer.init();
        
        GlBufferCache.init();
        
        CollisionHelper.initClient();
        
        PortalRenderInfo.init();
        
        CloudContext.init();
        
        OFInterface.isOptifinePresent = O_O.detectOptiFine();
        if (OFInterface.isOptifinePresent) {
            OFInterfaceInitializer.init();
            OFBuiltChunkStorageFix.init();
            showOptiFineWarning();
        }
        
        GcMonitor.initClient();
        
        Helper.log(OFInterface.isOptifinePresent ? "Optifine is present" : "Optifine is not present");
    }
    
}
