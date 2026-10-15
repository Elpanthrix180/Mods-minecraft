package com.panthrix.naturaldisasters.config;

import com.panthrix.naturaldisasters.core.CatastropheType;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracion del mod. Forge crea el archivo
 * config/panthrixsnaturaldisasters-common.toml la primera vez que se arranca el juego.
 * Los valores por defecto son conservadores: la destruccion esta DESACTIVADA.
 */
public final class CatastropheConfig {

    public enum Intensity { SOFT, MODERATE, EXTREME }

    public enum Destruction { OFF, PARTIAL, INTENSE }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MIN_DAYS;
    public static final ForgeConfigSpec.IntValue MAX_DAYS;
    public static final ForgeConfigSpec.BooleanValue WARNINGS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SIMULTANEOUS;
    public static final ForgeConfigSpec.EnumValue<Intensity> INTENSITY;
    public static final ForgeConfigSpec.EnumValue<Destruction> DESTRUCTION;
    public static final ForgeConfigSpec.IntValue RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_EVENT;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_MOBS;
    public static final ForgeConfigSpec.IntValue WARNING_SECONDS;
    public static final ForgeConfigSpec.IntValue PREPARATION_SECONDS;
    public static final ForgeConfigSpec.IntValue ENDING_SECONDS;
    public static final ForgeConfigSpec.IntValue RECOVERY_SECONDS;

    public static final ForgeConfigSpec.BooleanValue SNOW_ACCUMULATION;
    public static final ForgeConfigSpec.BooleanValue SNOW_TEMPORARY;
    public static final ForgeConfigSpec.BooleanValue FREEZING;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_FIRES;
    public static final ForgeConfigSpec.BooleanValue HAIL_CROPS;
    public static final ForgeConfigSpec.BooleanValue HAIL_GLASS;
    public static final ForgeConfigSpec.BooleanValue DROUGHT_CROPS;
    public static final ForgeConfigSpec.BooleanValue DROUGHT_DRY_FARMLAND;

    public static final ForgeConfigSpec.DoubleValue TORNADO_SPEED;
    public static final ForgeConfigSpec.IntValue TORNADO_PULL_RADIUS;
    public static final ForgeConfigSpec.BooleanValue TORNADO_LIFTS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue TORNADO_UPROOTS_TREES;
    public static final ForgeConfigSpec.IntValue FLOOD_HEIGHT;
    public static final ForgeConfigSpec.IntValue FLOOD_RADIUS;
    public static final ForgeConfigSpec.BooleanValue FLOOD_TEMPORARY;
    public static final ForgeConfigSpec.BooleanValue FLOOD_CURRENTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_ZONES;

    public static final Map<CatastropheType, ForgeConfigSpec.BooleanValue> ENABLED = new EnumMap<>(CatastropheType.class);
    public static final Map<CatastropheType, ForgeConfigSpec.IntValue> WEIGHT = new EnumMap<>(CatastropheType.class);
    public static final Map<CatastropheType, ForgeConfigSpec.IntValue> DURATION_SECONDS = new EnumMap<>(CatastropheType.class);

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Calendario de catastrofes (1 dia de Minecraft = 24000 ticks = 20 minutos reales)").push("calendario");
        MIN_DAYS = b.comment("Dias minimos entre catastrofes").defineInRange("diasMinimos", 5, 1, 1000);
        MAX_DAYS = b.comment("Dias maximos entre catastrofes (si es menor que el minimo, se usa el minimo)").defineInRange("diasMaximos", 20, 1, 1000);
        b.pop();

        b.comment("Opciones generales").push("general");
        WARNINGS = b.comment("Mostrar avisos de emergencia a los jugadores").define("avisos", true);
        ALLOW_SIMULTANEOUS = b.comment("Permitir varias catastrofes a la vez (aun no implementado: siempre hay como maximo una)").define("eventosSimultaneos", false);
        INTENSITY = b.comment("Intensidad: SOFT (suave), MODERATE (moderada), EXTREME (extrema)").defineEnum("intensidad", Intensity.MODERATE);
        DESTRUCTION = b.comment("Destruccion del terreno: OFF (desactivada), PARTIAL (parcial), INTENSE (intensa; puede afectar al rendimiento)").defineEnum("destruccion", Destruction.OFF);
        RADIUS = b.comment("Radio de los eventos en bloques").defineInRange("radio", 32, 8, 128);
        MAX_BLOCKS_PER_EVENT = b.comment("Maximo de bloques que un evento puede modificar").defineInRange("maxBloquesPorEvento", 1000, 0, 20000);
        DAMAGE_PLAYERS = b.comment("Los fenomenos pueden hacer dano a los jugadores").define("danoJugadores", true);
        DAMAGE_MOBS = b.comment("Los fenomenos pueden hacer dano a las criaturas").define("danoCriaturas", true);
        b.pop();

        b.comment("Duracion de las fases comunes, en segundos reales").push("fases");
        WARNING_SECONDS = b.defineInRange("segundosAviso", 30, 5, 600);
        PREPARATION_SECONDS = b.defineInRange("segundosPreparacion", 20, 0, 600);
        ENDING_SECONDS = b.defineInRange("segundosFinalizacion", 20, 0, 600);
        RECOVERY_SECONDS = b.defineInRange("segundosRecuperacion", 10, 0, 600);
        b.pop();

