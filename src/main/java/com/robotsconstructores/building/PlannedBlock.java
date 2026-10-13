package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Una posición del plano y el tipo de bloque que lleva.
 * Los planos copiados de estructuras o imágenes llevan además el bloque exacto ("state").
 */
public record PlannedBlock(BlockPos pos, BlockRole role, @Nullable BlockState state) {
    public PlannedBlock(BlockPos pos, BlockRole role) {
        this(pos, role, null);
    }
}
