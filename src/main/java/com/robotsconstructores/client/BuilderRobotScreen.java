package com.robotsconstructores.client;

import com.robotsconstructores.building.BlockRole;
import com.robotsconstructores.building.BuildPlan;
import com.robotsconstructores.building.BuildPlans;
import com.robotsconstructores.building.PathPlan;
import net.minecraft.client.gui.components.Button;
import com.robotsconstructores.entity.BuilderRobotEntity;
import com.robotsconstructores.menu.BuilderRobotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import java.util.Map;

public class BuilderRobotScreen extends RobotScreen<BuilderRobotMenu> {
    public BuilderRobotScreen(BuilderRobotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 280;
    }

    @Override
    protected void init() {
        super.init();
        button("gui.robotsconstructores.prev", 8, 16, 14, BuilderRobotEntity.BUTTON_PREV_PLAN);
        button("gui.robotsconstructores.next", 154, 16, 14, BuilderRobotEntity.BUTTON_NEXT_PLAN);
        lengthDown = button("gui.robotsconstructores.minus", 40, 42, 12, BuilderRobotEntity.BUTTON_LENGTH_DOWN);
        lengthUp = button("gui.robotsconstructores.plus", 70, 42, 12, BuilderRobotEntity.BUTTON_LENGTH_UP);
        widthDown = button("gui.robotsconstructores.minus", 116, 42, 12, BuilderRobotEntity.BUTTON_WIDTH_DOWN);
        widthUp = button("gui.robotsconstructores.plus", 140, 42, 12, BuilderRobotEntity.BUTTON_WIDTH_UP);
        button("gui.robotsconstructores.start", 8, 60, 38, BuilderRobotEntity.BUTTON_START);
        button("gui.robotsconstructores.pause", 48, 60, 38, BuilderRobotEntity.BUTTON_PAUSE);
        button("gui.robotsconstructores.resume", 88, 60, 38, BuilderRobotEntity.BUTTON_RESUME);
        button("gui.robotsconstructores.cancel", 128, 60, 40, BuilderRobotEntity.BUTTON_CANCEL);
        button("gui.robotsconstructores.take_all", 116, 105, 52, BuilderRobotEntity.BUTTON_TAKE_ALL);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        drawProgress(g, 8, 78, 160, menu.getPlaced(), menu.getTotal());
    }

    private Button lengthDown, lengthUp, widthDown, widthUp;

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean path = BuildPlans.byIndex(menu.getPlanIndex()) instanceof PathPlan;
        lengthDown.visible = lengthUp.visible = widthDown.visible = widthUp.visible = path;
    }

    private Component requirementsText(BuildPlan plan) {
        if (plan instanceof PathPlan path) {
            return path.isDirtPath()
                    ? Component.translatable("gui.robotsconstructores.needs_dirt_path")
                    : Component.translatable("gui.robotsconstructores.needs_path", menu.getPathLength() * menu.getPathWidth());
        }
        MutableComponent text = Component.translatable("gui.robotsconstructores.needs");
        boolean first = true;
        for (Map.Entry<BlockRole, Integer> e : plan.requirements().entrySet()) {
            text.append(first ? " " : ", ").append(String.valueOf(e.getValue())).append(" ")
                    .append(Component.translatable(e.getKey().translationKey()));
            first = false;
        }
        return text;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        BuildPlan plan = BuildPlans.byIndex(menu.getPlanIndex());
        g.drawString(font, title, 8, 6, TEXT, false);
        g.drawCenteredString(font, Component.translatable(plan.translationKey()), 88, 19, TEXT);
        g.pose().pushPose();
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, requirementsText(plan), (int) (8 / 0.75F), (int) (33 / 0.75F), TEXT_DIM, false);
        g.pose().popPose();
        if (plan instanceof PathPlan) {
            g.drawString(font, Component.translatable("gui.robotsconstructores.length"), 8, 45, TEXT_DIM, false);
            g.drawCenteredString(font, String.valueOf(menu.getPathLength()), 61, 45, TEXT);
            g.drawString(font, Component.translatable("gui.robotsconstructores.width"), 86, 45, TEXT_DIM, false);
            g.drawCenteredString(font, String.valueOf(menu.getPathWidth()), 134, 45, TEXT);
        }
        g.drawString(font, Component.translatable("gui.robotsconstructores.placed", menu.getPlaced(), menu.getTotal()), 8, 87, TEXT, false);
        g.drawString(font, stateText(menu.getJobState()), 100, 87, TEXT, false);
        g.drawString(font, statusText(menu.getStatus()), 8, 96, 0xFFFFD866, false);
        g.drawString(font, Component.translatable("gui.robotsconstructores.storage"), 8, 110, TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, BuilderRobotMenu.PLAYER_TOP - 10, TEXT_DIM, false);
    }
}
