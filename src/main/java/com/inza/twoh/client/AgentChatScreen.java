package com.inza.twoh.client;

import com.inza.twoh.command.agent.chat.ChatNetwork;
import com.inza.twoh.command.agent.chat.ChatNetwork.HistoryOp;
import com.inza.twoh.command.agent.chat.ChatText;
import com.inza.twoh.command.agent.entitiy.ChatTurn;
import com.inza.twoh.command.agent.entitiy.ChatView;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** EN: A private, non-pausing conversation screen. 中文：私人 AI 聊天窗口，不暂停游戏。 */
public final class AgentChatScreen extends Screen {
    private final List<IReorderingProcessor> lines = new ArrayList<>();
    private final List<Integer> owners = new ArrayList<>();
    private ChatView view;
    private TextFieldWidget input;
    private Button send;
    private Button summary;
    private TextFieldWidget budget;
    private Button apply;
    private Button older, newer, previousBranch, nextBranch, edit, delete, fork;
    private int selected = -1;
    private boolean confirmDelete;
    private ChatMessageDraft editor;
    private int recall = -1;
    private int seekIndex;
    private int seekDirection;
    private String savedDraft = "";
    private boolean awaiting;
    private int scroll;

    private AgentChatScreen(ChatView view) {
        super(new TranslationTextComponent(ChatText.TITLE));
        this.view = view;
    }

