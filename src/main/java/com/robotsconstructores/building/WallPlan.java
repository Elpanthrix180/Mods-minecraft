package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** Pared recta: se construye a "distance" bloques delante, centrada, de abajo hacia arriba. */
public class WallPlan implements BuildPlan {
    private final String id;
    private final int width;
    private final int height;
    private final int distance;
    private final List<Item> materials;

    public WallPlan(String id, int width, int height, int distance, List<Item> materials) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.distance = distance;
        this.materials = materials;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<BlockPos> positions(BlockPos origin, Direction facing) {
        List<BlockPos> list = new ArrayList<>(width * height);
        BlockPos base = origin.relative(facing, distance);
        Direction side = facing.getClockWise();
        int start = -(width / 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                list.add(base.relative(side, start + x).above(y).immutable());
            }
        }
        return list;
    }

    @Override
    public List<Item> acceptedMaterials() {
        return materials;
    }
}
