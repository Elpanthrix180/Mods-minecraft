package com.panthrix.naturaldisasters.core;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lista de volcanes conocidos (posicion del crater), guardada en el mundo.
 * La generacion del mundo ocurre en otros hilos, asi que los volcanes nuevos se ponen
 * primero en una cola segura y el hilo del servidor los guarda en cada tick.
 */
public class VolcanoRegistry extends SavedData {

    private static final String NAME = "panthrixsnaturaldisasters_volcanoes";
    private static final int MAX = 512;
    private static final ConcurrentLinkedQueue<Long> PENDING = new ConcurrentLinkedQueue<>();

    private final LongArrayList craters = new LongArrayList();

    public static void queue(BlockPos crater) {
        PENDING.add(crater.asLong());
    }

    public static VolcanoRegistry get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(VolcanoRegistry::load, VolcanoRegistry::new, NAME);
    }

    /** Pasa los volcanes recien generados a la lista guardada. Se llama en el tick del servidor. */
    public static void drainPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        VolcanoRegistry reg = get(server.overworld());
        Long l;
        while ((l = PENDING.poll()) != null) reg.add(BlockPos.of(l));
    }

    public void add(BlockPos crater) {
        long key = crater.asLong();
        if (craters.contains(key)) return;
        craters.add(key);
        while (craters.size() > MAX) craters.removeLong(0);
        setDirty();
    }

    /** El volcan conocido mas cercano dentro de la distancia indicada, o null. */
    public BlockPos nearest(int x, int z, int maxDistance) {
        BlockPos best = null;
        long bestD = (long) maxDistance * maxDistance;
        for (int i = 0; i < craters.size(); i++) {
            BlockPos p = BlockPos.of(craters.getLong(i));
            long dx = p.getX() - x, dz = p.getZ() - z;
            long dd = dx * dx + dz * dz;
            if (dd <= bestD) {
                bestD = dd;
                best = p;
            }
        }
        return best;
    }

    public int size() {
        return craters.size();
    }

    private static VolcanoRegistry load(CompoundTag tag) {
        VolcanoRegistry r = new VolcanoRegistry();
        for (long l : tag.getLongArray("craters")) r.craters.add(l);
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("craters", craters.toLongArray());
        return tag;
    }
}
