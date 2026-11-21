package com.inza.twoh.command;

import com.inza.twoh.AddonMain;
import com.inza.twoh.command.agent.ChatApi;
import com.inza.twoh.command.agent.ModelCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /twoh agent 的入口：主线程捕获目标，后台翻译，再回主线程执行。 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MiniAgentCommand {
    private static final int PERMISSION_LEVEL = 2;
    private static final int TARGET_RANGE = 16;
    private static final int MAX_REQUEST_LENGTH = 256;
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

    // 不排队：两个工作线程都忙时立即提示玩家，避免请求排到目标已经失效。
    private static final ExecutorService IO = new ThreadPoolExecutor(
            0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
            task -> {
                Thread thread = new Thread(task, "TWOH-model");
                thread.setDaemon(true);
                return thread;
            });

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("twoh")
                .then(Commands.literal("agent")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.argument("request", StringArgumentType.greedyString())
                                .executes(MiniAgentCommand::request))));
    }

    private static int request(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrException();
        String text = StringArgumentType.getString(context, "request");
        if (text.trim().isEmpty() || text.length() > MAX_REQUEST_LENGTH) {
            tell(source, "请求须为 1–256 个字符。");
            return 0;
        }

        // 在发话时锁定 UUID；即使玩家随后转头，也不能更换这条命令的目标。
        Entity entity = aimedTarget(player);
        UUID target = entity == null ? null : entity.getUUID();
        String type = entity == null ? null : entity.getType().getRegistryName().toString();
        Request request = new Request(source, player, target);
        if (!BUSY.add(request.playerId)) {
            tell(source, "上一条请求尚未完成。");
            return 0;
        }

        try {
            IO.execute(() -> translate(request, text, type));
        } catch (RejectedExecutionException busy) {
            BUSY.remove(request.playerId);
            tell(source, "模型正忙，请稍后重试。");
            return 0;
        }

        tell(source, "正在解析请求……");
        return 1;
    }

    private static Entity aimedTarget(ServerPlayerEntity player) {
        Vector3d eye = player.getEyePosition(1);
        Vector3d direction = player.getViewVector(1).scale(TARGET_RANGE);
        RayTraceResult wall = player.pick(TARGET_RANGE, 1, false);
        double distanceSquared = wall.getType() == RayTraceResult.Type.MISS
                ? TARGET_RANGE * TARGET_RANGE
                : eye.distanceToSqr(wall.getLocation());

        // 射线在最近的方块处截止；挡在前面的非生物实体也不能被穿透。
        EntityRayTraceResult hit = ProjectileHelper.getEntityHitResult(
                player, eye, eye.add(direction), player.getBoundingBox().expandTowards(direction).inflate(1),
                entity -> !entity.isSpectator() && entity.isPickable(), distanceSquared);
        if (hit == null || !(hit.getEntity() instanceof MobEntity)
                || eye.distanceToSqr(hit.getLocation()) > distanceSquared) {
            return null;
        }
        return hit.getEntity();
    }

    /** 模型线程只消费发话时的快照，不在此处读取实体或修改世界。 */
    private static void translate(Request request, String text, String type) {
        String command = null;
        String failure = null;
        try {
            String target = request.target == null ? null : request.target.toString();
            String content = ChatApi.translate(text, target, type);
            command = ModelCommand.parse(content, request.target);
        } catch (IOException exception) {
            String reason = exception.getMessage();
            boolean userFacing = reason != null && (reason.startsWith("请先") || reason.startsWith("模型 API 返回 HTTP"));
            failure = userFacing ? reason : "模型连接失败或超时；未执行。";
        } catch (RuntimeException exception) {
            failure = "模型返回了无效格式或未授权命令；未执行。";
        }

        final String parsedCommand = command;
        final String errorMessage = failure;
        try {
            request.server.execute(() -> execute(request, parsedCommand, errorMessage));
        } catch (RuntimeException schedulingFailure) {
            // 主线程未接收任务，不能依赖执行回调释放该玩家的占用标记。
            BUSY.remove(request.playerId);
        }
    }

    /** 返回主线程后重新核验状态；推理期间玩家可能退出、切换维度或失去权限。 */
    private static void execute(Request request, String command, String failure) {
        try {
            ServerPlayerEntity player = request.player;
            if (request.server.getPlayerList().getPlayer(request.playerId) != player) {
                return;
            }

            CommandSource source = player.createCommandSourceStack();
            if (player.getLevel() != request.world || !source.hasPermission(PERMISSION_LEVEL) || !player.isAlive()) {
                tell(source, "玩家状态或权限已变化；未执行。");
                return;
            }
            if (failure != null) {
                tell(source, failure);
                return;
            }
            if (command == null) {
                tell(source, "无法确定单条受支持的指令或目标；未执行。");
                return;
            }
            if (command.startsWith("kill ") && !isTargetValid(request)) {
                tell(source, "原目标失效、超距或已被遮挡；未执行。");
                return;
            }

            // 使用玩家当前的命令源，不借用控制台权限。
            request.server.getCommands().getDispatcher().execute(command, source);
            tell(source, "已执行 /" + command);
        } catch (CommandSyntaxException exception) {
            tell(request.source, "游戏拒绝执行：" + exception.getRawMessage().getString());
        } finally {
            BUSY.remove(request.playerId);
        }
    }

    private static boolean isTargetValid(Request request) {
        Entity entity = request.world.getEntity(request.target);
        return entity instanceof MobEntity
                && entity.isAlive()
                && request.player.distanceToSqr(entity) <= TARGET_RANGE * TARGET_RANGE
                && request.player.canSee(entity);
    }

    private static void tell(CommandSource source, String message) {
        source.sendSuccess(new StringTextComponent("[TWOH] " + message), false);
    }

    /** 从主线程捕获的请求上下文。游戏对象引用仅供回到主线程后使用。 */
    private static final class Request {
        final CommandSource source;
        final MinecraftServer server;
        final ServerPlayerEntity player;
        final ServerWorld world;
        final UUID playerId;
        final UUID target;

        Request(CommandSource source, ServerPlayerEntity player, UUID target) {
            this.source = source;
            this.server = source.getServer();
            this.player = player;
            this.world = player.getLevel();
            this.playerId = player.getUUID();
            this.target = target;
        }
    }
}
