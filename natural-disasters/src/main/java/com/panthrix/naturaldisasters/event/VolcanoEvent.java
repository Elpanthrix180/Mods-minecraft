package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.core.VolcanoRegistry;
import com.panthrix.naturaldisasters.network.ModNetwork;
import com.panthrix.naturaldisasters.worldgen.VolcanoBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

/**
 * Erupcion volcanica. Usa el volcan conocido mas cercano al jugador elegido. Si no hay ninguno
 * y la destruccion lo permite, crea uno pequeno durante la fase de preparacion.
 * Efectos: humo, ceniza, lava y llamas en el crater, temblores, calor en la zona de peligro.
 * Terreno (si la destruccion no es OFF): rocas volcanicas que caen como bloques y coladas de lava
 * (temporales por defecto: las fuentes de lava se retiran al terminar).
 */
public class VolcanoEvent extends CatastropheEvent {

    private static final String LAVA_KEY = "lava";

    @Override
    public void chooseLocation(ServerLevel level, CatastropheSavedData d) {
        BlockPos crater = VolcanoRegistry.get(level).nearest(d.centerX, d.centerZ, CatastropheConfig.VOLCANO_SEARCH_RADIUS.get());
        if (crater != null) {
            d.centerX = crater.getX();
            d.centerY = crater.getY();
            d.centerZ = crater.getZ();
            d.eventData.putBoolean("hasVolcano", true);
        } else {
            // Sin volcan conocido: la erupcion sera a cierta distancia del jugador.
            RandomSource r = level.getRandom();
            double a = r.nextDouble() * Math.PI * 2;
            d.centerX += (int) (Math.cos(a) * 28);
            d.centerZ += (int) (Math.sin(a) * 28);
            d.eventData.putBoolean("hasVolcano", false);
        }
        d.setDirty();
    }

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase == CatastrophePhase.PREPARATION && !d.eventData.getBoolean("hasVolcano")
                && !CatastropheConfig.destructionOff() && CatastropheConfig.VOLCANO_CREATE_IF_NONE.get()) {
            BlockPos center = new BlockPos(d.centerX, d.centerY, d.centerZ);
            if (level.isLoaded(center)) {
                BlockPos crater = VolcanoBuilder.build(level, center, level.getRandom(), true);
                if (crater != null) {
                    d.centerY = crater.getY();
                    d.eventData.putBoolean("hasVolcano", true);
                    VolcanoRegistry.get(level).add(crater);
                    d.setDirty();
                }
            }
        }
        if (phase == CatastrophePhase.RECOVERY) removeLava(level, d);
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.PREPARATION && phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        if (!level.isLoaded(new BlockPos(d.centerX, d.centerY, d.centerZ))) return; // sin cargar: no se hace nada
        double f = CatastropheConfig.intensityFactor();
        boolean active = phase == CatastrophePhase.ACTIVE;
        double x = d.centerX + 0.5, y = d.centerY, z = d.centerZ + 0.5;
        RandomSource r = level.getRandom();

        // Columna de humo (visible desde lejos) y, en la fase activa, lava y llamas.
        if (now % 3 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 1, z, active ? 6 : 2, 1.5, 0.5, 1.5, 0.02);
            if (active) {
                level.sendParticles(ParticleTypes.LAVA, x, y, z, (int) (10 * f), 1.5, 0.5, 1.5, 0.0);
                level.sendParticles(ParticleTypes.FLAME, x, y + 1, z, (int) (8 * f), 1.0, 2.0, 1.0, 0.08);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 4, z, 10, 3, 3, 3, 0.05);
            }
        }
        if (now % 6 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.ASH, p.getX(), p.getY() + 3, p.getZ(), active ? 40 : 10, 8, 3, 8, 0.0);
                if (active) level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 3, p.getZ(), 15, 8, 3, 8, 0.0);
            }
        }
        if (now % 20 == 0) {
            BlockPos at = BlockPos.containing(x, y, z);
            level.playSound(null, at, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 3.0f, 0.6f);
            if (active) level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.5f, 0.3f);
            if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
                for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) ModNetwork.sendShake(p, active ? (float) (0.6 * f) : 0.2f, 25);
            }
        }
        if (phase == CatastrophePhase.PREPARATION) return;

        if (now % 20 == 0) heatDamage(level, d, x, y, z);

        if (CatastropheConfig.destructionOff()) return;
        int bombInterval = (int) Math.max(5, 20 / f);
        if (phase == CatastrophePhase.ENDING) bombInterval *= 3;
        if (CatastropheConfig.VOLCANO_BOMBS.get() && now % bombInterval == 0) launchBomb(level, d, x, y, z, r);
        if (active && CatastropheConfig.VOLCANO_LAVA_FLOWS.get() && d.eventData.getBoolean("hasVolcano") && now % 40 == 0) {
            overflowLava(level, d, r);
        }
    }

    /** Zona de peligro: quema y dana a quien este cerca del crater. */
    private static void heatDamage(ServerLevel level, CatastropheSavedData d, double x, double y, double z) {
        int danger = CatastropheConfig.VOLCANO_DANGER_RADIUS.get();
        AABB box = new AABB(x - danger, y - danger, z - danger, x + danger, y + danger, z + danger);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!DisasterUtil.canHurt(e) || e.fireImmune()) continue;
            double dist = Math.sqrt(e.distanceToSqr(x, y, z));
            if (dist > danger) continue;
            if (dist < danger * 0.5) e.setSecondsOnFire(3);
            e.hurt(level.damageSources().hotFloor(), 1.0f);
        }
    }

    /** Lanza una roca volcanica (bloque que cae) desde el crater en una direccion al azar. */
    private static void launchBomb(ServerLevel level, CatastropheSavedData d, double x, double y, double z, RandomSource r) {
        if (!DisasterUtil.takeBlockBudget(d)) return;
        BlockPos start = BlockPos.containing(x, y + 2, z);
        if (!level.isEmptyBlock(start)) return;
        BlockState rock = r.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        level.setBlock(start, rock, 3);
        FallingBlockEntity bomb = FallingBlockEntity.fall(level, start, rock);
        double a = r.nextDouble() * Math.PI * 2;
        double speed = 0.4 + r.nextDouble() * 0.6;
        bomb.setDeltaMovement(Math.cos(a) * speed, 0.9 + r.nextDouble() * 0.6, Math.sin(a) * speed);
        bomb.hurtMarked = true;
        bomb.dropItem = false;
        if (CatastropheConfig.DAMAGE_PLAYERS.get() || CatastropheConfig.DAMAGE_MOBS.get()) bomb.setHurtsEntities(2.0f, 20);
    }

    /** Pone una fuente de lava en el borde del crater para que baje por la ladera. */
    private static void overflowLava(ServerLevel level, CatastropheSavedData d, RandomSource r) {
        long[] old = d.eventData.getLongArray(LAVA_KEY);
        if (old.length >= 8 * CatastropheConfig.intensityFactor()) return;
        double a = r.nextDouble() * Math.PI * 2;
        int lx = d.centerX + (int) Math.round(Math.cos(a) * 4);
        int lz = d.centerZ + (int) Math.round(Math.sin(a) * 4);
        BlockPos pos = new BlockPos(lx, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, lx, lz), lz);
        if (!level.isLoaded(pos) || !level.isEmptyBlock(pos) || !BlockProtection.canPlace(level, pos)) return;
        if (!DisasterUtil.takeBlockBudget(d)) return;
        level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
        long[] grown = Arrays.copyOf(old, old.length + 1);
        grown[old.length] = pos.asLong();
        d.eventData.putLongArray(LAVA_KEY, grown);
        d.setDirty();
    }

    private static void removeLava(ServerLevel level, CatastropheSavedData d) {
        if (!CatastropheConfig.VOLCANO_LAVA_TEMPORARY.get()) return;
        for (long l : d.eventData.getLongArray(LAVA_KEY)) {
            BlockPos pos = BlockPos.of(l);
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LAVA)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        d.eventData.remove(LAVA_KEY);
        d.setDirty();
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        removeLava(level, d);
    }
}
