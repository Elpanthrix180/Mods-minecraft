package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Agujero negro VERTICAL que crece. Es una columna oscura alta con un disco de luz que gira en
 * vertical y chorros de energia arriba y abajo. Su tamano crece durante la fase activa y, con el,
 * la fuerza y el alcance de la atraccion.
 * Con destruccion PARTIAL/INTENSE se traga todo lo que no este protegido dentro de su radio, que
 * avanza de dentro hacia fuera (arboles, terreno y construcciones/casas). Los bloques con inventario
 * (cofres, hornos, maquinas), la roca madre y las zonas protegidas nunca se tragan.
 * Al terminar se colapsa con un destello.
 */
public class BlackHoleEvent extends CatastropheEvent {

    @Override
    public void chooseLocation(ServerLevel level, CatastropheSavedData d) {
        RandomSource r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        d.centerX += (int) (Math.cos(a) * 20);
        d.centerZ += (int) (Math.sin(a) * 20);
        if (level.isLoaded(new BlockPos(d.centerX, d.centerY, d.centerZ))) {
            d.centerY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, d.centerX, d.centerZ);
        }
        d.centerY += CatastropheConfig.BLACK_HOLE_HEIGHT.get();
        d.setDirty();
    }

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase == CatastrophePhase.ACTIVE) {
            d.eventData.putLong("activeStart", level.getGameTime());
            d.setDirty();
        }
        if (phase == CatastrophePhase.RECOVERY) collapse(level, d);
    }

    /** Tamano actual: 0..1. Crece durante la fase activa y se mantiene al maximo en la final. */
    private static double growth(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase == CatastrophePhase.PREPARATION) return 0.05;
        long activeTicks = Math.max(1, CatastropheConfig.DURATION_SECONDS.get(d.activeType).get() * 20L);
        double g = (level.getGameTime() - d.eventData.getLong("activeStart")) / (double) activeTicks;
        return phase == CatastrophePhase.ENDING ? 1.0 : Math.max(0.05, Math.min(1.0, g * 1.15));
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.PREPARATION && phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        double cx = d.centerX + 0.5, cy = d.centerY + 0.5, cz = d.centerZ + 0.5;
        if (!level.isLoaded(BlockPos.containing(cx, cy, cz))) return;
        double f = CatastropheConfig.intensityFactor();
        double g = growth(level, d, phase);
        int maxR = CatastropheConfig.BLACK_HOLE_MAX_RADIUS.get();
        double eatRadius = 1.5 + (maxR - 1.5) * g;           // radio en el que traga bloques
        double coreW = 0.6 + eatRadius * 0.25;                // ancho visual del nucleo
        double coreH = coreW * 3.0;                           // es vertical: tres veces mas alto que ancho

        if (now % 2 == 0) draw(level, cx, cy, cz, now, coreW, coreH, eatRadius);
        if (now % 40 == 0) {
            BlockPos at = BlockPos.containing(cx, cy, cz);
            float vol = (float) (1.0 + 3.0 * g);
            level.playSound(null, at, SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, vol, 0.4f);
            level.playSound(null, at, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, vol, 0.5f);
            if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(cx, cy, cz) < Math.pow(pullRadius(eatRadius) * 2, 2)) {
                        ModNetwork.sendShake(p, (float) ((0.3 + 1.2 * g) * f), 45);
                    }
                }
            }
        }
        if (phase == CatastrophePhase.PREPARATION) return;

        pull(level, cx, cy, cz, pullRadius(eatRadius), (0.4 + 0.9 * g) * f, eatRadius, now);

        if (CatastropheConfig.destructionOff()) return;
        boolean intense = CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE;
        int perTick = (int) Math.max(2, (intense ? 70 : 35) * f * (0.3 + g));
        if (phase == CatastrophePhase.ENDING) perTick /= 2;
        RandomSource r = level.getRandom();
        for (int i = 0; i < perTick; i++) {
            if (!absorbBlock(level, d, cx, cy, cz, eatRadius, coreH, r, intense)) break;
        }
    }

    private static double pullRadius(double eatRadius) {
        return CatastropheConfig.BLACK_HOLE_PULL_RADIUS.get() + eatRadius * 1.5;
    }

    /** Columna oscura vertical + disco de luz girando en vertical + chorros arriba y abajo. */
    private static void draw(ServerLevel level, double x, double y, double z, long now,
                             double w, double h, double eat) {
        // Nucleo: columna negra, tres veces mas alta que ancha.
        int layers = Math.max(3, (int) (h * 1.5));
        for (int k = 0; k < layers; k++) {
            double ly = y - h + (2 * h) * k / (layers - 1.0);
            double t = (ly - y) / h;                          // -1..1
            double lw = w * Math.sqrt(Math.max(0.05, 1 - t * t)); // mas estrecho arriba y abajo
            level.sendParticles(ParticleTypes.SQUID_INK, x, ly, z, 3, lw * 0.5, 0.2, lw * 0.5, 0.0);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 15, w, h, w, 0.05);

        // Disco de acrecion VERTICAL (gira en el plano vertical que mira al norte-sur y va rotando despacio).
        double spin = now * 0.22;
        double tilt = now * 0.01;                             // el plano del disco gira lentamente
        double ax = Math.cos(tilt), az = Math.sin(tilt);
        int points = 24;
        for (int k = 0; k < points; k++) {
            double a = spin + k * (Math.PI * 2 / points);
            double rad = (w * 1.8 + 1.0) + (k % 3) * 0.6;
            double hx = Math.cos(a) * rad;
            double vy = Math.sin(a) * rad * 1.4;              // mas alto que ancho
            level.sendParticles(k % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
                    x + hx * ax, y + vy, z + hx * az, 1, 0, 0, 0, 0);
        }
        // Chorros de energia arriba y abajo.
        level.sendParticles(ParticleTypes.END_ROD, x, y + h + 1, z, 4, 0.2, 2.5, 0.2, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, x, y - h - 1, z, 4, 0.2, 2.5, 0.2, 0.15);
        // Polvo que cae en espiral hacia el borde de la zona de absorcion.
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 20, eat, eat * 0.6, eat, 0.6);
    }

    /** Atrae entidades, objetos y bloques sueltos; el dano solo ocurre dentro del nucleo. */
    private static void pull(ServerLevel level, double cx, double cy, double cz, double radius,
                             double strength, double eatRadius, long now) {
        AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        double coreR = 1.0 + eatRadius * 0.15;
        for (Entity e : level.getEntities((Entity) null, box, en -> en instanceof LivingEntity || en instanceof ItemEntity
                || en instanceof FallingBlockEntity)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 toCenter = new Vec3(cx - e.getX(), cy - e.getY(), cz - e.getZ());
            double dist = toCenter.length();
            if (dist > radius || dist < 0.01) continue;
            double closeness = 1.0 - dist / radius;
            Vec3 dir = toCenter.normalize();
            Vec3 around = new Vec3(-dir.z, 0, dir.x).scale(0.05 * strength * closeness);
            Vec3 v = e.getDeltaMovement().add(dir.scale(0.04 + 0.14 * strength * closeness)).add(around);
            if (v.length() > 1.4) v = v.normalize().scale(1.4);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0;

            if (dist < coreR) {
                if (e instanceof LivingEntity le && now % 10 == 0 && DisasterUtil.canHurt(le)) {
                    le.hurt(level.damageSources().magic(), (float) (5 * strength));
                } else if (e instanceof ItemEntity item && CatastropheConfig.BLACK_HOLE_CONSUMES_ITEMS.get()) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY(), item.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
                    item.discard();
                } else if (e instanceof FallingBlockEntity fb) {
                    fb.discard(); // los bloques tragados desaparecen en el centro
                }
            }
        }
    }

    /**
     * Traga un bloque. Elige puntos cada vez mas lejos del centro (de dentro hacia fuera), dentro de
     * un elipsoide un poco mas alto que ancho. Devuelve false si se ha agotado el limite de bloques.
     */
    private static boolean absorbBlock(ServerLevel level, CatastropheSavedData d, double cx, double cy, double cz,
                                       double radius, double coreH, RandomSource r, boolean intense) {
        // Direccion al azar y distancia sesgada hacia el borde actual.
        double u = r.nextDouble() * 2 - 1;
        double th = r.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        double dist = radius * (0.55 + 0.45 * Math.cbrt(r.nextDouble()));
        double vertical = 1.0 + Math.min(1.0, coreH / Math.max(1, radius));   // mas alcance vertical
        BlockPos pos = BlockPos.containing(cx + s * Math.cos(th) * dist, cy + u * dist * vertical, cz + s * Math.sin(th) * dist);
        if (!level.isLoaded(pos)) return true;
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return true;
        if (!st.getFluidState().isEmpty()) {
            // El agua y la lava tambien se las traga (si no estan en una zona protegida).
            if (!BlockProtection.canPlace(level, pos)) return true;
            if (!DisasterUtil.takeBlockBudget(d, CatastropheConfig.BLACK_HOLE_MAX_BLOCKS.get())) return false;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        }
        if (!BlockProtection.canModify(level, pos, st)) return true;
        if (!DisasterUtil.takeBlockBudget(d, CatastropheConfig.BLACK_HOLE_MAX_BLOCKS.get())) return false;
        if (intense && r.nextInt(3) == 0) {
            // Algunos bloques salen volando hacia el centro antes de desaparecer.
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, st);
            fb.dropItem = false;
            fb.setNoGravity(true);
            fb.setDeltaMovement(new Vec3(cx - fb.getX(), cy - fb.getY(), cz - fb.getZ()).normalize().scale(0.5));
            fb.hurtMarked = true;
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            Vec3 toC = new Vec3(cx - pos.getX(), cy - pos.getY(), cz - pos.getZ()).normalize();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    6, toC.x, toC.y, toC.z, 0.4);
        }
        return true;
    }

    /** Final: se colapsa con un destello y desaparecen los bloques que seguian flotando. */
    private static void collapse(ServerLevel level, CatastropheSavedData d) {
        double cx = d.centerX + 0.5, cy = d.centerY + 0.5, cz = d.centerZ + 0.5;
        if (!level.isLoaded(BlockPos.containing(cx, cy, cz))) return;
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 200, 3, 6, 3, 0.8);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 80, 1, 4, 1, 0.5);
        level.playSound(null, BlockPos.containing(cx, cy, cz), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 1.2f);
        double radius = pullRadius(CatastropheConfig.BLACK_HOLE_MAX_RADIUS.get());
        AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        for (FallingBlockEntity fb : level.getEntitiesOfClass(FallingBlockEntity.class, box, FallingBlockEntity::isNoGravity)) {
            fb.discard();
        }
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        collapse(level, d);
    }
}
