package com.robotsconstructores.registry;

import com.robotsconstructores.RobotsConstructores;
import com.robotsconstructores.entity.BuilderRobotEntity;
import com.robotsconstructores.entity.MinerRobotEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RobotsConstructores.MOD_ID);

    public static final RegistryObject<EntityType<BuilderRobotEntity>> BUILDER_ROBOT = ENTITIES.register("builder_robot",
            () -> EntityType.Builder.<BuilderRobotEntity>of(BuilderRobotEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.6F).clientTrackingRange(10).build("builder_robot"));

    public static final RegistryObject<EntityType<MinerRobotEntity>> MINER_ROBOT = ENTITIES.register("miner_robot",
            () -> EntityType.Builder.<MinerRobotEntity>of(MinerRobotEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.6F).clientTrackingRange(10).build("miner_robot"));

    private ModEntities() {}
}