    public static void receive(ChatView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AgentChatScreen) {
            AgentChatScreen screen = (AgentChatScreen) mc.screen;
            if (view.revision != screen.view.revision || view.first != screen.view.first) {
                screen.leaveEditor();
                screen.select(-1);
                screen.scroll = 0;
            }
            if (view.revision != screen.view.revision) {
                screen.resetRecall();
            }
            screen.view = view;
            screen.awaiting = false;
            if (!screen.budget.isFocused() || screen.settingsSent) {
                screen.budget.setValue(Integer.toString(view.window));
            }
            screen.settingsSent = false;
            screen.layoutLines();
            screen.updateControls();
            if (screen.seekDirection != 0) {
                screen.seek(screen.seekIndex, screen.seekDirection);
            }
        } else if (open) {
            mc.setScreen(new AgentChatScreen(view));
        }
    }

    @Override
    protected void init() {
        AgentReplyNotice.clear();
        String draft = input == null ? "" : input.getValue();
        minecraft.keyboardHandler.setSendRepeatsToGui(true);
        int x = left();
        int w = panelWidth();
        int bottom = height - 29;
        input = new TextFieldWidget(font, x + 4, bottom + 2, w - 65, 17, new TranslationTextComponent(ChatText.INPUT));
        input.setMaxLength(editor != null && editor.assistant ? ChatText.MAX_REPLY : ChatText.MAX_REQUEST);
        input.setValue(draft);
        children.add(input);
        setInitialFocus(input);

        send = button(x + w - 55, bottom, 51, I18n.get(ChatText.SEND), b -> sendMessage());
        summary = button(x + 4, 29, 100, "", b -> configure(!view.summarize, view.window));
        budget = new TextFieldWidget(font, x + w - 123, 30, 74, 16, new TranslationTextComponent(ChatText.LIMIT_LABEL));
        budget.setMaxLength(10);
        budget.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        budget.setValue(Integer.toString(view.window));
        children.add(budget);
        apply = button(x + w - 44, 29, 40, I18n.get(ChatText.APPLY), b -> configure(view.summarize, enteredBudget()));
        older = button(x + 4, 51, 43, I18n.get(ChatText.PREVIOUS), b -> page(view.first - 1));
        newer = button(x + 49, 51, 43, I18n.get(ChatText.NEXT), b -> page(view.first + view.turns.size()));
        previousBranch = button(x + w - 52, 51, 22, "<", b -> switchBranch(-1));
        nextBranch = button(x + w - 27, 51, 22, ">", b -> switchBranch(1));
        edit = button(x + 4, bottom - 43, 52, I18n.get(ChatText.EDIT), b -> editOrSave());
        delete = button(x + 59, bottom - 43, 64, I18n.get(ChatText.DELETE), b -> deleteOrCancel());
        fork = button(x + 126, bottom - 43, 74, I18n.get(ChatText.RESEND), b -> fork());
        budget.setResponder(value -> updateControls());
        input.setResponder(value -> updateControls());
        layoutLines();
        updateControls();
    }

    private Button button(int x, int y, int w, String text, Button.IPressable action) {
        return addButton(new Button(x, y, w, 19, new StringTextComponent(text), action));
    }

    private boolean settingsSent;

    private int enteredBudget() {
        try {
            return Integer.parseInt(budget.getValue());
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    private void configure(boolean enabled, int window) {
        settingsSent = true;
        awaiting = true;
        updateControls();
        ChatNetwork.configure(enabled, window);
    }

    private void sendMessage() {
        if (!send.active) {
            return;
        }
        if (editor != null) {
            if (editor.assistant) {
                editOrSave();
            } else {
                fork();
            }
            return;
        }
        String text = input.getValue().trim();
        resetRecall();
        awaiting = true;
        input.setValue("");
        scroll = 0;
        updateControls();
        ChatNetwork.send(text);
        setFocused(input);
        input.setFocus(true);
    }

    private void updateControls() {
        boolean idle = !awaiting && view.pending.isEmpty();
        boolean browsing = editor == null;
        boolean valid = browsing ? !input.getValue().trim().isEmpty() : validDraft();
        boolean hasSelection = idle && selected >= 0;
        send.active = valid && (idle || (browsing && ChatText.CLEAR.equals(input.getValue().trim()) && !awaiting));
        send.setMessage(new TranslationTextComponent(
                browsing ? ChatText.SEND : editor.assistant ? ChatText.SAVE : ChatText.RESEND));
        summary.setMessage(new TranslationTextComponent(
                view.summarize ? ChatText.SUMMARY_ON : ChatText.SUMMARY_OFF));
        summary.active = idle && browsing;
        budget.setEditable(idle && browsing);
        int entered = enteredBudget();
        apply.active = idle && browsing && entered >= 0 && entered <= view.maxWindow && entered != view.window;
        older.active = idle && browsing && view.first > 0;
        newer.active = idle && browsing && view.first + view.turns.size() < view.total;
        previousBranch.active = idle && browsing && view.branch > 0;
        nextBranch.active = idle && browsing && view.branch + 1 < view.branchCount;
        edit.active = hasSelection && (browsing || valid);
        delete.active = hasSelection;
        fork.active = hasSelection && (browsing || (!editor.assistant && valid));
        edit.setMessage(new TranslationTextComponent(
                browsing ? ChatText.EDIT : ChatText.SAVE));
        delete.setMessage(new TranslationTextComponent(
                !browsing ? ChatText.CANCEL : confirmDelete ? ChatText.CONFIRM_DELETE : ChatText.DELETE));
        fork.setMessage(new TranslationTextComponent(
                assistantSelected() ? ChatText.CONTINUE : ChatText.RESEND));
    }

    private boolean assistantSelected() {
        return selected >= 0 && (selected & 1) != 0;
    }

    private String selectedText() {
        ChatTurn turn = view.turns.get(selected / 2 - view.first);
        return assistantSelected() ? turn.answer : turn.request;
    }

    private void select(int key) {
        selected = key;
        confirmDelete = false;
    }

    private boolean validDraft() {
        String text = editor.text(input);
        return !text.trim().isEmpty() && text.length() <= (editor.assistant ? ChatText.MAX_REPLY : ChatText.MAX_REQUEST);
    }

    private void history(HistoryOp op, int index, boolean assistant, String text) {
        awaiting = true;
        updateControls();
        ChatNetwork.history(op, view.revision, index, assistant, text);
    }

    private void page(int target) {
        history(HistoryOp.PAGE, target, false, "");
    }

    private void switchBranch(int step) {
        history(HistoryOp.SWITCH, view.branch + step, false, "");
    }

    private void editOrSave() {
        if (selected < 0) {
            return;
        }
        if (editor == null) {
            resetRecall();
            confirmDelete = false;
            editor = new ChatMessageDraft(assistantSelected(), selectedText(), input.getValue());
            String initial = editor.line();
            input.setMaxLength(editor.assistant ? ChatText.MAX_REPLY : ChatText.MAX_REQUEST);
            input.setValue(initial);
            focusInput();
        } else {
            String replacement = editor.text(input);
            history(HistoryOp.EDIT, selected / 2, assistantSelected(), replacement);
        }
        updateControls();
    }

    private void deleteOrCancel() {
        if (editor != null) {
            leaveEditor();
        } else if (confirmDelete) {
            history(HistoryOp.DELETE, selected / 2, assistantSelected(), "");
        } else {
            confirmDelete = true;
        }
        updateControls();
    }

    private void fork() {
        boolean assistant = assistantSelected();
        String text = assistant ? "" : editor == null ? selectedText() : editor.text(input);
        history(assistant ? HistoryOp.CONTINUE : HistoryOp.RESEND, selected / 2, assistant, text);
    }

    private void leaveEditor() {
        if (editor != null) {
            String originalDraft = editor.composer;
            editor = null;
            input.setMaxLength(ChatText.MAX_REQUEST);
            input.setValue(originalDraft);
        }
    }

    private void focusInput() {
        budget.setFocus(false);
        setFocused(input);
        input.setFocus(true);
    }

    private void resetRecall() {
        recall = -1;
        seekDirection = 0;
        savedDraft = "";
    }

    // EN: Page through the transcript, not the model window; DOWN past the latest restores the unsent draft.
    // 中文：遍历完整分支而不是模型窗口；越过最新记录时恢复玩家原先尚未发送的草稿。
    private void seek(int index, int direction) {
        seekDirection = 0;
        while (index >= 0 && index < view.total) {
            if (index < view.first || index >= view.first + view.turns.size()) {
                seekIndex = index;
                seekDirection = direction;
                page(index);
                return;
            }
            String text = view.turns.get(index - view.first).request;
            if (!text.isEmpty()) {
                recall = index;
                input.setValue(text);
                return;
            }
            index += direction;
        }
        if (direction > 0 && recall >= 0) {
            input.setValue(savedDraft);
            resetRecall();
        }
    }

    private int panelWidth() {
        return Math.min(480, width - 12);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int visibleLines() {
        return Math.max(1, (height - 151) / (font.lineHeight + 2));
    }

    private void append(String text, TextFormatting color, int owner) {
        for (IReorderingProcessor line : font.split(new StringTextComponent(text).withStyle(color), panelWidth() - 20)) {
            lines.add(line);
            owners.add(owner);
        }
    }

    private void layoutLines() {
        lines.clear();
        owners.clear();
        if (!view.summary.isEmpty()) {
            append(I18n.get(ChatText.SUMMARY_LABEL) + view.summary, TextFormatting.GRAY, -1);
        }
        ChatTurn last = null;
        int index = view.first;
        for (ChatTurn turn : view.turns) {
            if (!turn.request.isEmpty()) {
                append(I18n.get(ChatText.YOU) + turn.request, TextFormatting.AQUA, index * 2);
            }
            if (!turn.answer.isEmpty()) {
                append(I18n.get(ChatText.AI) + turn.answer, TextFormatting.WHITE, index * 2 + 1);
            }
            index++;
            last = turn;
        }
        if (!view.pending.isEmpty()) {
            append(I18n.get(ChatText.YOU) + view.pending, TextFormatting.AQUA, -1);
            append(I18n.get(ChatText.WORKING), TextFormatting.GRAY, -1);
        }
        if (!view.notice.isEmpty() && (last == null || !view.notice.equals(last.answer))) {
            append(view.notice, TextFormatting.YELLOW, -1);
        }
        if (lines.isEmpty()) {
            append(I18n.get(ChatText.EMPTY), TextFormatting.GRAY, -1);
        }
        scroll = Math.min(scroll, Math.max(0, lines.size() - visibleLines()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int limit = Math.max(0, lines.size() - visibleLines());
        scroll = Math.max(0, Math.min(limit, scroll + (int) Math.signum(delta) * 3));
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && !awaiting && editor == null && view.pending.isEmpty()
                && x >= left() + 4 && x < left() + panelWidth() - 4 && y >= 76) {
            int end = lines.size() - scroll;
            int first = Math.max(0, end - visibleLines());
            int row = first + (int) ((y - 76) / (font.lineHeight + 2));
            if (row < end && y < height - 74) {
                select(owners.get(row));
                updateControls();
                return true;
            }
        }
        ChatMessageDraft before = editor;
        boolean handled = super.mouseClicked(x, y, button);
        if (before != editor) {
            focusInput();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editor != null && key == GLFW.GLFW_KEY_ESCAPE) {
            leaveEditor();
            updateControls();
            return true;
        }
        if (input.isFocused() && !awaiting && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
            if (editor != null) {
                editor.move(input, direction);
            } else {
                if (recall < 0) {
                    savedDraft = input.getValue();
                }
                seek((recall < 0 ? view.total : recall) + direction, direction);
            }
            return true;
        }
        if (input.isFocused() && editor != null && editor.assistant && !awaiting) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && editor.merge(input)) {
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && hasShiftDown()) {
                editor.split(input);
                return true;
            }
        }
        if (input.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendMessage();
            return true;
        }
        if (budget.isFocused() && apply.active && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            configure(view.summarize, enteredBudget());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrices);
        int x = left();
        int w = panelWidth();
        fill(matrices, x, 4, x + w, height - 5, 0xD2171823);
        drawCenteredString(matrices, font, title, width / 2, 10, 0xFFFFFF);
        font.draw(matrices, I18n.get(ChatText.LIMIT_LABEL), x + w - 128 - font.width(I18n.get(ChatText.LIMIT_LABEL)), 35, 0xDCDCDC);
        budget.render(matrices, mouseX, mouseY, partialTicks);
        String branch = I18n.get(ChatText.BRANCH) + (view.branch + 1) + "/" + view.branchCount;
        font.draw(matrices, branch, x + w - 58 - font.width(branch), 57, 0xDCDCDC);
        fill(matrices, x + 4, 73, x + w - 4, height - 74, 0xC00D0F18);
        String cap = I18n.get(ChatText.HINT) + view.maxWindow;
        if (font.width(cap) < w - 210) {
            font.draw(matrices, cap, x + w - 6 - font.width(cap), height - 66, 0xBCB9C9);
        }
        int end = lines.size() - scroll;
        int start = Math.max(0, end - visibleLines());
        for (int i = start; i < end; i++) {
            int y = 76 + (i - start) * (font.lineHeight + 2);
            if (selected >= 0 && owners.get(i) == selected) {
                fill(matrices, x + 5, y - 1, x + w - 5, y + font.lineHeight + 1, 0xFF403750);
            }
            font.draw(matrices, lines.get(i), x + 9, y, 0xFFFFFF);
        }
        String hint = editor == null ? I18n.get(ChatText.HISTORY_HINT) : I18n.get(ChatText.EDIT_HINT);
        if (editor != null && editor.assistant) {
            hint += I18n.get(ChatText.LINE_HINT) + " " + editor.position();
        }
        font.draw(matrices, hint, x + 5, height - 41, 0xBCB9C9);
        input.render(matrices, mouseX, mouseY, partialTicks);
        super.render(matrices, mouseX, mouseY, partialTicks);
        if (summary.isHovered() || budget.isHovered() || apply.isHovered()) {
            String help = summary.isHovered() ? I18n.get(ChatText.SUMMARY_HELP) : I18n.get(ChatText.WINDOW_HELP) + " "
                    + (view.maxWindow == 0 ? I18n.get(ChatText.UNKNOWN_LIMIT_LABEL) : I18n.get(ChatText.HINT) + view.maxWindow);
            renderTooltip(matrices, font.split(new StringTextComponent(help), Math.min(w - 20, 210)), mouseX, mouseY);
        }
    }

    @Override
    public void tick() {
        input.tick();
        budget.tick();
    }

    @Override
    public void removed() {
        minecraft.keyboardHandler.setSendRepeatsToGui(false);
        if (minecraft.getConnection() != null) {
            ChatNetwork.close();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
