package com.inza.twoh.command.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentStrings.Text;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.chat.ChatMemory;
import com.inza.twoh.command.agent.chat.ChatText;

import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * EN: Keep completed turns per player; tool calls and errors remain local to the current request.
 * 中文：按玩家保存已完成轮次，工具调用和错误处理只属于当前请求。
 */
public final class AgentRunner {
    private static final int MAX_ACTIVE = 2;
    private static final int MAX_TOOL_CALLS = 3;

    public interface Model {
        String complete(JsonArray messages) throws IOException;
    }

    /**
     * EN: Only the scheduling method may be called by model workers.
     * 中文：模型线程只能调用调度方法，其余游戏操作必须回到主线程。
     */
    public interface Game {
        void onServerThread(Runnable task);
        boolean isCurrent();
        JsonObject lookup(String playerName);
        Optional<String> submit(String command);
        void tell(String message);

        // EN: A terminal event, separate from progress and transcript refreshes.
        // 中文：一次请求的结束事件，与进度提示和聊天记录刷新分开。
        default void notifyCompletion(String message) {
        }
    }

    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, ChatMemory> memories = new HashMap<>();
    private final Executor workers;

    public AgentRunner(Executor workers) {
        this.workers = workers;
    }

    public boolean start(UUID issuer, String senderName, String request, Game game, Model model) {
        return start(issuer, senderName, request, game, model, () -> {});
    }

    public synchronized ChatMemory memory(UUID issuer) {
        return memories.computeIfAbsent(issuer, id -> new ChatMemory());
    }

    public synchronized void forget(UUID issuer) {
        memories.remove(issuer);
    }

    public boolean start(UUID issuer, String senderName, String request, Game game, Model model, Runnable refresh) {
        synchronized (active) {
            if (active.contains(issuer)) {
                game.tell(Text.IN_PROGRESS);
                return false;
            }
            if (active.size() >= MAX_ACTIVE) {
                game.tell(Text.BUSY);
                return false;
            }
            active.add(issuer);
        }

        ChatMemory memory = memory(issuer);
        memory.begin(request);
        Session session = new Session(issuer, game, model, memory, senderName, request, refresh);
        game.tell(Text.PARSING);
        session.next();
        return true;
    }

    private final class Session {
        private final UUID issuer;
        private final Game game;
        private final Model model;
        private final ChatMemory memory;
        private final String sender;
        private final String request;
        private final Runnable refresh;
        private final JsonArray history = new JsonArray();
        private int toolCalls;
        private String commandError;

        Session(UUID issuer, Game game, Model model, ChatMemory memory, String sender, String request, Runnable refresh) {
            this.issuer = issuer;
            this.game = game;
            // EN: Pin the client for all tool rounds, even if an administrator switches models.
            // 中文：整个工具循环绑定同一个客户端，不受管理员中途切换模型影响。
            this.model = model;
            this.memory = memory;
            this.sender = sender;
            this.request = request;
            this.refresh = refresh;
        }

        void append(String role, String content) {
            ChatMemory.append(history, role, content);
        }

        void next() {
            try {
                workers.execute(this::infer);
            } catch (RuntimeException rejected) {
                finishWithFallback(Text.BUSY);
            }
        }

        // EN: There is exactly one in-flight inference per session; history is handed back before mutation.
        // 中文：每个会话同时只有一次推理；主线程在推理结束后才会追加历史。
        void infer() {
            Runnable continuation;
            try {
                if (history.size() == 0) {
                    append(Wire.SYSTEM, AgentStrings.SYSTEM_PROMPT);
                    memory.appendContext(history, sender, model);
                    ChatMemory.appendRequest(history, sender, request);
                }
                String content = model.complete(history);
                ModelReplyParser.Decision decision = ModelReplyParser.parse(content);
                continuation = () -> accept(content, decision);
            } catch (IOException exception) {
                String reason = exception.getMessage();
                boolean known = Text.CONFIG_REQUIRED.equals(reason) || ChatText.SUMMARY_ERROR.equals(reason)
                        || (reason != null && reason.startsWith(Text.HTTP_ERROR_PREFIX));
                continuation = () -> finishWithFallback(known ? reason : Text.CONNECTION_FAILED);
            } catch (RuntimeException invalid) {
                continuation = () -> finishWithFallback(Text.INVALID_COMMAND);
            }

            final Runnable action = continuation;
            try {
                game.onServerThread(() -> {
                    if (!game.isCurrent()) {
                        release();
                        refresh.run();
                        return;
                    }
                    try {
                        action.run();
                    } catch (RuntimeException invalid) {
                        finishWithFallback(Text.INVALID_COMMAND);
                    }
                });
            } catch (RuntimeException stopped) {
                release();
            }
        }

        void accept(String raw, ModelReplyParser.Decision decision) {
            // EN: An error can follow partial effects. Let the model explain it, not repeat the command.
            // 中文：报错前可能已有部分效果；这一轮只让模型解释，不重复执行。
            if (commandError != null && decision.kind != ModelReplyParser.Kind.REPLY) {
                finish(commandError);
                return;
            }
            switch (decision.kind) {
                case REPLY:
                    memory.remember(decision.value);
                    finish(decision.value);
                    return;
                case COMMAND:
                    execute(raw, decision.value);
                    return;
                case TOOL:
                    if (toolCalls >= MAX_TOOL_CALLS) {
                        finish(Text.TOOL_LIMIT);
                        return;
                    }
                    JsonObject result = ToolDispatcher.dispatch(decision.value, decision.arguments, game::lookup);
                    toolCalls++;
                    JsonObject envelope = new JsonObject();
                    envelope.addProperty(Wire.TYPE, Wire.TOOL_RESULT);
                    envelope.addProperty(Wire.NAME, decision.value);
                    envelope.add(Wire.RESULT, result);
                    append(Wire.ASSISTANT, raw);
                    append(Wire.USER, envelope.toString());
                    next();
                    return;
                default:
                    throw new IllegalStateException();
            }
        }

        void execute(String raw, String command) {
            Optional<String> failure = game.submit(command);
            if (!failure.isPresent()) {
                String result = ChatText.DISPATCHED + command;
                memory.remember(result);
                release();
                refresh.run();
                game.notifyCompletion(result);
                return;
            }

            commandError = failure.get();
            JsonObject feedback = new JsonObject();
            feedback.addProperty(Wire.TYPE, Wire.COMMAND_ERROR);
            feedback.addProperty(Wire.ERROR, commandError);
            append(Wire.ASSISTANT, raw);
            append(Wire.USER, feedback.toString());
            next();
        }

        void finishWithFallback(String message) {
            finish(commandError != null ? commandError : message);
        }

        void finish(String text) {
            release();
            game.tell(text);
            game.notifyCompletion(text);
        }

        void release() {
            memory.end();
            synchronized (active) {
                active.remove(issuer);
            }
        }
    }
}
