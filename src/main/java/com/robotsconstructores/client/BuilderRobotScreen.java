package com.robotsconstructores.client;

import com.robotsconstructores.entity.BuilderRobotEntity;
import com.robotsconstructores.menu.BuilderRobotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BuilderRobotScreen extends RobotScreen<BuilderRobotMenu> {
    public BuilderRobotScreen(BuilderRobotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 252;
    }

    @Override
    protected void init() {
        super.init();
        button("gui.robotsconstructores.start", 8, 30, 38, BuilderRobotEntity.BUTTON_START);
        button("gui.robotsconstructores.pause", 48, 30, 38, BuilderRobotEntity.BUTTON_PAUSE);
        button("gui.robotsconstructores.resume", 88, 30, 38, BuilderRobotEntity.BUTTON_RESUME);
        button("gui.robotsconstructores.cancel", 128, 30, 40, BuilderRobotEntity.BUTTON_CANCEL);
        button("gui.robotsconstructores.take_all", 116, 77, 52, BuilderRobotEntity.BUTTON_TAKE_ALL);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        drawProgress(g, 8, 48, 160, menu.getPlaced(), menu.getTotal());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.plan",
                Component.translatable("plan.robotsconstructores.stone_wall_5x3")), 8, 18, TEXT_DIM, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.placed", menu.getPlaced(), menu.getTotal()), 8, 57, TEXT, false);
        g.drawString(font, stateText(menu.getJobState()), 100, 57, TEXT, false);
        g.drawString(font, statusText(menu.getStatus()), 8, 66, 0xFFFFD866, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.storage"), 8, 82, TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, BuilderRobotMenu.PLAYER_TOP - 10, TEXT_DIM, false);
    }
}
