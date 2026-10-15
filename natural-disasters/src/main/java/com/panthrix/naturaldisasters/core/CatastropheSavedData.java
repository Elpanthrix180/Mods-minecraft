package com.panthrix.naturaldisasters.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Estado del sistema guardado dentro del mundo (archivo data/panthrixsnaturaldisasters_state.dat).
 * Asi el calendario y el evento activo se conservan al cerrar y abrir el mundo.
 */
public class CatastropheSavedData extends SavedData {

    private static final String NAME = "panthrixsnaturaldisasters_state";

    public boolean randomEnabled = true;
    /** Tick del mundo en el que empezara la proxima catastrofe. -1 = sin programar. */
    public long nextEventTick = -1;
    public CatastrophePhase phase = CatastrophePhase.CALM;
    /** Tipo activo, o null si estamos en calma. */
    public CatastropheType activeType = null;
    /** Tick del mundo en el que termina la fase actual. */
    public long phaseEndTick = 0;
    public CatastropheType lastType = null;
    public int centerX, centerY, centerZ;

    public static CatastropheSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(CatastropheSavedData::load, CatastropheSavedData::new, NAME);
    }

    private static CatastropheSavedData load(CompoundTag tag) {
        CatastropheSavedData d = new CatastropheSavedData();
        d.randomEnabled = tag.getBoolean("randomEnabled");
        d.nextEventTick = tag.getLong("nextEventTick");
        d.phaseEndTick = tag.getLong("phaseEndTick");
        d.centerX = tag.getInt("centerX");
        d.centerY = tag.getInt("centerY");
        d.centerZ = tag.getInt("centerZ");
        d.activeType = CatastropheType.byId(tag.getString("activeType"));
        d.lastType = CatastropheType.byId(tag.getString("lastType"));
        try {
            d.phase = CatastrophePhase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            d.phase = CatastrophePhase.CALM;
        }
        // Si los datos estan incompletos, volvemos a la calma para no quedarnos bloqueados.
        if (d.activeType == null) d.phase = CatastrophePhase.CALM;
        if (d.phase == CatastrophePhase.CALM) d.activeType = null;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("randomEnabled", randomEnabled);
        tag.putLong("nextEventTick", nextEventTick);
        tag.putLong("phaseEndTick", phaseEndTick);
        tag.putInt("centerX", centerX);
        tag.putInt("centerY", centerY);
        tag.putInt("centerZ", centerZ);
        tag.putString("phase", phase.name());
        tag.putString("activeType", activeType == null ? "" : activeType.id);
        tag.putString("lastType", lastType == null ? "" : lastType.id);
        return tag;
    }
}
