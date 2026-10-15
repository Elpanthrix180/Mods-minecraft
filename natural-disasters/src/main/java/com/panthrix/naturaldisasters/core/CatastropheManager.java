package com.panthrix.naturaldisasters.core;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import com.panthrix.naturaldisasters.config.CatastropheConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Director central de catastrofes. Se ejecuta SOLO en el servidor, una vez por tick,
 * y hace muy poco trabajo: compara el tiempo del mundo con el tiempo guardado.
 * Por ahora (fase 2) las catastrofes solo recorren sus fases y envian avisos;
 * los efectos reales de cada fenomeno se anadiran en las fases siguientes.
 */
public final class CatastropheManager {

    /** Si no hay jugadores cuando toca una catastrofe, se vuelve a intentar pasado este tiempo. */
    private static final long RETRY_TICKS = 20L * 60;

    private CatastropheManager() {}

    public static CatastropheSavedData data(MinecraftServer server) {
        return CatastropheSavedData.get(server.overworld());
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Se llama al final de cada tick del servidor. */
    public static void tick(MinecraftServer server) {
        CatastropheSavedData d = data(server);
        try {
            long now = now(server);
            if (d.phase == CatastrophePhase.CALM) {
                tickCalm(server, d, now);
            } else {
                CatastropheEvent ev = CatastropheEvents.get(d.activeType);
                if (ev != null) ev.tick(server.overworld(), d, d.phase, now);
                if (d.phase != CatastrophePhase.CALM && now >= d.phaseEndTick) advancePhase(server, d, now);
            }
        } catch (Exception e) {
            // Mecanismo de recuperacion: si algo falla, se cancela el evento y se programa otro.
            NaturalDisastersMod.LOGGER.error("[Panthrix's Natural Disasters] Error en la catastrofe activa; se cancela.", e);
            resetToCalm(server, d);
        }
    }

    private static void tickCalm(MinecraftServer server, CatastropheSavedData d, long now) {
        if (!d.randomEnabled) return;
        // Sin programar, o programada mas lejos de lo que permite la configuracion actual: reprogramar.
        if (d.nextEventTick < 0 || d.nextEventTick - now > CatastropheScheduler.maxDelayTicks()) {
            scheduleNext(server, d);
            return;
        }
        if (now < d.nextEventTick) return;

        CatastropheType type = CatastropheScheduler.pickRandom(server.overworld().getRandom(), d.lastType);
        if (type == null) {
            NaturalDisastersMod.LOGGER.warn("[Panthrix's Natural Disasters] No hay ningun fenomeno activado; no se inicia nada.");
            scheduleNext(server, d);
            return;
        }
        if (start(server, type) != null) {
            d.nextEventTick = now + RETRY_TICKS;
            d.setDirty();
        }
    }

    /**
     * Inicia una catastrofe (empieza por la fase de AVISO).
     * Devuelve null si se ha iniciado, o un mensaje explicando por que no.
     */
    public static String start(MinecraftServer server, CatastropheType type) {
        CatastropheSavedData d = data(server);
        if (d.phase != CatastrophePhase.CALM) {
            return "Ya hay una catástrofe activa (" + d.activeType.displayName + "). Usa /pnd stop primero.";
        }
        ServerLevel overworld = server.overworld();
        List<ServerPlayer> players = overworld.players();
        if (players.isEmpty()) {
            return "No hay ningún jugador en el mundo normal (Overworld).";
        }
        ServerPlayer target = players.get(overworld.getRandom().nextInt(players.size()));
        d.activeType = type;
        d.eventData = new net.minecraft.nbt.CompoundTag();
        d.centerX = target.getBlockX();
        d.centerY = target.getBlockY();
        d.centerZ = target.getBlockZ();
        enterPhase(server, d, CatastrophePhase.WARNING, now(server));
        NaturalDisastersMod.LOGGER.info("[Panthrix's Natural Disasters] Comienza {} cerca de {} {} {}", type.id, d.centerX, d.centerY, d.centerZ);
        return null;
    }

    /** Detiene inmediatamente la catastrofe activa. Devuelve false si no habia ninguna. */
    public static boolean stop(MinecraftServer server) {
        CatastropheSavedData d = data(server);
        if (d.phase == CatastrophePhase.CALM) return false;
        CatastropheWarningManager.broadcast(server, "Un administrador ha detenido la catástrofe.", ChatFormatting.GREEN);
        resetToCalm(server, d);
        return true;
    }

    public static void setRandomEnabled(MinecraftServer server, boolean enabled) {
        CatastropheSavedData d = data(server);
        d.randomEnabled = enabled;
        if (enabled && d.nextEventTick < now(server)) scheduleNext(server, d);
        d.setDirty();
    }

    public static void scheduleNext(MinecraftServer server, CatastropheSavedData d) {
        d.nextEventTick = now(server) + CatastropheScheduler.randomDelayTicks(server.overworld().getRandom());
        d.setDirty();
    }

    private static void advancePhase(MinecraftServer server, CatastropheSavedData d, long now) {
        CatastrophePhase next = d.phase.next();
        if (next == CatastrophePhase.CALM) {
            CatastropheWarningManager.broadcast(server, "La catástrofe ha terminado. La zona puede seguir dañada.", ChatFormatting.GREEN);
            resetToCalm(server, d);
        } else {
            enterPhase(server, d, next, now);
        }
    }

    private static void enterPhase(MinecraftServer server, CatastropheSavedData d, CatastrophePhase phase, long now) {
        d.phase = phase;
        d.phaseEndTick = now + phaseDurationTicks(d.activeType, phase);
        d.setDirty();
        CatastropheType t = d.activeType;
        switch (phase) {
            case WARNING -> CatastropheWarningManager.announceWarning(server, t, d.centerX, d.centerZ);
            case PREPARATION -> CatastropheWarningManager.broadcast(server, "Aparecen las primeras señales: " + t.displayName + ".", ChatFormatting.YELLOW);
            case ACTIVE -> CatastropheWarningManager.broadcast(server, "¡" + t.displayName + " en curso! Buscad refugio.", ChatFormatting.RED);
            case ENDING -> CatastropheWarningManager.broadcast(server, t.displayName + ": el fenómeno pierde intensidad.", ChatFormatting.YELLOW);
            default -> { }
        }
        CatastropheEvent ev = CatastropheEvents.get(t);
        if (ev != null) ev.onPhaseStart(server.overworld(), d, phase);
    }

    private static void resetToCalm(MinecraftServer server, CatastropheSavedData d) {
        CatastropheEvent ev = CatastropheEvents.get(d.activeType);
        if (ev != null) {
            try {
                ev.onFinish(server.overworld(), d);
            } catch (Exception e) {
                NaturalDisastersMod.LOGGER.error("[Panthrix's Natural Disasters] Error al terminar la catastrofe.", e);
            }
        }
        d.eventData = new net.minecraft.nbt.CompoundTag();
        if (d.activeType != null) d.lastType = d.activeType;
        d.activeType = null;
        d.phase = CatastrophePhase.CALM;
        d.phaseEndTick = 0;
        scheduleNext(server, d);
    }

    public static long phaseDurationTicks(CatastropheType type, CatastrophePhase phase) {
        int seconds = switch (phase) {
            case WARNING -> CatastropheConfig.WARNING_SECONDS.get();
            case PREPARATION -> CatastropheConfig.PREPARATION_SECONDS.get();
            case ACTIVE -> CatastropheConfig.DURATION_SECONDS.get(type).get();
            case ENDING -> CatastropheConfig.ENDING_SECONDS.get();
            case RECOVERY -> CatastropheConfig.RECOVERY_SECONDS.get();
            case CALM -> 0;
        };
        return seconds * 20L;
    }
}
