package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lluvia de meteoritos. Cada meteorito es una estela de fuego y humo que baja del cielo en diagonal
 * hasta un punto de la zona (solo particulas: no se crean entidades). Al llegar:
 * - siempre: destello, sonido de explosion, onda que empuja y dana (segun la configuracion);
 * - con destruccion PARTIAL/INTENSE: un pequeno crater con roca volcanica y, a veces, hierro en bruto.
 * Los meteoritos en vuelo se guardan en el mundo, asi que un reinicio no los pierde.
 */
public class MeteorShowerEvent extends CatastropheEvent {

    private static final String KEY = "meteors";
    private static final int MAX_IN_FLIGHT = 24;

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase == CatastrophePhase.PREPARATION && now % 10 == 0) {
            // Primeras senales: estrellas fugaces lejanas.
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 40, p.getZ(), 4, 30, 5, 30, 0.2);
            }
        }
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING && phase != CatastrophePhase.RECOVERY) return;

        ListTag meteors = d.eventData.getList(KEY, Tag.TAG_COMPOUND);
        if (phase != CatastrophePhase.RECOVERY) {
            double perMinute = CatastropheConfig.METEORS_PER_MINUTE.get() * CatastropheConfig.intensityFactor();
            if (phase == CatastrophePhase.ENDING) perMinute /= 3;
            int interval = (int) Math.max(4, 1200 / Math.max(1, perMinute));
            if (now % interval == 0 && meteors.size() < MAX_IN_FLIGHT) spawnMeteor(level, d, meteors, now);
        }

        // Dibujar y hacer avanzar los meteoritos en vuelo (de atras hacia delante para poder quitarlos).
        for (int i = meteors.size() - 1; i >= 0; i--) {
            CompoundTag m = meteors.getCompound(i);
            double t = (now - m.getLong("t0")) / (double) m.getInt("dur");
            if (t >= 1.0) {
                impact(level, d, BlockPos.of(m.getLong("target")), m.getFloat("size"));
                meteors.remove(i);
                continue;
            }
            double x = lerp(m.getDouble("sx"), m.getDouble("tx"), t);
            double y = lerp(m.getDouble("sy"), m.getDouble("ty"), t);
            double z = lerp(m.getDouble("sz"), m.getDouble("tz"), t);
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.4, 0.4, 0.4, 0.01);
            if (now % 4 == 0) level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0.2, 0.2, 0.0);
        }
        d.eventData.put(KEY, meteors);
        d.setDirty();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void spawnMeteor(ServerLevel level, CatastropheSavedData d, ListTag meteors, long now) {
        RandomSource r = level.getRandom();
        BlockPos target = DisasterUtil.randomSurface(level, d, r);
        if (target == null) return;
        double a = r.nextDouble() * Math.PI * 2;
        CompoundTag m = new CompoundTag();
        m.putDouble("sx", target.getX() + 0.5 + Math.cos(a) * 45);
        m.putDouble("sy", Math.min(level.getMaxBuildHeight() - 2, target.getY() + 70));
        m.putDouble("sz", target.getZ() + 0.5 + Math.sin(a) * 45);
        m.putDouble("tx", target.getX() + 0.5);
        m.putDouble("ty", target.getY());
        m.putDouble("tz", target.getZ() + 0.5);
        m.putLong("target", target.asLong());
        m.putLong("t0", now);
        m.putInt("dur", 30 + r.nextInt(30));
        m.putFloat("size", (float) (0.6 + r.nextDouble() * 0.8 * CatastropheConfig.intensityFactor()));
        meteors.add(m);
    }

    private static void impact(ServerLevel level, CatastropheSavedData d, BlockPos pos, float size) {
        if (!level.isLoaded(pos)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 20, 1.5, 0.5, 1.5, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 30, 2, 1, 2, 0.05);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 3.0f + size, 0.7f);

        // Onda de choque: empuja y dana a quien este cerca (respeta la configuracion de dano).
        double reach = 3 + 2 * size;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(reach), LivingEntity::isAlive)) {
            double dist = Math.sqrt(e.distanceToSqr(x, y, z));
            if (dist > reach) continue;
            double k = 1.0 - dist / reach;
            if (DisasterUtil.canHurt(e)) {
                e.hurt(level.damageSources().explosion(null, null), (float) (8 * size * k) + 1);
                if (k > 0.5) e.setSecondsOnFire(3);
            }
            Vec3 away = e.position().subtract(x, y, z).normalize().scale(0.8 * k);
            e.setDeltaMovement(e.getDeltaMovement().add(away.x, 0.4 * k, away.z));
            e.hurtMarked = true;
        }
        for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
            if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get() && p.distanceToSqr(x, y, z) < 40 * 40) ModNetwork.sendShake(p, 1.0f * size, 12);
        }

        if (CatastropheConfig.destructionOff()) return;
        makeCrater(level, d, pos, size);
    }

    /** Excava un crater esferico pequeno y deja el meteorito en el fondo. */
    private static void makeCrater(ServerLevel level, CatastropheSavedData d, BlockPos center, float size) {
        int r = Math.max(1, Math.round(CatastropheConfig.METEOR_CRATER_RADIUS.get() * size));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!removable(level, p)) continue;
                    if (!DisasterUtil.takeBlockBudget(d)) return;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // El meteorito: unos bloques de roca volcanica (y a veces hierro) en el fondo del crater.
        RandomSource rand = level.getRandom();
        BlockPos bottom = center.below(r);
        for (BlockPos q : new BlockPos[]{bottom, bottom.north(), bottom.south(), bottom.east(), bottom.west()}) {
            if (!level.isEmptyBlock(q) && !removable(level, q)) continue;
            if (!BlockProtection.canPlace(level, q) || !DisasterUtil.takeBlockBudget(d)) return;
            BlockState rock;
            int n = rand.nextInt(10);
            if (CatastropheConfig.METEOR_ORES.get() && n < 2) rock = Blocks.RAW_IRON_BLOCK.defaultBlockState();
            else if (n < 5) rock = Blocks.MAGMA_BLOCK.defaultBlockState();
            else rock = Blocks.BLACKSTONE.defaultBlockState();
            level.setBlock(q, rock, 3);
        }
    }

    /** Se puede quitar si no es aire ni liquido, no toca agua o lava y no esta protegido. */
    private static boolean removable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(pos.relative(dir)).isEmpty()) return false;
        }
        return BlockProtection.canModify(level, pos, s);
    }
}
