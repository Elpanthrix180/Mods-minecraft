package com.robotsconstructores.network;

import com.robotsconstructores.entity.MinerRobotEntity;
import com.robotsconstructores.menu.MinerRobotMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "quiero esta cantidad objetivo". El servidor lo comprueba todo. */
public class SetTargetAmountPacket {
    private final int containerId;
    private final int amount;

    public SetTargetAmountPacket(int containerId, int amount) {
        this.containerId = containerId;
        this.amount = amount;
    }

    public SetTargetAmountPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
        this.amount = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(amount);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null
                && player.containerMenu instanceof MinerRobotMenu menu
                && menu.containerId == containerId
                && menu.stillValid(player)
                && menu.getRobot() instanceof MinerRobotEntity robot) {
            robot.setTargetAmount(amount);
        }
        ctx.get().setPacketHandled(true);
    }
}
