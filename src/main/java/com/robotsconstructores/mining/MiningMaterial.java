package com.robotsconstructores.mining;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Qué bloques se pueden minar y qué objeto se cuenta para la cantidad objetivo.
 * Si countedItem es null se cuentan TODOS los objetos que suelten los bloques.
 */
public record MiningMaterial(String key, List<Block> blocks, @Nullable Item countedItem, Component displayName) {
    public boolean matches(BlockState state) {
        for (Block b : blocks) {
            if (state.is(b)) {
                return true;
            }
        }
        return false;
    }
}
