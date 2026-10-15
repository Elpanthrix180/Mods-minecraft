package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Inundacion: lluvia intensa y el agua sube poco a poco en las zonas bajas de la zona.
 * - Solo coloca agua en huecos VACIOS: nunca rompe bloques para hacer sitio.
 * - El nivel maximo y el radio estan limitados, y cada bloque cuenta para maxBloquesPorEvento.
 * - Se recuerdan todas las posiciones con agua puesta para retirarla en la recuperacion.
 * - Colocar agua necesita que la destruccion no este en OFF; si esta en OFF solo hay lluvia y corrientes.
 */
public class FloodEvent extends CatastropheEvent {

    private static final String WATER_KEY = "water";

    /** Copia en memoria de las posiciones con agua (para no copiar arrays en cada bloque). */
    private CompoundTag cacheOwner;
    private LongArrayList placed = new LongArrayList();

    private LongArrayList placed(CatastropheSavedData d) {
        if (cacheOwner != d.eventData) {
            placed = new LongArrayList(d.eventData.getLongArray(WATER_KEY));
            cacheOwner = d.eventData;
        }
        return placed;
    }

    private void save(CatastropheSavedData d) {
        d.eventData.putLongArray(WATER_KEY, placed(d).toLongArray());
        d.setDirty();
    }

    private static int floodRadius() {
        return Math.min(CatastropheConfig.FLOOD_RADIUS.get(), DisasterUtil.radius());
    }

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        switch (phase) {
            case PREPARATION -> DisasterUtil.startRain(level, false);
            case ACTIVE -> {
                d.eventData.putInt("base", findBaseLevel(level, d));
                d.eventData.putDouble("flow", level.getRandom().nextDouble() * Math.PI * 2);
                d.eventData.putLong("activeStart", level.getGameTime());
                d.setDirty();
            }
            case ENDING, RECOVERY -> {
                save(d);
                if (phase == CatastrophePhase.RECOVERY) DisasterUtil.clearWeather(level);
            }
            default -> { }
        }
    }

    /** Nivel de partida: la superficie mas baja encontrada en la zona (o el nivel del mar). */
    private static int findBaseLevel(ServerLevel level, CatastropheSavedData d) {
        int lowest = Integer.MAX_VALUE;
        RandomSource r = level.getRandom();
        int fr = floodRadius();
        for (int i = 0; i < 24; i++) {
            int x = d.centerX + r.nextInt(2 * fr + 1) - fr;
            int z = d.centerZ + r.nextInt(2 * fr + 1) - fr;
            if (!level.isLoaded(new BlockPos(x, d.centerY, z))) continue;
            lowest = Math.min(lowest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        }
        if (lowest == Integer.MAX_VALUE) lowest = d.centerY;
        return Math.max(lowest, level.getSeaLevel());
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase == CatastrophePhase.RECOVERY) {
            if (CatastropheConfig.FLOOD_TEMPORARY.get()) removeSome(level, d, 40);
            return;
        }
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        double f = CatastropheConfig.intensityFactor();

        if (now % 5 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.2, p.getZ(), (int) (20 * f), 5, 0.2, 5, 0.1);
            }
        }
        if (CatastropheConfig.FLOOD_CURRENTS.get()) pushInWater(level, d, f);

        if (phase != CatastrophePhase.ACTIVE || CatastropheConfig.destructionOff()) return;
        // El agua sube un bloque cada cierto tiempo hasta la altura maxima.
        long activeTicks = CatastropheConfig.DURATION_SECONDS.get(d.activeType).get() * 20L;
        int maxRise = CatastropheConfig.FLOOD_HEIGHT.get();
        long elapsed = now - d.eventData.getLong("activeStart");
        int rise = (int) Math.min(maxRise, 1 + elapsed * maxRise / Math.max(1, activeTicks * 2 / 3));
        int waterTop = d.eventData.getInt("base") + rise;

        int perTick = (int) Math.max(2, 6 * f);
        RandomSource r = level.getRandom();
        int fr = floodRadius();
        for (int i = 0; i < perTick * 3 && perTick > 0; i++) {
            int x = d.centerX + r.nextInt(2 * fr + 1) - fr;
            int z = d.centerZ + r.nextInt(2 * fr + 1) - fr;
            int dx = x - d.centerX, dz = z - d.centerZ;
            if (dx * dx + dz * dz > fr * fr) continue;
            BlockPos column = new BlockPos(x, d.centerY, z);
            if (!level.isLoaded(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z); // primer hueco encima del suelo o del agua
            if (y >= waterTop) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isEmptyBlock(pos) || !BlockProtection.canPlace(level, pos)) continue;
            if (!DisasterUtil.takeBlockBudget(d)) break;
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            placed(d).add(pos.asLong());
            perTick--;
        }
        if (now % 20 == 0 && placed(d).size() != d.eventData.getLongArray(WATER_KEY).length) save(d);
    }

    /** Corriente: empuja suavemente en una direccion a quien este en el agua dentro de la zona. */
    private static void pushInWater(ServerLevel level, CatastropheSavedData d, double f) {
        double flow = d.eventData.getDouble("flow");
        Vec3 push = new Vec3(Math.cos(flow), 0, Math.sin(flow)).scale(0.03 * f);
        for (LivingEntity e : DisasterUtil.livingInArea(level, d)) {
            if (!e.isInWater()) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
    }

    /** Retira agua empezando por la ultima colocada (la mas alta), unas pocas por tick. */
    private void removeSome(ServerLevel level, CatastropheSavedData d, int max) {
        LongArrayList list = placed(d);
        int removed = 0;
        while (!list.isEmpty() && removed < max) {
            BlockPos pos = BlockPos.of(list.removeLong(list.size() - 1));
            removeWater(level, d, pos);
            removed++;
        }
        if (removed > 0) save(d);
    }

    private static void removeWater(ServerLevel level, CatastropheSavedData d, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        removeIfFloodSource(level, pos);
        // El juego puede crear fuentes nuevas junto a las nuestras: se quitan tambien las vecinas
        // que esten por encima del nivel de partida (el agua natural suele estar por debajo).
        int base = d.eventData.getInt("base");
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos n = pos.relative(dir);
            if (n.getY() >= base && level.isLoaded(n)) removeIfFloodSource(level, n);
        }
    }

    private static void removeIfFloodSource(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.is(Fluids.WATER) && fluid.isSource() && level.getBlockState(pos).is(Blocks.WATER)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        DisasterUtil.clearWeather(level);
        if (CatastropheConfig.FLOOD_TEMPORARY.get()) removeSome(level, d, Integer.MAX_VALUE);
        cacheOwner = null;
    }
}
