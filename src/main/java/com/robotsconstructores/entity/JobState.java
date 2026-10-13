package com.robotsconstructores.entity;

/** Estado del trabajo de un robot. */
public enum JobState {
    IDLE, SCANNING, WORKING, PAUSED, FINISHED;

    public boolean isActive() {
        return this == SCANNING || this == WORKING;
    }

    public static JobState byOrdinal(int i) {
        JobState[] v = values();
        return i >= 0 && i < v.length ? v[i] : IDLE;
    }

    public String translationKey() {
        return "state.robotsconstructores." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
