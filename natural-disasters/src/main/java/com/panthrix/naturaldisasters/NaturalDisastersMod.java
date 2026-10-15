package com.panthrix.naturaldisasters;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
        // Nos registramos en el bus de eventos del juego para recibir eventos del servidor.
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[Panthrix's Natural Disasters] Mod cargado correctamente.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Panthrix's Natural Disasters] El servidor esta arrancando. Sistema de catastrofes listo (fase 1).");
    }
}
