package com.inza.twoh.command;

import com.inza.twoh.AddonMain;
import com.inza.twoh.command.agent.AgentRunner;
import com.inza.twoh.command.agent.AgentStrings;
import com.inza.twoh.command.agent.AgentStrings.Command;
import com.inza.twoh.command.agent.AgentStrings.Text;
import com.inza.twoh.command.agent.ChatApi;
import com.inza.twoh.command.agent.chat.ChatMemory;
import com.inza.twoh.command.agent.chat.ChatNetwork;
import com.inza.twoh.command.agent.chat.ChatText;
import com.inza.twoh.command.agent.chat.TokenBudget;
import com.inza.twoh.command.agent.config.ConfigText;
import com.inza.twoh.command.agent.config.ModelRuntime;
import com.inza.twoh.command.agent.config.ServerModels;
import com.inza.twoh.command.agent.config.ConfigNetwork;
import com.inza.twoh.command.agent.CommandExecutor;
import com.inza.twoh.command.agent.GetPlayerContextTool;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Optional;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * EN: Accept an operator's request without collecting any subject's state in advance.
 * 中文：接收有权限玩家的请求，不预先采集任何查询对象的状态。
 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MiniAgentCommand {
    private static final Map<MinecraftServer, AgentRunner> RUNNERS = new IdentityHashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, AgentStrings.WORKER_NAME);
        thread.setDaemon(true);
        return thread;
    });

    private MiniAgentCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Command.ROOT)
                .then(Commands.literal(Command.AGENT)
                        .requires(source -> source.hasPermission(CommandExecutor.PERMISSION_LEVEL))
                        .executes(context -> openChat(context.getSource().getPlayerOrException()))
                        .then(Commands.literal(ChatText.CHAT)
                                .executes(context -> openChat(context.getSource().getPlayerOrException())))
                        .then(Commands.literal(ConfigText.COMMAND)
                                .requires(source -> source.hasPermission(ConfigText.ADMIN_LEVEL))
                                .executes(context -> ConfigNetwork.open(context.getSource().getPlayerOrException())))
                        .then(Commands.argument(Command.REQUEST_ARGUMENT, StringArgumentType.greedyString())
                                .executes(MiniAgentCommand::request))));
    }

    private static int request(CommandContext<CommandSource> context)
            throws CommandSyntaxException {
        return request(context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, Command.REQUEST_ARGUMENT), false);
    }

    private static AgentRunner runner(ServerPlayerEntity player) {
        return RUNNERS.computeIfAbsent(player.getServer(), server -> new AgentRunner(WORKERS));
    }

    private static ChatMemory memory(ServerPlayerEntity player) {
        ChatMemory memory = runner(player).memory(player.getUUID());
        ModelRuntime runtime = ServerModels.get(player.getServer());
        ChatApi client = runtime == null ? null : runtime.client();
        memory.bindModel(client == null ? 0 : client.contextTokens());
        CompoundNBT preferences = player.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG)
                .getCompound(ChatText.SETTINGS);
        if (!preferences.isEmpty()) {
            // EN: Keep the summary preference, but never reinterpret a legacy turn count as tokens.
            // 中文：保留旧摘要开关，不把旧版的轮数误读成 token。
            int tokens = preferences.contains(ChatText.WINDOW) ? preferences.getInt(ChatText.WINDOW) : TokenBudget.DEFAULT_WINDOW;
            memory.configure(preferences.getBoolean(ChatText.SUMMARY), tokens);
        }
        return memory;
    }

    private static int openChat(ServerPlayerEntity player) {
        ChatMemory chat = memory(player);
        chat.watch(true);
        ChatNetwork.show(player, chat.view(), true);
        return 1;
    }

    public static void closeChat(ServerPlayerEntity player) {
        runner(player).memory(player.getUUID()).watch(false);
    }

    public static void configureChat(ServerPlayerEntity player, boolean summary, int window) {
        ChatMemory memory = memory(player);
        if (window <= memory.view().maxWindow && memory.configure(summary, window)) {
            // EN: Persist preferences across respawns, not the player's private transcript.
            // 中文：偏好设置跨重生保留，私人对话正文不写入存档。
            CompoundNBT preferences = new CompoundNBT();
            preferences.putBoolean(ChatText.SUMMARY, summary);
            preferences.putInt(ChatText.WINDOW, window);
            CompoundNBT persisted = player.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
            persisted.put(ChatText.SETTINGS, preferences);
            player.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        }
        ChatNetwork.show(player, memory.view(), false);
    }

    public static int request(ServerPlayerEntity player, String text, boolean fromScreen) {
        if (!player.createCommandSourceStack().hasPermission(CommandExecutor.PERMISSION_LEVEL)) {
            return 0;
        }
        ChatMemory memory = memory(player);
        PlayerSession game = new PlayerSession(player.getServer(), player, memory, fromScreen);
        if (text.trim().isEmpty() || text.length() > ChatText.MAX_REQUEST
                || text.chars().anyMatch(Character::isISOControl)) {
            game.tell(Text.INVALID_LENGTH);
            return 0;
        }
        ModelRuntime runtime = ServerModels.get(player.getServer());
        ChatApi client = runtime == null ? null : runtime.client();
        if (client == null) {
            game.tell(runtime == null ? ConfigText.STORAGE_ERROR : Text.CONFIG_REQUIRED);
            return 0;
        }
        return runner(player).start(player.getUUID(), player.getGameProfile().getName(), text, game,
                client, game::refresh) ? 1 : 0;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        AgentRunner runner = RUNNERS.get(event.getPlayer().getServer());
        if (runner != null) {
            runner.forget(event.getPlayer().getUUID());
        }
    }

    @SubscribeEvent
    public static void stop(FMLServerStoppedEvent event) {
        RUNNERS.remove(event.getServer());
    }

    /**
     * EN: Keep issuer identity separate from the player named in a tool call.
     * 中文：发起者决定权限及命令来源，工具参数里的玩家名仅决定查询对象。
     */
    private static final class PlayerSession implements AgentRunner.Game {
        private final MinecraftServer server;
        private final ServerPlayerEntity issuer;
        private final ServerWorld initialWorld;
        private final ChatMemory memory;
        private final boolean fromScreen;

        PlayerSession(MinecraftServer server, ServerPlayerEntity issuer, ChatMemory memory, boolean fromScreen) {
            this.server = server;
            this.issuer = issuer;
            this.initialWorld = issuer.getLevel();
            this.memory = memory;
            this.fromScreen = fromScreen;
        }

        @Override
        public void onServerThread(Runnable task) {
            server.execute(task);
        }

        @Override
        public boolean isCurrent() {
            if (server.getPlayerList().getPlayer(issuer.getUUID()) != issuer) {
                return false;
            }
            if (!issuer.isAlive() || issuer.getLevel() != initialWorld
                    || !issuer.createCommandSourceStack().hasPermission(CommandExecutor.PERMISSION_LEVEL)) {
                tell(Text.PLAYER_CHANGED);
                notifyCompletion(Text.PLAYER_CHANGED);
                return false;
            }
            return true;
        }

        @Override
        public JsonObject lookup(String playerName) {
            return GetPlayerContextTool.lookup(server, playerName);
        }

        @Override
        public Optional<String> submit(String command) {
            return CommandExecutor.submit(server, issuer, command);
        }

        @Override
        public void tell(String message) {
            memory.notice(Text.PARSING.equals(message) ? "" : message);
            if (!fromScreen) {
                issuer.createCommandSourceStack().sendSuccess(new StringTextComponent(Text.CHAT_PREFIX + message), false);
            }
            refresh();
        }

        @Override
        public void notifyCompletion(String message) {
            // EN: The client decides whether the chat is visible when this message actually arrives.
            // 中文：是否弹窗由客户端在消息到达时判断，不依赖可能滞后的窗口订阅状态。
            if (server.getPlayerList().getPlayer(issuer.getUUID()) == issuer) {
                ChatNetwork.completed(issuer, message);
            }
        }

        private void refresh() {
            if (memory.watching() && server.getPlayerList().getPlayer(issuer.getUUID()) == issuer) {
                ChatNetwork.show(issuer, memory.view(), false);
            }
        }
    }
}
