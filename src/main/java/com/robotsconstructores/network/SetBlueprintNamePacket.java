package com.robotsconstructores.network;

import com.robotsconstructores.entity.BuilderRobotEntity;
import com.robotsconstructores.menu.BuilderRobotMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: nombre del archivo de plano/imagen. El servidor lo valida. */
public class SetBlueprintNamePacket {
    private static final int MAX_LENGTH = 130;
    private final int containerId;
    private final String name;

    public SetBlueprintNamePacket(int containerId, String name) {
        this.containerId = containerId;
        this.name = name;
    }

    public SetBlueprintNamePacket(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
        this.name = buf.readUtf(MAX_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeUtf(name, MAX_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null
                && player.containerMenu instanceof BuilderRobotMenu menu
                && menu.containerId == containerId
                && menu.stillValid(player)
                && menu.getRobot() instanceof BuilderRobotEntity robot) {
            robot.setBlueprintName(name.trim());
        }
        ctx.get().setPacketHandled(true);
    }
}
