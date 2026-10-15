package com.panthrix.naturaldisasters.core;

/** Los nueve fenomenos del mod. El "id" es el nombre que se usa en comandos y configuracion. */
public enum CatastropheType {
    TORNADO("tornado", "Tornado", "¡PELIGRO! Un tornado se acerca a esta zona."),
    BLIZZARD("blizzard", "Ventisca", "¡ALERTA! Se aproxima una ventisca muy fuerte."),
    EARTHQUAKE("earthquake", "Terremoto", "¡ALERTA! Se aproxima un terremoto."),
    FLOOD("flood", "Inundación", "¡ALERTA! Riesgo de inundación en esta zona."),
    VOLCANO("volcano", "Erupción volcánica", "¡ALERTA VOLCÁNICA! Se ha detectado actividad volcánica cerca."),
    THUNDERSTORM("thunderstorm", "Tormenta eléctrica extrema", "¡ALERTA! Se aproxima una tormenta eléctrica extrema."),
    LANDSLIDE("landslide", "Deslizamiento de tierra", "¡PELIGRO! Riesgo de deslizamiento de tierra en las laderas."),
    HAILSTORM("hailstorm", "Granizada", "¡ALERTA! Se aproxima una granizada. Buscad refugio."),
    DROUGHT("drought", "Sequía", "¡AVISO! Comienza un periodo de sequía.");

    public final String id;
    public final String displayName;
    public final String warningMessage;

    CatastropheType(String id, String displayName, String warningMessage) {
        this.id = id;
        this.displayName = displayName;
        this.warningMessage = warningMessage;
    }

    /** Devuelve el tipo con ese id, o null si no existe. */
    public static CatastropheType byId(String id) {
        for (CatastropheType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
