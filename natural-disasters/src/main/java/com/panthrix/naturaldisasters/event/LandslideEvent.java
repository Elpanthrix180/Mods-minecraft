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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Deslizamiento de tierra. Busca la ladera mas empinada cerca del jugador elegido.
 * Efectos (siempre): retumbo, polvo, temblor leve y empuje ladera abajo a quien este en ella.
 * Terreno (si destruccion no es OFF): las capas superficiales de tierra, grava, arena y piedra
 * se convierten en bloques que caen y se lanzan ladera abajo (movimiento real del terreno),
 * pudiendo bloquear caminos. Limites: radio, profundidad, maxBloques y maxBloquesPorEvento.
 */
public class LandslideEvent extends CatastropheEvent {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public void chooseLocation(ServerLevel level, CatastropheSavedData d) {
        RandomSource r = level.getRandom();
        int search = DisasterUtil.radius();
        int bestX = d.centerX, bestZ = d.centerZ, bestSlope = -1;
        for (int i = 0; i < 64; i++) {
            int x = d.centerX + r.nextInt(2 * search + 1) - search;
            int z = d.centerZ + r.nextInt(2 * search + 1) - search;
            int slope = slopeAt(level, x, z);
            if (slope > bestSlope) {
                bestSlope = slope;
                bestX = x;
                bestZ = z;
            }
        }
        d.centerX = bestX;
        d.centerZ = bestZ;
        if (level.isLoaded(new BlockPos(bestX, d.centerY, bestZ))) {
            d.centerY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bestX, bestZ);
        }
        d.eventData.putInt("slope", bestSlope);
        d.setDirty();
    }

    /** Diferencia de altura entre el punto mas alto y el mas bajo a 3 bloques. -1 si no esta cargado. */
    private static int slopeAt(ServerLevel level, int x, int z) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int[][] offs = {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] o : offs) {
            if (!level.isLoaded(new BlockPos(x + o[0], 64, z + o[1]))) return -1;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0], z + o[1]);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min;
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.PREPARATION && phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        if (!level.isLoaded(new BlockPos(d.centerX, d.centerY, d.centerZ))) return;
        double f = CatastropheConfig.intensityFactor();
        boolean active = phase == CatastrophePhase.ACTIVE;
        int sr = CatastropheConfig.SLIDE_RADIUS.get();
        RandomSource r = level.getRandom();

        if (now % 20 == 0) {
            BlockPos at = new BlockPos(d.centerX, d.centerY, d.centerZ);
            level.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, active ? 3.0f : 1.0f, 0.4f);
            level.playSound(null, at, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, active ? 2.0f : 0.7f, 0.5f);
            if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
                for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) ModNetwork.sendShake(p, active ? (float) (0.4 * f) : 0.15f, 25);
            }
        }
        if (now % 4 == 0) {
            BlockState dust = level.getBlockState(new BlockPos(d.centerX, d.centerY - 1, d.centerZ));
            if (dust.isAir()) dust = Blocks.DIRT.defaultBlockState();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), d.centerX + 0.5, d.centerY + 1, d.centerZ + 0.5,
                    active ? (int) (25 * f) : 6, sr * 0.5, 1, sr * 0.5, 0.1);
        }
        if (phase == CatastrophePhase.PREPARATION) return;

        if (now % 5 == 0) pushEntities(level, d, sr, f);

        if (CatastropheConfig.destructionOff()) return;
        int moved = d.eventData.getInt("moved");
        if (moved >= CatastropheConfig.SLIDE_MAX_BLOCKS.get()) return;
        int perTick = active ? (int) Math.max(1, 3 * f) : 1;
        for (int i = 0; i < perTick; i++) {
            moved += slideOne(level, d, r, sr);
        }
        d.eventData.putInt("moved", moved);
        d.setDirty();
    }

    /** Mueve los bloques superficiales de un punto ladera abajo. Devuelve cuantos se han movido. */
    private static int slideOne(ServerLevel level, CatastropheSavedData d, RandomSource r, int sr) {
        int x = d.centerX + r.nextInt(2 * sr + 1) - sr;
        int z = d.centerZ + r.nextInt(2 * sr + 1) - sr;
        if ((x - d.centerX) * (x - d.centerX) + (z - d.centerZ) * (z - d.centerZ) > sr * sr) return 0;
        if (!level.isLoaded(new BlockPos(x, d.centerY, z))) return 0;

        // Direccion ladera abajo: hacia el vecino mas bajo.
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int bestDx = 0, bestDz = 0, drop = 0;
        for (int[] o : DIRS) {
            if (!level.isLoaded(new BlockPos(x + o[0] * 2, h, z + o[1] * 2))) continue;
            int hn = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0] * 2, z + o[1] * 2);
            if (h - hn > drop) {
                drop = h - hn;
                bestDx = o[0];
                bestDz = o[1];
            }
        }
        if (drop < 2) return 0; // solo se desliza lo que esta en pendiente

        int layers = 1 + r.nextInt(CatastropheConfig.SLIDE_DEPTH.get());
        int count = 0;
        for (int k = 0; k < layers; k++) {
            BlockPos pos = new BlockPos(x, h - 1 - k, z);
            BlockState s = level.getBlockState(pos);
            if (!canSlide(s) || !BlockProtection.canModify(level, pos, s)) break;
            if (!DisasterUtil.takeBlockBudget(d)) break;
            // La piedra se rompe en grava o adoquin al deslizarse; la hierba pierde la hierba.
            BlockState moving = s.is(BlockTags.BASE_STONE_OVERWORLD)
                    ? (r.nextBoolean() ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState())
                    : s.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : s;
            level.setBlock(pos, moving, 3);
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, moving);
            fb.setDeltaMovement(bestDx * (0.35 + r.nextDouble() * 0.3), 0.15, bestDz * (0.35 + r.nextDouble() * 0.3));
            fb.hurtMarked = true;
            if (CatastropheConfig.DAMAGE_PLAYERS.get() || CatastropheConfig.DAMAGE_MOBS.get()) fb.setHurtsEntities(1.0f, 6);
            count++;
        }
        // Lo que habia encima (hierba alta, flores) se rompe.
        BlockPos above = new BlockPos(x, h, z);
        BlockState top = level.getBlockState(above);
        if (count > 0 && !top.isAir() && top.canBeReplaced() && top.getFluidState().isEmpty()
                && BlockProtection.canModify(level, above, top)) {
            level.destroyBlock(above, false);
        }
        return count;
    }

    private static boolean canSlide(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.SNOW_BLOCK)) {
            return true;
        }
        return CatastropheConfig.SLIDE_BUILDINGS.get()
                && (s.is(BlockTags.PLANKS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(Blocks.BRICKS)
                || s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.LOGS));
    }

    /** Empuja ladera abajo a quien este en la zona del deslizamiento. */
    private static void pushEntities(ServerLevel level, CatastropheSavedData d, int sr, double f) {
        AABB box = new AABB(d.centerX - sr, d.centerY - sr, d.centerZ - sr, d.centerX + sr, d.centerY + sr, d.centerZ + sr);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (!e.onGround()) continue;
            int x = e.getBlockX(), z = e.getBlockZ();
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            double bx = 0, bz = 0;
            for (int[] o : DIRS) {
                if (!level.isLoaded(new BlockPos(x + o[0] * 2, h, z + o[1] * 2))) continue;
                int hn = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0] * 2, z + o[1] * 2);
                if (hn < h) {
                    bx += o[0] * (h - hn);
                    bz += o[1] * (h - hn);
                }
            }
            double len = Math.sqrt(bx * bx + bz * bz);
            if (len < 0.01) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(bx / len * 0.08 * f, 0, bz / len * 0.08 * f));
            e.hurtMarked = true;
        }
    }
}
