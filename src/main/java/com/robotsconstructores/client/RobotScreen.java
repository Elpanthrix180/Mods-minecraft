package com.robotsconstructores.client;

import com.robotsconstructores.entity.JobState;
import com.robotsconstructores.entity.RobotStatus;
import com.robotsconstructores.menu.RobotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Pantalla base dibujada solo con rectángulos de color (no necesita imágenes). */
public abstract class RobotScreen<M extends RobotMenu> extends AbstractContainerScreen<M> {
    protected static final int BG = 0xFF3A2E4A;       // morado oscuro
    protected static final int PANEL = 0xFF55466B;    // morado
    protected static final int BORDER = 0xFFB07CFF;   // morado claro (luz)
    protected static final int SLOT = 0xFF2A2333;
    protected static final int TEXT = 0xFFFFFFFF;
    protected static final int TEXT_DIM = 0xFFC9C2D6;

    protected RobotScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected Button button(String key, int x, int y, int w, int id) {
        return addRenderableWidget(Button.builder(Component.translatable(key), b -> sendButton(id))
                .bounds(leftPos + x, topPos + y, w, 14).build());
    }

    protected void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, BORDER);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF8A8A96);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT);
        }
    }

    protected void drawProgress(GuiGraphics g, int x, int y, int w, int value, int max) {
        g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + 6, SLOT);
        int filled = max <= 0 ? 0 : (int) ((long) w * Math.min(value, max) / max);
        g.fill(leftPos + x, topPos + y, leftPos + x + filled, topPos + y + 6, BORDER);
    }

    protected Component stateText(JobState state) {
        return Component.translatable("gui.robotsconstructores.state", Component.translatable(state.translationKey()));
    }

    protected Component statusText(RobotStatus status) {
        // Los mensajes con datos (números) salen completos en el chat; aquí solo el resumen.
        return Component.translatable(status.translationKey() + ".short");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
