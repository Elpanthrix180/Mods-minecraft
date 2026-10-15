package com.panthrix.naturaldisasters.core;

/** Las seis fases por las que pasa cada catastrofe, en orden. */
public enum CatastrophePhase {
    CALM("Calma"),
    WARNING("Aviso"),
    PREPARATION("Preparación"),
    ACTIVE("Activo"),
    ENDING("Finalización"),
    RECOVERY("Recuperación");

    public final String displayName;

    CatastrophePhase(String displayName) {
        this.displayName = displayName;
    }

    /** Fase siguiente. Despues de RECOVERY se vuelve a CALM. */
    public CatastrophePhase next() {
        return this == RECOVERY ? CALM : values()[ordinal() + 1];
    }
}
