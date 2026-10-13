package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Casa sencilla: planta cuadrada, paredes, ventanas de cristal, puerta en el centro
 * de la fachada y techo plano. El suelo es el propio terreno.
 */
public class HousePlan implements BuildPlan {
    private final String id;
    private final int size;      // lado de la planta (impar)
    private final int wallHeight;
    private final int distance;  // distancia del robot a la fachada

    public HousePlan(String id, int size, int wallHeight, int distance) {
        this.id = id;
        this.size = size;
        this.wallHeight = wallHeight;
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
        for (int y = 0; y < wallHeight; y++) {
            for (int f = front; f <= back; f++) {
                for (int s = -half; s <= half; s++) {
                    boolean edge = f == front || f == back || s == -half || s == half;
                    if (!edge) {
                        continue;
                    }
                    BlockRole role = BlockRole.STRUCTURE;
                    if (f == front && s == 0) {
                        if (y == 0) {
                            role = BlockRole.DOOR;      // la puerta ocupa también el bloque de arriba
                        } else if (y == 1) {
                            continue;
                        }
                    } else if (y == 1 && ((f == front && Math.abs(s) == 2)
                            || (Math.abs(s) == half && f == middle)
                            || (f == back && s == 0))) {
                        role = BlockRole.GLASS;
                    }
                    list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, y), role));
                }
            }
        }
        for (int f = front; f <= back; f++) {
            for (int s = -half; s <= half; s++) {
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, wallHeight), BlockRole.STRUCTURE));
            }
        }
        return list;
    }
}
