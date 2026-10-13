package com.robotsconstructores.menu;

import com.robotsconstructores.entity.BaseRobotEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Parte común de las interfaces de los robots: ranuras del robot + inventario del jugador. */
public abstract class RobotMenu extends AbstractContainerMenu {
    @Nullable
    protected final BaseRobotEntity robot;
    protected int storageStart;
    protected int storageEnd;
    protected int playerStart;

    protected RobotMenu(MenuType<?> type, int containerId, @Nullable BaseRobotEntity robot) {
        super(type, containerId);
        this.robot = robot;
    }

    @Nullable
    public BaseRobotEntity getRobot() {
        return robot;
    }

    protected void addStorageSlots(Container storage, int left, int top) {
        storageStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(storage, col + row * 9, left + col * 18, top + row * 18));
            }
        }
        storageEnd = slots.size();
    }

    protected void addPlayerSlots(Inventory playerInventory, int left, int top) {
        playerStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, left + col * 18, top + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, storageStart, storageEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return robot == null || (robot.isAlive() && player.distanceToSqr(robot) <= 64.0D);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (robot != null && player instanceof ServerPlayer serverPlayer) {
            robot.handleButton(id, serverPlayer);
        }
        return true;
    }
}
