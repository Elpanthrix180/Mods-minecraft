package com.panthrix.naturaldisasters.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Mensaje "haz vibrar la camara con esta fuerza durante estos ticks". */
public class ShakePacket {

    private final float strength;
    private final int ticks;

    public ShakePacket(float strength, int ticks) {
        this.strength = strength;
        this.ticks = ticks;
    }

    public ShakePacket(FriendlyByteBuf buf) {
        this.strength = buf.readFloat();
        this.ticks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(strength);
        buf.writeVarInt(ticks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        float s = Math.min(strength, 5f);
        int t = Math.min(ticks, 200);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.panthrix.naturaldisasters.client.ClientShake.start(s, t));
        ctx.get().setPacketHandled(true);
    }
}
