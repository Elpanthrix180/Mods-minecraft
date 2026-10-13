package com.robotsconstructores.client;

import com.robotsconstructores.entity.JobState;
import com.robotsconstructores.entity.MinerRobotEntity;
import com.robotsconstructores.menu.MinerRobotMenu;
import com.robotsconstructores.mining.MiningMaterial;
import com.robotsconstructores.mining.MiningMaterials;
import com.robotsconstructores.network.ModNetwork;
import com.robotsconstructores.network.SetTargetAmountPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MinerRobotScreen extends RobotScreen<MinerRobotMenu> {
    private EditBox amountBox;
    private int lastServerAmount = -1;

    public MinerRobotScreen(MinerRobotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 298;
    }

    @Override
    protected void init() {
        super.init();
        // fila material
        button("gui.robotsconstructores.prev", 8, 18, 14, MinerRobotEntity.BUTTON_PREV_MATERIAL);
        button("gui.robotsconstructores.next", 132, 18, 14, MinerRobotEntity.BUTTON_NEXT_MATERIAL);
        // fila cantidad
        amountBox = new EditBox(font, leftPos + 62, topPos + 40, 44, 14, Component.translatable("gui.robotsconstructores.amount"));
        amountBox.setMaxLength(4);
        amountBox.setFilter(s -> s.matches("\\d{0,4}"));
        amountBox.setValue(String.valueOf(menu.getTargetAmount()));
        addRenderableWidget(amountBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.robotsconstructores.apply"), b -> sendAmount())
                .bounds(leftPos + 110, topPos + 40, 30, 14).build());
        // fila radio
        button("gui.robotsconstructores.minus", 62, 58, 14, MinerRobotEntity.BUTTON_RADIUS_DOWN);
        button("gui.robotsconstructores.plus", 110, 58, 14, MinerRobotEntity.BUTTON_RADIUS_UP);
        // órdenes
        button("gui.robotsconstructores.start", 8, 76, 38, MinerRobotEntity.BUTTON_START);
        button("gui.robotsconstructores.pause", 48, 76, 38, MinerRobotEntity.BUTTON_PAUSE);
        button("gui.robotsconstructores.resume", 88, 76, 38, MinerRobotEntity.BUTTON_RESUME);
        button("gui.robotsconstructores.cancel", 128, 76, 40, MinerRobotEntity.BUTTON_CANCEL);
        button("gui.robotsconstructores.take_all", 116, 128, 52, MinerRobotEntity.BUTTON_TAKE_ALL);
    }

    private void sendAmount() {
        String text = amountBox.getValue();
        int amount;
        try {
            amount = text.isEmpty() ? 0 : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            amount = 0;
        }
        ModNetwork.CHANNEL.sendToServer(new SetTargetAmountPacket(menu.containerId, amount));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        amountBox.tick();
        int server = menu.getTargetAmount();
        if (server != lastServerAmount && !amountBox.isFocused()) {
            amountBox.setValue(String.valueOf(server));
            lastServerAmount = server;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            onClose();
            return true;
        }
        if (amountBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { // Enter
                sendAmount();
                return true;
            }
            return amountBox.keyPressed(keyCode, scanCode, modifiers) || amountBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        int max = menu.getTargetAmount();
        drawProgress(g, 8, 94, 160, menu.getObtained(), max);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.sample"), 112, 6, TEXT_DIM, false);

        int index = menu.getMaterialIndex();
        Component name;
        if (index == MiningMaterials.sampleIndex()) {
            MiningMaterial m = MiningMaterials.resolve(index, menu.getSampleStack());
            name = m != null ? Component.translatable("gui.robotsconstructores.sample_block", m.displayName())
                    : Component.translatable("gui.robotsconstructores.sample_empty");
        } else {
            name = MiningMaterials.presets().get(Math.max(0, Math.min(index, MiningMaterials.presets().size() - 1))).displayName();
        }
        g.drawCenteredString(font, name, 77, 21, TEXT);

        g.drawString(font, Component.translatable("gui.robotsconstructores.amount"), 8, 43, TEXT_DIM, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.radius"), 8, 61, TEXT_DIM, false);
        g.drawCenteredString(font, String.valueOf(menu.getRadius()), 93, 61, TEXT);

        int obtained = menu.getObtained();
        int target = menu.getTargetAmount();
        g.drawString(font, Component.translatable("gui.robotsconstructores.obtained", obtained, target, Math.max(0, target - obtained)),
                8, 102, TEXT, false);
        JobState state = menu.getJobState();
        Component stateLine = state == JobState.SCANNING
                ? Component.translatable("gui.robotsconstructores.scanning", menu.getScanPercent())
                : stateText(state);
        g.drawString(font, stateLine, 8, 111, TEXT, false);
        g.drawString(font, statusText(menu.getStatus()), 8, 120, 0xFFFFD866, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.storage"), 8, 133, TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, MinerRobotMenu.PLAYER_TOP - 10, TEXT_DIM, false);
    }
}
