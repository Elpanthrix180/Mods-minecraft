package com.robotsconstructores.item;

import com.robotsconstructores.recall.RobotRecall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Mando de Robots: clic derecho para traer hasta ti todos tus robots que no estén trabajando. */
public class RobotRemoteItem extends Item {
    private static final int COOLDOWN = 40;

    public RobotRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int[] r = RobotRecall.recallAll(serverPlayer);
            if (r[0] + r[1] + r[2] == 0) {
                player.displayClientMessage(Component.translatable("remote.robotsconstructores.none"), true);
            } else {
                player.displayClientMessage(Component.translatable("remote.robotsconstructores.result", r[0], r[1], r[2]), false);
            }
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("remote.robotsconstructores.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("remote.robotsconstructores.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
