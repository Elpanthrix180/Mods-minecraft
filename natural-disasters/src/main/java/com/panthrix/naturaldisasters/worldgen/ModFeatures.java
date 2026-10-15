package com.panthrix.naturaldisasters.worldgen;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro de los elementos de generacion del mundo del mod. */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, NaturalDisastersMod.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> VOLCANO =
            FEATURES.register("volcano", () -> new VolcanoFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
