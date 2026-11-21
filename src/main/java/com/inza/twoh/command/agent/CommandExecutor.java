package com.inza.twoh.command.agent;

import java.util.Optional;

import net.minecraft.command.CommandSource;
import net.minecraft.command.ICommandSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

/**
 * EN: Dispatch once with the issuer's real source. Minecraft and Forge retain all permission checks.
 * 中文：使用发起玩家自己的命令源执行一次，保留 Minecraft 和 Forge 原有的权限检查。
 */
public final class CommandExecutor {
    public static final int PERMISSION_LEVEL = 2;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final ThreadLocal<CommandExecutor> ACTIVE = new ThreadLocal<>();

    private final ICommandSource recipient;
    private final StringBuilder failures = new StringBuilder();

    private CommandExecutor(ICommandSource recipient) {
        this.recipient = recipient;
    }

    public static Optional<String> submit(MinecraftServer server, ServerPlayerEntity issuer, String command) {
        CommandSource source = issuer.createCommandSourceStack();
        if (!source.hasPermission(PERMISSION_LEVEL)) {
            return Optional.of(AgentStrings.Text.PLAYER_CHANGED);
        }

        CommandExecutor previous = ACTIVE.get();
        CommandExecutor execution = new CommandExecutor(issuer);
        ACTIVE.set(execution);
        try {
            // EN: A zero result is valid. Listen for sendFailure instead of guessing from the return value.
            // 中文：合法命令也可能返回零，因此按实际的 sendFailure 反馈判断，而不是猜测返回值。
            server.getCommands().performCommand(source, command);
            return execution.failures.length() == 0
                    ? Optional.empty()
                    : Optional.of(execution.failures.toString());
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    /**
     * EN: Observe only errors for this command's recipient during synchronous dispatch; never intercept chat.
     * 中文：只记录本次同步派发中发给命令发起者的错误，不拦截聊天或原版反馈。
     */
    public static void recordFailure(ICommandSource recipient, ITextComponent message) {
        CommandExecutor execution = ACTIVE.get();
        if (execution == null || execution.recipient != recipient) {
            return;
        }

        StringBuilder errors = execution.failures;
        if (errors.length() < MAX_ERROR_LENGTH) {
            if (errors.length() > 0) {
                errors.append('\n');
            }
            errors.append(message.getString(MAX_ERROR_LENGTH - errors.length()));
            if (errors.length() == 0) {
                errors.append(AgentStrings.Text.EMPTY_COMMAND_ERROR);
            }
        }
    }
}
