package com.robotsconstructores.client;

import com.robotsconstructores.RobotsConstructores;
import com.robotsconstructores.registry.ModEntities;
import com.robotsconstructores.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Todo lo que solo existe en el cliente (gráficos e interfaces). */
@Mod.EventBusSubscriber(modid = RobotsConstructores.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static ResourceLocation tex(String name) {
        return new ResourceLocation(RobotsConstructores.MOD_ID, "textures/entity/" + name + ".png");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BUILDER_ROBOT.get(), BuilderRobotScreen::new);
            MenuScreens.register(ModMenus.MINER_ROBOT.get(), MinerRobotScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RobotModel.BUILDER_LAYER, RobotModel::createBuilderLayer);
        event.registerLayerDefinition(RobotModel.MINER_LAYER, RobotModel::createMinerLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BUILDER_ROBOT.get(),
                ctx -> new RobotRenderer<>(ctx, RobotModel.BUILDER_LAYER, tex("builder_robot"), tex("builder_robot_glow")));
        event.registerEntityRenderer(ModEntities.MINER_ROBOT.get(),
                ctx -> new RobotRenderer<>(ctx, RobotModel.MINER_LAYER, tex("miner_robot"), tex("miner_robot_glow")));
    }

    private ClientSetup() {}
}
