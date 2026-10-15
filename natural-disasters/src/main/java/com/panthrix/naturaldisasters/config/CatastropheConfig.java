package com.panthrix.naturaldisasters.config;

import com.panthrix.naturaldisasters.core.CatastropheType;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
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

    private CatastropheConfig() {}
}
