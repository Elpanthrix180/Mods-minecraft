package com.robotsconstructores.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import com.robotsconstructores.recall.RobotRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Base común de todos los robots: inventario propio, dueño, estado de trabajo,
 * mensajes al jugador y guardado en el mundo.
 */
public abstract class BaseRobotEntity extends PathfinderMob {
    public static final int INVENTORY_SIZE = 27;
    private static final EntityDataAccessor<Boolean> DATA_WORKING =
            SynchedEntityData.defineId(BaseRobotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double MESSAGE_RANGE = 32.0D;

    protected final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    protected JobState jobState = JobState.IDLE;
    protected RobotStatus status = RobotStatus.READY;
    @Nullable
    protected UUID ownerId;

    protected BaseRobotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WORKING, false);
    }

    @Override
    protected void registerGoals() {
        // Los robots se quedan en su puesto de trabajo: solo miran alrededor.
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /**
     * En el Nether (o en cualquier dimensión ardiente de otros mods) los robots son ignífugos:
     * no les hace daño el fuego ni la lava y no se quedan ardiendo.
     */
    @Override
    public boolean fireImmune() {
        Level level = level();
        return super.fireImmune() || level.dimension() == Level.NETHER || level.dimensionType().ultraWarm();
    }

    @Override
    public void tick() {
        super.tick();
        // apuntar dónde está el robot para que el Mando lo encuentre aunque la zona no esté cargada
        if (ownerId != null && tickCount % 40 == 0 && level() instanceof ServerLevel serverLevel) {
            RobotRegistry.get(serverLevel.getServer()).update(getUUID(), ownerId, serverLevel.dimension(), blockPosition());
        }
        if (isOnFire() && fireImmune()) {
            clearFire(); // en el Nether no se ven ardiendo
        }
    }

    /** Los robots no respiran: no se ahogan bajo el agua. */
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        // muerto, recogido o guardado en la varita: se borra de la lista (al descargarse la zona, no)
        if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            RobotRegistry.get(serverLevel.getServer()).remove(getUUID());
        }
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    public boolean isWorkingClient() {
        return this.entityData.get(DATA_WORKING);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public JobState getJobState() {
        return jobState;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    protected void setJobState(JobState state) {
        this.jobState = state;
        this.entityData.set(DATA_WORKING, state.isActive());
    }

    /** Objeto que se devuelve al recoger el robot. */
    protected abstract Item getSpawnItem();

    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory playerInventory);

    /** Botones de la interfaz (los procesa siempre el servidor). */
    public abstract void handleButton(int id, ServerPlayer player);

    /** Trabajo que se hace en cada tick del servidor. */
    protected abstract void workTick(ServerLevel level);

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (level() instanceof ServerLevel serverLevel) {
            workTick(serverLevel);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.getItemInHand(hand).getItem() instanceof com.robotsconstructores.item.RobotWandItem) {
            return InteractionResult.PASS; // la varita se encarga (guardar el robot)
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            pickUp(serverPlayer);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((id, inv, p) -> createMenu(id, inv), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
        return InteractionResult.CONSUME;
    }

    /** Mayús + clic derecho con la mano vacía: guarda el robot y devuelve su contenido. */
    private void pickUp(ServerPlayer player) {
        if (jobState.isActive()) {
            notifyStatus(RobotStatus.BUSY);
            return;
        }
        giveAllTo(player);
        Containers.dropContents(level(), this, inventory);
        dropExtraContents();
        ItemStack item = new ItemStack(getSpawnItem());
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        discard();
    }

    /** Contenedores adicionales (por ejemplo la ranura de muestra del minero). */
    protected void dropExtraContents() {
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        Containers.dropContents(level(), this, inventory);
        dropExtraContents();
    }

    /** Pasa todo el inventario del robot al jugador. Lo que no cabe se queda en el robot. */
    public boolean giveAllTo(Player player) {
        boolean everything = true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            player.getInventory().add(stack); // reduce "stack" con lo que entra
            if (!stack.isEmpty()) {
                everything = false;
            }
            inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        inventory.setChanged();
        return everything;
    }

    // ---------- utilidades de inventario ----------

    public int countItem(Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Quita un objeto concreto. Devuelve false (sin quitar nada) si no hay. */
    public boolean removeOne(Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                inventory.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Comprueba, sin modificar nada, si todos los objetos caben en el inventario. */
    public boolean canFitAll(List<ItemStack> stacks) {
        SimpleContainer copy = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            copy.setItem(i, inventory.getItem(i).copy());
        }
        for (ItemStack stack : stacks) {
            if (!copy.addItem(stack.copy()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------- mensajes ----------

    protected void notifyStatus(RobotStatus newStatus, Object... args) {
        this.status = newStatus;
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Component msg = Component.literal("[").append(getDisplayName()).append("] ")
                .append(Component.translatable(newStatus.translationKey(), args));
        for (ServerPlayer p : serverLevel.players()) {
            boolean isOwner = ownerId != null && ownerId.equals(p.getUUID());
            if (isOwner || p.distanceToSqr(this) <= MESSAGE_RANGE * MESSAGE_RANGE) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    protected static boolean isSameBlockPos(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    /** Posiciones que el robot nunca toca: donde está y el suelo bajo sus pies. */
    protected boolean isOwnSpace(BlockPos pos) {
        BlockPos feet = blockPosition();
        return isSameBlockPos(pos, feet) || isSameBlockPos(pos, feet.above()) || isSameBlockPos(pos, feet.below());
    }

    protected static boolean isSafeToTouch(BlockState state) {
        return !state.hasBlockEntity();
    }

    // ---------- guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("RobotInventory", saveContainer(inventory));
        tag.putString("JobState", jobState.name());
        tag.putInt("Status", status.ordinal());
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadContainer(inventory, tag.getList("RobotInventory", Tag.TAG_COMPOUND));
        try {
            setJobState(JobState.valueOf(tag.getString("JobState")));
        } catch (IllegalArgumentException e) {
            setJobState(JobState.IDLE);
        }
        status = RobotStatus.byOrdinal(tag.getInt("Status"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    protected static ListTag saveContainer(SimpleContainer container) {
        ListTag list = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag c = new CompoundTag();
                c.putByte("Slot", (byte) i);
                stack.save(c);
                list.add(c);
            }
        }
        return list;
    }

    protected static void loadContainer(SimpleContainer container, ListTag list) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int slot = c.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) {
                container.setItem(slot, ItemStack.of(c));
            }
        }
    }
}
