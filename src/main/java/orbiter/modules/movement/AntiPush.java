package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;

public class AntiPush extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blockWaterPush = sgGeneral.add(new BoolSetting.Builder()
        .name("water-push")
        .description("Blocks fluid push from water currents.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> blockLavaPush = sgGeneral.add(new BoolSetting.Builder()
        .name("lava-push")
        .description("Blocks fluid push from lava.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> blockEntityPush = sgGeneral.add(new BoolSetting.Builder()
        .name("entity-push")
        .description("Prevents push-away collisions from entities.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> antiExplosions = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-explosions")
        .description("Cancels explosion knockback packets.")
        .defaultValue(true)
        .build());

    public AntiPush() {
        super(Orbiter.CATEGORY, "anti-push", "Stops fluid and entity push.");
    }

    public boolean shouldBlock(TagKey<Fluid> fluidTag) {
        if (!isActive()) return false;

        if (fluidTag == FluidTags.WATER) return blockWaterPush.get();
        if (fluidTag == FluidTags.LAVA) return blockLavaPush.get();

        return false;
    }

    public boolean shouldBlockEntityPush() {
        return isActive() && blockEntityPush.get();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;
        if (!antiExplosions.get()) return;
        if (event.packet instanceof ClientboundExplodePacket) event.cancel();
    }
}
