package com.panthrix.naturaldisasters.network;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod. Solo envia del servidor al cliente la informacion minima (vibracion de camara). */
public final class ModNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NaturalDisastersMod.MOD_ID, "main"),
            () -> VERSION,
            // Se aceptan clientes sin el mod (vanilla): simplemente no veran la vibracion.
            NetworkRegistry.acceptMissingOr(VERSION),
            NetworkRegistry.acceptMissingOr(VERSION));

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(ShakePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShakePacket::encode)
                .decoder(ShakePacket::new)
                .consumerMainThread(ShakePacket::handle)
                .add();
    }

    public static void sendShake(ServerPlayer player, float strength, int ticks) {
        if (CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShakePacket(strength, ticks));
        }
    }
}
