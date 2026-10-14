package com.robotsconstructores.recall;

import com.robotsconstructores.RobotsConstructores;
import com.robotsconstructores.entity.BaseRobotEntity;
import com.robotsconstructores.entity.JobState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trae los robots al jugador. Los que están en zonas no cargadas se cargan un momento
 * (chunk forzado), se traen en cuanto aparecen y la zona se vuelve a liberar.
 */
@Mod.EventBusSubscriber(modid = RobotsConstructores.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RobotRecall {
    private static final int WAIT_TICKS = 200; // 10 segundos como máximo esperando a que cargue

    private record Pending(UUID robot, UUID player, ServerLevel level, ChunkPos chunk, boolean weForced, int[] ticksLeft) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Resultado: [traídos, trabajando, cargando] */
    public static int[] recallAll(ServerPlayer player) {
        MinecraftServer server = player.server;
        int brought = 0, busy = 0, loading = 0;
        List<UUID> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof BaseRobotEntity robot && player.getUUID().equals(robot.getOwnerId())) {
                    found.add(robot.getUUID());
                    if (isBusy(robot)) {
                        busy++;
                    } else if (teleportToPlayer(robot, player)) {
                        brought++;
                    }
                }
            }
        }
        // robots apuntados en la lista pero que no están cargados
        for (Map.Entry<UUID, RobotRegistry.Entry> e : RobotRegistry.get(server).ownedBy(player.getUUID())) {
            if (found.contains(e.getKey()) || isPending(e.getKey())) {
                continue;
            }
            ServerLevel level = server.getLevel(e.getValue().dimension());
            if (level == null) {
                continue;
            }
            ChunkPos chunk = new ChunkPos(e.getValue().pos());
            boolean alreadyForced = level.getForcedChunks().contains(chunk.toLong());
            if (!alreadyForced) {
                level.setChunkForced(chunk.x, chunk.z, true);
            }
            PENDING.add(new Pending(e.getKey(), player.getUUID(), level, chunk, !alreadyForced, new int[]{WAIT_TICKS}));
            loading++;
        }
        return new int[]{brought, busy, loading};
    }

    private static boolean isPending(UUID robot) {
        for (Pending p : PENDING) {
            if (p.robot().equals(robot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBusy(BaseRobotEntity robot) {
        return robot.getJobState().isActive() || robot.getJobState() == JobState.PAUSED;
    }

    /** Lleva el robot junto al jugador (también entre dimensiones). */
    private static boolean teleportToPlayer(BaseRobotEntity robot, ServerPlayer player) {
        ServerLevel from = (ServerLevel) robot.level();
        ServerLevel to = player.serverLevel();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        from.sendParticles(ParticleTypes.PORTAL, robot.getX(), robot.getY() + 0.8D, robot.getZ(), 30, 0.3D, 0.6D, 0.3D, 0.2D);
        from.playSound(null, robot.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.4F);
        if (from == to) {
            robot.getNavigation().stop();
            robot.teleportTo(x, y, z);
        } else {
            CompoundTag data = new CompoundTag();
            if (!robot.save(data)) {
                return false;
            }
            robot.discard();
            Entity copy = EntityType.loadEntityRecursive(data, to, e -> {
                e.moveTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
                return e;
            });
            if (copy == null || !to.tryAddFreshEntityWithPassengers(copy)) {
                return false;
            }
        }
        to.sendParticles(ParticleTypes.PORTAL, x, y + 0.8D, z, 30, 0.3D, 0.6D, 0.3D, 0.2D);
        to.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            Entity e = p.level().getEntity(p.robot());
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            boolean done = false;
            if (e instanceof BaseRobotEntity robot && player != null) {
                if (isBusy(robot)) {
                    player.displayClientMessage(Component.translatable("remote.robotsconstructores.busy_far", robot.getDisplayName()), false);
                } else if (teleportToPlayer(robot, player)) {
                    player.displayClientMessage(Component.translatable("remote.robotsconstructores.arrived", robot.getDisplayName()), false);
                }
                done = true;
            } else if (--p.ticksLeft()[0] <= 0 || player == null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("remote.robotsconstructores.lost",
                            p.chunk().getMiddleBlockX(), p.chunk().getMiddleBlockZ()), false);
                }
                // ya no está ahí (por ejemplo, lo rompieron): se borra de la lista
                RobotRegistry.get(server).remove(p.robot());
                done = true;
            }
            if (done) {
                if (p.weForced()) {
                    p.level().setChunkForced(p.chunk().x, p.chunk().z, false);
                }
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Pending p : PENDING) {
            if (p.weForced()) {
                p.level().setChunkForced(p.chunk().x, p.chunk().z, false);
            }
        }
        PENDING.clear();
    }

    private RobotRecall() {}
}
