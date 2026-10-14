package com.robotsconstructores.recall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lista (guardada con el mundo) de dónde está cada robot y de quién es.
 * Sirve para que el Mando encuentre robots que están en zonas no cargadas.
 */
public class RobotRegistry extends SavedData {
    private static final String NAME = "robotsconstructores_robots";

    public record Entry(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Map<UUID, Entry> robots = new HashMap<>();

    public static RobotRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RobotRegistry::load, RobotRegistry::new, NAME);
    }

    public void update(UUID robot, UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        Entry old = robots.get(robot);
        if (old == null || !old.owner().equals(owner) || old.dimension() != dimension || !old.pos().equals(pos)) {
            robots.put(robot, new Entry(owner, dimension, pos.immutable()));
            setDirty();
        }
    }

    public void remove(UUID robot) {
        if (robots.remove(robot) != null) {
            setDirty();
        }
    }

    public List<Map.Entry<UUID, Entry>> ownedBy(UUID owner) {
        List<Map.Entry<UUID, Entry>> list = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : robots.entrySet()) {
            if (e.getValue().owner().equals(owner)) {
                list.add(e);
            }
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : robots.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Robot", e.getKey());
            c.putUUID("Owner", e.getValue().owner());
            c.putString("Dim", e.getValue().dimension().location().toString());
            c.putLong("Pos", e.getValue().pos().asLong());
            list.add(c);
        }
        tag.put("Robots", list);
        return tag;
    }

    public static RobotRegistry load(CompoundTag tag) {
        RobotRegistry registry = new RobotRegistry();
        ListTag list = tag.getList("Robots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(c.getString("Dim"));
            if (dim == null || !c.hasUUID("Robot") || !c.hasUUID("Owner")) {
                continue;
            }
            registry.robots.put(c.getUUID("Robot"), new Entry(c.getUUID("Owner"),
                    ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(c.getLong("Pos"))));
        }
        return registry;
    }
}
