package com.robotsconstructores.entity;

import com.robotsconstructores.menu.MinerRobotMenu;
import com.robotsconstructores.mining.MiningMaterial;
import com.robotsconstructores.mining.MiningMaterials;
import com.robotsconstructores.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Robot minero MK-I.
 * Versión 1: se queda quieto en su puesto y extrae a distancia (con su "brazo largo")
 * los bloques reales que hay dentro del radio elegido. NO se desplaza por túneles todavía.
 */
public class MinerRobotEntity extends BaseRobotEntity {
    public static final int BUTTON_START = 0, BUTTON_PAUSE = 1, BUTTON_RESUME = 2, BUTTON_CANCEL = 3,
            BUTTON_PREV_MATERIAL = 4, BUTTON_NEXT_MATERIAL = 5, BUTTON_RADIUS_DOWN = 6, BUTTON_RADIUS_UP = 7, BUTTON_TAKE_ALL = 8;
    public static final int DATA_COUNT = 8;
    public static final int MIN_RADIUS = 2, MAX_RADIUS = 16, RADIUS_STEP = 2;
    public static final int MIN_AMOUNT = 1, MAX_AMOUNT = INVENTORY_SIZE * 64;
    private static final int SCAN_PER_TICK = 2048;   // bloques revisados por tick (límite de rendimiento)
    private static final int MINE_INTERVAL = 10;     // ticks entre bloque y bloque
    private static final int MAX_SKIPS_PER_TICK = 16;

    private final SimpleContainer sample = new SimpleContainer(1);
    private int materialIndex = MiningMaterials.defaultIndex();
    private int targetAmount = 32;
    private int radius = 8;

    // trabajo en curso
    private final List<Block> activeBlocks = new ArrayList<>();
    @Nullable
    private Item activeCounted;
    private int obtained;
    private BlockPos scanCenter = BlockPos.ZERO;
    private int scanRadius;
    private int scanCursor;
    private final Deque<BlockPos> targets = new ArrayDeque<>();
    private int cooldown;
    @Nullable
    private ItemStack tool;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> jobState.ordinal();
                case 1 -> Math.min(obtained, Short.MAX_VALUE);
                case 2 -> targetAmount;
                case 3 -> radius;
                case 4 -> materialIndex;
                case 5 -> status.ordinal();
                case 6 -> Math.min(targets.size(), Short.MAX_VALUE);
                case 7 -> scanProgressPercent();
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

    public MinerRobotEntity(EntityType<? extends MinerRobotEntity> type, Level level) {
        super(type, level);
    }

    public ContainerData getData() {
        return data;
    }

    public SimpleContainer getSample() {
        return sample;
    }

    private ItemStack tool() {
        if (tool == null) {
            tool = new ItemStack(Items.DIAMOND_PICKAXE); // herramienta interna: sin encantamientos
        }
        return tool;
    }

    @Override
    protected Item getSpawnItem() {
        return ModItems.MINER_ROBOT.get();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new MinerRobotMenu(containerId, playerInventory, this, inventory, sample, data);
    }

    @Override
    protected void dropExtraContents() {
        Containers.dropContents(level(), this, sample);
    }

    private int scanTotal() {
        int side = scanRadius * 2 + 1;
        return side * side * side;
    }

    private int scanProgressPercent() {
        if (jobState == JobState.IDLE || scanRadius == 0) {
            return 0;
        }
        return Math.min(100, (int) (100L * scanCursor / scanTotal()));
    }

    // ---------- órdenes ----------

    @Override
    public void handleButton(int id, ServerPlayer player) {
        switch (id) {
            case BUTTON_START -> start();
            case BUTTON_PAUSE -> pause();
            case BUTTON_RESUME -> resume();
            case BUTTON_CANCEL -> cancel();
            case BUTTON_PREV_MATERIAL -> changeMaterial(-1);
            case BUTTON_NEXT_MATERIAL -> changeMaterial(1);
            case BUTTON_RADIUS_DOWN -> changeRadius(-RADIUS_STEP);
            case BUTTON_RADIUS_UP -> changeRadius(RADIUS_STEP);
            case BUTTON_TAKE_ALL -> notifyStatus(giveAllTo(player) ? RobotStatus.TRANSFERRED : RobotStatus.TRANSFER_PARTIAL);
            default -> {
            }
        }
    }

