package com.panthrix.naturaldisasters.worldgen;

import com.mojang.serialization.Codec;
import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.VolcanoRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Elemento de generacion del mundo que coloca volcanes naturales en chunks nuevos.
 * Se registra en el sistema de Forge (biome modifier) y solo se aplica en los biomas
 * de la etiqueta panthrixsnaturaldisasters:has_volcano.
 */
public class VolcanoFeature extends Feature<NoneFeatureConfiguration> {

    public VolcanoFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!CatastropheConfig.VOLCANO_NATURAL.get()) return false;
        WorldGenLevel level = ctx.level();
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        RandomSource r = ctx.random();
        if (r.nextInt(CatastropheConfig.VOLCANO_RARITY.get()) != 0) return false;

        // Centro del chunk: asi el volcan cabe en este chunk y sus vecinos.
        BlockPos origin = ctx.origin();
        BlockPos center = new BlockPos((origin.getX() & ~15) + 8, 0, (origin.getZ() & ~15) + 8);
        if (looksBuilt(level, center)) return false;

        BlockPos crater = VolcanoBuilder.build(level, center, r, false);
        if (crater == null) return false;
        VolcanoRegistry.queue(crater);
        return true;
    }

    /** Evita colocar el volcan encima de construcciones (aldeas, ruinas...) que ya esten generadas. */
    private static boolean looksBuilt(WorldGenLevel level, BlockPos center) {
        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                if (s.hasBlockEntity() || s.is(BlockTags.PLANKS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS)
                        || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS) || s.is(BlockTags.DOORS) || s.is(BlockTags.FENCES)) {
                    return true;
                }
            }
        }
        return false;
    }
}
