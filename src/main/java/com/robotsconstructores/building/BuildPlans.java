package com.robotsconstructores.building;

import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lista de planos disponibles. Añade aquí los nuevos. */
public final class BuildPlans {
    private static final Map<String, BuildPlan> PLANS = new LinkedHashMap<>();

    public static final BuildPlan STONE_WALL = register(
            new WallPlan("stone_wall_5x3", 5, 3, 2, List.of(Items.STONE, Items.COBBLESTONE)));

    private static BuildPlan register(BuildPlan plan) {
        PLANS.put(plan.id(), plan);
        return plan;
    }

    public static BuildPlan get(String id) {
        return PLANS.getOrDefault(id, STONE_WALL);
    }

    private BuildPlans() {}
}
