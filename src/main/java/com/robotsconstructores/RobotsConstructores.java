package com.robotsconstructores;

import com.robotsconstructores.building.BlueprintLoader;
import com.robotsconstructores.entity.BaseRobotEntity;
import com.robotsconstructores.network.ModNetwork;
import com.robotsconstructores.registry.ModEntities;
import com.robotsconstructores.registry.ModItems;
import com.robotsconstructores.registry.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Clase principal del mod: aquí se registra todo lo que el mod añade al juego. */
@Mod(RobotsConstructores.MOD_ID)
public class RobotsConstructores {
    public static final String MOD_ID = "robotsconstructores";

    public RobotsConstructores() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::addCreativeTabItems);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        BlueprintLoader.createFolders();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BUILDER_ROBOT.get(), BaseRobotEntity.createAttributes().build());
        event.put(ModEntities.MINER_ROBOT.get(), BaseRobotEntity.createAttributes().build());
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.BUILDER_ROBOT.get());
            event.accept(ModItems.MINER_ROBOT.get());
            event.accept(ModItems.ROBOT_WAND.get());
        }
    }
}
