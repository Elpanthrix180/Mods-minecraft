package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Un "plano" de construcción. Para añadir caminos, murallas, torres, etc. basta con
 * crear otra clase que implemente esta interfaz y registrarla en {@link BuildPlans}.
 */
public interface BuildPlan {
    /** Identificador único (se guarda en el mundo). */
    String id();

    /** Bloques a colocar, en orden (de abajo arriba), a partir del origen y mirando hacia "facing". */
    List<PlannedBlock> blocks(BlockPos origin, Direction facing);

    default String translationKey() {
        return "plan.robotsconstructores." + id();
    }

    /** Cuántos bloques de cada tipo necesita. */
    default Map<BlockRole, Integer> requirements() {
        Map<BlockRole, Integer> map = new EnumMap<>(BlockRole.class);
        for (PlannedBlock b : blocks(BlockPos.ZERO, Direction.NORTH)) {
            map.merge(b.role(), 1, Integer::sum);
        }
        return map;
    }

    default int size() {
        return blocks(BlockPos.ZERO, Direction.NORTH).size();
    }

    /** Posición relativa: "forward" bloques delante, "side" a la derecha, "up" hacia arriba. */
    static BlockPos rel(BlockPos origin, Direction facing, int forward, int side, int up) {
        return origin.relative(facing, forward).relative(facing.getClockWise(), side).above(up).immutable();
    }
}
