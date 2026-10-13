package com.robotsconstructores.item;

import com.robotsconstructores.entity.BaseRobotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Varita de Robots: guarda robots dentro (con todo su inventario) y los vuelve a poner en el mundo.
 * - Clic derecho sobre un robot: lo guarda.
 * - Clic derecho sobre un bloque: saca el robot elegido.
 * - Mayús + clic derecho al aire: elige otro robot guardado.
 * Los robots se guardan en los datos de la propia varita, así que no se pierden al salir del mundo.
 */
public class RobotWandItem extends Item {
    public static final int MAX_ROBOTS = 8;
    private static final String TAG_ROBOTS = "Robots";
    private static final String TAG_SELECTED = "Selected";

    public RobotWandItem(Properties properties) {
        super(properties);
    }

    private static ListTag robots(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_ROBOTS, Tag.TAG_LIST)) {
            tag.put(TAG_ROBOTS, new ListTag());
        }
        return tag.getList(TAG_ROBOTS, Tag.TAG_COMPOUND);
    }

    private static int selected(ItemStack stack) {
        int size = robots(stack).size();
        return size == 0 ? 0 : Math.floorMod(stack.getOrCreateTag().getInt(TAG_SELECTED), size);
    }

    private static Component robotName(CompoundTag robot) {
        if (robot.contains("CustomName", Tag.TAG_STRING)) {
            Component custom = Component.Serializer.fromJson(robot.getString("CustomName"));
            if (custom != null) {
                return custom;
            }
        }
        return EntityType.byString(robot.getString("id")).map(EntityType::getDescription)
                .orElse(Component.literal("?"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && !robots(stack).isEmpty();
    }

    /** Guardar un robot (clic derecho sobre él). */
    @Override
    public InteractionResult interactLivingEntity(ItemStack ignored, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof BaseRobotEntity robot)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Se usa el objeto real de la mano (en creativo el juego pasa una copia y se perdería el robot).
        ItemStack wand = player.getItemInHand(hand);
        if (!(wand.getItem() instanceof RobotWandItem)) {
            return InteractionResult.PASS;
        }
        if (robot.getJobState().isActive() || robot.getJobState() == com.robotsconstructores.entity.JobState.PAUSED) {
            player.displayClientMessage(Component.translatable("wand.robotsconstructores.busy"), true);
            return InteractionResult.CONSUME;
        }
        ListTag list = robots(wand);
        if (list.size() >= MAX_ROBOTS) {
            player.displayClientMessage(Component.translatable("wand.robotsconstructores.full", MAX_ROBOTS), true);
            return InteractionResult.CONSUME;
        }
        CompoundTag data = new CompoundTag();
        if (!robot.save(data)) {
            return InteractionResult.CONSUME;
        }
        list.add(data);
        wand.getOrCreateTag().put(TAG_ROBOTS, list);
        wand.getOrCreateTag().putInt(TAG_SELECTED, list.size() - 1);
        player.level().playSound(null, robot.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    robot.getX(), robot.getY() + 0.8D, robot.getZ(), 30, 0.3D, 0.6D, 0.3D, 0.2D);
        }
        robot.discard();
        player.displayClientMessage(Component.translatable("wand.robotsconstructores.stored", robotName(data), list.size(), MAX_ROBOTS), true);
        return InteractionResult.CONSUME;
    }

    /** Sacar el robot elegido (clic derecho sobre un bloque). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack wand = context.getItemInHand();
        Player player = context.getPlayer();
        ListTag list = robots(wand);
        if (list.isEmpty()) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("wand.robotsconstructores.empty"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        int index = selected(wand);
        CompoundTag data = list.getCompound(index);
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        Entity entity = EntityType.loadEntityRecursive(data, serverLevel, e -> {
            e.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
            return e;
        });
        if (entity == null || !serverLevel.tryAddFreshEntityWithPassengers(entity)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("wand.robotsconstructores.cannot_place"), true);
            }
            return InteractionResult.FAIL;
        }
        list.remove(index);
        wand.getOrCreateTag().put(TAG_ROBOTS, list);
        wand.getOrCreateTag().putInt(TAG_SELECTED, Math.max(0, index - 1));
        serverLevel.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.2F);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                entity.getX(), entity.getY() + 0.8D, entity.getZ(), 30, 0.3D, 0.6D, 0.3D, 0.2D);
        if (player != null) {
            player.displayClientMessage(Component.translatable("wand.robotsconstructores.released", robotName(data)), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Mayús + clic derecho al aire: elegir el siguiente robot guardado. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        ListTag list = robots(wand);
        if (!player.isShiftKeyDown() || list.isEmpty()) {
            return InteractionResultHolder.pass(wand);
        }
        if (!level.isClientSide) {
            int next = (selected(wand) + 1) % list.size();
            wand.getOrCreateTag().putInt(TAG_SELECTED, next);
            player.displayClientMessage(Component.translatable("wand.robotsconstructores.selected",
                    robotName(list.getCompound(next)), next + 1, list.size()), true);
        }
        return InteractionResultHolder.sidedSuccess(wand, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ListTag list = stack.hasTag() ? robots(stack) : new ListTag();
        tooltip.add(Component.translatable("wand.robotsconstructores.tooltip.count", list.size(), MAX_ROBOTS)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        int sel = selected(stack);
        for (int i = 0; i < list.size(); i++) {
            Component name = robotName(list.getCompound(i));
            tooltip.add(Component.literal(i == sel ? " > " : "   ").append(name)
                    .withStyle(i == sel ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("wand.robotsconstructores.tooltip.help1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("wand.robotsconstructores.tooltip.help2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("wand.robotsconstructores.tooltip.help3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
