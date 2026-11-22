package com.inza.twoh.client;

import com.inza.twoh.AddonMain;
import com.inza.twoh.command.agent.chat.ChatText;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * EN: Non-modal, client-only reply cards. A late packet must never reopen a screen or cross connections.
 * 中文：只在客户端显示的非模态回复卡片。延迟到达的包不会重开界面，也不会跨连接显示。
 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class AgentReplyNotice {
    private static final int MARGIN = 8;
    private static final int PADDING = 8;
    private static final int MAX_WIDTH = 224;
    private static final int MAX_LINES = 4;
    private static final int BACKLOG = 8;
    private static final long PAGE_MILLIS = 6500;
    private static final Deque<String> waiting = new ArrayDeque<>();
    private static String current;
    private static List<IReorderingProcessor> lines = Collections.emptyList();
    private static int wrappedWidth;
    private static int firstLine;
    private static long pageStarted;

    private AgentReplyNotice() {
    }

    public static void receive(NetworkManager origin, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getConnection().getConnection() != origin
                || mc.screen instanceof AgentChatScreen || message.isEmpty()) {
            return;
        }
        if (waiting.size() == BACKLOG) {
            waiting.removeFirst();
        }
        waiting.addLast(message);
    }

    @SubscribeEvent
    public static void onHud(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && Minecraft.getInstance().screen == null) {
            draw(event.getMatrixStack(), true);
        }
    }

    @SubscribeEvent
    public static void onScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof AgentChatScreen)) {
            draw(event.getMatrixStack(), false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        clear();
    }

    static void clear() {
        waiting.clear();
        current = null;
        lines = Collections.emptyList();
    }

    private static void draw(MatrixStack matrices, boolean hud) {
        if (current == null && waiting.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        FontRenderer font = mc.font;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int cardWidth = Math.min(MAX_WIDTH, Math.min(width - 2 * MARGIN, Math.max(140, width / 2)));
        int lineHeight = font.lineHeight + 2;
        int perPage = Math.max(1, Math.min(MAX_LINES, (height / 2 - 34) / lineHeight));
        long now = Util.getMillis();

        // EN: Start the clock on first render. Long replies turn pages instead of clipping the text.
        // 中文：首次真正绘制时才开始计时。长回复自动翻页，不直接截断正文。
        if (current != null && now - pageStarted >= PAGE_MILLIS) {
            firstLine += perPage;
            pageStarted = now;
            if (firstLine >= lines.size()) {
                current = null;
                lines = Collections.emptyList();
            }
        }
        if (current == null) {
            current = waiting.pollFirst();
            if (current == null) {
                return;
            }
            wrappedWidth = 0;
        }
        if (wrappedWidth != cardWidth) {
            wrappedWidth = cardWidth;
            lines = font.split(new StringTextComponent(current), Math.max(1, cardWidth - 2 * PADDING));
            firstLine = 0;
            pageStarted = now;
        }

        int end = Math.min(lines.size(), firstLine + perPage);
        int right = width - MARGIN;
        int left = right - cardWidth;
        int bottom = height - (hud ? 40 : MARGIN);
        int top = bottom - 2 * PADDING - font.lineHeight - 4 - (end - firstLine) * lineHeight;

        matrices.pushPose();
        matrices.translate(0, 0, 400);
        AbstractGui.fill(matrices, left, top, right, bottom, 0xF0211E2D);
        AbstractGui.fill(matrices, left, top, left + 2, bottom, 0xFFB68BF2);
        font.draw(matrices, I18n.get(ChatText.NOTIFICATION_TITLE), left + PADDING, top + PADDING, 0xE0C8FF);
        if (lines.size() > perPage) {
            String pages = (firstLine / perPage + 1) + "/" + ((lines.size() + perPage - 1) / perPage);
            font.draw(matrices, pages, right - PADDING - font.width(pages), top + PADDING, 0xBAAEC9);
        }
        for (int index = firstLine; index < end; index++) {
            int y = top + PADDING + font.lineHeight + 4 + (index - firstLine) * lineHeight;
            font.draw(matrices, lines.get(index), left + PADDING, y, 0xFFFFFF);
        }
        matrices.popPose();
    }
}
