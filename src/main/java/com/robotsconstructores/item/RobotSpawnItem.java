package com.robotsconstructores.item;

import com.robotsconstructores.entity.BaseRobotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** Objeto que coloca un robot en el mundo al hacer clic derecho sobre un bloque. */
public class RobotSpawnItem extends Item {
    private final Supplier<? extends EntityType<? extends BaseRobotEntity>> type;

    public RobotSpawnItem(Supplier<? extends EntityType<? extends BaseRobotEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        BaseRobotEntity robot = type.get().create(serverLevel);
        if (robot == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        robot.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        robot.setYHeadRot(yaw);
        robot.setYBodyRot(yaw);
        if (player != null) {
            robot.setOwner(player.getUUID());
        }
        serverLevel.addFreshEntity(robot);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
