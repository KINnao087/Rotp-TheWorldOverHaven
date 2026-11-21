package com.inza.twoh.command.agent.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentRunner;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.entitiy.ChatTurn;
import com.inza.twoh.command.agent.entitiy.ChatView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EN: Keep the editable transcript separate from the bounded model input.
 * 中文：完整的可编辑记录与发给模型的有限上下文分别维护。
 */
public final class ChatMemory {
    private static final AtomicLong REVISIONS = new AtomicLong();
    private final List<List<ChatTurn>> branches = new ArrayList<>();
    private int branch;
    private int contextStart;
    private int page = -1;
    private long revision = REVISIONS.incrementAndGet();
    private int requestedTokens = TokenBudget.DEFAULT_WINDOW;
    private int modelLimit;
    private boolean summarize;
    private String summary = "";
    private String pending = "";
    private String notice = "";
    private boolean watching;

    public ChatMemory() {
        branches.add(new ArrayList<>());
    }

    private List<ChatTurn> transcript() {
        return branches.get(branch);
    }

    // EN: A cleared conversation has a new identity; in-flight work keeps only the detached old memory.
    // 中文：清空后换用新会话对象，仍在运行的任务只持有旧记录。
    public synchronized ChatMemory cleared() {
        ChatMemory fresh = new ChatMemory();
        fresh.requestedTokens = requestedTokens;
        fresh.modelLimit = modelLimit;
        fresh.summarize = summarize;
        fresh.watching = watching;
        fresh.notice = ChatText.CLEARED;
        return fresh;
    }

    public synchronized boolean navigate(long expected, int index) {
        if (revision != expected || index < 0 || index >= transcript().size()) {
            return false;
        }
        page = index / ChatText.DISPLAY_TURNS;
        return true;
    }

    private boolean editable(long expected) {
        return pending.isEmpty() && expected == revision;
    }

    private boolean validIndex(int index) {
        return index >= 0 && index < transcript().size();
    }

    private void invalidateContext() {
        // EN: Never reuse a summary that may mention an edited message or a different future.
        // 中文：修改节点或切换分支后，不能复用可能包含旧文字或另一条时间线的摘要。
        revision = REVISIONS.incrementAndGet();
        contextStart = 0;
        summary = "";
        notice = "";
    }

    public synchronized boolean edit(long expected, int index, boolean assistant, String text) {
        if (!editable(expected) || !validIndex(index)) {
            return false;
        }
        ChatTurn old = transcript().get(index);
        if ((assistant ? old.answer : old.request).isEmpty()) {
            return false;
        }
        ChatTurn replacement = assistant ? new ChatTurn(old.request, text) : new ChatTurn(text, old.answer);
        if (replacement.request.isEmpty() && replacement.answer.isEmpty()) {
            transcript().remove(index);
        } else {
            transcript().set(index, replacement);
        }
        invalidateContext();
        return true;
    }

    public synchronized boolean fork(long expected, int index, boolean afterAnswer) {
        if (!editable(expected) || !validIndex(index)) {
            return false;
        }
        ChatTurn point = transcript().get(index);
        if ((afterAnswer ? point.answer : point.request).isEmpty()) {
            return false;
        }
        // EN: Turns are immutable; the new branch shares only the retained prefix.
        // 中文：问答对象不可变，新分支只引用所选节点之前需要保留的前缀。
        branches.add(new ArrayList<>(transcript().subList(0, afterAnswer ? index + 1 : index)));
        return switchBranch(expected, branches.size() - 1);
    }

    public synchronized boolean switchBranch(long expected, int target) {
        if (!editable(expected) || target < 0 || target >= branches.size()) {
            return false;
        }
        branch = target;
        page = -1;
        invalidateContext();
        return true;
    }

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
        page = -1;
        pending = request;
        notice = "";
    }

    public synchronized void remember(String answer) {
        transcript().add(new ChatTurn(pending, answer));
        revision = REVISIONS.incrementAndGet();
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
        int total = transcript().size();
        int lastPage = Math.max(0, total - 1) / ChatText.DISPLAY_TURNS;
        int first = (page < 0 ? lastPage : Math.min(page, lastPage)) * ChatText.DISPLAY_TURNS;
        int end = first + Math.min(ChatText.DISPLAY_TURNS, total - first);
        return new ChatView(summarize, Math.min(requestedTokens, modelLimit), modelLimit,
                summary, transcript().subList(first, end), pending, notice, revision, first, total, branch, branches.size());
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
            turns = new ArrayList<>(transcript().subList(contextStart, transcript().size()));
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
            contextStart += first;
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
            if (!turn.request.isEmpty()) {
                appendRequest(messages, sender, turn.request);
            }
            if (turn.answer.isEmpty()) {
                continue;
            }
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
            if (!turn.request.isEmpty()) {
                exchange.addProperty(Wire.REQUEST, turn.request);
            }
            if (!turn.answer.isEmpty()) {
                exchange.addProperty(Wire.REPLY, turn.answer);
            }
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
