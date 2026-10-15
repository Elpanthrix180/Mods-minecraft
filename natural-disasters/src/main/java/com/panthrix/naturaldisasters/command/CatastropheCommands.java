package com.panthrix.naturaldisasters.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.panthrix.naturaldisasters.config.CatastropheConfig;
import com.panthrix.naturaldisasters.core.CatastropheManager;
import com.panthrix.naturaldisasters.core.CatastrophePhase;
import com.panthrix.naturaldisasters.core.CatastropheSavedData;
import com.panthrix.naturaldisasters.core.CatastropheScheduler;
import com.panthrix.naturaldisasters.core.CatastropheType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;

/**
 * Comandos /pnd. help, status y next los puede usar cualquiera;
 * el resto exige permiso de administrador (nivel 2, el de los operadores).
 */
public final class CatastropheCommands {

    private CatastropheCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pnd")
                .executes(CatastropheCommands::help)
                .then(Commands.literal("help").executes(CatastropheCommands::help))
                .then(Commands.literal("status").executes(CatastropheCommands::status))
                .then(Commands.literal("next").executes(CatastropheCommands::next))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("fenomeno", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(CatastropheType.values()).map(t -> t.id), builder))
                                .executes(CatastropheCommands::start)))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(CatastropheCommands::stop))
                .then(Commands.literal("enable").requires(s -> s.hasPermission(2)).executes(ctx -> setEnabled(ctx, true)))
                .then(Commands.literal("disable").requires(s -> s.hasPermission(2)).executes(ctx -> setEnabled(ctx, false)))
                .then(Commands.literal("config").requires(s -> s.hasPermission(2)).executes(CatastropheCommands::config))
                .then(Commands.literal("volcano").requires(s -> s.hasPermission(2))
                        .executes(CatastropheCommands::volcanoInfo)
                        .then(Commands.literal("create").executes(CatastropheCommands::volcanoCreate))));
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text, ChatFormatting color) {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    /** Convierte ticks en un texto como "3 días y 5 horas de Minecraft". */
    private static String formatTicks(long ticks) {
        if (ticks < 0) ticks = 0;
        long days = ticks / CatastropheScheduler.TICKS_PER_DAY;
        long hours = (ticks % CatastropheScheduler.TICKS_PER_DAY) / 1000;
        long realMinutes = ticks / 1200;
        return days + " días y " + hours + " horas de Minecraft (unos " + realMinutes + " minutos reales)";
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, "=== Panthrix's Natural Disasters ===", ChatFormatting.GOLD);
        reply(ctx, "/pnd help - muestra esta ayuda", ChatFormatting.YELLOW);
        reply(ctx, "/pnd status - estado del sistema y catástrofe activa", ChatFormatting.YELLOW);
        reply(ctx, "/pnd next - cuándo será la próxima catástrofe", ChatFormatting.YELLOW);
        reply(ctx, "/pnd start <fenómeno> - inicia un fenómeno (admin)", ChatFormatting.YELLOW);
        reply(ctx, "/pnd stop - detiene la catástrofe actual (admin)", ChatFormatting.YELLOW);
        reply(ctx, "/pnd enable | disable - activa o desactiva las catástrofes aleatorias (admin)", ChatFormatting.YELLOW);
        reply(ctx, "/pnd config - muestra la configuración (admin)", ChatFormatting.YELLOW);
        reply(ctx, "/pnd volcano - volcán conocido más cercano (admin)", ChatFormatting.YELLOW);
        reply(ctx, "/pnd volcano create - crea un volcán pequeño donde estás (admin, modifica el terreno)", ChatFormatting.YELLOW);
        StringBuilder ids = new StringBuilder("Fenómenos: ");
        for (CatastropheType t : CatastropheType.values()) ids.append(t.id).append(' ');
        reply(ctx, ids.toString().trim(), ChatFormatting.GRAY);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        CatastropheSavedData d = CatastropheManager.data(server);
        reply(ctx, "Catástrofes aleatorias: " + (d.randomEnabled ? "ACTIVADAS" : "DESACTIVADAS"),
                d.randomEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (d.phase == CatastrophePhase.CALM) {
            reply(ctx, "Fase: Calma. No hay ninguna catástrofe activa.", ChatFormatting.GREEN);
        } else {
            long left = d.phaseEndTick - CatastropheManager.now(server);
            reply(ctx, "Catástrofe activa: " + d.activeType.displayName + " en X " + d.centerX + ", Z " + d.centerZ,
                    ChatFormatting.RED);
            reply(ctx, "Fase: " + d.phase.displayName + " (quedan " + Math.max(0, left / 20) + " segundos)", ChatFormatting.YELLOW);
        }
        return 1;
    }

    private static int next(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        CatastropheSavedData d = CatastropheManager.data(server);
        if (!d.randomEnabled) {
            reply(ctx, "Las catástrofes aleatorias están desactivadas.", ChatFormatting.RED);
        } else if (d.nextEventTick < 0) {
            reply(ctx, "La próxima catástrofe aún no está programada.", ChatFormatting.YELLOW);
        } else {
            reply(ctx, "Próxima catástrofe dentro de " + formatTicks(d.nextEventTick - CatastropheManager.now(server)) + ".",
                    ChatFormatting.YELLOW);
        }
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "fenomeno");
        CatastropheType type = CatastropheType.byId(id);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Fenómeno desconocido: " + id + ". Usa /pnd help para ver la lista."));
            return 0;
        }
        String error = CatastropheManager.start(ctx.getSource().getServer(), type);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Iniciado: " + type.displayName).withStyle(ChatFormatting.GREEN), true);
        if (!com.panthrix.naturaldisasters.core.CatastropheEvents.isImplemented(type)) {
            reply(ctx, "Aviso: este fenómeno aún no tiene efectos (llegará en una fase posterior). Solo se mostrarán los avisos.", ChatFormatting.GRAY);
        }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (!CatastropheManager.stop(ctx.getSource().getServer())) {
            ctx.getSource().sendFailure(Component.literal("No hay ninguna catástrofe activa."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Catástrofe detenida.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        CatastropheManager.setRandomEnabled(ctx.getSource().getServer(), enabled);
        ctx.getSource().sendSuccess(() -> Component.literal(enabled
                ? "Catástrofes aleatorias ACTIVADAS."
                : "Catástrofes aleatorias DESACTIVADAS.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int volcanoInfo(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.server.level.ServerLevel overworld = ctx.getSource().getServer().overworld();
        com.panthrix.naturaldisasters.core.VolcanoRegistry reg = com.panthrix.naturaldisasters.core.VolcanoRegistry.get(overworld);
        net.minecraft.world.phys.Vec3 pos = ctx.getSource().getPosition();
        net.minecraft.core.BlockPos near = reg.nearest((int) pos.x, (int) pos.z, 100000);
        reply(ctx, "Volcanes conocidos: " + reg.size(), ChatFormatting.GOLD);
        if (near != null) {
            reply(ctx, "El más cercano: X " + near.getX() + ", Y " + near.getY() + ", Z " + near.getZ(), ChatFormatting.YELLOW);
        }
        return 1;
    }

    private static int volcanoCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getLevel() != src.getServer().overworld()) {
            src.sendFailure(Component.literal("Solo se pueden crear volcanes en el mundo normal (Overworld)."));
            return 0;
        }
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(src.getPosition());
        net.minecraft.core.BlockPos crater = com.panthrix.naturaldisasters.worldgen.VolcanoBuilder.build(src.getLevel(), at, src.getLevel().getRandom(), true);
        if (crater == null) {
            src.sendFailure(Component.literal("Aquí no se puede crear un volcán (¿hay agua debajo?)."));
            return 0;
        }
        com.panthrix.naturaldisasters.core.VolcanoRegistry.get(src.getLevel()).add(crater);
        src.sendSuccess(() -> Component.literal("Volcán creado. Cráter en X " + crater.getX() + ", Y " + crater.getY() + ", Z " + crater.getZ())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int config(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, "=== Configuración ===", ChatFormatting.GOLD);
        reply(ctx, "Intervalo: " + CatastropheConfig.MIN_DAYS.get() + " a " + CatastropheConfig.MAX_DAYS.get() + " días", ChatFormatting.YELLOW);
        reply(ctx, "Intensidad: " + CatastropheConfig.INTENSITY.get() + " | Destrucción: " + CatastropheConfig.DESTRUCTION.get(), ChatFormatting.YELLOW);
        reply(ctx, "Radio: " + CatastropheConfig.RADIUS.get() + " | Máx. bloques por evento: " + CatastropheConfig.MAX_BLOCKS_PER_EVENT.get(), ChatFormatting.YELLOW);
        reply(ctx, "Avisos: " + CatastropheConfig.WARNINGS.get() + " | Daño a jugadores: " + CatastropheConfig.DAMAGE_PLAYERS.get()
                + " | Daño a criaturas: " + CatastropheConfig.DAMAGE_MOBS.get(), ChatFormatting.YELLOW);
        for (CatastropheType t : CatastropheType.values()) {
            reply(ctx, " - " + t.id + ": " + (CatastropheConfig.ENABLED.get(t).get() ? "sí" : "no")
                    + ", peso " + CatastropheConfig.WEIGHT.get(t).get()
                    + ", " + CatastropheConfig.DURATION_SECONDS.get(t).get() + " s", ChatFormatting.GRAY);
        }
        reply(ctx, "Archivo: config/panthrixsnaturaldisasters-common.toml", ChatFormatting.GRAY);
        return 1;
    }
}
