package com.robotsconstructores.building;

import com.robotsconstructores.entity.RobotStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Carga planos desde archivos:
 * - Estructuras: .minecraft/robotsconstructores/planos/NOMBRE.nbt, o una estructura guardada en el mundo
 *   con el bloque de estructuras (por ejemplo "casa1" o "minecraft:casa1").
 * - Imágenes: .minecraft/robotsconstructores/imagenes/NOMBRE.png -> mural de hormigón (o lana) de colores.
 * Todo se lee en el servidor; el cliente solo envía el nombre (validado).
 */
public final class BlueprintLoader {
    public static final int MAX_SIZE = 64;       // lado máximo en cualquier dirección
    public static final int MAX_BLOCKS = 8192;
    private static final int DISTANCE = 2;       // bloques entre el robot y el plano
    private static final Pattern SAFE_NAME = Pattern.compile("[a-zA-Z0-9_\\-.]{1,64}(:[a-zA-Z0-9_\\-./]{1,64})?");

    /** Resultado: o bloques, o un error con sus datos. */
    public record Result(List<PlannedBlock> blocks, @Nullable RobotStatus error, Object[] args) {
        static Result ok(List<PlannedBlock> blocks) {
            return new Result(blocks, null, new Object[0]);
        }

        static Result fail(RobotStatus error, Object... args) {
            return new Result(List.of(), error, args);
        }
    }

    public static Path baseDir() {
        return FMLPaths.GAMEDIR.get().resolve("robotsconstructores");
    }

    public static void createFolders() {
        try {
            Files.createDirectories(baseDir().resolve("planos"));
            Files.createDirectories(baseDir().resolve("imagenes"));
        } catch (IOException ignored) {
            // si no se puede crear, se avisará al intentar cargar
        }
    }