    private boolean canConfigure() {
        if (jobState.isActive() || jobState == JobState.PAUSED) {
            notifyStatus(RobotStatus.BUSY);
            return false;
        }
        return true;
    }

    private void changeMaterial(int delta) {
        if (!canConfigure()) {
            return;
        }
        int n = MiningMaterials.optionCount();
        materialIndex = ((materialIndex + delta) % n + n) % n;
        status = RobotStatus.READY;
    }

    private void changeRadius(int delta) {
        if (!canConfigure()) {
            return;
        }
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius + delta));
        status = RobotStatus.READY;
    }

    /** Llamado desde el paquete de red. El valor se valida aquí, en el servidor. */
    public void setTargetAmount(int amount) {
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            notifyStatus(RobotStatus.INVALID_AMOUNT, MIN_AMOUNT, MAX_AMOUNT);
            return;
        }
        if (!canConfigure()) {
            return;
        }
        targetAmount = amount;
        notifyStatus(RobotStatus.CONFIG_SET, amount);
    }

    private Component activeName() {
        if (activeBlocks.isEmpty()) {
            return Component.literal("?");
        }
        return activeCounted != null ? activeCounted.getDescription() : activeBlocks.get(0).getName();
    }

    private void start() {
        if (jobState.isActive()) {
            notifyStatus(RobotStatus.BUSY);
            return;
        }
        if (jobState == JobState.PAUSED) {
            resume();
            return;
        }
        MiningMaterial material = MiningMaterials.resolve(materialIndex, sample.getItem(0));
        if (material == null) {
            notifyStatus(RobotStatus.NO_SAMPLE);
            return;
        }
        activeBlocks.clear();
        activeBlocks.addAll(material.blocks());
        activeCounted = material.countedItem();
        obtained = 0;
        targets.clear();
        scanCenter = blockPosition();
        scanRadius = radius;
        scanCursor = 0;
        setJobState(JobState.SCANNING);
        notifyStatus(RobotStatus.SCAN_STARTED, activeName(), targetAmount, radius);
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
        setJobState(scanCursor < scanTotal() ? JobState.SCANNING : JobState.WORKING);
        notifyStatus(RobotStatus.RESUMED);
    }

    private void cancel() {
        if (jobState == JobState.IDLE || jobState == JobState.FINISHED) {
            notifyStatus(RobotStatus.NOT_WORKING);
            return;
        }
        targets.clear();
        setJobState(JobState.IDLE);
        notifyStatus(RobotStatus.CANCELLED);
    }

    private boolean matches(BlockState state) {
        for (Block b : activeBlocks) {
            if (state.is(b)) {
                return true;
            }
        }
        return false;
    }

    // ---------- trabajo por tick ----------

    @Override
    protected void workTick(ServerLevel level) {
        if (jobState == JobState.SCANNING) {
            scanTick(level);
        } else if (jobState == JobState.WORKING) {
            if (--cooldown > 0) {
                return;
            }
            cooldown = MINE_INTERVAL;
            mineTick(level);
        }
    }

    /** Revisa como máximo SCAN_PER_TICK posiciones por tick para no congelar el juego. */
    private void scanTick(ServerLevel level) {
        int side = scanRadius * 2 + 1;
        int total = scanTotal();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SCAN_PER_TICK && scanCursor < total; i++) {
            int idx = scanCursor++;
            int dx = idx % side - scanRadius;
            int dz = (idx / side) % side - scanRadius;
            int dy = idx / (side * side) - scanRadius;
            pos.set(scanCenter.getX() + dx, scanCenter.getY() + dy, scanCenter.getZ() + dz);
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos) || isOwnSpace(pos)) {
                continue;
            }
            if (matches(level.getBlockState(pos))) {
                targets.add(pos.immutable());
            }
        }
        if (scanCursor >= total) {
            if (targets.isEmpty()) {
                setJobState(JobState.FINISHED);
                notifyStatus(RobotStatus.NO_TARGETS, activeName(), scanRadius);
                return;
            }
            // primero los más cercanos al robot
            List<BlockPos> sorted = new ArrayList<>(targets);
            sorted.sort(Comparator.comparingDouble(p -> p.distSqr(scanCenter)));
            targets.clear();
            targets.addAll(sorted);
            cooldown = MINE_INTERVAL;
            setJobState(JobState.WORKING);
            notifyStatus(RobotStatus.TARGETS_FOUND, targets.size(), activeName());
        }
    }

    private void mineTick(ServerLevel level) {
        if (obtained >= targetAmount) {
            finishDone();
            return;
        }
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        for (int attempt = 0; attempt < MAX_SKIPS_PER_TICK; attempt++) {
            BlockPos pos = targets.peekFirst();
            if (pos == null) {
                break;
            }
            if (!level.isLoaded(pos)) {
                targets.pollFirst();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // Solo el material elegido; nada de bloques con contenido (cofres, etc.) ni irrompibles.
            if (!matches(state) || !isSafeToTouch(state) || state.getDestroySpeed(level, pos) < 0) {
                targets.pollFirst();
                continue;
            }
            // Respeta protecciones de otros mods (zonas protegidas).
            if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, fakePlayer))) {
                targets.pollFirst();
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, this, tool());
            if (!canFitAll(drops)) {
                setJobState(JobState.PAUSED);
                notifyStatus(RobotStatus.INVENTORY_FULL, obtained, targetAmount);
                return;
            }
            targets.pollFirst();
            state.spawnAfterBreak(level, pos, tool(), true);
            level.destroyBlock(pos, false, this);
            for (ItemStack drop : drops) {
                if (activeCounted == null || drop.is(activeCounted)) {
                    obtained += drop.getCount();
                }
                ItemStack rest = inventory.addItem(drop);
                if (!rest.isEmpty()) {
                    // No debería pasar (ya se comprobó el espacio), pero nunca perdemos objetos.
                    Containers.dropItemStack(level, getX(), getY() + 0.5D, getZ(), rest);
                }
            }
            swing(InteractionHand.MAIN_HAND);
            break;
        }
        if (obtained >= targetAmount) {
            finishDone();
        } else if (targets.isEmpty()) {
            setJobState(JobState.FINISHED);
            notifyStatus(RobotStatus.NOT_ENOUGH_FOUND, obtained, targetAmount, activeName());
        }
    }

    private void finishDone() {
        targets.clear();
        setJobState(JobState.FINISHED);
        notifyStatus(RobotStatus.MINE_DONE, obtained, activeName(), targetAmount);
    }

    // ---------- guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Sample", saveContainer(sample));
        tag.putInt("MaterialIndex", materialIndex);
        tag.putInt("TargetAmount", targetAmount);
        tag.putInt("Radius", radius);
        tag.putInt("Obtained", obtained);
        ListTag blocks = new ListTag();
        for (Block b : activeBlocks) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(b);
            if (key != null) {
                blocks.add(StringTag.valueOf(key.toString()));
            }
        }
        tag.put("ActiveBlocks", blocks);
        if (activeCounted != null) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(activeCounted);
            if (key != null) {
                tag.putString("ActiveCounted", key.toString());
            }
        }
        tag.putLong("ScanCenter", scanCenter.asLong());
        tag.putInt("ScanRadius", scanRadius);
        tag.putInt("ScanCursor", scanCursor);
        long[] arr = new long[targets.size()];
        int i = 0;
        for (BlockPos p : targets) {
            arr[i++] = p.asLong();
        }
        tag.putLongArray("Targets", arr);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadContainer(sample, tag.getList("Sample", Tag.TAG_COMPOUND));
        if (tag.contains("MaterialIndex")) {
            materialIndex = Math.floorMod(tag.getInt("MaterialIndex"), MiningMaterials.optionCount());
            targetAmount = Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, tag.getInt("TargetAmount")));
            radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, tag.getInt("Radius")));
        }
        obtained = tag.getInt("Obtained");
        activeBlocks.clear();
        ListTag blocks = tag.getList("ActiveBlocks", Tag.TAG_STRING);
        for (int i = 0; i < blocks.size(); i++) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blocks.getString(i)));
            if (b != null) {
                activeBlocks.add(b);
            }
        }
        activeCounted = tag.contains("ActiveCounted")
                ? ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("ActiveCounted"))) : null;
        scanCenter = BlockPos.of(tag.getLong("ScanCenter"));
        scanRadius = Math.max(0, Math.min(MAX_RADIUS, tag.getInt("ScanRadius")));
        scanCursor = tag.getInt("ScanCursor");
        targets.clear();
        for (long l : tag.getLongArray("Targets")) {
            targets.add(BlockPos.of(l));
        }
        if ((jobState.isActive() || jobState == JobState.PAUSED) && activeBlocks.isEmpty()) {
            setJobState(JobState.IDLE);
        }
    }
}
