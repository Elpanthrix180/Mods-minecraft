package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Muralla: un muro grueso que va de lado a lado delante del robot (centrado),
 * con almenas en la cara exterior y una escalera de mano en el centro de la cara interior
 * para subir al adarve (la parte de arriba por donde se camina).
 */
public class RampartPlan implements BuildPlan {
    private final String id;
    private final int height;
    private final int thickness;
    private final int distance;

    public RampartPlan(String id, int height, int thickness, int distance) {
        this.id = id;
        this.height = height;
        this.thickness = thickness;
        this.distance = distance;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<PlannedBlock> blocks(BlockPos origin, Direction facing) {
        return blocks(origin, facing, PathPlan.DEFAULT_LENGTH);
    }

    public List<PlannedBlock> blocks(BlockPos origin, Direction facing, int length) {
        List<PlannedBlock> list = new ArrayList<>();
        int start = -(length / 2);
        int end = start + length - 1;
        int inner = distance;                   // cara que mira al robot
        int outer = distance + thickness - 1;   // cara exterior
        for (int y = 0; y < height; y++) {
            for (int s = start; s <= end; s++) {
                for (int f = inner; f <= outer; f++) {
                    list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, y), BlockRole.STRUCTURE));
                }
            }
            // escalera pegada a la cara interior, en el centro
            list.add(new PlannedBlock(BuildPlan.rel(origin, facing, inner - 1, 0, y), BlockRole.LADDER));
        }
        for (int s = start; s <= end; s++) {
            if (Math.floorMod(s - start, 2) == 0) {
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, outer, s, height), BlockRole.STRUCTURE));
            }
        }
        return list;
    }

    public Map<BlockRole, Integer> requirements(int length) {
        Map<BlockRole, Integer> map = new EnumMap<>(BlockRole.class);
        for (PlannedBlock b : blocks(BlockPos.ZERO, Direction.NORTH, length)) {
            map.merge(b.role(), 1, Integer::sum);
        }
        return map;
    }
}