        b.comment("Fenomenos meteorologicos").push("meteorologia");
        SNOW_ACCUMULATION = b.comment("Ventisca: acumular capas de nieve en el suelo (cuenta para maxBloquesPorEvento)").define("acumularNieve", false);
        SNOW_TEMPORARY = b.comment("Ventisca: retirar la nieve acumulada al terminar").define("nieveTemporal", true);
        FREEZING = b.comment("Ventisca: congelar a los jugadores expuestos al cielo (necesita danoJugadores)").define("congelacion", true);
        LIGHTNING_FIRES = b.comment("Tormenta: los rayos pueden provocar incendios (necesita destruccion distinta de OFF)").define("incendiosPorRayos", false);
        HAIL_CROPS = b.comment("Granizada: dana los cultivos, reduce su crecimiento (necesita destruccion distinta de OFF)").define("granizoDanaCultivos", true);
        HAIL_GLASS = b.comment("Granizada: rompe cristales expuestos (solo con destruccion INTENSE)").define("granizoRompeCristales", false);
        DROUGHT_CROPS = b.comment("Sequia: los cultivos de la zona crecen mas despacio (efecto temporal)").define("sequiaFrenaCultivos", true);
        DROUGHT_DRY_FARMLAND = b.comment("Sequia: seca la tierra de cultivo (necesita destruccion distinta de OFF)").define("sequiaSecaTierras", false);
        b.pop();

        b.comment("Tornados").push("tornado");
        TORNADO_SPEED = b.comment("Velocidad de desplazamiento en bloques por segundo").defineInRange("velocidad", 2.0, 0.0, 10.0);
        TORNADO_PULL_RADIUS = b.comment("Radio en el que atrae a jugadores y criaturas").defineInRange("radioAtraccion", 8, 2, 24);
        TORNADO_LIFTS_BLOCKS = b.comment("Levantar tierra, arena, grava, hojas y plantas (necesita destruccion distinta de OFF)").define("levantaBloques", true);
        TORNADO_UPROOTS_TREES = b.comment("Arrancar troncos de arboles (solo con destruccion INTENSE)").define("arrancaArboles", false);
        b.pop();

        b.comment("Inundaciones (colocar agua necesita destruccion distinta de OFF)").push("inundacion");
        FLOOD_HEIGHT = b.comment("Cuantos bloques sube el agua como maximo").defineInRange("alturaMaxima", 3, 1, 10);
        FLOOD_RADIUS = b.comment("Radio de la zona inundada (nunca mayor que 'radio')").defineInRange("radioInundacion", 24, 4, 64);
        FLOOD_TEMPORARY = b.comment("Retirar el agua al terminar").define("aguaTemporal", true);
        FLOOD_CURRENTS = b.comment("Corrientes que arrastran a quien este en el agua").define("corrientes", true);
        b.pop();

        b.comment("Proteccion del mundo. Ademas de esta lista, NUNCA se modifican bloques con inventario",
                "o datos (cofres, hornos, maquinas de otros mods...) ni bloques irrompibles (roca madre...).").push("proteccion");
        PROTECTED_BLOCKS = b.comment("Bloques que nunca se modifican (id completo, por ejemplo minecraft:glass)")
                .defineList("bloquesProtegidos", List.of("minecraft:spawner", "minecraft:beacon", "minecraft:respawn_anchor",
                        "minecraft:end_portal_frame", "minecraft:nether_portal", "minecraft:obsidian", "minecraft:crying_obsidian",
                        "minecraft:reinforced_deepslate", "minecraft:budding_amethyst"), o -> o instanceof String);
        PROTECTED_ZONES = b.comment("Zonas protegidas en el mundo normal: \"x1,z1,x2,z2\" (esquinas). Ejemplo: \"-100,-100,100,100\"",
                        "Dentro de ellas ningun fenomeno modifica bloques.")
                .defineList("zonasProtegidas", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("Ajustes de cada fenomeno").push("fenomenos");
        for (CatastropheType t : CatastropheType.values()) {
            b.comment(t.displayName).push(t.id);
            ENABLED.put(t, b.comment("Activar este fenomeno").define("activado", true));
            WEIGHT.put(t, b.comment("Probabilidad relativa (0 = nunca al azar)").defineInRange("peso", 10, 0, 1000));
            DURATION_SECONDS.put(t, b.comment("Duracion de la fase activa, en segundos").defineInRange("duracionSegundos", 120, 10, 3600));
            b.pop();
        }
        b.pop();

        SPEC = b.build();
    }

    /** Multiplicador segun la intensidad: suave 0.5, moderada 1, extrema 2. */
    public static double intensityFactor() {
        return switch (INTENSITY.get()) {
            case SOFT -> 0.5;
            case MODERATE -> 1.0;
            case EXTREME -> 2.0;
        };
    }

    public static boolean destructionOff() {
        return DESTRUCTION.get() == Destruction.OFF;
    }

    private CatastropheConfig() {}
}
