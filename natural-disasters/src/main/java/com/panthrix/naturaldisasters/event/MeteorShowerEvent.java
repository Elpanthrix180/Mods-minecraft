package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Lluvia de meteoritos gigantes.
 * - Aviso y preparacion: el cielo se tine de naranja y se ven estrellas fugaces.
 * - Cada meteorito es una BOLA GIGANTE de magma brillante (un "block display" de Minecraft, solo visual,
 *   de 4 a 9 bloques) envuelta en fuego, con una estela larga. Baja del cielo en diagonal y acelera.
 * - Impacto: explosiones, onda de choque fuerte y temblor. Con destruccion PARTIAL/INTENSE deja un
 *   CRATER GIGANTE (cuenco de 7 a 20 bloques de radio) con paredes de obsidiana, magma y piedra negra
 *   y un lago de lava en el fondo. El crater se excava poco a poco (bloquesPorTick) para no congelar el juego.
 * Todo (meteoritos en vuelo y crateres a medias) se guarda en el mundo.
 */
public class MeteorShowerEvent extends CatastropheEvent {

    private static final String KEY = "meteors";
    private static final String CRATERS = "craters";
    private static final String ENTITY_TAG = NaturalDisastersMod.MOD_ID + "_meteor";
    private static final int MAX_IN_FLIGHT = 8;

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        tintSky(level, d, phase, now);

