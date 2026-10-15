package com.panthrix.naturaldisasters.core;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** Calcula cuando ocurre la proxima catastrofe y cual se elige. */
public final class CatastropheScheduler {

    public static final long TICKS_PER_DAY = 24000L;

    private CatastropheScheduler() {}

    public static long minDelayTicks() {
        return CatastropheConfig.MIN_DAYS.get() * TICKS_PER_DAY;
    }

    public static long maxDelayTicks() {
        return Math.max(CatastropheConfig.MIN_DAYS.get(), CatastropheConfig.MAX_DAYS.get()) * TICKS_PER_DAY;
    }

    /** Un retraso aleatorio entre el minimo y el maximo de dias configurado. */
    public static long randomDelayTicks(RandomSource random) {
        long min = minDelayTicks();
        long max = maxDelayTicks();
        return min + (long) (random.nextDouble() * (max - min));
    }

    /**
     * Elige un fenomeno al azar segun los pesos. Evita repetir el ultimo si hay otras opciones.
     * Devuelve null si no hay ningun fenomeno activado con peso mayor que 0.
     */
    public static CatastropheType pickRandom(RandomSource random, CatastropheType last) {
        List<CatastropheType> candidates = new ArrayList<>();
        for (CatastropheType t : CatastropheType.values()) {
            if (CatastropheConfig.ENABLED.get(t).get() && CatastropheConfig.WEIGHT.get(t).get() > 0) {
                candidates.add(t);
            }
        }
        if (candidates.size() > 1 && last != null) candidates.remove(last);
        int total = 0;
        for (CatastropheType t : candidates) total += CatastropheConfig.WEIGHT.get(t).get();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (CatastropheType t : candidates) {
            roll -= CatastropheConfig.WEIGHT.get(t).get();
            if (roll < 0) return t;
        }
        return candidates.get(candidates.size() - 1);
    }
}
