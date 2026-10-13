package com.robotsconstructores.network;

import com.robotsconstructores.RobotsConstructores;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RobotsConstructores.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(SetTargetAmountPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTargetAmountPacket::encode)
                .decoder(SetTargetAmountPacket::new)
                .consumerMainThread(SetTargetAmountPacket::handle)
                .add();
    }

    private ModNetwork() {}
}
