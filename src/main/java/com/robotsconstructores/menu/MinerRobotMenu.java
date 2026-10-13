package com.robotsconstructores.menu;

import com.robotsconstructores.entity.BaseRobotEntity;
import com.robotsconstructores.entity.JobState;
import com.robotsconstructores.entity.MinerRobotEntity;
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

public class MinerRobotMenu extends RobotMenu {
    public static final int SAMPLE_X = 152, SAMPLE_Y = 18;
    public static final int STORAGE_TOP = 144;
    public static final int PLAYER_TOP = 216;
    private final ContainerData data;
    private final Slot sampleSlot;

    public MinerRobotMenu(int containerId, Inventory playerInventory, @Nullable BaseRobotEntity robot,
                          Container storage, Container sample, ContainerData data) {
        super(ModMenus.MINER_ROBOT.get(), containerId, robot);
        checkContainerDataCount(data, MinerRobotEntity.DATA_COUNT);
        this.data = data;
        addStorageSlots(storage, 8, STORAGE_TOP);
        // Ranura de muestra: el jugador pone aquí un bloque para minar ese bloque concreto.
        this.sampleSlot = addSlot(new Slot(sample, 0, SAMPLE_X, SAMPLE_Y) {
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

    public static MinerRobotMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        Entity e = playerInventory.player.level().getEntity(buf.readVarInt());
        BaseRobotEntity robot = e instanceof MinerRobotEntity r ? r : null;
        return new MinerRobotMenu(containerId, playerInventory, robot, new SimpleContainer(BaseRobotEntity.INVENTORY_SIZE),
                new SimpleContainer(1), new SimpleContainerData(MinerRobotEntity.DATA_COUNT));
    }

    public ItemStack getSampleStack() {
        return sampleSlot.getItem();
    }

    public JobState getJobState() {
        return JobState.byOrdinal(data.get(0));
    }

    public int getObtained() {
        return data.get(1);
    }

    public int getTargetAmount() {
        return data.get(2);
    }

    public int getRadius() {
        return data.get(3);
    }

    public int getMaterialIndex() {
        return data.get(4);
    }

    public RobotStatus getStatus() {
        return RobotStatus.byOrdinal(data.get(5));
    }

    public int getTargetsLeft() {
        return data.get(6);
    }

    public boolean isUsingChest() {
        return data.get(8) != 0;
    }

    public int getScanPercent() {
        return data.get(7);
    }
}
