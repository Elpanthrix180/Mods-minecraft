package com.robotsconstructores.menu;

import com.robotsconstructores.entity.BaseRobotEntity;
import com.robotsconstructores.entity.BuilderRobotEntity;
import com.robotsconstructores.entity.JobState;
import com.robotsconstructores.entity.RobotStatus;
import com.robotsconstructores.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class BuilderRobotMenu extends RobotMenu {
    public static final int STORAGE_TOP = 122;
    public static final int SAMPLE_X = 158, SAMPLE_Y = 41;
    public static final int PLAYER_TOP = 198;
    private final ContainerData data;

    public BuilderRobotMenu(int containerId, Inventory playerInventory, @Nullable BaseRobotEntity robot,
                            Container storage, Container sample, ContainerData data) {
        super(ModMenus.BUILDER_ROBOT.get(), containerId, robot);
        checkContainerDataCount(data, BuilderRobotEntity.DATA_COUNT);
        this.data = data;
        addStorageSlots(storage, 8, STORAGE_TOP);
        // muestra del material de los caminos
        addSlot(new Slot(sample, 0, SAMPLE_X, SAMPLE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BlockItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerSlots(playerInventory, 8, PLAYER_TOP);
        addDataSlots(data);
    }

    public static BuilderRobotMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        Entity e = playerInventory.player.level().getEntity(buf.readVarInt());
        BaseRobotEntity robot = e instanceof BuilderRobotEntity r ? r : null;
        return new BuilderRobotMenu(containerId, playerInventory, robot,
                new SimpleContainer(BaseRobotEntity.INVENTORY_SIZE), new SimpleContainer(1),
                new SimpleContainerData(BuilderRobotEntity.DATA_COUNT));
    }

    public JobState getJobState() {
        return JobState.byOrdinal(data.get(0));
    }

    public int getPlaced() {
        return data.get(1);
    }

    public int getTotal() {
        return data.get(2);
    }

    public RobotStatus getStatus() {
        return RobotStatus.byOrdinal(data.get(3));
    }

    public int getPlanIndex() {
        return data.get(4);
    }

    public int getPathLength() {
        return data.get(5);
    }

    public int getPathWidth() {
        return data.get(6);
    }
}
