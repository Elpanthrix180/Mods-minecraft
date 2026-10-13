package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Plano que se carga de un archivo: una estructura (.nbt, como las del bloque de estructuras)
 * o una imagen (.png) que se convierte en pixel art. Lo carga {@link BlueprintLoader}.
 */
public class BlueprintPlan implements BuildPlan {
    private final String id;
    private final boolean image;

    public BlueprintPlan(String id, boolean image) {
        this.id = id;
        this.image = image;
    }

    @Override
    public String id() {
        return id;
    }

    public boolean isImage() {
        return image;
    }

    @Override
    public List<PlannedBlock> blocks(BlockPos origin, Direction facing) {
        return List.of(); // depende del archivo elegido
    }
}
