package com.robotsconstructores.entity;

import com.robotsconstructores.menu.MinerRobotMenu;
import com.robotsconstructores.mining.MiningMaterial;
import com.robotsconstructores.mining.MiningMaterials;
import com.robotsconstructores.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Robot minero MK-I.
 * Versión 1: se queda quieto en su puesto y extrae a distancia (con su "brazo largo")
 * los bloques reales que hay dentro del radio elegido. NO se desplaza por túneles todavía.
 */
public class MinerRobotEntity extends BaseRobotEntity {
    public static final int BUTTON_START = 0, BUTTON_PAUSE = 1, BUTTON_RESUME = 2, BUTTON_CANCEL = 3,
            BUTTON_PREV_MATERIAL = 4, BUTTON_NEXT_MATERIAL = 5, BUTTON_RADIUS_DOWN = 6, BUTTON_RADIUS_UP = 7, BUTTON_TAKE_ALL = 8, BUTTON_TOGGLE_CHEST = 9, BUTTON_TOGGLE_MODE = 10;
    public static final int DATA_COUNT = 10;
    public static final int STRIP_LENGTH_PER_RADIUS = 4; // largo del túnel = radio x 4
    public static final int BRANCH_LENGTH = 8, BRANCH_EVERY = 3, TORCH_EVERY = 8;
    public static final int CHEST_RANGE = 4; // distancia máxima al cofre desde el punto de trabajo
    public static final int MIN_RADIUS = 2, MAX_RADIUS = 16, RADIUS_STEP = 2;
    public static final int MIN_AMOUNT = 1, MAX_AMOUNT = INVENTORY_SIZE * 64;
    private static final int SCAN_PER_TICK = 2048;   // bloques revisados por tick (límite de rendimiento)
    private static final int MINE_INTERVAL = 10;     // ticks entre bloque y bloque
    private static final List<Item> FILLERS = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT,
            Items.NETHERRACK, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.BLACKSTONE, Items.STONE, Items.DEEPSLATE);
    private static final double REACH = 3.5D;            // distancia a la que puede picar
    private static final int STUCK_LIMIT = 60;           // ticks sin acercarse = atascado
    private static final int MAX_TICKS_PER_TARGET = 2400; // 2 minutos por bloque como máximo
    private static final int RETURN_LIMIT = 3600;         // 3 minutos para volver

    private final SimpleContainer sample = new SimpleContainer(1);
    private int materialIndex = MiningMaterials.defaultIndex();
    private int targetAmount = 32;
    private int radius = 8;
    private boolean useChest;
    // minería en tiras
    private boolean stripMode;
    private final List<BlockPos> stripCells = new ArrayList<>();
    private final List<Integer> stripBranch = new ArrayList<>(); // 0 = túnel principal, n = galería n
    private int stripIndex;
    private final List<BlockPos> pendingOres = new ArrayList<>();

    // trabajo en curso
    private final List<Block> activeBlocks = new ArrayList<>();
    @Nullable
    private Item activeCounted;
    private int obtained;
    private BlockPos scanCenter = BlockPos.ZERO;
    private int scanRadius;
    private int scanCursor;
    private final List<BlockPos> targets = new ArrayList<>();
    @Nullable
    private BlockPos current;
    private boolean tunneling;
    @Nullable
    private BlockPos stepDest;
    private int targetTicks;
    private int stuckTicks;
    private double bestDist = Double.MAX_VALUE;
    private JobState afterReturn = JobState.FINISHED;
    private int returnTicks;
    @Nullable
    private BlockPos pillarFrom;
    private int pillarTicks;
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
                case 8 -> useChest ? 1 : 0;
                case 9 -> stripMode ? 1 : 0;
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
            case BUTTON_START -> start(player.getDirection());
            case BUTTON_TOGGLE_MODE -> {
                if (canConfigure()) {
                    stripMode = !stripMode;
                    status = RobotStatus.READY;
                }
            }
            case BUTTON_PAUSE -> pause();
            case BUTTON_RESUME -> resume();
            case BUTTON_CANCEL -> cancel();
            case BUTTON_PREV_MATERIAL -> changeMaterial(-1);
            case BUTTON_NEXT_MATERIAL -> changeMaterial(1);
            case BUTTON_RADIUS_DOWN -> changeRadius(-RADIUS_STEP);
            case BUTTON_RADIUS_UP -> changeRadius(RADIUS_STEP);
            case BUTTON_TOGGLE_CHEST -> {
                useChest = !useChest;
                status = RobotStatus.READY;
            }
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

    private void start(Direction playerFacing) {
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
        current = null;
        resetTracking();
        scanCenter = blockPosition();
        if (stripMode) {
            planStrips(playerFacing);
            cooldown = MINE_INTERVAL;
            setJobState(JobState.WORKING);
            notifyStatus(RobotStatus.STRIP_STARTED, activeName(), targetAmount, radius * STRIP_LENGTH_PER_RADIUS);
            return;
        }
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
        getNavigation().stop();
        setJobState(JobState.PAUSED);
        notifyStatus(RobotStatus.PAUSED);
    }

    private void resume() {
        if (jobState != JobState.PAUSED) {
            notifyStatus(RobotStatus.NOT_PAUSED);
            return;
        }
        setJobState(!stripMode && scanCursor < scanTotal() ? JobState.SCANNING : JobState.WORKING);
        notifyStatus(RobotStatus.RESUMED);
    }

    private void cancel() {
        if (jobState == JobState.IDLE || jobState == JobState.FINISHED) {
            notifyStatus(RobotStatus.NOT_WORKING);
            return;
        }
        targets.clear();
        current = null;
        resetTracking();
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
        if (cooldown > 0) {
            cooldown--;
        }
        switch (jobState) {
            case SCANNING -> scanTick(level);
            case WORKING -> workingTick(level);
            case RETURNING -> returningTick();
            default -> {
            }
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
            cooldown = MINE_INTERVAL;
            setJobState(JobState.WORKING);
            notifyStatus(RobotStatus.TARGETS_FOUND, targets.size(), activeName());
        }
    }

    /** Elige el bloque objetivo más cercano a donde está ahora el robot. */
    @Nullable
    private BlockPos pickNearest() {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        BlockPos here = blockPosition();
        for (int i = 0; i < targets.size(); i++) {
            double d = targets.get(i).distSqr(here);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best < 0 ? null : targets.remove(best);
    }

    private void resetTracking() {
        tunneling = false;
        stepDest = null;
        pillarFrom = null;
        targetTicks = 0;
        stuckTicks = 0;
        bestDist = Double.MAX_VALUE;
        getNavigation().stop();
    }

    private void skipCurrent(boolean warn) {
        if (warn && current != null) {
            notifyStatus(RobotStatus.TARGET_SKIPPED, current.getX(), current.getY(), current.getZ());
        }
        current = null;
        resetTracking();
    }

    private boolean inReach(BlockPos pos) {
        return getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
    }

    private static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        return isSafeToTouch(state) && state.getDestroySpeed(level, pos) >= 0 && state.getFluidState().isEmpty();
    }

    private static boolean isLava(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    /** ¿Hay agua o lava tocando esta casilla? Entonces no cavamos ahí (seguridad). */
    private static boolean touchesFluid(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) {
                return true;
            }
        }
        return !level.getFluidState(pos).isEmpty();
    }

    // ---------- minería en tiras ----------

    /** Túnel principal hacia delante y galerías a los lados cada BRANCH_EVERY bloques (ida y vuelta). */
    private void planStrips(Direction facing) {
        stripCells.clear();
        stripBranch.clear();
        pendingOres.clear();
        stripIndex = 0;
        int length = radius * STRIP_LENGTH_PER_RADIUS;
        Direction right = facing.getClockWise();
        int branch = 0;
        for (int i = 1; i <= length; i++) {
            BlockPos main = scanCenter.relative(facing, i);
            stripCells.add(main);
            stripBranch.add(0);
            if (i % BRANCH_EVERY == 0) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    branch++;
                    for (int j = 1; j <= BRANCH_LENGTH; j++) {
                        stripCells.add(main.relative(right, sign * j));
                        stripBranch.add(branch);
                    }
                    stripCells.add(main);   // volver al túnel principal
                    stripBranch.add(branch);
                }
            }
        }
    }

    /** Si una galería no se puede seguir (agua, lava, protegido...), se pasa a la siguiente. */
    private void skipStripBranch() {
        int branch = stripBranch.get(stripIndex);
        if (branch == 0) {
            stripIndex = stripCells.size(); // el túnel principal está bloqueado: se termina
            return;
        }
        while (stripIndex < stripCells.size() && stripBranch.get(stripIndex) == branch) {
            stripIndex++;
        }
        stuckTicks = 0;
        getNavigation().stop();
    }

    /** Apunta los bloques del material que quedan a la vista alrededor de una posición (vetas). */
    private void addVisibleOres(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (pendingOres.size() < 64 && !pendingOres.contains(n) && level.isLoaded(n) && matches(level.getBlockState(n))) {
                pendingOres.add(n.immutable());
            }
        }
    }

    private static boolean isOpenCell(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || (s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty());
    }

    private void stripTick(ServerLevel level) {
        // 1) primero el mineral que ha quedado a la vista (siguiendo la veta)
        while (!pendingOres.isEmpty()) {
            BlockPos ore = pendingOres.get(0);
            BlockState st = level.getBlockState(ore);
            if (!matches(st) || !canBreak(level, ore, st) || touchesFluid(level, ore) || !inReach(ore)) {
                pendingOres.remove(0);
                continue;
            }
            getNavigation().stop();
            getLookControl().setLookAt(Vec3.atCenterOf(ore));
            if (cooldown > 0) {
                return;
            }
            cooldown = MINE_INTERVAL;
            if (breakAndCollect(level, ore, st, true)) {
                pendingOres.remove(0);
                addVisibleOres(level, ore);
                if (obtained >= targetAmount) {
                    finishDone();
                }
            }
            return;
        }
        if (stripIndex >= stripCells.size()) {
            notifyStatus(RobotStatus.STRIP_DONE, obtained, targetAmount, activeName());
            beginReturn(JobState.FINISHED);
            return;
        }
        BlockPos cell = stripCells.get(stripIndex);
        // 2) ¿ya ha llegado a la casilla? mira las paredes, pone suelo y antorcha
        if (isSameBlockPos(blockPosition(), cell)) {
            addVisibleOres(level, cell);
            addVisibleOres(level, cell.above());
            if (stripBranch.get(stripIndex) == 0 && cell.distManhattan(scanCenter) % TORCH_EVERY == 0
                    && countItem(Items.TORCH) > 0 && level.getBlockState(cell).isAir()
                    && Blocks.TORCH.defaultBlockState().canSurvive(level, cell) && removeOne(Items.TORCH)) {
                level.setBlock(cell, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
            }
            stripIndex++;
            stuckTicks = 0;
            return;
        }
        if (touchesFluid(level, cell) || touchesFluid(level, cell.above())) {
            skipStripBranch();
            return;
        }
        // 3) cavar la casilla (2 de alto)
        for (BlockPos c : new BlockPos[]{cell.above(), cell}) {
            if (isOpenCell(level, c)) {
                continue;
            }
            BlockState s = level.getBlockState(c);
            if (!canBreak(level, c, s)) {
                skipStripBranch();
                return;
            }
            getLookControl().setLookAt(Vec3.atCenterOf(c));
            if (cooldown > 0) {
                return;
            }
            cooldown = MINE_INTERVAL;
            breakAndCollect(level, c, s, matches(s));
            if (obtained >= targetAmount) {
                finishDone();
            }
            return;
        }
        // 4) tapar huecos del suelo con roca/tierra para no caerse
        BlockPos floor = cell.below();
        if (isOpenCell(level, floor) && !isLava(level, floor)) {
            Item filler = findFiller();
            if (filler != null && removeOne(filler)) {
                level.setBlock(floor, Block.byItem(filler).defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        // 5) caminar hasta la casilla
        if (getNavigation().isDone()) {
            getNavigation().moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, 1.0D);
        }
        if (++stuckTicks > STUCK_LIMIT * 2) {
            skipStripBranch();
        }
    }

    private void workingTick(ServerLevel level) {
        if (stripMode) {
            if (obtained >= targetAmount) {
                finishDone();
            } else {
                stripTick(level);
            }
            return;
        }
        if (obtained >= targetAmount) {
            finishDone();
            return;
        }
        if (current == null) {
            resetTracking();
            current = pickNearest();
            if (current == null) {
                notifyStatus(RobotStatus.NOT_ENOUGH_FOUND, obtained, targetAmount, activeName());
                beginReturn(JobState.FINISHED);
                return;
            }
        }
        if (!level.isLoaded(current)) {
            skipCurrent(false);
            return;
        }
        BlockState state = level.getBlockState(current);
        if (!matches(state) || !canBreak(level, current, state)) {
            skipCurrent(false); // ya no está o no se puede romper
            return;
        }
        if (++targetTicks > MAX_TICKS_PER_TARGET) {
            skipCurrent(true);
            return;
        }
        if (inReach(current)) {
            getNavigation().stop();
            getLookControl().setLookAt(Vec3.atCenterOf(current));
            if (cooldown > 0) {
                return;
            }
            cooldown = MINE_INTERVAL;
            if (breakAndCollect(level, current, state, true)) {
                current = null;
                if (obtained >= targetAmount) {
                    finishDone();
                }
            }
            return;
        }
        if (!tunneling) {
            walkTowards(current);
        } else {
            if (!tunnelTick(level, current)) {
                skipCurrent(true);
            }
        }
    }

    /** Camina usando las rutas normales de Minecraft. Si no avanza, pasa a cavar un túnel. */
    private void walkTowards(BlockPos target) {
        if (getNavigation().isDone()) {
            boolean ok = getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
            if (!ok) {
                tunneling = true;
                stuckTicks = 0;
                return;
            }
        }
        double d = distanceToSqr(Vec3.atCenterOf(target));
        if (d < bestDist - 0.25D) {
            bestDist = d;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_LIMIT) {
            getNavigation().stop();
            tunneling = true;
            stuckTicks = 0;
        }
    }

    /**
     * Túnel en escalera: cava 1 casilla hacia el objetivo (2 bloques de alto),
     * subiendo o bajando un bloque si hace falta, y luego camina a esa casilla.
     */
    /**
     * Avanza un paso hacia "goal": cava un túnel en escalera (2 bloques de alto), o,
     * si el objetivo está justo encima, trepa haciendo una torre bajo sus pies.
     * Devuelve false si no puede avanzar más.
     */
    private boolean tunnelTick(ServerLevel level, BlockPos goal) {
        if (pillarFrom != null) {
            return pillarTick(level);
        }
        if (stepDest != null) {
            if (isSameBlockPos(blockPosition(), stepDest)) {
                stepDest = null;
                stuckTicks = 0;
            } else {
                if (getNavigation().isDone()) {
                    getNavigation().moveTo(stepDest.getX() + 0.5D, stepDest.getY(), stepDest.getZ() + 0.5D, 1.0D);
                }
                return ++stuckTicks <= STUCK_LIMIT * 2;
            }
        }
        BlockPos feet = blockPosition();
        List<BlockPos> toClear = new ArrayList<>(3);
        BlockPos dest = planStep(feet, goal, toClear);
        if (dest == null || touchesFluid(level, dest) || isLava(level, dest.below())) {
            return false;
        }
        for (BlockPos cell : toClear) {
            BlockState s = level.getBlockState(cell);
            if (s.isAir() || (s.getCollisionShape(level, cell).isEmpty() && s.getFluidState().isEmpty())) {
                continue; // ya está libre
            }
            if (touchesFluid(level, cell) || !canBreak(level, cell, s)) {
                return false;
            }
            if (cooldown > 0) {
                return true;
            }
            cooldown = MINE_INTERVAL;
            return breakAndCollect(level, cell, s, matches(s)) || jobState.isActive(); // un bloque cada vez
        }
        if (isSameBlockPos(dest, feet.above())) {
            // Trepar: saltar y poner un bloque debajo.
            if (findFiller() == null) {
                notifyStatus(RobotStatus.NO_FILLER);
                return false;
            }
            pillarFrom = feet;
            pillarTicks = 0;
            return true;
        }
        stepDest = dest;
        stuckTicks = 0;
        return true;
    }

    /** Bloque de relleno para trepar: roca, tierra... nunca el material que se está contando. */
    @Nullable
    private Item findFiller() {
        for (Item item : FILLERS) {
            if (item != activeCounted && countItem(item) > 0) {
                return item;
            }
        }
        return null;
    }

    private boolean pillarTick(ServerLevel level) {
        BlockPos from = pillarFrom;
        if (++pillarTicks > 60) {
            pillarFrom = null;
            return false;
        }
        if (getY() >= from.getY() + 1.0D) {
            BlockState there = level.getBlockState(from);
            Item filler = findFiller();
            if (filler == null) {
                pillarFrom = null;
                notifyStatus(RobotStatus.NO_FILLER);
                return false;
            }
            if (there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty())) {
                BlockState block = Block.byItem(filler).defaultBlockState();
                if (removeOne(filler)) {
                    level.setBlock(from, block, Block.UPDATE_ALL);
                    level.playSound(null, from, block.getSoundType(level, from, this).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    swing(InteractionHand.MAIN_HAND);
                }
            }
            pillarFrom = null;
            stuckTicks = 0;
            return true;
        }
        if (onGround() && pillarTicks % 10 == 1) {
            getNavigation().stop();
            getJumpControl().jump();
        }
        return true;
    }

    @Nullable
    private static BlockPos planStep(BlockPos feet, BlockPos target, List<BlockPos> toClear) {
        int dx = target.getX() - feet.getX();
        int dy = target.getY() - feet.getY();
        int dz = target.getZ() - feet.getZ();
        if (dx == 0 && dz == 0) {
            if (dy < 0) {
                toClear.add(feet.below());
                return feet.below();
            }
            if (dy > 0) {
                toClear.add(feet.above(2)); // hueco para la cabeza al subir
                return feet.above();          // trepar haciendo una torre
            }
            return null;
        }
        Direction dir = Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos f = feet.relative(dir);
        if (dy > 0) {
            toClear.add(feet.above(2));
            toClear.add(f.above());
            toClear.add(f.above(2));
            return f.above();
        } else if (dy < 0) {
            toClear.add(f.above());
            toClear.add(f);
            toClear.add(f.below());
            return f.below();
        }
        toClear.add(f.above());
        toClear.add(f);
        return f;
    }

    /**
     * Rompe un bloque de verdad y guarda lo que suelta.
     * Devuelve false si no se ha roto (inventario lleno o zona protegida).
     */
    private boolean breakAndCollect(ServerLevel level, BlockPos pos, BlockState state, boolean count) {
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, fakePlayer))) {
            skipCurrent(true); // protegido por otro mod
            return false;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, this, tool());
        if (!canFitAll(drops) && jobState == JobState.RETURNING) {
            getNavigation().stop();
            setJobState(afterReturn);
            return false;
        }
        if (!canFitAll(drops)) {
            if (current != null) {
                targets.add(current); // lo intentaremos después
            }
            notifyStatus(RobotStatus.INVENTORY_FULL, obtained, targetAmount);
            beginReturn(JobState.PAUSED);
            return false;
        }
        state.spawnAfterBreak(level, pos, tool(), true);
        level.destroyBlock(pos, false, this);
        for (ItemStack drop : drops) {
            if (count && (activeCounted == null || drop.is(activeCounted))) {
                obtained += drop.getCount();
            }
            ItemStack rest = inventory.addItem(drop);
            if (!rest.isEmpty()) {
                // No debería pasar (ya se comprobó el espacio), pero nunca perdemos objetos.
                Containers.dropItemStack(level, getX(), getY() + 0.5D, getZ(), rest);
            }
        }
        swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private void beginReturn(JobState after) {
        afterReturn = after;
        current = null;
        resetTracking();
        returnTicks = 0;
        setJobState(JobState.RETURNING);
        notifyStatus(RobotStatus.RETURNING);
    }

    private void returningTick() {
        double d = distanceToSqr(Vec3.atBottomCenterOf(scanCenter));
        boolean arrived = d <= 2.25D;
        if (arrived || ++returnTicks > RETURN_LIMIT) {
            getNavigation().stop();
            JobState next = afterReturn;
            if (arrived && useChest && level() instanceof ServerLevel serverLevel) {
                int moved = depositToChests(serverLevel);
                boolean empty = isStorageEmpty();
                if (moved == 0 && !empty && findChests(serverLevel).isEmpty()) {
                    notifyStatus(RobotStatus.NO_CHEST, CHEST_RANGE);
                } else if (!empty) {
                    notifyStatus(RobotStatus.CHEST_FULL, moved);
                } else {
                    notifyStatus(RobotStatus.DEPOSITED, moved);
                    // Había vuelto por tener el inventario lleno: ahora hay sitio, así que sigue.
                    if (next == JobState.PAUSED && !targets.isEmpty() && obtained < targetAmount) {
                        next = JobState.WORKING;
                    }
                }
            }
            setJobState(next);
            return;
        }
        if (tunneling) {
            if (!(level() instanceof ServerLevel serverLevel) || !tunnelTick(serverLevel, scanCenter)) {
                getNavigation().stop();
                setJobState(afterReturn); // no puede volver: se queda donde está
            }
            return;
        }
        if (getNavigation().isDone() && returnTicks % 20 == 1) {
            getNavigation().moveTo(scanCenter.getX() + 0.5D, scanCenter.getY(), scanCenter.getZ() + 0.5D, 1.0D);
        }
        if (d < bestDist - 0.25D) {
            bestDist = d;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_LIMIT) {
            getNavigation().stop();
            tunneling = true; // atascado (por ejemplo en un agujero): cava o trepa
            stuckTicks = 0;
        }
    }

    /** Cofres, barriles y otros contenedores cerca del robot (los de otros mods también). */
    private List<IItemHandler> findChests(ServerLevel level) {
        List<IItemHandler> found = new ArrayList<>();
        BlockPos here = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-CHEST_RANGE, -2, -CHEST_RANGE), here.offset(CHEST_RANGE, 2, CHEST_RANGE))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(found::add);
        }
        return found;
    }

    /** Mueve el almacén del robot a los cofres cercanos. Devuelve cuántos objetos ha movido. */
    private int depositToChests(ServerLevel level) {
        List<IItemHandler> chests = findChests(level);
        int moved = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int before = stack.getCount();
            ItemStack rest = stack;
            for (IItemHandler chest : chests) {
                rest = ItemHandlerHelper.insertItemStacked(chest, rest, false);
                if (rest.isEmpty()) {
                    break;
                }
            }
            moved += before - rest.getCount();
            inventory.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
        }
        inventory.setChanged();
        return moved;
    }

    private boolean isStorageEmpty() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void finishDone() {
        targets.clear();
        notifyStatus(RobotStatus.MINE_DONE, obtained, activeName(), targetAmount);
        beginReturn(JobState.FINISHED);
    }

    /** No se hace daño al cavar: ni asfixia en paredes, ni caídas, ni ahogo. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL) || source.is(DamageTypes.DROWN)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    // ---------- guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Sample", saveContainer(sample));
        tag.putInt("MaterialIndex", materialIndex);
        tag.putInt("TargetAmount", targetAmount);
        tag.putInt("Radius", radius);
        tag.putBoolean("UseChest", useChest);
        tag.putBoolean("StripMode", stripMode);
        long[] cells = new long[stripCells.size()];
        int[] branches = new int[stripCells.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = stripCells.get(i).asLong();
            branches[i] = stripBranch.get(i);
        }
        tag.putLongArray("StripCells", cells);
        tag.putIntArray("StripBranch", branches);
        tag.putInt("StripIndex", stripIndex);
        long[] ores = new long[pendingOres.size()];
        for (int i = 0; i < ores.length; i++) {
            ores[i] = pendingOres.get(i).asLong();
        }
        tag.putLongArray("PendingOres", ores);
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
        if (current != null) {
            tag.putLong("Current", current.asLong());
        }
        tag.putString("AfterReturn", afterReturn.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadContainer(sample, tag.getList("Sample", Tag.TAG_COMPOUND));
        if (tag.contains("MaterialIndex")) {
            materialIndex = Math.floorMod(tag.getInt("MaterialIndex"), MiningMaterials.optionCount());
            targetAmount = Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, tag.getInt("TargetAmount")));
            radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, tag.getInt("Radius")));
            useChest = tag.getBoolean("UseChest");
            stripMode = tag.getBoolean("StripMode");
            stripCells.clear();
            stripBranch.clear();
            long[] cells = tag.getLongArray("StripCells");
            int[] branches = tag.getIntArray("StripBranch");
            for (int i = 0; i < cells.length && i < branches.length; i++) {
                stripCells.add(BlockPos.of(cells[i]));
                stripBranch.add(branches[i]);
            }
            stripIndex = Math.max(0, Math.min(stripCells.size(), tag.getInt("StripIndex")));
            pendingOres.clear();
            for (long l : tag.getLongArray("PendingOres")) {
                pendingOres.add(BlockPos.of(l));
            }
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
        if (tag.contains("Current")) {
            targets.add(BlockPos.of(tag.getLong("Current"))); // se vuelve a elegir al cargar
        }
        try {
            afterReturn = JobState.valueOf(tag.getString("AfterReturn"));
        } catch (IllegalArgumentException e) {
            afterReturn = JobState.FINISHED;
        }
        if ((jobState.isActive() || jobState == JobState.PAUSED) && activeBlocks.isEmpty()) {
            setJobState(JobState.IDLE);
        }
    }
}
