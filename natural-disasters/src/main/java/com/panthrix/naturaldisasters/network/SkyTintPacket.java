package com.panthrix.naturaldisasters.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Mensaje "tine el cielo de naranja con esta fuerza durante estos ticks". */
public class SkyTintPacket {

    private final float strength;
    private final int ticks;

    public SkyTintPacket(float strength, int ticks) {
        this.strength = strength;
        this.ticks = ticks;
    }

    public SkyTintPacket(FriendlyByteBuf buf) {
        this.strength = buf.readFloat();
        this.ticks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(strength);
        buf.writeVarInt(ticks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        float s = Math.max(0f, Math.min(strength, 1f));
        int t = Math.min(ticks, 400);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.panthrix.naturaldisasters.client.ClientSkyTint.set(s, t));
        ctx.get().setPacketHandled(true);
    }
}
