package orbiter.commands;

import orbiter.modules.ForceInvisibility;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class FixDeathCommand extends Command {
    public FixDeathCommand() {
        super("fixdeath", "Stops fake death loops and force-resyncs client/server state.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                error("Player is null.");
                return SINGLE_SUCCESS;
            }

            Modules modules = Modules.get();
            if (modules == null) {
                error("Modules not initialized.");
                return SINGLE_SUCCESS;
            }
            ForceInvisibility forceInvisibility = modules.get(ForceInvisibility.class);
            if (forceInvisibility != null && forceInvisibility.isActive()) {
                forceInvisibility.toggle();
                info("Disabled ForceInvisibility before recovery.");
            }

            boolean wasDead = mc.player.isDead() || mc.player.getHealth() <= 0.0f || mc.player.deathTime > 0;
            mc.player.deathTime = 0;
            mc.player.fallDistance = 0.0f;
            mc.player.setVelocity(Vec3d.ZERO);

            if (mc.getNetworkHandler() != null) {
                if (wasDead) {
                    mc.player.requestRespawn();
                }

                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    true,
                    mc.player.horizontalCollision
                ));
            }

            info(wasDead ? "Death loop recovery packet sent and client synced." : "Client death flags cleared and position synced.");
            return SINGLE_SUCCESS;
        });
    }
}
