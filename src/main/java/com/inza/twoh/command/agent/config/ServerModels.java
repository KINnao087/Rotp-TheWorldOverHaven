package com.inza.twoh.command.agent.config;

import com.inza.twoh.AddonMain;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * EN: Tie model configuration to the server lifecycle, including single-player integrated servers.
 * 中文：配置随服务端实例初始化和释放，覆盖单人游戏内置服务端。
 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID)
public final class ServerModels {
    private static final Map<MinecraftServer, ModelRuntime> INSTANCES = new IdentityHashMap<>();

    private ServerModels() {
    }

    @SubscribeEvent
    public static synchronized void start(FMLServerStartingEvent event) {
        try {
            ModelRuntime runtime = new ModelRuntime(FMLPaths.CONFIGDIR.get().resolve(ConfigText.DIRECTORY));
            INSTANCES.put(event.getServer(), runtime);
        } catch (IOException | RuntimeException failure) {
            AddonMain.LOGGER.error(ConfigText.STORAGE_ERROR);
        }
    }

    @SubscribeEvent
    public static synchronized void stop(FMLServerStoppedEvent event) {
        // EN: Saves are write-through. There are no committed dirty drafts to flush on shutdown.
        // 中文：保存即写盘，退出时不存在需要补写的已确认草稿。
        INSTANCES.remove(event.getServer());
    }

    public static synchronized ModelRuntime get(MinecraftServer server) {
        return INSTANCES.get(server);
    }
}
