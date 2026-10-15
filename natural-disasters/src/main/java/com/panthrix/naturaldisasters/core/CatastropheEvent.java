package com.panthrix.naturaldisasters.core;

import net.minecraft.server.level.ServerLevel;

/**
 * Comportamiento de un fenomeno. El director llama a estos metodos SOLO en el servidor.
 * Cada fenomeno decide cada cuantos ticks trabaja para no cargar el servidor.
 */
public abstract class CatastropheEvent {

    /** Se llama al entrar en cada fase (AVISO, PREPARACION, ACTIVO, FINALIZACION, RECUPERACION). */
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {}

    /** Se llama en cada tick mientras el fenomeno no esta en calma. */
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {}

    /** Se llama al terminar, tambien si un administrador usa /pnd stop. Debe deshacer lo temporal. */
    public void onFinish(ServerLevel level, CatastropheSavedData d) {}
}
