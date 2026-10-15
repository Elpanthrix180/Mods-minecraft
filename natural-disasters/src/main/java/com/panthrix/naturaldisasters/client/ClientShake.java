package com.panthrix.naturaldisasters.client;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vibracion de la camara durante los terremotos. Solo existe en el cliente y no toca el mundo. */
@Mod.EventBusSubscriber(modid = NaturalDisastersMod.MOD_ID, value = Dist.CLIENT)
public final class ClientShake {

    private static float strength;
    private static int ticksLeft;
    private static int age;

    private ClientShake() {}

    public static void start(float s, int ticks) {
        strength = Math.max(strength * (ticksLeft > 0 ? 1 : 0), s);
        ticksLeft = Math.max(ticksLeft, ticks);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().isPaused()) return;
        age++;
        if (ticksLeft > 0 && --ticksLeft == 0) strength = 0;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ticksLeft <= 0 || strength <= 0) return;
        double t = age + event.getPartialTick();
        float fade = Math.min(1f, ticksLeft / 10f);
        float s = strength * fade;
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 1.9) * s));
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 1.3 + 1.0) * s * 0.6));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 2.3 + 2.0) * s * 0.8));
    }
}
