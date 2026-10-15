package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tornado que se desplaza y cambia de direccion. Dibuja un embudo de particulas, atrae y gira
 * a las entidades cercanas y, si la destruccion lo permite, arranca bloques sueltos de la superficie
 * (tierra, arena, grava, hojas, plantas y, en modo intenso, troncos) y los lanza por el aire.
 * Nunca toca bloques protegidos (ver BlockProtection). Solo se mueve por chunks ya cargados.
 */
public class TornadoEvent extends CatastropheEvent {

    private static final int FUNNEL_HEIGHT = 28;

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        switch (phase) {
            case WARNING -> {
                // El tornado aparece en el borde de la zona y se dirige mas o menos hacia el centro.
                RandomSource r = level.getRandom();
                double angle = r.nextDouble() * Math.PI * 2;
                double dist = DisasterUtil.radius() * 0.8;
                d.eventData.putDouble("tx", d.centerX + Math.cos(angle) * dist);
                d.eventData.putDouble("tz", d.centerZ + Math.sin(angle) * dist);
                d.eventData.putDouble("dir", angle + Math.PI + (r.nextDouble() - 0.5));
                d.setDirty();
            }
            case PREPARATION -> DisasterUtil.startRain(level, true);
            case RECOVERY -> DisasterUtil.clearWeather(level);
            default -> { }
        }
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.PREPARATION && phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        double f = CatastropheConfig.intensityFactor();
        // Tamano del tornado segun la fase: pequeno al formarse y al disiparse.
        double size = phase == CatastrophePhase.ACTIVE ? 1.0 : 0.4;

