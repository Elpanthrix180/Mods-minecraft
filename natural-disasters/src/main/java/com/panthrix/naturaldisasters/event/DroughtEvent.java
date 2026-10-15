package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastropheManager;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.core.CatastropheType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Sequia: impide la lluvia, muestra polvo y ceniza en el aire, frena el crecimiento de los
 * cultivos de la zona (temporal) y, si se permite, seca la tierra de cultivo.
 * No elimina agua ni cultivos. Al terminar llega una lluvia suave de recuperacion.
 */
public class DroughtEvent extends CatastropheEvent {

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        switch (phase) {
            case PREPARATION, ACTIVE -> level.setWeatherParameters(20 * 60 * 30, 0, false, false);
            case RECOVERY -> level.setWeatherParameters(0, 20 * 60 * 2, true, false);
            default -> { }
        }
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        if (now % 200 == 0 && level.isRaining()) level.setWeatherParameters(20 * 60 * 30, 0, false, false);

        if (now % 10 == 0) {
            int count = (int) (12 * CatastropheConfig.intensityFactor());
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 1, p.getZ(), count, 6, 2, 6, 0.02);
            }
        }

        if (phase == CatastrophePhase.ACTIVE && now % 40 == 0
                && CatastropheConfig.DROUGHT_DRY_FARMLAND.get() && !CatastropheConfig.destructionOff()) {
            int tries = 1 + (int) (2 * CatastropheConfig.intensityFactor());
            for (int i = 0; i < tries; i++) {
                BlockPos pos = DisasterUtil.randomSurface(level, d, level.getRandom());
                if (pos == null) continue;
                // La tierra de cultivo esta debajo del cultivo o es la propia superficie.
                for (BlockPos p : new BlockPos[]{pos.below(), pos}) {
                    BlockState s = level.getBlockState(p);
                    if (s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE) > 0 && DisasterUtil.takeBlockBudget(d)) {
                        level.setBlock(p, s.setValue(FarmBlock.MOISTURE, 0), 2);
                    }
                }
            }
        }
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        // No hay nada que deshacer: la tierra se vuelve a humedecer sola con el agua cercana o la lluvia.
    }

    /** Evento de Forge: frena el crecimiento de los cultivos dentro de la zona durante la sequia. */
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!CatastropheConfig.DROUGHT_CROPS.get()) return;
        CatastropheSavedData d = CatastropheManager.data(level.getServer());
        if (d.activeType != CatastropheType.DROUGHT) return;
        if (d.phase != CatastrophePhase.ACTIVE && d.phase != CatastrophePhase.ENDING) return;
        BlockPos pos = event.getPos();
        if (!DisasterUtil.inArea(d, pos.getX(), pos.getZ())) return;
        double chance = Math.min(0.9, 0.45 * CatastropheConfig.intensityFactor());
        if (level.getRandom().nextDouble() < chance) event.setResult(Event.Result.DENY);
    }
}
