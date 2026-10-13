package com.robotsconstructores.mining;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;

/** Materiales predefinidos + la opción "bloque de muestra" (cualquier bloque del juego). */
public final class MiningMaterials {
    private static List<MiningMaterial> presets;

    private static MiningMaterial preset(String key, Item counted, Block... blocks) {
        return new MiningMaterial(key, List.of(blocks), counted, Component.translatable("material.robotsconstructores." + key));
    }

    public static List<MiningMaterial> presets() {
        if (presets == null) {
            presets = List.of(
                    preset("stone", Items.COBBLESTONE, Blocks.STONE),
                    preset("deepslate", Items.COBBLED_DEEPSLATE, Blocks.DEEPSLATE),
                    preset("coal", Items.COAL, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
                    preset("iron", Items.RAW_IRON, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
                    preset("copper", Items.RAW_COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
                    preset("gold", Items.RAW_GOLD, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
                    preset("redstone", Items.REDSTONE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
                    preset("lapis", Items.LAPIS_LAZULI, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
                    preset("diamond", Items.DIAMOND, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
                    preset("emerald", Items.EMERALD, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
                    preset("quartz", Items.QUARTZ, Blocks.NETHER_QUARTZ_ORE),
                    preset("ancient_debris", Items.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS));
        }
        return presets;
    }

    /** Índice especial: usar el bloque puesto en la ranura de muestra. */
    public static int sampleIndex() {
        return presets().size();
    }

    public static int optionCount() {
        return presets().size() + 1;
    }

    public static int defaultIndex() {
        return 8; // diamante
    }

    /** Devuelve el material elegido o null si la muestra no es un bloque. */
    @Nullable
    public static MiningMaterial resolve(int index, ItemStack sample) {
        if (index >= 0 && index < presets().size()) {
            return presets().get(index);
        }
        if (sample.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return new MiningMaterial("sample", List.of(block), null, block.getName());
        }
        return null;
    }

    private MiningMaterials() {}
}
