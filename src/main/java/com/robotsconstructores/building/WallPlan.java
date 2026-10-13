package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Pared recta: se construye a "distance" bloques delante, centrada, de abajo hacia arriba. */
public class WallPlan implements BuildPlan {
    private final String id;
    private final int width;
    private final int height;
    private final int distance;
    private final BlockRole role;

    public WallPlan(String id, int width, int height, int distance, BlockRole role) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.distance = distance;
        this.role = role;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<PlannedBlock> blocks(BlockPos origin, Direction facing) {
        List<PlannedBlock> list = new ArrayList<>(width * height);
        int start = -(width / 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, distance, start + x, y), role));
            }
        }
        return list;
    }
}
