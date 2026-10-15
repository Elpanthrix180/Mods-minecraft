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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Terremoto.
 * Efectos (siempre): vibracion de camara, sonidos subterraneos, polvo y sacudidas a las entidades.
 * Terreno (solo si destruccion no es OFF): grietas profundas, zanjas anchas, derrumbes en los bordes,
 * cuevas nuevas (INTENSE) y dano a edificios (opcional). Todo se excava POCO A POCO
 * (bloquesPorTick) siguiendo un "plan" guardado en el mundo, asi que un reinicio no lo repite ni lo corta.
 * Nunca excava bloques protegidos ni bloques junto a agua o lava (para no vaciar lagos ni liberar lava).
 */
public class EarthquakeEvent extends CatastropheEvent {

    private static final int TYPE_CRACK = 0;
    private static final int TYPE_CAVE = 1;

    @Override
    public void onPhaseStart(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase) {
        if (phase == CatastrophePhase.ACTIVE && !CatastropheConfig.destructionOff()) {
            d.eventData.put("plans", makePlans(level, d));
            d.eventData.putInt("plan", 0);
            d.setDirty();
        }
    }

    @Override
    public void tick(ServerLevel level, CatastropheSavedData d, CatastrophePhase phase, long now) {
        if (phase != CatastrophePhase.PREPARATION && phase != CatastrophePhase.ACTIVE && phase != CatastrophePhase.ENDING) return;
        double f = CatastropheConfig.intensityFactor();
        float shake = switch (phase) {
            case PREPARATION -> 0.35f;
            case ACTIVE -> (float) (1.2 * f);
            default -> 0.5f;
        };

        if (now % 20 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                if (CatastropheConfig.QUAKE_CAMERA_SHAKE.get()) ModNetwork.sendShake(p, shake, 25);
                level.playSound(null, p.blockPosition().below(3), SoundEvents.GRAVEL_BREAK, SoundSource.AMBIENT, 1.5f, 0.3f);
                if (phase == CatastrophePhase.ACTIVE && level.getRandom().nextInt(3) == 0) {
                    level.playSound(null, p.blockPosition().below(8), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 0.5f, 0.4f);
                }
            }
        }
        if (now % 4 == 0) {
            for (ServerPlayer p : DisasterUtil.playersInArea(level, d)) {
                BlockState ground = level.getBlockState(p.blockPosition().below());
                if (!ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.1, p.getZ(),
                            (int) (6 * shake) + 2, 4, 0.1, 4, 0.1);
                }
            }
        }
        if (phase != CatastrophePhase.PREPARATION && now % 5 == 0) shakeEntities(level, d, shake);

        if (phase == CatastrophePhase.PREPARATION || CatastropheConfig.destructionOff()) return;
        runPlans(level, d);
        if (phase == CatastrophePhase.ACTIVE && now % 20 == 0 && CatastropheConfig.QUAKE_BUILDING_DAMAGE.get()) damageBuilding(level, d);
    }

    /** Pequenos empujones aleatorios a quien este en el suelo. */
    private static void shakeEntities(ServerLevel level, CatastropheSavedData d, float shake) {
        RandomSource r = level.getRandom();
        for (LivingEntity e : DisasterUtil.livingInArea(level, d)) {
            if (!e.onGround()) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double k = 0.04 * shake;
            e.setDeltaMovement(e.getDeltaMovement().add((r.nextDouble() - 0.5) * k, 0.02 * shake, (r.nextDouble() - 0.5) * k));
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ plan

    private static ListTag makePlans(ServerLevel level, CatastropheSavedData d) {
        RandomSource r = level.getRandom();
        double f = CatastropheConfig.intensityFactor();
        boolean intense = CatastropheConfig.DESTRUCTION.get() == CatastropheConfig.Destruction.INTENSE;
        int radius = DisasterUtil.radius();
        ListTag plans = new ListTag();

        int cracks = Math.max(1, (int) Math.round(CatastropheConfig.QUAKE_CRACK_COUNT.get() * Math.min(f, 1.5)));
        for (int i = 0; i < cracks; i++) {
            plans.add(crackPlan(r, d, radius, CatastropheConfig.QUAKE_CRACK_WIDTH.get(), CatastropheConfig.QUAKE_CRACK_DEPTH.get(), f));
        }
        if (intense) {
            // Zanja: mas ancha y menos profunda.
            int trenches = 1 + (f > 1 ? 1 : 0);
            for (int i = 0; i < trenches; i++) {
                plans.add(crackPlan(r, d, radius, Math.min(7, CatastropheConfig.QUAKE_CRACK_WIDTH.get() + 3),
                        Math.max(3, CatastropheConfig.QUAKE_CRACK_DEPTH.get() / 3), f));
            }
            if (CatastropheConfig.QUAKE_CAVES.get()) {
                int caves = 1 + (f > 1 ? 1 : 0);
                for (int i = 0; i < caves; i++) {
                    CompoundTag cave = cavePlan(level, r, d, radius);
                    if (cave != null) plans.add(cave);
                }
            }
        }
        return plans;
    }

    private static CompoundTag crackPlan(RandomSource r, CatastropheSavedData d, int radius, int width, int depth, double f) {
        CompoundTag p = new CompoundTag();
        p.putInt("type", TYPE_CRACK);
        double a = r.nextDouble() * Math.PI * 2;
        double off = r.nextDouble() * radius * 0.6;
        p.putDouble("x", d.centerX + Math.cos(a) * off);
        p.putDouble("z", d.centerZ + Math.sin(a) * off);
        p.putDouble("ang", r.nextDouble() * Math.PI * 2);
        p.putDouble("seed", r.nextDouble() * 100);
        int len = Math.max(5, (int) (CatastropheConfig.QUAKE_CRACK_LENGTH.get() * (0.6 + 0.4 * Math.min(f, 1.5) * r.nextDouble() * 1.5)));
        p.putInt("len", Math.min(len, CatastropheConfig.QUAKE_CRACK_LENGTH.get()));
        p.putInt("width", width);
        p.putInt("depth", Math.max(2, (int) (depth * Math.min(1.0, 0.6 + 0.4 * f))));
        p.putInt("t", 0);
        return p;
    }

    private static CompoundTag cavePlan(ServerLevel level, RandomSource r, CatastropheSavedData d, int radius) {
        int x = d.centerX + r.nextInt(radius) - radius / 2;
        int z = d.centerZ + r.nextInt(radius) - radius / 2;
        if (!level.isLoaded(new BlockPos(x, d.centerY, z))) return null;
        int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        int y = surface - 10 - r.nextInt(12);
        if (y < level.getMinBuildHeight() + 6) return null;
        CompoundTag p = new CompoundTag();
        p.putInt("type", TYPE_CAVE);
        p.putInt("cx", x);
        p.putInt("cy", y);
        p.putInt("cz", z);
        p.putInt("rx", 4 + r.nextInt(4));
        p.putInt("ry", 3 + r.nextInt(2));
        p.putInt("rz", 4 + r.nextInt(4));
        p.putInt("t", 0);
        return p;
    }

    /** Avanza el plan actual gastando como maximo bloquesPorTick bloques. */
    private static void runPlans(ServerLevel level, CatastropheSavedData d) {
        ListTag plans = d.eventData.getList("plans", Tag.TAG_COMPOUND);
        int index = d.eventData.getInt("plan");
        int[] quota = {CatastropheConfig.QUAKE_BLOCKS_PER_TICK.get()};
        while (index < plans.size() && quota[0] > 0) {
            CompoundTag p = plans.getCompound(index);
            boolean finished = p.getInt("type") == TYPE_CAVE ? caveStep(level, d, p, quota) : crackStep(level, d, p, quota);
            if (finished) index++;
            if (d.eventData.getBoolean("budgetFull")) index = plans.size();
        }
        d.eventData.putInt("plan", index);
        d.setDirty();
    }

    // ------------------------------------------------------------------ grietas

    /** Excava una "rebanada" transversal de la grieta. Devuelve true cuando la grieta esta terminada. */
    private static boolean crackStep(ServerLevel level, CatastropheSavedData d, CompoundTag p, int[] quota) {
        int t = p.getInt("t");
        int len = p.getInt("len");
        if (t > len) return true;
        double ang = p.getDouble("ang") + Math.sin(t * 0.25 + p.getDouble("seed")) * 0.35; // la grieta serpentea
        double cx = p.getDouble("x"), cz = p.getDouble("z");
        double frac = (double) t / len;
        double taper = Math.max(0.3, Math.sin(Math.PI * frac));           // mas ancha y profunda en el centro
        double halfW = Math.max(0.5, p.getInt("width") / 2.0 * taper);
        int depth = Math.max(2, (int) Math.round(p.getInt("depth") * taper));
        double px = -Math.sin(ang), pz = Math.cos(ang);
        int reach = (int) Math.ceil(halfW);

        for (int off = -reach; off <= reach; off++) {
            int bx = Mth.floor(cx + px * off), bz = Mth.floor(cz + pz * off);
            BlockPos column = new BlockPos(bx, d.centerY, bz);
            if (!level.isLoaded(column)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
            if (!level.getFluidState(new BlockPos(bx, top, bz)).isEmpty()) continue; // no vaciar rios ni lagos
            for (int dy = 0; dy <= depth; dy++) {
                // Forma de V: cada vez mas estrecha hacia el fondo.
                double allowed = halfW * (1.0 - 0.7 * dy / depth);
                if (Math.abs(off) > allowed + 0.5) continue;
                if (!carve(level, d, new BlockPos(bx, top - dy, bz), quota)) break;
            }
        }
        if (CatastropheConfig.QUAKE_COLLAPSES.get() && level.getRandom().nextInt(3) == 0) {
            int side = level.getRandom().nextBoolean() ? 1 : -1;
            int ex = Mth.floor(cx + px * (reach + 1) * side), ez = Mth.floor(cz + pz * (reach + 1) * side);
            collapse(level, d, ex, ez, -px * side, -pz * side);
        }
        p.putDouble("x", cx + Math.cos(ang));
        p.putDouble("z", cz + Math.sin(ang));
        p.putInt("t", t + 1);
        quota[0] -= 4; // cada rebanada cuesta un minimo, para que el avance sea gradual
        return t + 1 > len;
    }

    /** Derrumbe: el bloque del borde cae hacia la grieta como bloque que cae. */
    private static void collapse(ServerLevel level, CatastropheSavedData d, int x, int z, double dirX, double dirZ) {
        BlockPos column = new BlockPos(x, d.centerY, z);
        if (!level.isLoaded(column)) return;
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        BlockState s = level.getBlockState(pos);
        if (!isLoose(s) || !BlockProtection.canModify(level, pos, s) || !DisasterUtil.takeBlockBudget(d)) return;
        BlockState falling = s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) ? Blocks.COBBLESTONE.defaultBlockState() : s;
        level.setBlock(pos, falling, 3);
        FallingBlockEntity e = FallingBlockEntity.fall(level, pos, falling);
        e.setDeltaMovement(dirX * 0.25, 0.1, dirZ * 0.25);
        e.hurtMarked = true;
    }

    private static boolean isLoose(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRASS_BLOCK);
    }

    // ------------------------------------------------------------------ cuevas

    private static boolean caveStep(ServerLevel level, CatastropheSavedData d, CompoundTag p, int[] quota) {
        int rx = p.getInt("rx"), ry = p.getInt("ry"), rz = p.getInt("rz");
        int rows = (2 * ry + 1) * (2 * rx + 1);
        int t = p.getInt("t");
        while (t < rows && quota[0] > 0) {
            int dy = t / (2 * rx + 1) - ry;
            int dx = t % (2 * rx + 1) - rx;
            for (int dz = -rz; dz <= rz; dz++) {
                double e = sq(dx / (double) rx) + sq(dy / (double) ry) + sq(dz / (double) rz);
                if (e > 1.0) continue;
                BlockPos pos = new BlockPos(p.getInt("cx") + dx, p.getInt("cy") + dy, p.getInt("cz") + dz);
                if (!carve(level, d, pos, quota)) break;
            }
            t++;
            quota[0] -= 2;
        }
        p.putInt("t", t);
        return t >= rows;
    }

    private static double sq(double v) {
        return v * v;
    }

    // ------------------------------------------------------------------ comun

    /**
     * Quita un bloque si esta permitido. Devuelve false si se ha agotado el limite de bloques del evento.
     * Salta (sin cortar) los bloques protegidos, el aire, los liquidos y los que tocan agua o lava.
     */
    private static boolean carve(ServerLevel level, CatastropheSavedData d, BlockPos pos, int[] quota) {
        if (pos.getY() <= level.getMinBuildHeight() + 1 || !level.isLoaded(pos)) return true;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return true;
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(pos.relative(dir)).isEmpty()) return true;
        }
        if (!BlockProtection.canModify(level, pos, s)) return true;
        if (!DisasterUtil.takeBlockBudget(d)) {
            d.eventData.putBoolean("budgetFull", true);
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        quota[0]--;
        return true;
    }

    /** Hace caer un bloque de construccion de la superficie (madera, ladrillo, piedra labrada...). */
    private static void damageBuilding(ServerLevel level, CatastropheSavedData d) {
        BlockPos top = DisasterUtil.randomSurface(level, d, level.getRandom());
        if (top == null) return;
        BlockPos pos = top.below();
        BlockState s = level.getBlockState(pos);
        boolean building = s.is(BlockTags.PLANKS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.WALLS)
                || s.is(BlockTags.STONE_BRICKS) || s.is(Blocks.BRICKS) || s.is(Blocks.COBBLESTONE) || s.is(BlockTags.LOGS);
        if (!building || !BlockProtection.canModify(level, pos, s) || !DisasterUtil.takeBlockBudget(d)) return;
        FallingBlockEntity.fall(level, pos, s);
    }
}
