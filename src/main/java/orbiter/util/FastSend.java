package orbiter.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public final class FastSend {
    private FastSend() {
    }

    public static boolean command(String command) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) return false;
        player.connection.send(new ServerboundChatCommandPacket(command));
        return true;
    }
}
