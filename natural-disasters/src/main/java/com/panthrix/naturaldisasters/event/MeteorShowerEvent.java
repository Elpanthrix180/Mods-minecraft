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

import java.util.ArrayList;
import java.util.List;

/**
 * Lluvia de meteoritos gigantes.
 * - Preparacion: el cielo se tine de naranja y se ven estrellas fugaces.
 * - Cada meteorito es una gran bola de fuego (particulas, sin entidades) con una estela larga que
 *   baja del cielo en diagonal. Se oye un silbido al acercarse.
 * - Impacto: varias explosiones, onda de choque fuerte, temblor de camara y, con destruccion
 *   PARTIAL/INTENSE, un crater grande con paredes de obsidiana y magma, piedra negra y lava en el fondo.
 * Los meteoritos en vuelo se guardan en el mundo.
 */
public class MeteorShowerEvent extends CatastropheEvent {

    private static final String KEY = "meteors";
    private static final int MAX_IN_FLIGHT = 12;

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        tintSky(level, d, phase, now);

        if (phase == CatastrophePhase.PREPARATION && now % 8 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 45, p.getZ(), 6, 35, 6, 35, 0.3);
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 50, p.getZ(), 4, 40, 4, 40, 0.1);
            }
        }
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING && phase != CatastrophePhase.RECOVERY) return;

        ListTag meteors = d.eventData.getList(KEY, Tag.TAG_COMPOUND);
        if (phase != CatastrophePhase.RECOVERY) {
            double perMinute = CatastropheConfig.METEORS_PER_MINUTE.get() * CatastropheConfig.intensityFactor();
            if (phase == CatastrophePhase.ENDING) perMinute /= 3;
            int interval = (int) Math.max(10, 1200 / Math.max(1, perMinute));
            if (now % interval == 0 && meteors.size() < MAX_IN_FLIGHT) spawnMeteor(level, d, meteors, now);
        }

        for (int i = meteors.size() - 1; i >= 0; i--) {
            CompoundTag m = meteors.getCompound(i);
            double t = (now - m.getLong("t0")) / (double) m.getInt("dur");
            float size = m.getFloat("size");
            if (t >= 1.0) {
                impact(level, d, BlockPos.of(m.getLong("target")), size);
                meteors.remove(i);
                continue;
            }
            // Aceleracion: va cada vez mas rapido al acercarse al suelo.
            double e = t * t;
            Vec3 start = new Vec3(m.getDouble("sx"), m.getDouble("sy"), m.getDouble("sz"));
            Vec3 end = new Vec3(m.getDouble("tx"), m.getDouble("ty"), m.getDouble("tz"));
            Vec3 pos = start.lerp(end, e);
            Vec3 back = start.subtract(end).normalize();
            drawMeteor(level, pos, back, size);
            if (now % 20 == 0) {
                level.playSound(null, BlockPos.containing(pos), SoundEvents.FIRECHARGE_USE, SoundSource.WEATHER, 4.0f, 0.4f);
            }
        }
        d.eventData.put(KEY, meteors);
        d.setDirty();
    }

    /** Cielo naranja: sube en la preparacion, maximo durante la lluvia y se apaga al final. */
    private static void tintSky(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (!CatastropheConfig.METEOR_SKY_TINT.get() || now % 20 != 0) return;
        float strength = switch (phase) {
            case WARNING -> 0.25f;
            case PREPARATION -> 0.7f;
            case ACTIVE -> 1.0f;
            case ENDING -> 0.5f;
            default -> 0f;
        };
        if (strength <= 0f) return;
        // El cielo se ve desde lejos: se avisa a los jugadores hasta tres veces el radio.
        double far = DisasterUtil.radius() * 3.0;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - d.centerX, dz = p.getZ() - d.centerZ;
            if (dx * dx + dz * dz <= far * far) ModNetwork.sendSkyTint(p, strength, 60);
        }
    }

    /** Gran bola de fuego con estela. */
    private static void drawMeteor(ServerLevel level, Vec3 pos, Vec3 back, float size) {
        double r = 1.2 * size;
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, (int) (25 * size), r, r, r, 0.05);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, (int) (4 * size), r * 0.6, r * 0.6, r * 0.6, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, (int) (8 * size), r, r, r, 0.02);
        // Estela larga hacia atras.
        for (int k = 1; k <= 8; k++) {
            Vec3 p = pos.add(back.scale(k * 2.2 * size));
            double spread = r * (1.0 - k / 10.0);
            level.sendParticles(k < 4 ? ParticleTypes.FLAME : ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z,
                    (int) (6 * size), spread, spread, spread, 0.01);
        }
    }

    private static void spawnMeteor(ServerLevel level, CatastropheSavedData d, ListTag meteors, long now) {
        RandomSource r = level.getRandom();
        BlockPos target = DisasterUtil.randomSurface(level, d, r);
        if (target == null) return;
        double a = r.nextDouble() * Math.PI * 2;
        CompoundTag m = new CompoundTag();
        m.putDouble("sx", target.getX() + 0.5 + Math.cos(a) * 60);
        m.putDouble("sy", Math.min(level.getMaxBuildHeight() - 2, target.getY() + 120));
        m.putDouble("sz", target.getZ() + 0.5 + Math.sin(a) * 60);
        m.putDouble("tx", target.getX() + 0.5);
        m.putDouble("ty", target.getY());
        m.putDouble("tz", target.getZ() + 0.5);
        m.putLong("target", target.asLong());
        m.putLong("t0", now);
        m.putInt("dur", 60 + r.nextInt(40));
        m.putFloat("size", (float) ((1.5 + r.nextDouble() * 1.5) * Math.min(1.5, CatastropheConfig.intensityFactor())));
        meteors.add(m);
    }

    private static void impact(ServerLevel level, CatastropheSavedData d, BlockPos pos, float size) {
        if (!level.isLoaded(pos)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        RandomSource rand = level.getRandom();
        for (int i = 0; i < 3 + (int) size; i++) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x + rand.nextGaussian() * 2, y + rand.nextDouble() * 2,
                    z + rand.nextGaussian() * 2, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.LAVA, x, y + 1, z, (int) (40 * size), 3, 1, 3, 0.0);
        level.sendParticles(ParticleTypes.FLAME, x, y + 1, z, (int) (60 * size), 3, 2, 3, 0.25);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 2, z, (int) (25 * size), 3, 1, 3, 0.05);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 8.0f, 0.5f);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0f, 0.4f);

        // Onda de choque fuerte.
        double reach = 6 + 4 * size;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(reach), LivingEntity::isAlive)) {
            double dist = Math.sqrt(e.distanceToSqr(x, y, z));
            if (dist > reach) continue;
            double k = 1.0 - dist / reach;
            if (DisasterUtil.canHurt(e)) {
                e.hurt(level.damageSources().explosion(null, null), (float) (12 * size * k) + 1);
                if (k > 0.4) e.setSecondsOnFire(5);
            }
            Vec3 away = e.position().subtract(x, y, z).normalize().scale(1.6 * k);
            e.setDeltaMovement(e.getDeltaMovement().add(away.x, 0.7 * k, away.z));
            e.hurtMarked = true;
        }
        if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
            for (ServerPlayer p : level.players()) {
                double dd = p.distanceToSqr(x, y, z);
                if (dd < 80 * 80) ModNetwork.sendShake(p, (float) (2.5 * size * (1.0 - Math.sqrt(dd) / 80)), 30);
            }
        }

        if (CatastropheConfig.destructionOff()) return;
        makeCrater(level, d, pos, size);
    }

    /**
     * Crater: cuenco excavado; las paredes se cubren de obsidiana, magma y piedra negra,
     * y en el fondo queda un charco de lava con el nucleo del meteorito.
     */
    private static void makeCrater(ServerLevel level, CatastropheSavedData d, BlockPos center, float size) {
        int limit = CatastropheConfig.METEOR_MAX_BLOCKS.get();
        int r = Math.max(2, Math.round(CatastropheConfig.METEOR_CRATER_RADIUS.get() * size * 0.7f));
        int depth = Math.max(2, (int) (r * 0.7));
        RandomSource rand = level.getRandom();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<BlockPos> shell = new ArrayList<>();

        // 1) Excavar el cuenco (elipsoide aplastado) y todo lo que haya encima.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double hd = Math.sqrt(dx * dx + dz * dz) / r;
                if (hd > 1.0) continue;
                int bottom = (int) Math.round(-depth * Math.sqrt(1.0 - hd * hd));
                for (int dy = bottom; dy <= 4; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!removable(level, p)) continue;
                    if (!DisasterUtil.takeBlockBudget(d, limit)) return;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                shell.add(new BlockPos(center.getX() + dx, center.getY() + bottom - 1, center.getZ() + dz));
            }
        }
        // 2) Revestir el fondo y las paredes con roca fundida.
        for (BlockPos q : shell) {
            BlockState old = level.getBlockState(q);
            if (!old.isAir() && !removable(level, q)) continue;
            if (!BlockProtection.canPlace(level, q) || !DisasterUtil.takeBlockBudget(d, limit)) return;
            int n = rand.nextInt(10);
            BlockState rock = n < 4 ? Blocks.OBSIDIAN.defaultBlockState()
                    : n < 7 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState();
            level.setBlock(q, rock, 3);
        }
        // 3) Charco de lava en el centro del fondo, con el nucleo del meteorito debajo.
        int poolR = Math.max(1, r / 3);
        BlockPos floor = center.below(depth);
        for (int dx = -poolR; dx <= poolR; dx++) {
            for (int dz = -poolR; dz <= poolR; dz++) {
                if (dx * dx + dz * dz > poolR * poolR) continue;
                BlockPos lava = floor.offset(dx, 0, dz);
                BlockPos below = lava.below();
                if (!level.isEmptyBlock(lava) || !BlockProtection.canPlace(level, lava)) continue;
                if (!DisasterUtil.takeBlockBudget(d, limit)) return;
                if (removable(level, below) || level.isEmptyBlock(below)) {
                    boolean ore = CatastropheConfig.METEOR_ORES.get() && rand.nextInt(4) == 0;
                    level.setBlock(below, ore ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                }
                level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 3);
            }
        }
    }

    /** Se puede quitar si no es aire ni liquido, no toca agua o lava y no esta protegido. */
    private static boolean removable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (level.getFluidState(n).is(net.minecraft.world.level.material.Fluids.WATER)
                    || level.getFluidState(n).is(net.minecraft.world.level.material.Fluids.FLOWING_WATER)) return false;
        }
        return BlockProtection.canModify(level, pos, s);
    }
}
