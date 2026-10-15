package com.panthrix.naturaldisasters;

import com.mojang.logging.LogUtils;
import com.panthrix.naturaldisasters.command.CatastropheCommands;
import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.CatastropheManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * Clase principal de Panthrix's Natural Disasters.
 * Forge la encuentra gracias a la anotacion @Mod y la crea al arrancar el juego.
 */
@Mod(NaturalDisastersMod.MOD_ID)
public class NaturalDisastersMod {

    /** Identificador tecnico del mod. Debe coincidir con mod_id en gradle.properties. */
    public static final String MOD_ID = "panthrixsnaturaldisasters";

    public static final Logger LOGGER = LogUtils.getLogger();

    public NaturalDisastersMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CatastropheConfig.SPEC);
        // Nos registramos en el bus de eventos del juego para recibir eventos del servidor.
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(com.panthrix.naturaldisasters.event.DroughtEvent::onCropGrow);
        LOGGER.info("[Panthrix's Natural Disasters] Mod cargado correctamente.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Panthrix's Natural Disasters] El servidor esta arrancando. Director de catastrofes listo.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CatastropheCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) CatastropheManager.tick(server);
    }
}
