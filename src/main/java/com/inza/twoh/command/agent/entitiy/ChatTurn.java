package com.inza.twoh.command.agent.entitiy;

/** EN: A completed exchange. 中文：一轮完整的玩家请求和回复。 */
public final class ChatTurn {
    public final String request;
    public final String answer;

    public ChatTurn(String request, String answer) {
        this.request = request;
        this.answer = answer;
    }
}
