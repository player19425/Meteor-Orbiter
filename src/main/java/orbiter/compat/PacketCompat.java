package orbiter.compat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class PacketCompat {
    private PacketCompat() {}

    public static int particleCount(ClientboundLevelParticlesPacket packet) {
        return packet.count();
    }

    public static double particleX(ClientboundLevelParticlesPacket packet) {
        return packet.x();
    }

    public static double particleY(ClientboundLevelParticlesPacket packet) {
        return packet.y();
    }

    public static double particleZ(ClientboundLevelParticlesPacket packet) {
        return packet.z();
    }

    public static float particleXDist(ClientboundLevelParticlesPacket packet) {
        return packet.xDist();
    }

    public static float particleYDist(ClientboundLevelParticlesPacket packet) {
        return packet.yDist();
    }

    public static float particleZDist(ClientboundLevelParticlesPacket packet) {
        return packet.zDist();
    }

    public static float particleXMaxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.xMaxSpeed();
    }

    public static float particleYMaxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.yMaxSpeed();
    }

    public static float particleZMaxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.zMaxSpeed();
    }

    public static ParticleOptions particleOptions(ClientboundLevelParticlesPacket packet) {
        return packet.particle();
    }

    public static boolean particleOverrideLimiter(ClientboundLevelParticlesPacket packet) {
        return packet.overrideLimiter();
    }

    public static boolean particleAlwaysShow(ClientboundLevelParticlesPacket packet) {
        return packet.alwaysShow();
    }

    public static int chunkX(ClientboundLevelChunkWithLightPacket packet) {
        return packet.x();
    }

    public static int chunkZ(ClientboundLevelChunkWithLightPacket packet) {
        return packet.z();
    }

    public static Map<Identifier, AdvancementProgress> advancementsProgress(ClientboundUpdateAdvancementsPacket packet) {
        return packet.progress();
    }

    public static List<Identifier> advancementsRemoved(ClientboundUpdateAdvancementsPacket packet) {
        return List.copyOf(packet.removed());
    }

    public static BlockHitResult useItemOnHitResult(ServerboundUseItemOnPacket packet) {
        return packet.hitResult();
    }

    public static InteractionHand useItemOnHand(ServerboundUseItemOnPacket packet) {
        return packet.hand();
    }

    public static InteractionHand useItemHand(ServerboundUseItemPacket packet) {
        return packet.hand();
    }

    public static Component unsignedChatContent(ClientboundPlayerChatPacket packet) {
        return packet.unsignedContent().orElse(null);
    }

    public static Component teamDisplayName(ClientboundSetPlayerTeamPacket.Parameters team) {
        return team.displayName();
    }

    public static Component teamPlayerPrefix(ClientboundSetPlayerTeamPacket.Parameters team) {
        return team.playerPrefix();
    }

    public static Component teamPlayerSuffix(ClientboundSetPlayerTeamPacket.Parameters team) {
        return team.playerSuffix();
    }

    public static boolean isSwingPacket(Object packet) {
        return packet instanceof ServerboundPunchPacket;
    }

    public static void acceptTeleport(ClientPacketListener connection, LocalPlayer player, int id) {
        connection.send(new ServerboundAcceptTeleportationPacket(id, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
    }
}
