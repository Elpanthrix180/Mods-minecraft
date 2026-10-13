package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Camino que sigue el terreno. Empieza 1 bloque delante del robot.
 * - Modo bloque: cambia el suelo natural (césped, tierra, arena, grava, piedra...) por el bloque de muestra,
 *   y si hay un hueco pone el bloque encima del aire (como un puente).
 * - Modo senda: convierte césped/tierra en senda de tierra, como una pala. No gasta materiales.
 */
public class PathPlan implements BuildPlan {
    public static final int MIN_LENGTH = 4, MAX_LENGTH = 64, DEFAULT_LENGTH = 16;
    public static final int MIN_WIDTH = 1, MAX_WIDTH = 5, DEFAULT_WIDTH = 3;
    private static final int SEARCH_UP = 4, SEARCH_DOWN = 5;

    private final String id;
    private final boolean dirtPath;

    public PathPlan(String id, boolean dirtPath) {
        this.id = id;
        this.dirtPath = dirtPath;
    }

    @Override
    public String id() {
        return id;
    }

    public boolean isDirtPath() {
        return dirtPath;
    }

    private BlockRole role() {
        return dirtPath ? BlockRole.DIRT_PATH : BlockRole.PATH;
    }

    /** Vista plana (solo para mostrar tamaños); el plano real se mide en el terreno con {@link #survey}. */
    @Override
    public List<PlannedBlock> blocks(BlockPos origin, Direction facing) {
        return flat(origin, facing, DEFAULT_LENGTH, DEFAULT_WIDTH);
    }

    private List<PlannedBlock> flat(BlockPos origin, Direction facing, int length, int width) {
        List<PlannedBlock> list = new ArrayList<>();
        for (int f = 1; f <= length; f++) {
            for (int s = -(width / 2); s <= width / 2; s++) {
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, -1), role()));
            }
        }
        return list;
    }

    /** Suelo natural que se puede cambiar por el camino. */
    public static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SNOW_BLOCK);
    }

    /** Bloques que una pala convierte en senda. */
    public static boolean isShovelable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT);
    }

    private static boolean isOpen(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /** Mide el terreno columna a columna y devuelve los bloques del camino. */
    public List<PlannedBlock> survey(Level level, BlockPos origin, Direction facing, int length, int width) {
        List<PlannedBlock> list = new ArrayList<>();
        int baseY = origin.getY() - 1;
        for (int f = 1; f <= length; f++) {
            for (int s = -(width / 2); s <= width / 2; s++) {
                BlockPos column = BuildPlan.rel(origin, facing, f, s, -1);
                BlockPos surface = null;
                for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
                    BlockPos p = column.above(dy);
                    if (!level.isLoaded(p)) {
                        break;
                    }
                    BlockState st = level.getBlockState(p);
                    if (!isOpen(st) && isOpen(level.getBlockState(p.above()))) {
                        surface = p;
                        break;
                    }
                }
                if (surface != null) {
                    BlockState st = level.getBlockState(surface);
                    if (dirtPath ? isShovelable(st) : isGround(st)) {
                        list.add(new PlannedBlock(surface, role()));
                    }
                } else if (!dirtPath) {
                    // no hay suelo cerca: puente a la altura del robot
                    BlockPos bridge = new BlockPos(column.getX(), baseY, column.getZ());
                    if (level.isLoaded(bridge) && isOpen(level.getBlockState(bridge))) {
                        list.add(new PlannedBlock(bridge, role()));
                    }
                }
            }
        }
        return list;
    }
}
