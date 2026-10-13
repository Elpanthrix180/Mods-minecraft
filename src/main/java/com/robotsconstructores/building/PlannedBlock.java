package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;

/** Una posición del plano y el tipo de bloque que lleva. */
public record PlannedBlock(BlockPos pos, BlockRole role) {
}
