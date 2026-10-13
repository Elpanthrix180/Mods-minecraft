package com.robotsconstructores.entity;

import com.robotsconstructores.building.BlockRole;
import com.robotsconstructores.building.BuildPlan;
import com.robotsconstructores.building.BuildPlans;
import com.robotsconstructores.building.PlannedBlock;
import com.robotsconstructores.menu.BuilderRobotMenu;
import com.robotsconstructores.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Robot constructor MK-I: construye planos (pared, casa...) bloque a bloque usando su inventario. */
public class BuilderRobotEntity extends BaseRobotEntity {
    public static final int BUTTON_START = 0, BUTTON_PAUSE = 1, BUTTON_RESUME = 2, BUTTON_CANCEL = 3, BUTTON_TAKE_ALL = 4,
            BUTTON_PREV_PLAN = 5, BUTTON_NEXT_PLAN = 6;
    public static final int DATA_COUNT = 5;
    private static final int PLACE_INTERVAL = 10; // ticks entre bloque y bloque (medio segundo)

    private BuildPlan plan = BuildPlans.STONE_WALL;
    private final List<PlannedBlock> blocks = new ArrayList<>();
    private BlockPos origin = BlockPos.ZERO;
    private Direction facing = Direction.NORTH;
    private int nextIndex;
    private int cooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> jobState.ordinal();
                case 1 -> nextIndex;
                case 2 -> blocks.isEmpty() ? plan.size() : blocks.size();
                case 3 -> status.ordinal();
                case 4 -> BuildPlans.indexOf(plan);
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
            case BUTTON_PREV_PLAN -> changePlan(-1);
            case BUTTON_NEXT_PLAN -> changePlan(1);
            default -> {
            }
        }
    }

    private void changePlan(int delta) {
        if (jobState.isActive() || jobState == JobState.PAUSED) {
            notifyStatus(RobotStatus.BUSY);
            return;
        }
        plan = BuildPlans.byIndex(BuildPlans.indexOf(plan) + delta);
        status = RobotStatus.READY;
    }

    private Component planName() {
        return Component.translatable(plan.translationKey());
    }

    // ---------- materiales ----------

    private int countRole(BlockRole role) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (role.accepts(s) && s.getItem() instanceof BlockItem) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Primer material del inventario que sirve para este papel (o para su papel de reserva). */
    @Nullable
    private Item findMaterial(BlockRole role) {
        for (BlockRole r = role; r != null; r = r.fallback()) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack s = inventory.getItem(i);
                if (r.accepts(s) && s.getItem() instanceof BlockItem) {
                    return s.getItem();
                }
            }
        }
        return null;
    }

    /** Cuántos bloques obligatorios faltan para terminar desde "fromIndex". 0 = hay suficientes. */
    private int missingMaterials(int fromIndex) {
        Map<BlockRole, Integer> need = new EnumMap<>(BlockRole.class);
        for (int i = fromIndex; i < blocks.size(); i++) {
            need.merge(blocks.get(i).role(), 1, Integer::sum);
        }
        Map<BlockRole, Integer> have = new EnumMap<>(BlockRole.class);
        for (BlockRole r : BlockRole.values()) {
            have.put(r, countRole(r));
        }
        int missing = 0;
        // primero los papeles con reserva (el cristal que falta pasa a pedir estructura)
        for (BlockRole r : BlockRole.values()) {
            int n = need.getOrDefault(r, 0);
            if (n == 0 || r.isOptional()) {
                continue;
            }
            int lack = Math.max(0, n - have.get(r));
            if (lack > 0 && r.fallback() != null) {
                need.merge(r.fallback(), lack, Integer::sum);
                need.put(r, n - lack);
            }
        }
        for (BlockRole r : BlockRole.values()) {
            if (r.isOptional() || r.fallback() != null) {
                continue;
            }
            missing += Math.max(0, need.getOrDefault(r, 0) - have.get(r));
        }
        return missing;
    }

    // ---------- zona ----------

    /** ¿Se puede poner un bloque aquí sin romper nada importante? Solo aire o plantas sustituibles. */
    private boolean canPlaceAt(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos) || isOwnSpace(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        boolean free = current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty());
        return free && isSafeToTouch(current)
                && level.isUnobstructed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), pos, CollisionContext.empty());
    }

    private boolean canPlace(Level level, PlannedBlock b) {
        return canPlaceAt(level, b.pos()) && (b.role() != BlockRole.DOOR || canPlaceAt(level, b.pos().above()));
    }

    // ---------- órdenes ----------

    private void start(Direction playerFacing) {
        if (jobState.isActive()) {
            notifyStatus(RobotStatus.BUSY);
            return;
        }
        if (jobState == JobState.PAUSED) {
            resume();
            return;
        }
        Level level = level();
        List<PlannedBlock> list = plan.blocks(blockPosition(), playerFacing);
        for (PlannedBlock b : list) {
            if (!canPlace(level, b)) {
                notifyStatus(RobotStatus.ZONE_BLOCKED, b.pos().getX(), b.pos().getY(), b.pos().getZ());
                return;
            }
        }
        blocks.clear();
        blocks.addAll(list);
        int missing = missingMaterials(0);
        if (missing > 0) {
            blocks.clear();
            notifyStatus(RobotStatus.NO_MATERIALS, missing, list.size());
            return;
        }
        origin = blockPosition();
        facing = playerFacing;
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
        int missing = missingMaterials(nextIndex);
        if (missing > 0) {
            notifyStatus(RobotStatus.NO_MATERIALS, missing, blocks.size() - nextIndex);
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
        blocks.clear();
        nextIndex = 0;
        setJobState(JobState.IDLE);
        notifyStatus(RobotStatus.CANCELLED);
    }

    // ---------- trabajo ----------

    @Override
    protected void workTick(ServerLevel level) {
        if (jobState != JobState.WORKING) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = PLACE_INTERVAL;
        if (nextIndex >= blocks.size()) {
            finish();
            return;
        }
        PlannedBlock planned = blocks.get(nextIndex);
        BlockPos pos = planned.pos();
        if (!level.isLoaded(pos)) {
            return; // esperamos a que el chunk esté cargado
        }
        Item material = findMaterial(planned.role());
        if (material == null) {
            if (planned.role().isOptional()) {
                nextIndex++; // sin puerta: se deja el hueco
                cooldown = 1;
                return;
            }
            setJobState(JobState.PAUSED);
            notifyStatus(RobotStatus.NO_MATERIALS, Math.max(1, missingMaterials(nextIndex)), blocks.size() - nextIndex);
            return;
        }
        if (!canPlace(level, planned)) {
            setJobState(JobState.PAUSED);
            notifyStatus(RobotStatus.ZONE_BLOCKED, pos.getX(), pos.getY(), pos.getZ());
            return;
        }
        if (!removeOne(material)) {
            return;
        }
        BlockState toPlace = Block.byItem(material).defaultBlockState();
        if (toPlace.getBlock() instanceof DoorBlock) {
            BlockState lower = toPlace.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(pos, lower, Block.UPDATE_ALL);
            level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            toPlace = lower;
        } else {
            level.setBlock(pos, toPlace, Block.UPDATE_ALL);
        }
        level.playSound(null, pos, toPlace.getSoundType(level, pos, this).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        swing(InteractionHand.MAIN_HAND);
        nextIndex++;
        if (nextIndex >= blocks.size()) {
            finish();
        }
    }

    private void finish() {
        setJobState(JobState.FINISHED);
        notifyStatus(RobotStatus.BUILD_DONE, planName(), blocks.size());
        blocks.clear();
        nextIndex = 0;
    }

    // ---------- guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Plan", plan.id());
        tag.putBoolean("HasJob", !blocks.isEmpty());
        tag.putLong("Origin", origin.asLong());
        tag.putInt("Facing", facing.get3DDataValue());
        tag.putInt("NextIndex", nextIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        plan = BuildPlans.get(tag.getString("Plan"));
        origin = BlockPos.of(tag.getLong("Origin"));
        Direction d = Direction.from3DDataValue(tag.getInt("Facing"));
        facing = d.getAxis().isHorizontal() ? d : Direction.NORTH;
        nextIndex = tag.getInt("NextIndex");
        blocks.clear();
        if (tag.getBoolean("HasJob")) {
            blocks.addAll(plan.blocks(origin, facing)); // el plano se recalcula igual que al empezar
        }
        if (blocks.isEmpty() && (jobState.isActive() || jobState == JobState.PAUSED)) {
            setJobState(JobState.IDLE);
        }
    }
}
