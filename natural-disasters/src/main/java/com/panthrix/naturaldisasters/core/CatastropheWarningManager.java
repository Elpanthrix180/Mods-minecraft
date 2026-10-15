package com.panthrix.naturaldisasters.core;

import com.panthrix.naturaldisasters.config.CatastropheConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Envia los avisos de emergencia (chat y titulo en pantalla) a los jugadores. */
public final class CatastropheWarningManager {

    private CatastropheWarningManager() {}

    public static void announceWarning(MinecraftServer server, CatastropheType type, int x, int z) {
        if (!CatastropheConfig.WARNINGS.get()) return;
        Component title = Component.literal(type.displayName.toUpperCase()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.literal(type.warningMessage).withStyle(ChatFormatting.GOLD);
        Component chat = Component.literal("[Catastrofes] ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(type.warningMessage + " Zona: X " + x + ", Z " + z).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.sendSystemMessage(chat);
        }
    }

    public static void broadcast(MinecraftServer server, String message, ChatFormatting color) {
        if (!CatastropheConfig.WARNINGS.get()) return;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Catastrofes] ").withStyle(ChatFormatting.DARK_RED)
                        .append(Component.literal(message).withStyle(color)), false);
    }
}
