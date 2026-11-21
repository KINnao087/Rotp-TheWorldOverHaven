package com.inza.twoh.command.agent.entitiy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** EN: A private snapshot for the owning player. 中文：仅发给所属玩家的聊天快照。 */
public final class ChatView {
    public final boolean summarize;
    public final int window;
    public final String summary;
    public final List<ChatTurn> turns;
    public final String pending;
    public final String notice;

    public ChatView(boolean summarize, int window, String summary, List<ChatTurn> turns,
                    String pending, String notice) {
        this.summarize = summarize;
        this.window = window;
        this.summary = summary;
        this.turns = Collections.unmodifiableList(new ArrayList<>(turns));
        this.pending = pending;
        this.notice = notice;
    }
}
