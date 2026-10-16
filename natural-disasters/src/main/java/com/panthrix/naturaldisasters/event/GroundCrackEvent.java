package com.panthrix.naturaldisasters.event;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.BlockProtection;
import com.panthrix.naturaldisasters.core.CatastropheEvent;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Agrietamiento del terreno. A diferencia del terremoto (sacudida fuerte y grietas de golpe), aqui el
 * suelo se va abriendo despacio: varias grietas salen del centro, avanzan unos bloques por segundo,
 * serpentean y se ramifican como una telarana. Son anchas y profundas cerca del centro y se estrechan
 * hacia la punta.
 * Siempre: crujidos, polvo y vapor en la punta de cada grieta y un temblor ligero (la grieta se "dibuja"
 * con particulas aunque la destruccion este en OFF).
 * Con destruccion PARTIAL/INTENSE la grieta se excava de verdad; con INTENSE, la parte mas profunda puede
 * tener lava en el fondo. Nunca toca bloques protegidos ni excava junto a agua.
 */
public class GroundCrackEvent extends CatastropheEvent {

    private static final String KEY = "cracks";
    private static final int MAX_CRACKS = 30;

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase != CatastrophePhase.ACTIVE) return;
        RandomSource r = level.getRandom();
        ListTag cracks = new ListTag();
        int n = CatastropheConfig.CRACKS_COUNT.get();
        double base = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double ang = base + i * (Math.PI * 2 / n) + (r.nextDouble() - 0.5) * 0.6;
            cracks.add(newCrack(d.centerX + 0.5, d.centerZ + 0.5, ang, CatastropheConfig.CRACKS_LENGTH.get(),
                    CatastropheConfig.CRACKS_WIDTH.get(), CatastropheConfig.CRACKS_DEPTH.get(), 0));
        }
        d.eventData.put(KEY, cracks);
        d.setDirty();
    }

    private static CompoundTag newCrack(double x, double z, double ang, int len, double width, int depth, int gen) {
        CompoundTag c = new CompoundTag();
        c.putDouble("x", x);
        c.putDouble("z", z);
        c.putDouble("ang", ang);
        c.putInt("len", Math.max(4, len));
        c.putDouble("width", Math.max(1.0, width));
        c.putInt("depth", Math.max(2, depth));
        c.putInt("gen", gen);
        c.putInt("t", 0);
        return c;
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase == CatastrophePhase.PREPARATION) {
            // Primeras senales: crujidos y polvo que sale del suelo alrededor del centro.
            if (now % 10 == 0) {
                BlockPos at = new BlockPos(d.centerX, d.centerY, d.centerZ);
                level.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5f, 0.4f);
                surfaceDust(level, d.centerX + 0.5, d.centerZ + 0.5, 8, 3.0);
            }
            return;
        }
        if (phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;

        ListTag cracks = d.eventData.getList(KEY, Tag.TAG_COMPOUND);
        if (cracks.isEmpty()) return;
        int interval = Math.max(1, 20 / CatastropheConfig.CRACKS_SPEED.get());
        if (phase == CatastrophePhase.ENDING) interval *= 2;

        if (now % 20 == 0 && CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) ModNetwork.sendShake(p, 0.35f, 25);
        }
        if (now % interval != 0) {
            steam(level, cracks, now);
            return;
        }

        boolean destroy = !CatastropheConfig.destructionOff();
        RandomSource r = level.getRandom();
        int size = cracks.size();
        for (int i = 0; i < size; i++) {
            CompoundTag c = cracks.getCompound(i);
            int t = c.getInt("t");
            if (t > c.getInt("len")) continue;
            step(level, d, c, destroy, r);
            // Ramificacion: una grieta nueva sale hacia un lado, mas corta y estrecha.
            if (CatastropheConfig.CRACKS_BRANCHES.get() && c.getInt("gen") < 2 && cracks.size() < MAX_CRACKS
                    && t > 3 && r.nextInt(14) == 0) {
                double side = r.nextBoolean() ? 1 : -1;
                double ang = c.getDouble("ang") + side * (0.5 + r.nextDouble() * 0.7);
                int left = c.getInt("len") - t;
                cracks.add(newCrack(c.getDouble("x"), c.getDouble("z"), ang, (int) (left * (0.5 + r.nextDouble() * 0.4)),
                        c.getDouble("width") * 0.6, (int) (c.getInt("depth") * 0.7), c.getInt("gen") + 1));
            }
        }
        d.eventData.put(KEY, cracks);
        d.setDirty();
    }

    /** Avanza una grieta un bloque: crujido y polvo en la punta y, si se permite, excava la rebanada. */
    private static void step(ServerLevel level, CatastropheSavedData d, CompoundTag c, boolean destroy, RandomSource r) {
        int t = c.getInt("t"), len = c.getInt("len");
        double x = c.getDouble("x"), z = c.getDouble("z"), ang = c.getDouble("ang");
        double frac = (double) t / len;
        double taper = 1.0 - 0.75 * frac;                         // ancha al principio, fina en la punta
        double halfW = Math.max(0.5, c.getDouble("width") / 2.0 * taper);
        int depth = Math.max(2, (int) Math.round(c.getInt("depth") * taper));

        BlockPos column = BlockPos.containing(x, d.centerY, z);
        if (level.isLoaded(column)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            BlockPos tip = new BlockPos(column.getX(), top, column.getZ());
            if (t % 3 == 0) level.playSound(null, tip, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
            surfaceDust(level, x, z, 6, halfW + 0.5);
            pushAway(level, x, top, z, ang);
            if (destroy) carveSlice(level, d, x, z, ang, halfW, depth, c.getInt("depth"), r);
        }
        // Avanzar, serpenteando un poco.
        ang += (r.nextDouble() - 0.5) * 0.35;
        c.putDouble("ang", ang);
        c.putDouble("x", x + Math.cos(ang));
        c.putDouble("z", z + Math.sin(ang));
        c.putInt("t", t + 1);
    }

    /** Excava una rebanada transversal en forma de V. */
    private static void carveSlice(ServerLevel level, CatastropheSavedData d, double x, double z, double ang,
                                   double halfW, int depth, int maxDepth, RandomSource r) {
        int limit = CatastropheConfig.CRACKS_MAX_BLOCKS.get();
        double px = -Math.sin(ang), pz = Math.cos(ang);
        int reach = (int) Math.ceil(halfW);
        boolean lava = CatastropheConfig.CRACKS_LAVA.get()
                && CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE
                && depth >= Math.max(4, maxDepth * 0.7);
        for (int off = -reach; off <= reach; off++) {
            int bx = Mth.floor(x + px * off), bz = Mth.floor(z + pz * off);
            if (!level.isLoaded(new BlockPos(bx, d.centerY, bz))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
            if (!level.getFluidState(new BlockPos(bx, top, bz)).isEmpty()) continue; // no vaciar rios ni lagos
            int bottom = top;
            for (int dy = 0; dy <= depth; dy++) {
                double allowed = halfW * (1.0 - 0.7 * dy / depth);
                if (Math.abs(off) > allowed + 0.5) break;
                BlockPos p = new BlockPos(bx, top - dy, bz);
                if (!removable(level, p)) continue;
                if (!DisasterUtil.takeBlockBudget(d, limit)) return;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                bottom = top - dy;
            }
            // Lava en el fondo del centro de las partes mas profundas.
            if (lava && off == 0 && bottom < top - 3 && r.nextInt(3) == 0) {
                BlockPos p = new BlockPos(bx, bottom, bz);
                if (level.isEmptyBlock(p) && BlockProtection.canPlace(level, p) && DisasterUtil.takeBlockBudget(d, limit)) {
                    level.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean removable(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() + 1) return false;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(pos.relative(dir)).isEmpty()) return false;
        }
        return BlockProtection.canModify(level, pos, s);
    }

    /** Polvo del propio suelo saliendo de la grieta. */
    private static void surfaceDust(ServerLevel level, double x, double z, int count, double spread) {
        BlockPos col = BlockPos.containing(x, 64, z);
        if (!level.isLoaded(col)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
        BlockState ground = level.getBlockState(new BlockPos(col.getX(), top - 1, col.getZ()));
        if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, top + 0.2, z, count, spread * 0.5, 0.2, spread * 0.5, 0.15);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, top, z, 1, 0.3, 0.1, 0.3, 0.02);
    }

    /** Vapor que sale de las grietas ya abiertas. */
    private static void steam(ServerLevel level, ListTag cracks, long now) {
        if (now % 7 != 0 || cracks.isEmpty()) return;
        CompoundTag c = cracks.getCompound((int) (now % cracks.size()));
        double x = c.getDouble("x") - Math.cos(c.getDouble("ang")) * 2, z = c.getDouble("z") - Math.sin(c.getDouble("ang")) * 2;
        BlockPos col = BlockPos.containing(x, 64, z);
        if (!level.isLoaded(col)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
        level.sendParticles(ParticleTypes.CLOUD, x, top + 0.5, z, 4, 0.3, 0.6, 0.3, 0.02);
    }

    /** Quien esta justo encima de la punta pierde el equilibrio y es empujado hacia un lado. */
    private static void pushAway(ServerLevel level, double x, int y, double z, double ang) {
        AABB box = new AABB(x - 2, y - 1, z - 2, x + 2, y + 3, z + 2);
        double px = -Math.sin(ang), pz = Math.cos(ang);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double side = (e.getX() - x) * px + (e.getZ() - z) * pz >= 0 ? 1 : -1;
            e.setDeltaMovement(e.getDeltaMovement().add(px * side * 0.25, 0.15, pz * side * 0.25));
            e.hurtMarked = true;
        }
    }
}
