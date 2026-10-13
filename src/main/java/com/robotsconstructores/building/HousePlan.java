package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

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
    private final boolean gableRoof; // techo en pico (a dos aguas) en vez de plano

    public HousePlan(String id, int size, int wallHeight, int distance, boolean gableRoof) {
        this.id = id;
        this.size = size;
        this.wallHeight = wallHeight;
        this.distance = distance;
        this.gableRoof = gableRoof;
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
        if (gableRoof) {
            addGableRoof(list, origin, facing, half, front, back);
            return list;
        }
        for (int f = front; f <= back; f++) {
            for (int s = -half; s <= half; s++) {
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, s, wallHeight), BlockRole.STRUCTURE));
            }
        }
        return list;
    }

    /**
     * Tejado a dos aguas: la cumbrera va de delante a atrás. Cada piso sube un bloque y se mete
     * uno hacia el centro. Sobresale 1 bloque por cada lado (alero). Las fachadas de delante y
     * detrás se cierran con triángulos de pared.
     */
    private void addGableRoof(List<PlannedBlock> list, BlockPos origin, Direction facing, int half, int front, int back) {
        int outer = half + 1; // alero lateral
        // escalón "alto" mirando hacia el centro
        BlockState rightStair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getCounterClockWise());
        BlockState leftStair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getClockWise());
        for (int k = 0; k < outer; k++) {
            int y = wallHeight - 1 + k; // el alero empieza a la altura de lo alto de la pared
            int side = outer - k;
            // triángulo de las fachadas (por dentro de las escaleras)
            if (k >= 1 && side - 1 > 0) {
                for (int s = -(side - 1); s <= side - 1; s++) {
                    list.add(new PlannedBlock(BuildPlan.rel(origin, facing, front, s, y), BlockRole.STRUCTURE));
                    list.add(new PlannedBlock(BuildPlan.rel(origin, facing, back, s, y), BlockRole.STRUCTURE));
                }
            }
            for (int f = front - 1; f <= back + 1; f++) {
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, -side, y), BlockRole.ROOF_STAIRS, leftStair));
                list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, side, y), BlockRole.ROOF_STAIRS, rightStair));
            }
        }
        // cumbrera
        for (int f = front - 1; f <= back + 1; f++) {
            list.add(new PlannedBlock(BuildPlan.rel(origin, facing, f, 0, wallHeight - 1 + outer), BlockRole.STRUCTURE));
        }
    }
}
