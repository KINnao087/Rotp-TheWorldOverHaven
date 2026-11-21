package com.inza.twoh.client;

import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** EN: Edit multiline AI messages without flattening command receipts. 中文：逐行编辑 AI 消息，保留命令回执中的换行。 */
final class ChatMessageDraft {
    final boolean assistant;
    final String composer;
    private final List<String> lines;
    private int current;

    ChatMessageDraft(boolean assistant, String original, String composer) {
        this.assistant = assistant;
        this.composer = composer;
        lines = new ArrayList<>(Arrays.asList(original.split("\n", -1)));
    }

    String line() {
        return lines.get(current);
    }

    String position() {
        return (current + 1) + "/" + lines.size();
    }

    String text(TextFieldWidget field) {
        lines.set(current, field.getValue());
        return String.join("\n", lines);
    }

    void move(TextFieldWidget field, int direction) {
        lines.set(current, field.getValue());
        current = Math.max(0, Math.min(lines.size() - 1, current + direction));
        field.setValue(line());
    }

    void split(TextFieldWidget field) {
        String value = field.getValue();
        int column = field.getCursorPosition();
        lines.set(current, value.substring(0, column));
        lines.add(++current, value.substring(column));
        field.setValue(line());
        field.moveCursorToStart();
    }

    boolean merge(TextFieldWidget field) {
        if (current == 0 || field.getCursorPosition() != 0 || !field.getHighlighted().isEmpty()) {
            return false;
        }
        String suffix = field.getValue();
        lines.remove(current--);
        int join = line().length();
        field.setValue(line() + suffix);
        field.moveCursorTo(join);
        return true;
    }
}
