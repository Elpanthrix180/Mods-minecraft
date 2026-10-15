package com.panthrix.naturaldisasters.core;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import com.panthrix.naturaldisasters.config.CatastropheConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Decide si un fenomeno puede tocar un bloque. Es la unica puerta para modificar el terreno.
 * Limitacion: no detecta las protecciones de otros mods (claims, etc.). Para eso usa zonasProtegidas.
 */
public final class BlockProtection {

    private static List<? extends String> cachedZoneSource;
    private static final List<int[]> ZONES = new ArrayList<>();

    private BlockProtection() {}

    /** true si el bloque se puede modificar o eliminar. */
    public static boolean canModify(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos)) return false;
        if (state.hasBlockEntity()) return false;                 // cofres, hornos, maquinas, carteles...
        if (state.getDestroySpeed(level, pos) < 0) return false;  // roca madre, barreras, bloques de comandos
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id != null && CatastropheConfig.PROTECTED_BLOCKS.get().contains(id.toString())) return false;
        return !inProtectedZone(pos.getX(), pos.getZ());
    }

    /** true si se puede colocar algo (agua, nieve) en esta posicion vacia. */
    public static boolean canPlace(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && !inProtectedZone(pos.getX(), pos.getZ());
    }

    public static boolean inProtectedZone(int x, int z) {
        List<? extends String> source = CatastropheConfig.PROTECTED_ZONES.get();
        if (source != cachedZoneSource) {
            ZONES.clear();
            for (String s : source) {
                String[] parts = s.split(",");
                try {
                    if (parts.length != 4) throw new NumberFormatException();
                    int x1 = Integer.parseInt(parts[0].trim()), z1 = Integer.parseInt(parts[1].trim());
                    int x2 = Integer.parseInt(parts[2].trim()), z2 = Integer.parseInt(parts[3].trim());
                    ZONES.add(new int[]{Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)});
                } catch (NumberFormatException e) {
                    NaturalDisastersMod.LOGGER.warn("[Panthrix's Natural Disasters] Zona protegida mal escrita, se ignora: {}", s);
                }
            }
            cachedZoneSource = source;
        }
        for (int[] zn : ZONES) {
            if (x >= zn[0] && x <= zn[2] && z >= zn[1] && z <= zn[3]) return true;
        }
        return false;
    }
}