        double tx = d.eventData.getDouble("tx");
        double tz = d.eventData.getDouble("tz");
        BlockPos column = BlockPos.containing(tx, d.centerY, tz);
        if (!level.isLoaded(column)) return; // si el tornado esta en una zona no cargada, espera
        int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());

        if (phase != CatastrophePhase.PREPARATION) move(level, d, tx, tz, now);
        if (now % 2 == 0) drawFunnel(level, tx, baseY, tz, now, size * (0.7 + 0.3 * f));
        if (now % 20 == 0) {
            level.playSound(null, column.atY(baseY), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, (float) (3.0 * size), 0.5f);
        }
        if (phase == CatastrophePhase.PREPARATION) return;

        pullEntities(level, tx, baseY, tz, now, f * size);
        if (!CatastropheConfig.destructionOff() && CatastropheConfig.TORNADO_LIFTS_BLOCKS.get()) {
            int interval = CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE ? 3 : 8;
            if (phase == CatastrophePhase.ENDING) interval *= 2;
            if (now % interval == 0) liftBlock(level, d, tx, tz, f);
        }
    }

    private static void move(ServerLevel level, CatastropheSavedData d, double tx, double tz, long now) {
        RandomSource r = level.getRandom();
        double dir = d.eventData.getDouble("dir");
        if (now % 100 == 0) dir += (r.nextDouble() - 0.5) * Math.PI / 2; // cambia de rumbo de vez en cuando
        // Si se aleja demasiado del centro, gira hacia el.
        double dx = d.centerX - tx, dz = d.centerZ - tz;
        if (dx * dx + dz * dz > Math.pow(DisasterUtil.radius() * 1.5, 2)) dir = Math.atan2(dz, dx);
        double step = CatastropheConfig.TORNADO_SPEED.get() / 20.0;
        double nx = tx + Math.cos(dir) * step;
        double nz = tz + Math.sin(dir) * step;
        if (!level.isLoaded(BlockPos.containing(nx, d.centerY, nz))) {
            dir += Math.PI; // no entra en zonas sin cargar: da la vuelta
            nx = tx;
            nz = tz;
        }
        d.eventData.putDouble("tx", nx);
        d.eventData.putDouble("tz", nz);
        d.eventData.putDouble("dir", dir);
        d.setDirty();
    }

    private static void drawFunnel(ServerLevel level, double x, int baseY, double z, long now, double size) {
        double spin = now * 0.35;
        for (int h = 0; h < FUNNEL_HEIGHT; h += 2) {
            double radius = (0.6 + h * 0.22) * size;
            for (int arm = 0; arm < 3; arm++) {
                double a = spin + arm * (Math.PI * 2 / 3) + h * 0.3;
                level.sendParticles(ParticleTypes.CLOUD, x + Math.cos(a) * radius, baseY + h, z + Math.sin(a) * radius,
                        1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        BlockState ground = level.getBlockState(BlockPos.containing(x, baseY - 1, z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, baseY + 0.5, z, 8, 1.5 * size, 0.3, 1.5 * size, 0.2);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, baseY + FUNNEL_HEIGHT, z, 3, 3 * size, 0.5, 3 * size, 0.01);
    }

    private static void pullEntities(ServerLevel level, double tx, int baseY, double tz, long now, double strength) {
        int pull = CatastropheConfig.TORNADO_PULL_RADIUS.get();
        AABB box = new AABB(tx - pull, baseY - 4, tz - pull, tx + pull, baseY + FUNNEL_HEIGHT, tz + pull);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double dx = tx - e.getX(), dz = tz - e.getZ();
            double dist = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            if (dist > pull) continue;
            double closeness = 1.0 - dist / pull; // 1 en el centro, 0 en el borde
            Vec3 inward = new Vec3(dx / dist, 0, dz / dist).scale(0.06 * strength * closeness);
            Vec3 around = new Vec3(-dz / dist, 0, dx / dist).scale(0.10 * strength * closeness);
            // Solo se levanta a quien puede recibir dano (la caida hace dano).
            double up = DisasterUtil.canHurt(e) && dist < 3 && e.getY() < baseY + 18 ? 0.12 * strength : 0;
            Vec3 v = e.getDeltaMovement().add(inward).add(around).add(0, up, 0);
            e.setDeltaMovement(v.x, Mth.clamp(v.y, -1.0, 0.7), v.z);
            e.hurtMarked = true; // para que el cliente del jugador reciba el empujon
            if (now % 20 == 0 && dist < 3 && DisasterUtil.canHurt(e)) {
                e.hurt(level.damageSources().generic(), (float) Math.max(1.0, 2.0 * strength));
            }
        }
    }

    /** Arranca un bloque suelto de la superficie cerca de la base del tornado y lo lanza. */
    private static void liftBlock(ServerLevel level, CatastropheSavedData d, double tx, double tz, double f) {
        RandomSource r = level.getRandom();
        double reach = 2 + 2 * f;
        int x = Mth.floor(tx + (r.nextDouble() * 2 - 1) * reach);
        int z = Mth.floor(tz + (r.nextDouble() * 2 - 1) * reach);
        BlockPos top = new BlockPos(x, d.centerY, z);
        if (!level.isLoaded(top)) return;
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, top).below();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || !isLiftable(state)) return;
        if (!BlockProtection.canModify(level, pos, state) || !DisasterUtil.takeBlockBudget(d)) return;

        if (state.canBeReplaced()) {
            // Hierba alta, flores...: se rompen sin soltar objetos.
            level.destroyBlock(pos, false);
            return;
        }
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        double a = r.nextDouble() * Math.PI * 2;
        falling.setDeltaMovement(Math.cos(a) * 0.5, 0.8 + r.nextDouble() * 0.6, Math.sin(a) * 0.5);
        falling.hurtMarked = true;
    }

    private static boolean isLiftable(BlockState s) {
        if (s.canBeReplaced()) return true;
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.DIRT)
                || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) {
            return true;
        }
        return s.is(BlockTags.LOGS) && CatastropheConfig.TORNADO_UPROOTS_TREES.get()
                && CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE;
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        DisasterUtil.clearWeather(level);
    }
}
