package com.robotsconstructores.building;

import java.util.ArrayList;
import java.util.List;

/** Lista de planos disponibles. Añade aquí los nuevos. */
public final class BuildPlans {
    private static final List<BuildPlan> PLANS = new ArrayList<>();

    public static final BuildPlan STONE_WALL = register(new WallPlan("stone_wall_5x3", 5, 3, 2, BlockRole.STONE_WALL));
    public static final BuildPlan SMALL_HOUSE = register(new HousePlan("small_house_7x7", 7, 4, 2));
    public static final BuildPlan TOWER = register(new TowerPlan("tower_5x5", 5, 10, 2));

    private static BuildPlan register(BuildPlan plan) {
        PLANS.add(plan);
        return plan;
    }

    public static BuildPlan get(String id) {
        for (BuildPlan p : PLANS) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return STONE_WALL;
    }

    public static int count() {
        return PLANS.size();
    }

    public static BuildPlan byIndex(int index) {
        return PLANS.get(Math.floorMod(index, PLANS.size()));
    }

    public static int indexOf(BuildPlan plan) {
        return Math.max(0, PLANS.indexOf(plan));
    }

    private BuildPlans() {}
}
