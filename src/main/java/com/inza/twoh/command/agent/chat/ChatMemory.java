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
    private int requestedTokens = TokenBudget.DEFAULT_WINDOW;
    private int modelLimit;
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
        if (!pending.isEmpty() || limit < 0) {
            return false;
        }
        requestedTokens = limit;
        summarize = enabled;
        if (!enabled) {
            summary = "";
        }
        return true;
    }

    public synchronized void bindModel(int contextTokens) {
        modelLimit = TokenBudget.available(contextTokens);
    }

    public synchronized void begin(String request) {
        pending = request;
        notice = "";
    }

    public synchronized void remember(String answer) {
        recent.addLast(new ChatTurn(pending, answer));
    }

    public synchronized void end() {
        pending = "";
    }

    public synchronized void notice(String message) {
        notice = message;
    }

    public synchronized ChatView view() {
        // EN: Limit the GUI packet, not the model's history.
        // 中文：只限制界面快照的大小，不按轮数限制模型上下文。
        Deque<ChatTurn> visible = new ArrayDeque<>();
        java.util.Iterator<ChatTurn> cursor = recent.descendingIterator();
        while (cursor.hasNext() && visible.size() < ChatText.DISPLAY_TURNS) {
            visible.addFirst(cursor.next());
        }
        return new ChatView(summarize, Math.min(requestedTokens, modelLimit), modelLimit,
                summary, new ArrayList<>(visible), pending, notice);
    }

    /**
     * EN: Fit whole exchanges around the mandatory messages on every model call, including tool continuations.
     * 中文：每次推理（含工具续轮）都先给必需消息留空间，再装入最近的完整问答。网络调用不持锁。
     */
    public JsonArray prepare(JsonArray mandatory, String sender, AgentRunner.Model model) throws IOException {
        int cap = TokenBudget.available(model.contextTokens());
        if (cap == 0) {
            throw new IOException(ChatText.UNKNOWN_LIMIT);
        }
        int base = model.inputTokens(mandatory);
        if (base > cap) {
            throw new IOException(ChatText.BUDGET_ERROR);
        }
        List<ChatTurn> turns;
        String digest;
        int limit;
        boolean compress;
        synchronized (this) {
            turns = new ArrayList<>(recent);
            digest = summary;
            compress = summarize;
            limit = base + Math.min(requestedTokens, cap - base);
        }
        int first = 0;
        JsonArray result;
        while (true) {
            // EN: A digest is optional; never let it displace the current player's request.
            // 中文：摘要是可选资料，不允许它挤掉当前请求。
            String included = digest;
            if (!digest.isEmpty() && model.inputTokens(compose(mandatory, sender, turns, turns.size(), digest)) > limit) {
                included = "";
            }
            int start = first;
            result = compose(mandatory, sender, turns, first, included);
            if (first == turns.size() || model.inputTokens(result) <= limit) {
                break;
            }
            int low = first + 1;
            int high = turns.size();
            while (low < high) {
                int mid = low + (high - low) / 2;
                if (model.inputTokens(compose(mandatory, sender, turns, mid, included)) <= limit) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            first = low;
            if (compress) {
                digest = summarize(turns.subList(start, first), digest, model, cap);
            }
        }
        synchronized (this) {
            for (int i = 0; i < first; i++) {
                recent.removeFirst();
            }
            summary = digest;
        }
        return result;
    }

    private static JsonArray compose(JsonArray mandatory, String sender, List<ChatTurn> turns, int first, String digest) {
        JsonArray messages = new JsonArray();
        messages.add(mandatory.get(0));
        if (!digest.isEmpty()) {
            JsonObject note = new JsonObject();
            note.addProperty(ChatText.SUMMARY, digest);
            append(messages, Wire.USER, note.toString());
        }
        for (int i = first; i < turns.size(); i++) {
            ChatTurn turn = turns.get(i);
            appendRequest(messages, sender, turn.request);
            JsonObject reply = new JsonObject();
            reply.addProperty(Wire.TYPE, Wire.REPLY);
            reply.addProperty(Wire.TEXT, turn.answer);
            append(messages, Wire.ASSISTANT, reply.toString());
        }
        for (int i = 1; i < mandatory.size(); i++) {
            messages.add(mandatory.get(i));
        }
        return messages;
    }

    private static String summarize(List<ChatTurn> turns, String digest, AgentRunner.Model model, int cap)
            throws IOException {
        int first = 0;
        while (first < turns.size()) {
            int end = turns.size();
            JsonArray input = summaryInput(turns.subList(first, end), digest);
            while (model.inputTokens(input) > cap) {
                if (end == first + 1) {
                    throw new IOException(ChatText.SUMMARY_ERROR);
                }
                end = first + (end - first) / 2;
                input = summaryInput(turns.subList(first, end), digest);
            }
            try {
                String next = model.complete(input).trim();
                if (next.isEmpty()) {
                    throw new IOException(ChatText.SUMMARY_ERROR);
                }
                digest = next.substring(0, Math.min(next.length(), ChatText.MAX_SUMMARY));
            } catch (IOException | RuntimeException failed) {
                throw new IOException(ChatText.SUMMARY_ERROR, failed);
            }
            first = end;
        }
        return digest;
    }

    private static JsonArray summaryInput(List<ChatTurn> turns, String digest) {
        JsonArray messages = new JsonArray();
        append(messages, Wire.SYSTEM, ChatText.SUMMARY_PROMPT);
        JsonObject data = new JsonObject();
        data.addProperty(ChatText.SUMMARY, digest);
        JsonArray exchanges = new JsonArray();
        for (ChatTurn turn : turns) {
            JsonObject exchange = new JsonObject();
            exchange.addProperty(Wire.REQUEST, turn.request);
            exchange.addProperty(Wire.REPLY, turn.answer);
            exchanges.add(exchange);
        }
        data.add(Wire.MESSAGES, exchanges);
        append(messages, Wire.USER, data.toString());
        return messages;
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
