package com.robotsconstructores.entity;

import com.robotsconstructores.building.BuildPlan;
import com.robotsconstructores.building.BuildPlans;
import com.robotsconstructores.menu.BuilderRobotMenu;
import com.robotsconstructores.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/** Robot constructor MK-I: construye planos bloque a bloque usando su inventario. */
public class BuilderRobotEntity extends BaseRobotEntity {
    public static final int BUTTON_START = 0, BUTTON_PAUSE = 1, BUTTON_RESUME = 2, BUTTON_CANCEL = 3, BUTTON_TAKE_ALL = 4;
    public static final int DATA_COUNT = 4;
    private static final int PLACE_INTERVAL = 10; // ticks entre bloque y bloque (medio segundo)

    private BuildPlan plan = BuildPlans.STONE_WALL;
    private final List<BlockPos> positions = new ArrayList<>();
    private int nextIndex;
    private int cooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> jobState.ordinal();
                case 1 -> nextIndex;
                case 2 -> positions.isEmpty() ? 15 : positions.size();
                case 3 -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BuilderRobotEntity(EntityType<? extends BuilderRobotEntity> type, Level level) {
        super(type, level);
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    protected Item getSpawnItem() {
        return ModItems.BUILDER_ROBOT.get();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new BuilderRobotMenu(containerId, playerInventory, this, inventory, data);
    }

    @Override
    public void handleButton(int id, ServerPlayer player) {
        switch (id) {
            case BUTTON_START -> start(player.getDirection());
            case BUTTON_PAUSE -> pause();
            case BUTTON_RESUME -> resume();
            case BUTTON_CANCEL -> cancel();
            case BUTTON_TAKE_ALL -> notifyStatus(giveAllTo(player) ? RobotStatus.TRANSFERRED : RobotStatus.TRANSFER_PARTIAL);
            default -> {
            }
        }
    }

    private Component planName() {
        return Component.translatable(plan.translationKey());
    }

    private int countMaterials() {
        int total = 0;
        for (Item item : plan.acceptedMaterials()) {
            total += countItem(item);
        }
        return total;
    }

    /** ¿Se puede poner un bloque aquí sin romper nada importante? Solo aire o plantas sustituibles. */
    private boolean canPlaceAt(Level level, BlockPos pos, BlockState toPlace) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos) || isOwnSpace(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        boolean free = current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty());
        return free && isSafeToTouch(current) && level.isUnobstructed(toPlace, pos, CollisionContext.empty());
    }

    private void start(Direction facing) {
        if (jobState.isActive()) {
            notifyStatus(RobotStatus.BUSY);
            return;
        }
        if (jobState == JobState.PAUSED) {
            resume();
            return;
        }
        Level level = level();
        List<BlockPos> list = plan.positions(blockPosition(), facing);
        BlockState sample = Block.byItem(plan.acceptedMaterials().get(0)).defaultBlockState();
        for (BlockPos p : list) {
            if (!canPlaceAt(level, p, sample)) {
                notifyStatus(RobotStatus.ZONE_BLOCKED, p.getX(), p.getY(), p.getZ());
                return;
            }
        }
        int have = countMaterials();
        if (have < list.size()) {
            notifyStatus(RobotStatus.NO_MATERIALS, list.size() - have, list.size());
            return;
        }
        positions.clear();
        positions.addAll(list);
        nextIndex = 0;
        cooldown = PLACE_INTERVAL;
        setJobState(JobState.WORKING);
        notifyStatus(RobotStatus.BUILD_STARTED, planName(), list.size());
    }

    private void pause() {
        if (!jobState.isActive()) {
            notifyStatus(RobotStatus.NOT_WORKING);
            return;
        }
        setJobState(JobState.PAUSED);
        notifyStatus(RobotStatus.PAUSED);
    }

    private void resume() {
        if (jobState != JobState.PAUSED) {
            notifyStatus(RobotStatus.NOT_PAUSED);
            return;
        }
        int remaining = positions.size() - nextIndex;
        int have = countMaterials();
        if (have < remaining) {
            notifyStatus(RobotStatus.NO_MATERIALS, remaining - have, remaining);
            return;
        }
        setJobState(JobState.WORKING);
        notifyStatus(RobotStatus.RESUMED);
    }

    private void cancel() {
        if (jobState == JobState.IDLE || jobState == JobState.FINISHED) {
            notifyStatus(RobotStatus.NOT_WORKING);
            return;
        }
        positions.clear();
        nextIndex = 0;
        setJobState(JobState.IDLE);
        notifyStatus(RobotStatus.CANCELLED);
    }

    @Override
    protected void workTick(ServerLevel level) {
        if (jobState != JobState.WORKING) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = PLACE_INTERVAL;
        if (nextIndex >= positions.size()) {
            finish();
            return;
        }
        BlockPos pos = positions.get(nextIndex);
        if (!level.isLoaded(pos)) {
            return; // esperamos a que el chunk esté cargado
        }
        Item material = null;
        for (Item item : plan.acceptedMaterials()) {
            if (countItem(item) > 0) {
                material = item;
                break;
            }
        }
        if (material == null) {
            setJobState(JobState.PAUSED);
            notifyStatus(RobotStatus.NO_MATERIALS, positions.size() - nextIndex, positions.size() - nextIndex);
            return;
        }
        BlockState toPlace = Block.byItem(material).defaultBlockState();
        if (!canPlaceAt(level, pos, toPlace)) {
            setJobState(JobState.PAUSED);
            notifyStatus(RobotStatus.ZONE_BLOCKED, pos.getX(), pos.getY(), pos.getZ());
            return;
        }
        if (!removeOne(material)) {
            return;
        }
        level.setBlock(pos, toPlace, Block.UPDATE_ALL);
        level.playSound(null, pos, toPlace.getSoundType(level, pos, this).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        nextIndex++;
        if (nextIndex >= positions.size()) {
            finish();
        }
    }

    private void finish() {
        setJobState(JobState.FINISHED);
        notifyStatus(RobotStatus.BUILD_DONE, planName(), positions.size());
        positions.clear();
        nextIndex = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Plan", plan.id());
        long[] arr = new long[positions.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = positions.get(i).asLong();
        }
        tag.putLongArray("Positions", arr);
        tag.putInt("NextIndex", nextIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        plan = BuildPlans.get(tag.getString("Plan"));
        positions.clear();
        for (long l : tag.getLongArray("Positions")) {
            positions.add(BlockPos.of(l));
        }
        nextIndex = tag.getInt("NextIndex");
        if (positions.isEmpty() && jobState.isActive()) {
            setJobState(JobState.IDLE);
        }
    }
}
