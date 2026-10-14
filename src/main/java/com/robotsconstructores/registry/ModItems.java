package com.robotsconstructores.registry;

import com.robotsconstructores.RobotsConstructores;
import com.robotsconstructores.item.RobotSpawnItem;
import com.robotsconstructores.item.RobotRemoteItem;
import com.robotsconstructores.item.RobotWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RobotsConstructores.MOD_ID);

    public static final RegistryObject<Item> BUILDER_ROBOT = ITEMS.register("builder_robot",
            () -> new RobotSpawnItem(ModEntities.BUILDER_ROBOT::get, new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> MINER_ROBOT = ITEMS.register("miner_robot",
            () -> new RobotSpawnItem(ModEntities.MINER_ROBOT::get, new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> ROBOT_WAND = ITEMS.register("robot_wand",
            () -> new RobotWandItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROBOT_REMOTE = ITEMS.register("robot_remote",
            () -> new RobotRemoteItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
