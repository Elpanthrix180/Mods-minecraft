package com.panthrix.naturaldisasters.core;

/** Los fenomenos del mod. El "id" es el nombre que se usa en comandos y configuracion. */
public enum CatastropheType {
    TORNADO("tornado", "Tornado", "¡PELIGRO! Un tornado se acerca a esta zona."),
    BLIZZARD("blizzard", "Ventisca", "¡ALERTA! Se aproxima una ventisca muy fuerte."),
    EARTHQUAKE("earthquake", "Terremoto", "¡ALERTA! Se aproxima un terremoto."),
    FLOOD("flood", "Inundación", "¡ALERTA! Riesgo de inundación en esta zona."),
    VOLCANO("volcano", "Erupción volcánica", "¡ALERTA VOLCÁNICA! Se ha detectado actividad volcánica cerca."),
    THUNDERSTORM("thunderstorm", "Tormenta eléctrica extrema", "¡ALERTA! Se aproxima una tormenta eléctrica extrema."),
    LANDSLIDE("landslide", "Deslizamiento de tierra", "¡PELIGRO! Riesgo de deslizamiento de tierra en las laderas."),
    HAILSTORM("hailstorm", "Granizada", "¡ALERTA! Se aproxima una granizada. Buscad refugio."),
    DROUGHT("drought", "Sequía", "¡AVISO! Comienza un periodo de sequía."),
    METEOR_SHOWER("meteor_shower", "Lluvia de meteoritos", "¡ALERTA! Se aproxima una lluvia de meteoritos. Buscad refugio.", 6),
    BLACK_HOLE("black_hole", "Agujero negro", "¡PELIGRO EXTREMO! Se está formando un agujero negro en esta zona.", 3),
    GROUND_CRACKS("ground_cracks", "Agrietamiento del terreno", "¡PELIGRO! El terreno empieza a agrietarse en esta zona. Alejaos de las grietas.", 8);

    public final String id;
    public final String displayName;
    public final String warningMessage;
    /** Peso al azar por defecto (los fenomenos mas extremos salen menos). */
    public final int defaultWeight;

    CatastropheType(String id, String displayName, String warningMessage) {
        this(id, displayName, warningMessage, 10);
    }

    CatastropheType(String id, String displayName, String warningMessage, int defaultWeight) {
        this.id = id;
        this.displayName = displayName;
        this.warningMessage = warningMessage;
        this.defaultWeight = defaultWeight;
    }

    /** Devuelve el tipo con ese id, o null si no existe. */
    public static CatastropheType byId(String id) {
        for (CatastropheType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
