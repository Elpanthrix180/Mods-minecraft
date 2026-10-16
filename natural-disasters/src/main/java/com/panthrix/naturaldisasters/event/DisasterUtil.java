package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Funciones comunes de los fenomenos: zona, jugadores afectados, dano y limite de bloques. */
public final class DisasterUtil {

    private DisasterUtil() {}

    public static int radius() {
        return CatastropheConfig.RADIUS.get();
    }

    public static boolean inArea(CatastropheSavedData d, double x, double z) {
        double dx = x - d.centerX;
        double dz = z - d.centerZ;
        int r = radius();
        return dx * dx + dz * dz <= (double) r * r;
    }

    /** Jugadores dentro del radio del evento (solo en el mundo normal). */
    public static List<ServerPlayer> playersInArea(ServerLevel level, CatastropheSavedData d) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (inArea(d, p.getX(), p.getZ())) result.add(p);
        }
        return result;
    }

    /** Criaturas y jugadores dentro del radio. */
    public static List<LivingEntity> livingInArea(ServerLevel level, CatastropheSavedData d) {
        int r = radius();
        AABB box = new AABB(d.centerX - r, level.getMinBuildHeight(), d.centerZ - r,
                d.centerX + r, level.getMaxBuildHeight(), d.centerZ + r);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && inArea(d, e.getX(), e.getZ()));
    }

    /** Respeta la configuracion de dano y nunca dana a jugadores en creativo o espectador. */
    public static boolean canHurt(LivingEntity e) {
        if (e instanceof Player p) {
            return CatastropheConfig.DAMAGE_PLAYERS.get() && !p.isCreative() && !p.isSpectator();
        }
        return CatastropheConfig.DAMAGE_MOBS.get();
    }

    public static boolean exposedToSky(ServerLevel level, LivingEntity e) {
        return level.canSeeSky(e.blockPosition());
    }

    /**
     * Posicion de la superficie en un punto al azar de la zona.
     * Devuelve null si ese chunk no esta cargado: nunca cargamos chunks a proposito.
     */
    public static BlockPos randomSurface(ServerLevel level, CatastropheSavedData d, RandomSource random) {
        int r = radius();
        int x = d.centerX + random.nextInt(2 * r + 1) - r;
        int z = d.centerZ + random.nextInt(2 * r + 1) - r;
        if (!inArea(d, x, z)) return null;
        BlockPos column = new BlockPos(x, d.centerY, z);
        if (!level.isLoaded(column)) return null;
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
    }

    /** Reserva un bloque del limite maxBloquesPorEvento. Devuelve false si ya se ha alcanzado. */
    public static boolean takeBlockBudget(CatastropheSavedData d) {
        return takeBlockBudget(d, CatastropheConfig.MAX_BLOCKS_PER_EVENT.get());
    }

    /** Igual, pero con un limite propio (para fenomenos que mueven muchos bloques). */
    public static boolean takeBlockBudget(CatastropheSavedData d, int limit) {
        int used = d.eventData.getInt("blocks");
        if (used >= limit) return false;
        d.eventData.putInt("blocks", used + 1);
        d.setDirty();
        return true;
    }

    public static void clearWeather(ServerLevel level) {
        level.setWeatherParameters(12000, 0, false, false);
    }

    public static void startRain(ServerLevel level, boolean thunder) {
        level.setWeatherParameters(0, 20 * 60 * 30, true, thunder);
    }
}
