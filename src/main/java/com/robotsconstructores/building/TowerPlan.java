package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Torre hueca de planta cuadrada: puerta en la fachada, ventanas de cristal,
 * escalera de mano por dentro (pegada a la pared del fondo), plataforma arriba
 * con un hueco para la escalera y almenas alrededor.
 */
public class TowerPlan implements BuildPlan {
    private final String id;
    private final int size;      // lado (impar)
    private final int height;    // alto de las paredes
    private final int distance;  // distancia del robot a la fachada

    public TowerPlan(String id, int size, int height, int distance) {
        this.id = id;
        this.size = size;
        this.height = height;
        this.distance = distance;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<PlannedBlock> blocks(BlockPos origin, Direction facing) {
        List<PlannedBlock> list = new ArrayList<>();
        int half = size / 2;
        int front = distance;
        int back = distance + size - 1;
        int middle = (front + back) / 2;
        int ladderF = back - 1; // dentro, pegada a la pared del fondo
        for (int y = 0; y < height; y++) {
            for (int f = front; f <= back; f++) {
                for (int s = -half; s <= half; s++) {
                    boolean edge = f == front || f == back || s == -half || s == half;
                    if (!edge) {
                        continue;
                    }
                    BlockRole role = BlockRole.STRUCTURE;
                    boolean centerOfSide = (s == 0 && (f == front || f == back)) || (f == middle && Math.abs(s) == half);
                    if (f == front && s == 0 && y <= 1) {
                        if (y == 1) {
                            continue; // parte de arriba de la puerta
                        }
                        role = BlockRole.DOOR;
                    } else if (centerOfSide && (y == 4 || y == 7)) {
                        role = BlockRole.GLASS;
                    }
                    list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, y), role));
                }
            }
            // la escalera se pone después de la pared que la sujeta
            list.add(new PlannedBlock(BuildPlan.rel(origin, facing, ladderF, 0, y), BlockRole.LADDER));
        }
        for (int f = front; f <= back; f++) {
            for (int s = -half; s <= half; s++) {
                if (f == ladderF && s == 0) {
                    continue; // hueco para subir a la plataforma
                }
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, height), BlockRole.STRUCTURE));
            }
        }
        for (int f = front; f <= back; f++) {
            for (int s = -half; s <= half; s++) {
                boolean edge = f == front || f == back || s == -half || s == half;
                if (edge && Math.floorMod(f + s, 2) == 0) {
                    list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, height + 1), BlockRole.STRUCTURE));
                }
            }
        }
        return list;
    }
}
