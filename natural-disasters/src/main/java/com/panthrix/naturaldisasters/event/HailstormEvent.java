package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.Tags;

/**
 * Granizada: particulas de granizo grandes y pequenas, sonido de impactos, dano limitado
 * a quien este al aire libre sin casco, y (opcional) dano a cultivos y cristales.
 * No se crea ningun bloque ni entidad por cada granizo: son solo particulas.
 */
public class HailstormEvent extends CatastropheEvent {

    private static final BlockParticleOption SMALL_HAIL =
            new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.PACKED_ICE.defaultBlockState());

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        switch (phase) {
            case PREPARATION -> DisasterUtil.startRain(level, false);
            case RECOVERY -> DisasterUtil.clearWeather(level);
            default -> { }
        }
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        double f = CatastropheConfig.intensityFactor();
        boolean active = phase == CatastrophePhase.ACTIVE;

        if (now % 4 == 0) {
            int count = (int) ((active ? 30 : 10) * f);
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.getX(), p.getY() + 5, p.getZ(), count, 6, 1, 6, 0.05);
                level.sendParticles(SMALL_HAIL, p.getX(), p.getY() + 6, p.getZ(), count, 7, 1, 7, 0.0);
            }
        }
        if (now % 8 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                BlockPos near = p.blockPosition().offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
                level.playSound(null, near, SoundEvents.STONE_HIT, SoundSource.WEATHER, 0.5f, 1.6f + level.getRandom().nextFloat() * 0.4f);
            }
        }
        if (!active) return;

        if (now % 40 == 0) {
            float damage = (float) Math.max(1.0, f);
            for (LivingEntity e : DisasterUtil.livingInArea(level, d)) {
                if (!DisasterUtil.canHurt(e) || !DisasterUtil.exposedToSky(level, e)) continue;
                if (!e.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) continue; // el casco protege del granizo
                e.hurt(level.damageSources().generic(), damage);
            }
        }

        if (now % 20 == 0 && !CatastropheConfig.destructionOff()) {
            int tries = 1 + (int) (3 * f);
            for (int i = 0; i < tries; i++) {
                BlockPos pos = DisasterUtil.randomSurface(level, d, level.getRandom());
                if (pos == null) continue;
                if (CatastropheConfig.HAIL_CROPS.get()) damageCrop(level, d, pos);
                if (CatastropheConfig.HAIL_GLASS.get() && CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE) {
                    BlockPos below = pos.below();
                    BlockState s = level.getBlockState(below);
                    if ((s.is(Tags.Blocks.GLASS) || s.is(Tags.Blocks.GLASS_PANES)) && DisasterUtil.takeBlockBudget(d)) {
                        level.destroyBlock(below, false);
                    }
                }
            }
        }
    }

    /** Hace retroceder un paso el crecimiento del cultivo. Nunca lo elimina. */
    private static void damageCrop(ServerLevel level, CatastropheSavedData d, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CropBlock)) return;
        IntegerProperty age = s.hasProperty(BlockStateProperties.AGE_7) ? BlockStateProperties.AGE_7
                : s.hasProperty(BlockStateProperties.AGE_3) ? BlockStateProperties.AGE_3 : null;
        if (age == null || s.getValue(age) <= 0) return;
        if (!DisasterUtil.takeBlockBudget(d)) return;
        level.setBlock(pos, s.setValue(age, s.getValue(age) - 1), 2);
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        DisasterUtil.clearWeather(level);
    }
}
