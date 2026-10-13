package com.robotsconstructores.entity;

import java.util.Locale;

/** Mensajes de estado que un robot puede mostrar. El texto está en los archivos de idioma. */
public enum RobotStatus {
    READY, BUSY, CANCELLED, PAUSED, RESUMED, NOTHING_TO_RESUME, NOT_PAUSED, NOT_WORKING, TRANSFERRED, TRANSFER_PARTIAL,
    // constructor
    BUILD_STARTED, BUILD_DONE, NO_MATERIALS, ZONE_BLOCKED,
    // minero
    SCAN_STARTED, TARGETS_FOUND, NO_TARGETS, MINE_DONE, NOT_ENOUGH_FOUND, INVENTORY_FULL, NO_SAMPLE, INVALID_AMOUNT, CONFIG_SET, RETURNING, TARGET_SKIPPED, DEPOSITED, CHEST_FULL, NO_CHEST, NO_FILLER, NO_PATH_SAMPLE, NO_PATH_GROUND,
    NO_BLUEPRINT_NAME, BLUEPRINT_NOT_FOUND, BLUEPRINT_ERROR, BLUEPRINT_TOO_BIG, BLUEPRINT_EMPTY, BLUEPRINT_SET, MISSING_ITEMS;

    public String translationKey() {
        return "status.robotsconstructores." + name().toLowerCase(Locale.ROOT);
    }

    public static RobotStatus byOrdinal(int i) {
        RobotStatus[] v = values();
        return i >= 0 && i < v.length ? v[i] : READY;
    }
}
