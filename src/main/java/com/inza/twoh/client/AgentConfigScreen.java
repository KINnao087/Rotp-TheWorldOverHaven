package com.inza.twoh.client;

import com.inza.twoh.command.agent.config.ConfigNetwork;
import com.inza.twoh.command.agent.config.ConfigText;
import com.inza.twoh.command.agent.entitiy.ConfigDraft;
import com.inza.twoh.command.agent.entitiy.ProfileSummary;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;

import java.util.Collections;
import java.util.List;

/**
 * EN: Edit a local draft. Esc discards it, and the server never returns an existing key.
 * 中文：界面只编辑草稿；Esc 放弃修改，服务端从不回传已有密钥。
 */
public final class AgentConfigScreen extends Screen {
    private final String active;
    private final long revision;
    private final List<ProfileSummary> profiles;
    private final TextFieldWidget[] fields = new TextFieldWidget[6];
    private int selection;
    private boolean clear;
    private boolean consent;
    private String status = "";
    private Button clearButton;
    private Button consentButton;

    private AgentConfigScreen(String active, long revision, List<ProfileSummary> profiles) {
        super(label(I18n.get(ConfigText.TITLE)));
        this.active = active;
        this.revision = revision;
        this.profiles = profiles;
        this.selection = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(active)) {
                selection = i;
            }
        }
    }

    public static void open(String active, long revision, List<ProfileSummary> profiles) {
        Minecraft.getInstance().setScreen(new AgentConfigScreen(active, revision, profiles));
    }

    public static void feedback(String text) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof AgentConfigScreen) {
            ((AgentConfigScreen) screen).status = text;
        }
    }

    @Override
    protected void init() {
        ConfigDraft previous = fields[0] == null ? null : draft();
        int left = left();
        int top = top();
        int span = span();
        int[] limits = {48, 48, 512, 128, 2048, 9};
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new TextFieldWidget(font, left + 96, top + 31 + i * 19, span - 96, 16,
                    label(I18n.get(ConfigText.LABELS[i])));
            fields[i].setMaxLength(limits[i]);
            children.add(fields[i]);
        }
        fields[4].setFormatter((text, offset) -> IReorderingProcessor.forward(
                String.join("", Collections.nCopies(text.length(), "*")), Style.EMPTY));
        fields[5].setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));

        int half = (span - 4) / 2;
        button(left, top + 145, half, I18n.get(ConfigText.SELECT), b -> {
            if (!profiles.isEmpty()) {
                selection = (selection + 1) % profiles.size();
                loadSelection();
            }
        });
        button(left + half + 4, top + 145, half, I18n.get(ConfigText.NEW), b -> {
            selection = -1;
            loadSelection();
        });
        clearButton = button(left, top + 166, half, "", b -> {
            clear = !clear;
            flags();
        });
        consentButton = button(left + half + 4, top + 166, half, "", b -> {
            consent = !consent;
            flags();
        });
        button(left, top + 187, half, I18n.get(ConfigText.TEST), b -> ConfigNetwork.send(draft(), revision, true));
        button(left + half + 4, top + 187, half, I18n.get(ConfigText.SAVE), b -> ConfigNetwork.send(draft(), revision, false));
        if (previous == null) {
            loadSelection();
        } else {
            fill(previous.id, previous.provider, previous.baseUrl, previous.model, previous.replacement, capacity(previous.contextTokens));
            flags();
        }
    }

    private void loadSelection() {
        if (selection < 0) {
            fill(ConfigText.LOCAL, ConfigText.OLLAMA, ConfigText.LOCAL_URL, "", "", "");
        } else {
            ProfileSummary profile = profiles.get(selection);
            fill(profile.id, profile.provider, profile.baseUrl, profile.model, "", capacity(profile.contextTokens));
        }
        clear = false;
        consent = false;
        status = "";
        flags();
    }

    private void fill(String... values) {
        for (int i = 0; i < fields.length; i++) {
            fields[i].setValue(values[i]);
        }
    }

    private ConfigDraft draft() {
        return new ConfigDraft(fields[0].getValue(), fields[1].getValue(), fields[2].getValue(),
                fields[3].getValue(), fields[4].getValue(), clear, consent,
                fields[5].getValue().isEmpty() ? 0 : Integer.parseInt(fields[5].getValue()));
    }

    private static String capacity(int tokens) {
        return tokens == 0 ? "" : Integer.toString(tokens);
    }

    private void flags() {
        clearButton.setMessage(label(I18n.get(ConfigText.CLEAR) + ": " + I18n.get(clear ? ConfigText.ON : ConfigText.OFF)));
        consentButton.setMessage(label(I18n.get(ConfigText.CONSENT) + ": " + I18n.get(consent ? ConfigText.ON : ConfigText.OFF)));
    }

    private Button button(int x, int y, int width, String text, Button.IPressable callback) {
        return addButton(new Button(x, y, width, 18, label(text), callback));
    }

    private static StringTextComponent label(String text) {
        return new StringTextComponent(text);
    }

    private int span() {
        return Math.min(420, width - 12);
    }

    private int left() {
        return (width - span()) / 2;
    }

    private int top() {
        return Math.max(0, (height - 232) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        for (TextFieldWidget field : fields) {
            field.tick();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrices);
        drawCenteredString(matrices, font, title, width / 2, top() + 3, 0xFFFFFF);
        ProfileSummary selected = selection < 0 ? null : profiles.get(selection);
        String keyState = selected != null && selected.hasKey ? I18n.get(ConfigText.HAS_KEY) : I18n.get(ConfigText.NO_KEY);
        font.draw(matrices, I18n.get(ConfigText.ACTIVE) + active + "  |  " + keyState, left(), top() + 20, 0xDCDCDC);
        for (int i = 0; i < fields.length; i++) {
            font.draw(matrices, I18n.get(ConfigText.LABELS[i]), left(), top() + 35 + i * 19, 0xEEEEEE);
            fields[i].render(matrices, mouseX, mouseY, partialTicks);
        }
        font.drawWordWrap(label(status), left(), top() + 210, span(), 0xEEEEEE);
        super.render(matrices, mouseX, mouseY, partialTicks);
    }
}
