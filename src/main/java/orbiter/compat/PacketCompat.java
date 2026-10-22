package orbiter.compat;

import java.util.List;
import java.util.Map;

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
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class PacketCompat {
    private PacketCompat() {}

    public static int particleCount(ClientboundLevelParticlesPacket packet) {
        return packet.getCount();
    }

    public static double particleX(ClientboundLevelParticlesPacket packet) {
        return packet.getX();
    }

    public static double particleY(ClientboundLevelParticlesPacket packet) {
        return packet.getY();
    }

    public static double particleZ(ClientboundLevelParticlesPacket packet) {
        return packet.getZ();
    }

    public static float particleXDist(ClientboundLevelParticlesPacket packet) {
        return packet.getXDist();
    }

    public static float particleYDist(ClientboundLevelParticlesPacket packet) {
        return packet.getYDist();
    }

    public static float particleZDist(ClientboundLevelParticlesPacket packet) {
        return packet.getZDist();
    }

    public static float particleXMaxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.getMaxSpeed();
    }

    public static float particleYMaxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.getMaxSpeed();
    }

    public static float particleZMaxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.getMaxSpeed();
    }

    public static ParticleOptions particleOptions(ClientboundLevelParticlesPacket packet) {
        return packet.getParticle();
    }

    public static boolean particleOverrideLimiter(ClientboundLevelParticlesPacket packet) {
        return packet.isOverrideLimiter();
    }

    public static boolean particleAlwaysShow(ClientboundLevelParticlesPacket packet) {
        return packet.alwaysShow();
    }

    public static int chunkX(ClientboundLevelChunkWithLightPacket packet) {
        return packet.getX();
    }

    public static int chunkZ(ClientboundLevelChunkWithLightPacket packet) {
        return packet.getZ();
    }

    public static Map<Identifier, AdvancementProgress> advancementsProgress(ClientboundUpdateAdvancementsPacket packet) {
        return packet.getProgress();
    }

    public static List<Identifier> advancementsRemoved(ClientboundUpdateAdvancementsPacket packet) {
        return List.copyOf(packet.getRemoved());
    }

    public static BlockHitResult useItemOnHitResult(ServerboundUseItemOnPacket packet) {
        return packet.getHitResult();
    }

    public static InteractionHand useItemOnHand(ServerboundUseItemOnPacket packet) {
        return packet.getHand();
    }

    public static InteractionHand useItemHand(ServerboundUseItemPacket packet) {
        return packet.getHand();
    }

    public static Component unsignedChatContent(ClientboundPlayerChatPacket packet) {
        return packet.unsignedContent();
    }

    public static Component teamDisplayName(ClientboundSetPlayerTeamPacket.Parameters team) {
        return team.getDisplayName();
    }

    public static Component teamPlayerPrefix(ClientboundSetPlayerTeamPacket.Parameters team) {
        return team.getPlayerPrefix();
    }

    public static Component teamPlayerSuffix(ClientboundSetPlayerTeamPacket.Parameters team) {
        return team.getPlayerSuffix();
    }

    public static boolean isSwingPacket(Object packet) {
        return packet instanceof ServerboundSwingPacket;
    }

    public static void acceptTeleport(ClientPacketListener connection, LocalPlayer player, int id) {
        connection.send(new ServerboundAcceptTeleportationPacket(id));
    }
}
