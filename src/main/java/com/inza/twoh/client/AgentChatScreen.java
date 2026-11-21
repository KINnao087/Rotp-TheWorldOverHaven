package com.inza.twoh.client;

import com.inza.twoh.command.agent.chat.ChatNetwork;
import com.inza.twoh.command.agent.chat.ChatText;
import com.inza.twoh.command.agent.entitiy.ChatTurn;
import com.inza.twoh.command.agent.entitiy.ChatView;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** EN: A private, non-pausing conversation screen. 中文：私人 AI 聊天窗口，不暂停游戏。 */
public final class AgentChatScreen extends Screen {
    private final List<IReorderingProcessor> lines = new ArrayList<>();
    private ChatView view;
    private TextFieldWidget input;
    private Button send;
    private Button summary;
    private Button minus;
    private Button plus;
    private boolean awaiting;
    private int scroll;

    private AgentChatScreen(ChatView view) {
        super(new StringTextComponent(ChatText.TITLE));
        this.view = view;
    }

    public static void receive(ChatView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AgentChatScreen) {
            AgentChatScreen screen = (AgentChatScreen) mc.screen;
            screen.view = view;
            screen.awaiting = false;
            screen.layoutLines();
            screen.updateControls();
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
        input = new TextFieldWidget(font, x + 4, bottom + 2, w - 65, 17, new StringTextComponent(ChatText.INPUT));
        input.setMaxLength(ChatText.MAX_REQUEST);
        input.setValue(draft);
        children.add(input);
        setInitialFocus(input);

        send = button(x + w - 55, bottom, 51, ChatText.SEND, b -> sendMessage());
        summary = button(x + 4, 29, 100, "", b -> configure(!view.summarize, view.window));
        minus = button(x + w - 53, 29, 22, "−", b -> configure(view.summarize, view.window - 1));
        plus = button(x + w - 26, 29, 22, "+", b -> configure(view.summarize, view.window + 1));
        input.setResponder(value -> updateControls());
        layoutLines();
        updateControls();
    }

    private Button button(int x, int y, int w, String text, Button.IPressable action) {
        return addButton(new Button(x, y, w, 19, new StringTextComponent(text), action));
    }

    private void configure(boolean enabled, int window) {
        awaiting = true;
        updateControls();
        ChatNetwork.configure(enabled, window);
    }

    private void sendMessage() {
        if (!send.active) {
            return;
        }
        String text = input.getValue().trim();
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
        send.active = idle && !input.getValue().trim().isEmpty();
        summary.setMessage(new StringTextComponent(view.summarize ? ChatText.SUMMARY_ON : ChatText.SUMMARY_OFF));
        summary.active = idle;
        minus.active = idle && view.window > ChatText.MIN_WINDOW;
        plus.active = idle && view.window < ChatText.MAX_WINDOW;
    }

    private int panelWidth() {
        return Math.min(480, width - 12);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int visibleLines() {
        return Math.max(1, (height - 102) / (font.lineHeight + 2));
    }

    private void append(String text, TextFormatting color) {
        lines.addAll(font.split(new StringTextComponent(text).withStyle(color), panelWidth() - 20));
    }

    private void layoutLines() {
        lines.clear();
        if (!view.summary.isEmpty()) {
            append(ChatText.SUMMARY_LABEL + view.summary, TextFormatting.GRAY);
        }
        ChatTurn last = null;
        for (ChatTurn turn : view.turns) {
            append(ChatText.YOU + turn.request, TextFormatting.AQUA);
            append(ChatText.AI + turn.answer, TextFormatting.WHITE);
            last = turn;
        }
        if (!view.pending.isEmpty()) {
            append(ChatText.YOU + view.pending, TextFormatting.AQUA);
            append(ChatText.WORKING, TextFormatting.GRAY);
        }
        if (!view.notice.isEmpty() && (last == null || !view.notice.equals(last.answer))) {
            append(view.notice, TextFormatting.YELLOW);
        }
        if (lines.isEmpty()) {
            append(ChatText.EMPTY, TextFormatting.GRAY);
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
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (input.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendMessage();
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
        font.draw(matrices, ChatText.LIMIT + view.window, x + w - 130, 35, 0xDCDCDC);
        fill(matrices, x + 4, 52, x + w - 4, height - 45, 0xC00D0F18);
        int end = lines.size() - scroll;
        int start = Math.max(0, end - visibleLines());
        for (int i = start; i < end; i++) {
            font.draw(matrices, lines.get(i), x + 9, 56 + (i - start) * (font.lineHeight + 2), 0xFFFFFF);
        }
        font.draw(matrices, ChatText.HINT, x + 5, height - 41, 0xBCB9C9);
        input.render(matrices, mouseX, mouseY, partialTicks);
        super.render(matrices, mouseX, mouseY, partialTicks);
        if (summary.isHovered() || minus.isHovered() || plus.isHovered()) {
            String help = summary.isHovered() ? ChatText.SUMMARY_HELP : ChatText.WINDOW_HELP;
            renderTooltip(matrices, font.split(new StringTextComponent(help), Math.min(w - 20, 210)), mouseX, mouseY);
        }
    }

    @Override
    public void tick() {
        input.tick();
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
