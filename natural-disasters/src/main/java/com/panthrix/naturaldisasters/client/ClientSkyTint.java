package com.panthrix.naturaldisasters.client;

import com.panthrix.naturaldisasters.NaturalDisastersMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tinte naranja del cielo (color de la niebla y del horizonte) durante la lluvia de meteoritos.
 * Solo existe en el cliente; cambia suavemente hacia el valor que pide el servidor.
 */
@Mod.EventBusSubscriber(modid = NaturalDisastersMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSkyTint {

    private static final float R = 1.0f, G = 0.45f, B = 0.12f;

    private static float target;
    private static float current;
    private static int ticksLeft;

    private ClientSkyTint() {}

    public static void set(float strength, int ticks) {
        target = strength;
        ticksLeft = ticks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        if (ticksLeft > 0) ticksLeft--;
        float goal = ticksLeft > 0 ? target : 0f;
        current += (goal - current) * 0.03f; // transicion lenta
        if (current < 0.001f && goal == 0f) current = 0f;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (current <= 0f) return;
        float k = current * 0.75f;
        event.setRed(event.getRed() + (R - event.getRed()) * k);
        event.setGreen(event.getGreen() + (G - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (B - event.getBlue()) * k);
    }
}
