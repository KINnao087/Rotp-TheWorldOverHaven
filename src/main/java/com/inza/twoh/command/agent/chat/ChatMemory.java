package com.inza.twoh.command.agent.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentRunner;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.entitiy.ChatTurn;
import com.inza.twoh.command.agent.entitiy.ChatView;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * EN: One player's bounded history. Evict whole turns; never split a question from its answer.
 * 中文：维护单个玩家的有限历史，按整轮淘汰，不把提问和回答拆开。
 */
public final class ChatMemory {
    private final Deque<ChatTurn> recent = new ArrayDeque<>();
    private final List<ChatTurn> toSummarize = new ArrayList<>();
    private int window = ChatText.DEFAULT_WINDOW;
    private boolean summarize;
    private String summary = "";
    private String pending = "";
    private String notice = "";
    private boolean watching;

    public synchronized void watch(boolean open) {
        watching = open;
    }

    public synchronized boolean watching() {
        return watching;
    }

    public synchronized boolean configure(boolean enabled, int limit) {
        if (!pending.isEmpty() || limit < ChatText.MIN_WINDOW || limit > ChatText.MAX_WINDOW) {
            return false;
        }
        window = limit;
        summarize = enabled;
        if (!enabled) {
            summary = "";
            toSummarize.clear();
        }
        trim();
        return true;
    }

    private void trim() {
        while (recent.size() > window) {
            ChatTurn oldest = recent.removeFirst();
            if (summarize) {
                toSummarize.add(oldest);
            }
        }
    }

    public synchronized void begin(String request) {
        pending = request;
        notice = "";
    }

    public synchronized void remember(String answer) {
        recent.addLast(new ChatTurn(pending, answer));
        trim();
    }

    public synchronized void end() {
        pending = "";
    }

    public synchronized void notice(String message) {
        notice = message;
    }

    public synchronized ChatView view() {
        return new ChatView(summarize, window, summary, new ArrayList<>(recent), pending, notice);
    }

    /**
     * EN: Called once per accepted request on its model worker. Never hold a lock during a network call.
     * 中文：每个请求仅在模型线程准备一次历史；网络调用期间不持锁。
     */
    public void appendContext(JsonArray messages, String sender, AgentRunner.Model model) throws IOException {
        List<ChatTurn> evicted;
        ChatView snapshot;
        synchronized (this) {
            evicted = new ArrayList<>(toSummarize);
            snapshot = view();
        }
        String digest = snapshot.summary;
        if (!evicted.isEmpty()) {
            JsonArray input = new JsonArray();
            append(input, Wire.SYSTEM, ChatText.SUMMARY_PROMPT);
            JsonObject data = new JsonObject();
            data.addProperty(ChatText.SUMMARY, digest);
            JsonArray exchanges = new JsonArray();
            for (ChatTurn turn : evicted) {
                JsonObject exchange = new JsonObject();
                exchange.addProperty(Wire.REQUEST, turn.request);
                exchange.addProperty(Wire.REPLY, turn.answer);
                exchanges.add(exchange);
            }
            data.add(Wire.MESSAGES, exchanges);
            append(input, Wire.USER, data.toString());
            try {
                digest = model.complete(input).trim();
                if (digest.isEmpty()) {
                    throw new IOException(ChatText.SUMMARY_ERROR);
                }
            } catch (RuntimeException | IOException failed) {
                throw new IOException(ChatText.SUMMARY_ERROR, failed);
            }
            digest = digest.substring(0, Math.min(digest.length(), ChatText.MAX_SUMMARY));
            synchronized (this) {
                summary = digest;
                toSummarize.clear();
            }
        }
        if (!digest.isEmpty()) {
            JsonObject note = new JsonObject();
            note.addProperty(ChatText.SUMMARY, digest);
            append(messages, Wire.USER, note.toString());
        }
        for (ChatTurn turn : snapshot.turns) {
            appendRequest(messages, sender, turn.request);
            JsonObject reply = new JsonObject();
            reply.addProperty(Wire.TYPE, Wire.REPLY);
            reply.addProperty(Wire.TEXT, turn.answer);
            append(messages, Wire.ASSISTANT, reply.toString());
        }
    }

    public static void appendRequest(JsonArray messages, String sender, String request) {
        JsonObject input = new JsonObject();
        input.addProperty(Wire.SENDER, sender);
        input.addProperty(Wire.REQUEST, request);
        append(messages, Wire.USER, input.toString());
    }

    public static void append(JsonArray messages, String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty(Wire.ROLE, role);
        message.addProperty(Wire.CONTENT, content);
        messages.add(message);
    }
}