        if (phase == CatastrophePhase.PREPARATION && now % 8 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 45, p.getZ(), 6, 35, 6, 35, 0.3);
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 50, p.getZ(), 4, 40, 4, 40, 0.1);
            }
        }
        // Los crateres se siguen excavando aunque la lluvia este terminando.
        digCraters(level, d);
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING && phase != CatastrophePhase.RECOVERY) return;

        ListTag meteors = d.eventData.getList(KEY, Tag.TAG_COMPOUND);
        if (phase != CatastrophePhase.RECOVERY) {
            double perMinute = CatastropheConfig.METEORS_PER_MINUTE.get() * CatastropheConfig.intensityFactor();
            if (phase == CatastrophePhase.ENDING) perMinute /= 3;
            int interval = (int) Math.max(10, 1200 / Math.max(1, perMinute));
            if (now % interval == 0 && meteors.size() < MAX_IN_FLIGHT) spawnMeteor(level, d, meteors, now);
        }

        for (int i = meteors.size() - 1; i >= 0; i--) {
            CompoundTag m = meteors.getCompound(i);
            double t = (now - m.getLong("t0")) / (double) m.getInt("dur");
            float size = m.getFloat("size");
            Entity ball = m.hasUUID("ent") ? level.getEntity(m.getUUID("ent")) : null;
            if (t >= 1.0) {
                if (ball != null) ball.discard();
                impact(level, d, BlockPos.of(m.getLong("target")), size);
                meteors.remove(i);
                continue;
            }
            double e = t * t; // acelera al acercarse al suelo
            Vec3 start = new Vec3(m.getDouble("sx"), m.getDouble("sy"), m.getDouble("sz"));
            Vec3 end = new Vec3(m.getDouble("tx"), m.getDouble("ty"), m.getDouble("tz"));
            Vec3 pos = start.lerp(end, e);
            if (ball != null) ball.setPos(pos.x, pos.y, pos.z);
            drawMeteor(level, pos, start.subtract(end).normalize(), size);
            if (now % 20 == 0) {
                level.playSound(null, BlockPos.containing(pos), SoundEvents.FIRECHARGE_USE, SoundSource.WEATHER, 6.0f, 0.3f);
            }
        }
        d.eventData.put(KEY, meteors);
        d.setDirty();
    }

    // ------------------------------------------------------------------ cielo y meteoritos

    /** Cielo naranja: sube en el aviso y la preparacion, maximo durante la lluvia y se apaga al final. */
    private static void tintSky(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (!CatastropheConfig.METEOR_SKY_TINT.get() || now % 20 != 0) return;
        float strength = switch (phase) {
            case WARNING -> 0.35f;
            case PREPARATION -> 0.75f;
            case ACTIVE -> 1.0f;
            case ENDING -> 0.5f;
            default -> 0f;
        };
        if (strength <= 0f) return;
        double far = DisasterUtil.radius() * 4.0; // el cielo se ve desde lejos
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - d.centerX, dz = p.getZ() - d.centerZ;
            if (dx * dx + dz * dz <= far * far) ModNetwork.sendSkyTint(p, strength, 60);
        }
    }

    /** Fuego, lava y humo alrededor de la bola, y una estela larga hacia atras. */
    private static void drawMeteor(ServerLevel level, Vec3 pos, Vec3 back, float size) {
        double r = 1.6 * size;
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, (int) (30 * size), r, r, r, 0.06);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, (int) (5 * size), r * 0.7, r * 0.7, r * 0.7, 0.0);
        for (int k = 1; k <= 10; k++) {
            Vec3 p = pos.add(back.scale(k * 3.0 * size));
            double spread = r * (1.0 - k / 12.0);
            level.sendParticles(k < 4 ? ParticleTypes.FLAME : ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z,
                    (int) (7 * size), spread, spread, spread, 0.01);
        }
    }

    private static void spawnMeteor(ServerLevel level, CatastropheSavedData d, ListTag meteors, long now) {
        RandomSource r = level.getRandom();
        BlockPos target = DisasterUtil.randomSurface(level, d, r);
        if (target == null) return;
        double a = r.nextDouble() * Math.PI * 2;
        float size = (float) ((1.5 + r.nextDouble() * 1.5) * Math.min(1.5, CatastropheConfig.intensityFactor()));
        CompoundTag m = new CompoundTag();
        m.putDouble("sx", target.getX() + 0.5 + Math.cos(a) * 70);
        m.putDouble("sy", Math.min(level.getMaxBuildHeight() - 2, target.getY() + 140));
        m.putDouble("sz", target.getZ() + 0.5 + Math.sin(a) * 70);
        m.putDouble("tx", target.getX() + 0.5);
        m.putDouble("ty", target.getY());
        m.putDouble("tz", target.getZ() + 0.5);
        m.putLong("target", target.asLong());
        m.putLong("t0", now);
        m.putInt("dur", 70 + r.nextInt(40));
        m.putFloat("size", size);
        Entity ball = spawnBall(level, new Vec3(m.getDouble("sx"), m.getDouble("sy"), m.getDouble("sz")), 3.0f * size);
        if (ball != null) m.putUUID("ent", ball.getUUID());
        meteors.add(m);
    }

    /**
     * Crea la bola gigante: un "block display" (entidad solo visual de Minecraft 1.19.4+) que muestra un
     * bloque de magma escalado y brillante. No colisiona ni hace nada por si misma.
     */
    private static Entity spawnBall(ServerLevel level, Vec3 pos, float scale) {
        try {
            Display.BlockDisplay ball = EntityType.BLOCK_DISPLAY.create(level);
            if (ball == null) return null;
            CompoundTag tag = new CompoundTag();
            CompoundTag state = new CompoundTag();
            state.putString("Name", "minecraft:magma_block");
            tag.put("block_state", state);
            CompoundTag tr = new CompoundTag();
            tr.put("translation", floats(-scale / 2, -scale / 2, -scale / 2));
            tr.put("scale", floats(scale, scale, scale));
            tr.put("left_rotation", floats(0, 0, 0, 1));
            tr.put("right_rotation", floats(0, 0, 0, 1));
            tag.put("transformation", tr);
            CompoundTag light = new CompoundTag();
            light.putInt("sky", 15);
            light.putInt("block", 15);
            tag.put("brightness", light);
            tag.putFloat("view_range", 8.0f);
            ball.load(tag);
            ball.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
            ball.addTag(ENTITY_TAG);
            level.addFreshEntity(ball);
            return ball;
        } catch (Exception e) {
            NaturalDisastersMod.LOGGER.warn("[Panthrix's Natural Disasters] No se pudo crear la bola del meteorito; solo habra particulas.", e);
            return null;
        }
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    // ------------------------------------------------------------------ impacto

    private static void impact(ServerLevel level, CatastropheSavedData d, BlockPos pos, float size) {
        if (!level.isLoaded(pos)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        RandomSource rand = level.getRandom();
        for (int i = 0; i < 4 + (int) (2 * size); i++) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x + rand.nextGaussian() * 3 * size, y + rand.nextDouble() * 3,
                    z + rand.nextGaussian() * 3 * size, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.LAVA, x, y + 1, z, (int) (60 * size), 4 * size, 1.5, 4 * size, 0.0);
        level.sendParticles(ParticleTypes.FLAME, x, y + 1, z, (int) (80 * size), 4 * size, 3, 4 * size, 0.3);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 2, z, (int) (40 * size), 4 * size, 2, 4 * size, 0.06);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 12.0f, 0.4f);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10.0f, 0.3f);

        double reach = 8 + 5 * size;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(reach), LivingEntity::isAlive)) {
            double dist = Math.sqrt(e.distanceToSqr(x, y, z));
            if (dist > reach) continue;
            double k = 1.0 - dist / reach;
            if (DisasterUtil.canHurt(e)) {
                e.hurt(level.damageSources().explosion(null, null), (float) (14 * size * k) + 1);
                if (k > 0.4) e.setSecondsOnFire(6);
            }
            Vec3 away = e.position().subtract(x, y, z).normalize().scale(2.0 * k);
            e.setDeltaMovement(e.getDeltaMovement().add(away.x, 0.8 * k, away.z));
            e.hurtMarked = true;
        }
        if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
            for (ServerPlayer p : level.players()) {
                double dist = Math.sqrt(p.distanceToSqr(x, y, z));
                if (dist < 100) ModNetwork.sendShake(p, (float) (3.0 * size * (1.0 - dist / 100)), 35);
            }
        }
        if (CatastropheConfig.destructionOff()) return;

        // El crater se excava poco a poco en los siguientes ticks.
        int r = Math.max(3, Math.round(CatastropheConfig.METEOR_CRATER_RADIUS.get() * size * 0.6f));
        CompoundTag job = new CompoundTag();
        job.putInt("x", pos.getX());
        job.putInt("y", pos.getY());
        job.putInt("z", pos.getZ());
        job.putInt("r", r);
        job.putInt("depth", Math.max(3, (int) (r * 0.65)));
        job.putInt("stage", 0);
        job.putInt("idx", 0);
        ListTag craters = d.eventData.getList(CRATERS, Tag.TAG_COMPOUND);
        craters.add(job);
        d.eventData.put(CRATERS, craters);
        d.setDirty();
    }

    // ------------------------------------------------------------------ crateres por tandas

    private static void digCraters(ServerLevel level, CatastropheSavedData d) {
        ListTag craters = d.eventData.getList(CRATERS, Tag.TAG_COMPOUND);
        if (craters.isEmpty()) return;
        int[] quota = {CatastropheConfig.METEOR_BLOCKS_PER_TICK.get()};
        while (!craters.isEmpty() && quota[0] > 0) {
            CompoundTag job = craters.getCompound(0);
            if (craterStep(level, d, job, quota)) craters.remove(0);
        }
        d.eventData.put(CRATERS, craters);
        d.setDirty();
    }

    /**
     * Avanza el crater columna a columna. Etapas: 0 = excavar el cuenco (y lo que haya encima),
     * 1 = revestir con obsidiana/magma/piedra negra, 2 = lago de lava en el centro.
     * Devuelve true cuando el crater esta terminado (o se ha agotado el limite de bloques).
     */
    private static boolean craterStep(ServerLevel level, CatastropheSavedData d, CompoundTag job, int[] quota) {
        int cx = job.getInt("x"), cy = job.getInt("y"), cz = job.getInt("z");
        int r = job.getInt("r"), depth = job.getInt("depth");
        int side = 2 * r + 1, columns = side * side;
        int limit = CatastropheConfig.METEOR_MAX_BLOCKS.get();
        RandomSource rand = level.getRandom();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        while (quota[0] > 0) {
            int stage = job.getInt("stage"), idx = job.getInt("idx");
            if (idx >= columns) {
                if (stage >= 2) return true;
                job.putInt("stage", stage + 1);
                job.putInt("idx", 0);
                continue;
            }
            job.putInt("idx", idx + 1);
            int dx = idx % side - r, dz = idx / side - r;
            double hd = Math.sqrt(dx * dx + dz * dz) / r;
            if (hd > 1.0) continue;
            int bottom = (int) Math.round(-depth * Math.sqrt(1.0 - hd * hd));
            int x = cx + dx, z = cz + dz;
            if (!level.isLoaded(p.set(x, cy, z))) continue;

            if (stage == 0) {
                // Excavar desde el fondo del cuenco hasta bastante por encima (arboles, casas...).
                for (int dy = bottom; dy <= Math.max(6, r / 2); dy++) {
                    p.set(x, cy + dy, z);
                    if (!removable(level, p)) continue;
                    if (!DisasterUtil.takeBlockBudget(d, limit)) return true;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                    quota[0]--;
                }
                quota[0]--;
            } else if (stage == 1) {
                // Revestimiento: dos capas de roca fundida bajo la superficie del cuenco.
                for (int layer = 1; layer <= 2; layer++) {
                    p.set(x, cy + bottom - layer, z);
                    BlockState old = level.getBlockState(p);
                    if (!old.isAir() && !removable(level, p)) continue;
                    if (!BlockProtection.canPlace(level, p) || !DisasterUtil.takeBlockBudget(d, limit)) return true;
                    int n = rand.nextInt(10);
                    BlockState rock = n < 4 ? Blocks.OBSIDIAN.defaultBlockState()
                            : n < 7 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState();
                    level.setBlock(p, rock, 2);
                    quota[0]--;
                }
                // Algunos fuegos en el borde del crater.
                if (hd > 0.85 && rand.nextInt(6) == 0) {
                    p.set(x, cy + bottom, z);
                    if (level.isEmptyBlock(p) && BlockProtection.canPlace(level, p)) level.setBlock(p, Blocks.FIRE.defaultBlockState(), 3);
                }
            } else {
                // Lago de lava en el centro del fondo, con hierro en bruto a veces debajo.
                if (hd > 0.35) continue;
                p.set(x, cy + bottom, z);
                if (!level.isEmptyBlock(p) || !BlockProtection.canPlace(level, p)) continue;
                if (!DisasterUtil.takeBlockBudget(d, limit)) return true;
                BlockPos below = p.below();
                if (CatastropheConfig.METEOR_ORES.get() && rand.nextInt(5) == 0
                        && (level.isEmptyBlock(below) || removable(level, below))) {
                    level.setBlock(below, Blocks.RAW_IRON_BLOCK.defaultBlockState(), 2);
                }
                level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                quota[0] -= 2;
            }
        }
        return false;
    }

    /** Se puede quitar si no es aire ni liquido, no toca agua y no esta protegido. */
    private static boolean removable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        for (Direction dir : Direction.values()) {
            if (level.getFluidState(pos.relative(dir)).is(Fluids.WATER)) return false;
        }
        return BlockProtection.canModify(level, pos, s);
    }

    // ------------------------------------------------------------------ final

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase == CatastrophePhase.RECOVERY) removeBalls(level, d);
    }

    @Override
    public void onFinish(ServerLevel level, CatastropheSavedData d) {
        removeBalls(level, d);
        // Terminar los crateres que quedaran a medias (con el mismo limite de bloques).
        ListTag craters = d.eventData.getList(CRATERS, Tag.TAG_COMPOUND);
        int[] quota = {Integer.MAX_VALUE};
        for (int i = 0; i < craters.size(); i++) craterStep(level, d, craters.getCompound(i), quota);
    }

    /** Quita las bolas que queden en el aire (por ejemplo, tras /pnd stop). */
    private static void removeBalls(ServerLevel level, CatastropheSavedData d) {
        for (Tag t : d.eventData.getList(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            if (m.hasUUID("ent")) {
                UUID id = m.getUUID("ent");
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
        double far = DisasterUtil.radius() * 4.0 + 100;
        AABB box = new AABB(d.centerX - far, level.getMinBuildHeight(), d.centerZ - far,
                d.centerX + far, level.getMaxBuildHeight(), d.centerZ + far);
        for (Display.BlockDisplay e : level.getEntitiesOfClass(Display.BlockDisplay.class, box, en -> en.getTags().contains(ENTITY_TAG))) {
            e.discard();
        }
    }
}
