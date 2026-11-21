package com.inza.twoh.command.agent.entitiy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** EN: A private snapshot for the owning player. 中文：仅发给所属玩家的聊天快照。 */
public final class ChatView {
    public final boolean summarize;
    public final int window;
    public final int maxWindow;
    public final String summary;
    public final List<ChatTurn> turns;
    public final String pending;
    public final String notice;
    public final long revision;
    public final int first;
    public final int total;
    public final int branch;
    public final int branchCount;

    public ChatView(boolean summarize, int window, int maxWindow, String summary, List<ChatTurn> turns,
                    String pending, String notice) {
        this(summarize, window, maxWindow, summary, turns, pending, notice, 0, 0, turns.size(), 0, 1);
    }

    public ChatView(boolean summarize, int window, int maxWindow, String summary, List<ChatTurn> turns,
                    String pending, String notice, long revision, int first, int total, int branch, int branchCount) {
        this.summarize = summarize;
        this.window = window;
        this.maxWindow = maxWindow;
        this.summary = summary;
        this.turns = Collections.unmodifiableList(new ArrayList<>(turns));
        this.pending = pending;
        this.notice = notice;
        this.revision = revision;
        this.first = first;
        this.total = total;
        this.branch = branch;
        this.branchCount = branchCount;
    }
}
