package com.panthrix.naturaldisasters.core;

import com.panthrix.naturaldisasters.event.BlizzardEvent;
import com.panthrix.naturaldisasters.event.DroughtEvent;
import com.panthrix.naturaldisasters.event.EarthquakeEvent;
import com.panthrix.naturaldisasters.event.ExtremeThunderstormEvent;
import com.panthrix.naturaldisasters.event.FloodEvent;
import com.panthrix.naturaldisasters.event.TornadoEvent;
import com.panthrix.naturaldisasters.event.HailstormEvent;

import java.util.EnumMap;
import java.util.Map;

/** Relaciona cada tipo con su comportamiento. Los que aun no existen solo envian avisos. */
public final class CatastropheEvents {

    private static final Map<CatastropheType, CatastropheEvent> EVENTS = new EnumMap<>(CatastropheType.class);

    static {
        EVENTS.put(CatastropheType.BLIZZARD, new BlizzardEvent());
        EVENTS.put(CatastropheType.THUNDERSTORM, new ExtremeThunderstormEvent());
        EVENTS.put(CatastropheType.HAILSTORM, new HailstormEvent());
        EVENTS.put(CatastropheType.DROUGHT, new DroughtEvent());
        EVENTS.put(CatastropheType.TORNADO, new TornadoEvent());
        EVENTS.put(CatastropheType.FLOOD, new FloodEvent());
        EVENTS.put(CatastropheType.EARTHQUAKE, new EarthquakeEvent());
    }

    private CatastropheEvents() {}

    public static CatastropheEvent get(CatastropheType type) {
        return type == null ? null : EVENTS.get(type);
    }

    public static boolean isImplemented(CatastropheType type) {
        return EVENTS.containsKey(type);
    }
}
