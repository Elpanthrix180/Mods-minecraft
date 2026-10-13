package com.robotsconstructores.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Un "plano" de construcción. Para añadir casas, caminos, torres, etc. basta con
 * crear otra clase que implemente esta interfaz y registrarla en {@link BuildPlans}.
 */
public interface BuildPlan {
    /** Identificador único (se guarda en el mundo). */
    String id();

    /** Posiciones a colocar, en orden, a partir del origen y mirando hacia "facing". */
    List<BlockPos> positions(BlockPos origin, Direction facing);

    /** Materiales válidos para este plano (por orden de preferencia). */
    List<Item> acceptedMaterials();

    default String translationKey() {
        return "plan.robotsconstructores." + id();
    }
}
