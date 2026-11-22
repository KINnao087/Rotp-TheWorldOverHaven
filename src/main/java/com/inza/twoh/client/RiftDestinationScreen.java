package com.inza.twoh.client;

import com.inza.twoh.network.RiftDestinations;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;
import java.util.UUID;

/** EN: Clicking a dimension commits it; cancelling leaves the previous choice unchanged.
 * 中文：点击维度即提交；取消或按 Esc 保留原目的地。 */
public final class RiftDestinationScreen extends Screen {
    private final UUID token;
    private final List<ResourceLocation> worlds;
    private final ResourceLocation current;
    private boolean submitted;
    private int page;
    private int rows;

    private RiftDestinationScreen(UUID token, List<ResourceLocation> worlds, ResourceLocation current) {
        super(new TranslationTextComponent("rift.twohaddon.select_title"));
        this.token = token;
        this.worlds = worlds;
        this.current = current;
    }

    public static void open(UUID token, List<ResourceLocation> worlds, ResourceLocation current) {
        Minecraft.getInstance().setScreen(new RiftDestinationScreen(token, worlds, current));
    }

    @Override
    protected void init() {
        rows = Math.max(1, Math.min(9, (height - 100) / 22));
        page = Math.min(page, Math.max(0, (worlds.size() - 1) / rows));
        refresh();
    }

    private void refresh() {
        buttons.clear();
        children.clear();
        int w = Math.min(width - 16, 300);
        int x = (width - w) / 2;
        for (int row = 0; row < rows && page * rows + row < worlds.size(); row++) {
            ResourceLocation id = worlds.get(page * rows + row);
            ITextComponent label = dimensionLabel(id);
            addButton(new Button(x, 32 + 22 * row, w, 20, label, b -> select(id)));
        }
        int y = 36 + 22 * rows;
        addButton(new Button(x, y, 30, 20, new StringTextComponent("<"), b -> turn(-1))).active = page > 0;
        addButton(new Button(x + w - 30, y, 30, 20, new StringTextComponent(">"), b -> turn(1))).active =
                (page + 1) * rows < worlds.size();
        addButton(new Button(x + 34, y, w - 68, 20, new TranslationTextComponent("gui.cancel"), b -> onClose()));
    }

    private ITextComponent dimensionLabel(ResourceLocation id) {
        String key = null;
        if (World.OVERWORLD.location().equals(id)) {
            key = "rift.twohaddon.world.overworld";
        } else if (World.NETHER.location().equals(id)) {
            key = "rift.twohaddon.world.nether";
        } else if (World.END.location().equals(id)) {
            key = "rift.twohaddon.world.end";
        }
        StringTextComponent prefix = new StringTextComponent(id.equals(current) ? "✓ " : "");
        return prefix.append(key == null ? new StringTextComponent(id.toString())
                : new TranslationTextComponent(key, id.toString()));
    }

    private void select(ResourceLocation id) {
        if (!submitted) {
            submitted = true;
            RiftDestinations.choose(token, id);
            onClose();
        }
    }

    private void turn(int direction) {
        page = Math.max(0, Math.min(Math.max(0, (worlds.size() - 1) / rows), page + direction));
        refresh();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (delta != 0) {
            turn(delta > 0 ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack matrix, int mouseX, int mouseY, float partial) {
        renderBackground(matrix);
        drawCenteredString(matrix, font, title, width / 2, 12, 0xFFFFFF);
        super.render(matrix, mouseX, mouseY, partial);
    }
}
