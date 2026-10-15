package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tormenta electrica extrema: lluvia y truenos con rayos frecuentes dentro de la zona.
 * Los rayos se limitan por intervalo y por un maximo por evento para no saturar el servidor.
 * Si los incendios estan desactivados, los rayos son solo visuales y el dano se aplica a mano.
 */
public class ExtremeThunderstormEvent extends CatastropheEvent {

    private static final int MAX_BOLTS_PER_EVENT = 300;

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        switch (phase) {
            case PREPARATION -> DisasterUtil.startRain(level, true);
            case RECOVERY -> DisasterUtil.clearWeather(level);
            default -> { }
        }
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase == CatastrophePhase.PREPARATION && now % 100 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6f, 0.6f);
            }
            return;
        }
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;

        int interval = (int) Math.max(10, 60 / CatastropheConfig.intensityFactor());
        if (phase == CatastrophePhase.ENDING) interval *= 3;
        if (now % interval != 0) return;

        int bolts = d.eventData.getInt("bolts");
        if (bolts >= MAX_BOLTS_PER_EVENT) return;
        BlockPos pos = DisasterUtil.randomSurface(level, d, level.getRandom());
        if (pos == null) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(pos));

        boolean realBolt = CatastropheConfig.LIGHTNING_FIRES.get() && !CatastropheConfig.destructionOff()
                && CatastropheConfig.DAMAGE_PLAYERS.get() && CatastropheConfig.DAMAGE_MOBS.get();
        if (!realBolt) {
            // Rayo visual: no quema ni dana por si mismo. Aplicamos el dano respetando la configuracion.
            bolt.setVisualOnly(true);
            AABB box = new AABB(pos).inflate(3);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (DisasterUtil.canHurt(e)) e.hurt(level.damageSources().lightningBolt(), 5.0f);
            }
        }
        level.addFreshEntity(bolt);
        d.eventData.putInt("bolts", bolts + 1);
        d.setDirty();
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        DisasterUtil.clearWeather(level);
    }
}
