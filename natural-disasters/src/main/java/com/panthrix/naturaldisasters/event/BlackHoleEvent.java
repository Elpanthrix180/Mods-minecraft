package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Agujero negro. Aparece flotando sobre el suelo, cerca del jugador elegido.
 * Siempre: esfera oscura con disco de particulas que gira, sonido, temblor, y atrae con fuerza a
 * jugadores, criaturas y objetos tirados; quien llega al centro recibe mucho dano.
 * Con destruccion PARTIAL/INTENSE: absorbe bloques a su alrededor en una esfera que crece poco a poco
 * (con limites y respetando la proteccion). Al terminar se colapsa con un destello.
 */
public class BlackHoleEvent extends CatastropheEvent {

    @Override
    public void chooseLocation(ServerLevel level, CatastropheSavedData d) {
        RandomSource r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        d.centerX += (int) (Math.cos(a) * 14);
        d.centerZ += (int) (Math.sin(a) * 14);
        if (level.isLoaded(new BlockPos(d.centerX, d.centerY, d.centerZ))) {
            d.centerY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, d.centerX, d.centerZ);
        }
        d.centerY += CatastropheConfig.BLACK_HOLE_HEIGHT.get();
        d.setDirty();
    }

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase == CatastrophePhase.ACTIVE) {
            d.eventData.putLong("activeStart", level.getGameTime());
            d.setDirty();
        }
        if (phase == CatastrophePhase.RECOVERY) collapse(level, d);
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.PREPARATION && phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        double cx = d.centerX + 0.5, cy = d.centerY + 0.5, cz = d.centerZ + 0.5;
        if (!level.isLoaded(BlockPos.containing(cx, cy, cz))) return;
        double f = CatastropheConfig.intensityFactor();
        // Tamano visual y fuerza segun la fase.
        double power = switch (phase) {
            case PREPARATION -> 0.25;
            case ENDING -> 0.5;
            default -> 1.0;
        };

        if (now % 2 == 0) draw(level, cx, cy, cz, now, power);
        if (now % 40 == 0) {
            BlockPos at = BlockPos.containing(cx, cy, cz);
            level.playSound(null, at, SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, (float) (2.0 * power) + 0.5f, 0.5f);
            level.playSound(null, at, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, (float) (2.0 * power), 0.5f);
            if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
                for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) ModNetwork.sendShake(p, (float) (0.5 * power * f), 45);
            }
        }
        if (phase == CatastrophePhase.PREPARATION) return;

        pull(level, cx, cy, cz, power * f, now);

        if (CatastropheConfig.destructionOff()) return;
        // La esfera de absorcion crece durante la fase activa hasta radioAbsorcion.
        long activeTicks = Math.max(1, CatastropheConfig.DURATION_SECONDS.get(d.activeType).get() * 20L);
        double grow = Math.min(1.0, (now - d.eventData.getLong("activeStart")) / (double) activeTicks * 1.5);
        int maxR = CatastropheConfig.BLACK_HOLE_MAX_RADIUS.get();
        double radius = 1 + (maxR - 1) * grow;
        boolean intense = CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE;
        int perTick = (int) Math.max(1, (intense ? 6 : 2) * f * power);
        RandomSource r = level.getRandom();
        for (int i = 0; i < perTick; i++) absorbBlock(level, d, cx, cy, cz, radius, r, intense);
    }

    private static void draw(ServerLevel level, double x, double y, double z, long now, double power) {
        double core = 1.2 * power + 0.3;
        // Esfera oscura.
        level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, (int) (12 * power) + 2, core * 0.5, core * 0.5, core * 0.5, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, (int) (10 * power) + 2, core, core, core, 0.05);
        // Disco de acrecion que gira.
        double spin = now * 0.25;
        int points = 16;
        for (int k = 0; k < points; k++) {
            double a = spin + k * (Math.PI * 2 / points);
            double rad = (2.5 + (k % 3) * 0.7) * (0.4 + 0.6 * power);
            level.sendParticles(k % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME,
                    x + Math.cos(a) * rad, y + Math.sin(a * 2) * 0.15, z + Math.sin(a) * rad, 1, 0, 0, 0, 0);
        }
    }

    /** Atrae entidades y objetos hacia el centro; el dano solo ocurre muy cerca del centro. */
    private static void pull(ServerLevel level, double cx, double cy, double cz, double strength, long now) {
        int radius = CatastropheConfig.BLACK_HOLE_PULL_RADIUS.get();
        AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        for (Entity e : level.getEntities((Entity) null, box, en -> en instanceof LivingEntity || en instanceof ItemEntity
                || en instanceof FallingBlockEntity)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 toCenter = new Vec3(cx - e.getX(), cy - e.getY(), cz - e.getZ());
            double dist = toCenter.length();
            if (dist > radius || dist < 0.01) continue;
            double closeness = 1.0 - dist / radius;
            Vec3 dir = toCenter.normalize();
            // Atraccion + un poco de giro alrededor (orbita).
            Vec3 around = new Vec3(-dir.z, 0, dir.x).scale(0.04 * strength * closeness);
            Vec3 add = dir.scale(0.05 + 0.12 * strength * closeness).add(around);
            Vec3 v = e.getDeltaMovement().add(add);
            double max = 1.2;
            if (v.length() > max) v = v.normalize().scale(max);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0; // la caida tras ser arrastrado no debe matar por si sola

            if (dist < 1.8) {
                if (e instanceof LivingEntity le && now % 10 == 0 && DisasterUtil.canHurt(le)) {
                    le.hurt(level.damageSources().magic(), (float) (4 * strength));
                } else if (e instanceof ItemEntity item && CatastropheConfig.BLACK_HOLE_CONSUMES_ITEMS.get()) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY(), item.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
                    item.discard();
                } else if (e instanceof FallingBlockEntity fb && dist < 1.0) {
                    fb.discard(); // los bloques absorbidos desaparecen en el centro
                }
            }
        }
    }

    /** Absorbe un bloque al azar dentro de la esfera de absorcion. */
    private static void absorbBlock(ServerLevel level, CatastropheSavedData d, double cx, double cy, double cz,
                                    double radius, RandomSource r, boolean intense) {
        double dx = (r.nextDouble() * 2 - 1) * radius;
        double dy = (r.nextDouble() * 2 - 1) * radius;
        double dz = (r.nextDouble() * 2 - 1) * radius;
        if (dx * dx + dy * dy + dz * dz > radius * radius) return;
        BlockPos pos = BlockPos.containing(cx + dx, cy + dy, cz + dz);
        if (!level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return;
        if (!BlockProtection.canModify(level, pos, s) || !DisasterUtil.takeBlockBudget(d)) return;
        if (intense) {
            // En modo intenso el bloque sale volando hacia el centro antes de desaparecer.
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, s);
            fb.dropItem = false;
            fb.setNoGravity(true);
            Vec3 dir = new Vec3(cx - fb.getX(), cy - fb.getY(), cz - fb.getZ()).normalize().scale(0.4);
            fb.setDeltaMovement(dir);
            fb.hurtMarked = true;
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.1);
        }
    }

    /** Final: el agujero negro se colapsa con un destello y suelta lo que tuviera atrapado. */
    private static void collapse(ServerLevel level, CatastropheSavedData d) {
        double cx = d.centerX + 0.5, cy = d.centerY + 0.5, cz = d.centerZ + 0.5;
        if (!level.isLoaded(BlockPos.containing(cx, cy, cz))) return;
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 120, 2, 2, 2, 0.6);
        level.playSound(null, BlockPos.containing(cx, cy, cz), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.0f, 1.2f);
        // Los bloques que seguian flotando hacia el centro desaparecen.
        int radius = CatastropheConfig.BLACK_HOLE_PULL_RADIUS.get();
        AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        for (FallingBlockEntity fb : level.getEntitiesOfClass(FallingBlockEntity.class, box, FallingBlockEntity::isNoGravity)) {
            fb.discard();
        }
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        collapse(level, d);
    }
}
