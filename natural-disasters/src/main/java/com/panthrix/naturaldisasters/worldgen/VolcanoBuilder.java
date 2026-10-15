package com.panthrix.naturaldisasters.worldgen;

import com.panthrix.naturaldisasters.core.BlockProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Construye un volcan pequeno: cono de roca volcanica con crater y lava en el fondo.
 * Se usa en la generacion del mundo (runtime = false) y, durante una erupcion, en un mundo ya cargado
 * (runtime = true, respetando la proteccion de bloques y los chunks cargados).
 * El radio maximo es 14 para no salirse de los chunks vecinos durante la generacion.
 */
public final class VolcanoBuilder {

    public static final int MAX_RADIUS = 14;

    private VolcanoBuilder() {}

    /** Devuelve la posicion del crater (encima de la lava) o null si el sitio no es apto. */
    public static BlockPos build(WorldGenLevel level, BlockPos center, RandomSource r, boolean runtime) {
        Heightmap.Types ground = runtime ? Heightmap.Types.MOTION_BLOCKING_NO_LEAVES : Heightmap.Types.OCEAN_FLOOR_WG;
        int cx = center.getX(), cz = center.getZ();
        int baseY = level.getHeight(ground, cx, cz);
        BlockPos baseTop = new BlockPos(cx, baseY, cz);
        if (!level.getFluidState(baseTop).isEmpty() || !level.getFluidState(baseTop.below()).isEmpty()) return null; // nada de volcanes en el agua
        if (baseY + 30 >= level.getMaxBuildHeight()) return null;

        int radius = 10 + r.nextInt(MAX_RADIUS - 9);
        int height = 12 + r.nextInt(9);
        int craterR = 3 + r.nextInt(2);
        int craterDepth = 4;
        double rimH = coneHeight(craterR, radius, height);
        int floorY = baseY + (int) Math.round(rimH) - craterDepth;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                int x = cx + dx, z = cz + dz;
                if (runtime && !((ServerLevel) level).isLoaded(new BlockPos(x, baseY, z))) continue;
                int local = level.getHeight(ground, x, z);
                if (!runtime && !level.getFluidState(new BlockPos(x, local, z)).isEmpty()) continue;
                // Ruido suave para que el cono no sea perfecto.
                double noise = (r.nextDouble() - 0.5) * 1.2;
                int top = dist < craterR ? floorY : baseY + (int) Math.round(coneHeight(dist, radius, height) + noise);
                for (int y = local - 2; y <= top; y++) {
                    put(level, new BlockPos(x, y, z), material(dist, radius, craterR, y == top, r), runtime);
                }
                // Quitar troncos y hojas que quedarian flotando encima del cono.
                for (int y = top + 1; y <= top + 12; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) put(level, p, Blocks.AIR.defaultBlockState(), runtime);
                }
                if (dist < craterR) {
                    put(level, new BlockPos(x, floorY + 1, z), Blocks.LAVA.defaultBlockState(), runtime);
                    put(level, new BlockPos(x, floorY + 2, z), Blocks.LAVA.defaultBlockState(), runtime);
                    for (int y = floorY + 3; y <= baseY + height + 2; y++) put(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), runtime);
                    for (int y = floorY - 3; y <= floorY; y++) put(level, new BlockPos(x, y, z), Blocks.MAGMA_BLOCK.defaultBlockState(), runtime);
                }
            }
        }
        return new BlockPos(cx, floorY + 3, cz);
    }

    private static double coneHeight(double dist, int radius, int height) {
        return height * Math.pow(Math.max(0, 1.0 - dist / radius), 1.3);
    }

    private static BlockState material(double dist, int radius, int craterR, boolean surface, RandomSource r) {
        if (dist < craterR + 1.5) return r.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        if (surface) {
            int n = r.nextInt(10);
            if (dist < radius * 0.5) return n < 5 ? Blocks.BASALT.defaultBlockState() : n < 8 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
            return n < 4 ? Blocks.TUFF.defaultBlockState() : n < 7 ? Blocks.ANDESITE.defaultBlockState() : n < 9 ? Blocks.STONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        }
        return dist < radius * 0.6 ? Blocks.BASALT.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState state, boolean runtime) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return;
        if (runtime) {
            ServerLevel sl = (ServerLevel) level;
            BlockState old = sl.getBlockState(pos);
            if (!old.isAir() && !BlockProtection.canModify(sl, pos, old)) return;
            if (old.is(state.getBlock())) return;
            sl.setBlock(pos, state, 2);
        } else {
            level.setBlock(pos, state, 2);
        }
    }
}
