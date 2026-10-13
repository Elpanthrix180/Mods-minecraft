package com.robotsconstructores.building;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Predicate;

/** Qué tipo de bloque va en cada posición de un plano y qué materiales sirven. */
public enum BlockRole {
    /** Piedra o roca (la pared de piedra original). */
    STONE_WALL(false, s -> s.is(Items.STONE) || s.is(Items.COBBLESTONE)),
    /** Paredes y techo: tablones de cualquier madera, roca, piedra, ladrillos... */
    STRUCTURE(false, s -> s.is(ItemTags.PLANKS) || s.is(Items.COBBLESTONE) || s.is(Items.STONE)
            || s.is(Items.STONE_BRICKS) || s.is(Items.BRICKS) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.DEEPSLATE_BRICKS)),
    /** Ventanas: cristal. Si no hay, se usa material de estructura. */
    GLASS(false, s -> s.is(Tags.Items.GLASS)),
    /** Puerta de madera (opcional: si no hay, se deja el hueco). */
    DOOR(true, s -> s.is(ItemTags.WOODEN_DOORS)),
    /** Escalera de mano (opcional). */
    LADDER(true, s -> s.is(Items.LADDER)),
    /** Camino: el bloque que el jugador pone en la ranura de muestra (se gestiona en el robot). */
    PATH(false, s -> false),
    /** Senda de tierra: no gasta materiales. */
    DIRT_PATH(true, s -> false),
    /** Bloque exacto de un plano o imagen (el material se calcula a partir del bloque). */
    BLUEPRINT(false, s -> false);

    private final boolean optional;
    private final Predicate<ItemStack> accepts;

    BlockRole(boolean optional, Predicate<ItemStack> accepts) {
        this.optional = optional;
        this.accepts = accepts;
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && accepts.test(stack);
    }

    /** Si falta este material, se puede usar el de este otro papel. */
    @Nullable
    public BlockRole fallback() {
        return this == GLASS ? STRUCTURE : null;
    }

    public boolean isOptional() {
        return optional;
    }

    public String translationKey() {
        return "role.robotsconstructores." + name().toLowerCase(Locale.ROOT);
    }
}