    public static boolean isSafeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches() && !name.contains("..");
    }

    public static Result load(ServerLevel level, BlueprintPlan plan, String name, BlockPos origin, Direction facing) {
        if (name == null || name.isEmpty()) {
            return Result.fail(RobotStatus.NO_BLUEPRINT_NAME);
        }
        if (!isSafeName(name)) {
            return Result.fail(RobotStatus.BLUEPRINT_NOT_FOUND, name);
        }
        try {
            return plan.isImage() ? loadImage(name, origin, facing) : loadStructure(level, name, origin, facing);
        } catch (Exception e) {
            return Result.fail(RobotStatus.BLUEPRINT_ERROR, name);
        }
    }

    // ---------- estructuras ----------

    private static Result loadStructure(ServerLevel level, String name, BlockPos origin, Direction facing) throws IOException {
        CompoundTag tag = null;
        String fileName = name.endsWith(".nbt") ? name : name + ".nbt";
        if (!name.contains(":")) {
            Path file = baseDir().resolve("planos").resolve(fileName);
            if (Files.isRegularFile(file)) {
                tag = NbtIo.readCompressed(file.toFile());
            }
        }
        if (tag == null) {
            ResourceLocation id = ResourceLocation.tryParse(name.endsWith(".nbt") ? name.substring(0, name.length() - 4) : name);
            if (id != null) {
                Optional<StructureTemplate> template = level.getStructureManager().get(id);
                if (template.isPresent()) {
                    tag = template.get().save(new CompoundTag());
                }
            }
        }
        if (tag == null) {
            return Result.fail(RobotStatus.BLUEPRINT_NOT_FOUND, name);
        }
        ListTag size = tag.getList("size", Tag.TAG_INT);
        if (size.size() == 3 && (size.getInt(0) > MAX_SIZE || size.getInt(1) > MAX_SIZE || size.getInt(2) > MAX_SIZE)) {
            return Result.fail(RobotStatus.BLUEPRINT_TOO_BIG, MAX_SIZE);
        }
        int sizeX = size.size() == 3 ? size.getInt(0) : 0;

        ListTag paletteTag = tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND)
                : tag.getList("palettes", Tag.TAG_LIST).isEmpty() ? new ListTag() : tag.getList("palettes", Tag.TAG_LIST).getList(0);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i)));
        }

        Rotation rotation = Rotation.NONE;
        for (Rotation r : Rotation.values()) {
            if (r.rotate(Direction.SOUTH) == facing) {
                rotation = r;
            }
        }
        Direction left = facing.getCounterClockWise();
        List<PlannedBlock> list = new ArrayList<>();
        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag b = blocksTag.getCompound(i);
            int index = b.getInt("state");
            if (index < 0 || index >= palette.size()) {
                continue;
            }
            BlockState state = palette.get(index);
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) {
                continue;
            }
            ListTag p = b.getList("pos", Tag.TAG_INT);
            int x = p.getInt(0), y = p.getInt(1), z = p.getInt(2);
            // x de la estructura -> hacia la izquierda, z -> hacia delante (como si el plano mirara al sur)
            BlockPos pos = origin.relative(facing, DISTANCE + z).relative(left, x - sizeX / 2).above(y).immutable();
            list.add(new PlannedBlock(pos, BlockRole.BLUEPRINT, state.rotate(rotation)));
            if (list.size() > MAX_BLOCKS) {
                return Result.fail(RobotStatus.BLUEPRINT_TOO_BIG, MAX_SIZE);
            }
        }
        if (list.isEmpty()) {
            return Result.fail(RobotStatus.BLUEPRINT_EMPTY, name);
        }
        list.sort(Comparator.comparingInt(pb -> pb.pos().getY()));
        return Result.ok(list);
    }

    // ---------- imágenes ----------

    private static final int[][] CONCRETE_RGB = {
            {207, 213, 214}, {224, 97, 1}, {169, 48, 159}, {36, 137, 199}, {241, 175, 21}, {94, 169, 24},
            {214, 101, 143}, {55, 58, 62}, {125, 125, 115}, {21, 119, 136}, {100, 32, 156}, {45, 47, 143},
            {96, 60, 32}, {73, 91, 36}, {142, 33, 33}, {8, 10, 15}};

    private static Result loadImage(String name, BlockPos origin, Direction facing) throws IOException {
        String fileName = name.toLowerCase().endsWith(".png") ? name : name + ".png";
        Path file = baseDir().resolve("imagenes").resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Result.fail(RobotStatus.BLUEPRINT_NOT_FOUND, fileName);
        }
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) {
            return Result.fail(RobotStatus.BLUEPRINT_ERROR, fileName);
        }
        // se reduce (sin suavizar) para que quepa en MAX_SIZE x MAX_SIZE
        int srcW = img.getWidth(), srcH = img.getHeight();
        double scale = Math.min(1.0D, (double) MAX_SIZE / Math.max(srcW, srcH));
        int w = Math.max(1, (int) Math.round(srcW * scale));
        int h = Math.max(1, (int) Math.round(srcH * scale));
        DyeColor[] colors = DyeColor.values();
        List<PlannedBlock> list = new ArrayList<>();
        for (int py = h - 1; py >= 0; py--) {          // de abajo arriba
            for (int px = 0; px < w; px++) {
                int argb = img.getRGB(Math.min(srcW - 1, (int) (px / scale)), Math.min(srcH - 1, (int) (py / scale)));
                if (((argb >>> 24) & 0xFF) < 128) {
                    continue; // transparente: se deja vacío
                }
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                int best = 0;
                long bestDist = Long.MAX_VALUE;
                for (int c = 0; c < CONCRETE_RGB.length; c++) {
                    long dr = r - CONCRETE_RGB[c][0], dg = g - CONCRETE_RGB[c][1], db = b - CONCRETE_RGB[c][2];
                    long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (d < bestDist) {
                        bestDist = d;
                        best = c;
                    }
                }
                Block concrete = coloredBlock(colors[best], "concrete");
                BlockPos pos = BuildPlan.rel(origin, facing, DISTANCE, px - w / 2, h - 1 - py);
                list.add(new PlannedBlock(pos, BlockRole.BLUEPRINT, concrete.defaultBlockState()));
            }
        }
        if (list.isEmpty()) {
            return Result.fail(RobotStatus.BLUEPRINT_EMPTY, fileName);
        }
        return Result.ok(list);
    }

    private static Block coloredBlock(DyeColor color, String kind) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", color.getName() + "_" + kind));
        return b != null ? b : Blocks.WHITE_CONCRETE;
    }

    // ---------- materiales ----------

    /** Bloques que no gastan material: la mitad de arriba de puertas/plantas altas y la cabecera de las camas. */
    public static boolean isSecondHalf(BlockState state) {
        return (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                || (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }

    /** Objeto que hace falta para poner este bloque (null = no gasta nada o no se puede conseguir). */
    @Nullable
    public static Item requiredItem(BlockState state) {
        if (isSecondHalf(state)) {
            return null;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    /** Sustituto aceptado: la lana del mismo color en vez del hormigón. */
    @Nullable
    public static Item alternativeItem(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key != null && key.getPath().endsWith("_concrete")) {
            return ForgeRegistries.ITEMS.getValue(new ResourceLocation(key.getNamespace(),
                    key.getPath().replace("_concrete", "_wool")));
        }
        return null;
    }

    private BlueprintLoader() {}
}
