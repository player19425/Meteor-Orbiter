package orbiter.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import orbiter.Orbiter;
import orbiter.util.ConfigModifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class SlimeJump extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> bounceMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("bounce-multiplier")
        .description("Height multiplier per consecutive bounce (e.g., 1.5 = 50% higher each bounce).")
        .defaultValue(1.5)
        .min(1.0)
        .max(5.0)
        .sliderRange(1.0, 3.0)
        .build()
    );

    private final Setting<Double> maxVelocity = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-velocity")
        .description("Maximum upward velocity cap to prevent anti-cheat flags.")
        .defaultValue(5.0)
        .min(0.5)
        .max(20.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    private final Setting<Boolean> autoBounce = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-bounce")
        .description("Automatically bounce when standing on slime (no fall needed).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> resetTicks = sgGeneral.add(new IntSetting.Builder()
        .name("reset-ticks")
        .description("Ticks of being off slime before bounce count resets.")
        .defaultValue(5)
        .min(1)
        .max(40)
        .sliderRange(1, 20)
        .build()
    );

    private int bounceCount = 0;
    private int offSlimeTicks = 0;
    private boolean wasFalling = false;
    private boolean wasOnSlime = false;

    public SlimeJump() {
        super(Orbiter.CATEGORY_STUPID, "slime-jump", "Progressive slime block bouncing. Each bounce makes you jump higher.");
    }

    @Override
    public void onActivate() {
        if (!ConfigModifier.get().stupidModules.get()) {
            info("Stupid Modules is disabled. Enable it in Meteor Config → Orbiter → Stupid Modules");
            toggle();
            return;
        }
        bounceCount = 0;
        offSlimeTicks = 0;
        wasFalling = false;
        wasOnSlime = false;
    }

    @Override
    public void onDeactivate() {
        bounceCount = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (!ConfigModifier.get().stupidModules.get()) {
            info("Stupid Modules was disabled. SlimeJump turning off.");
            toggle();
            return;
        }

        boolean onSlime = isOnSlime();

        if (onSlime) {
            offSlimeTicks = 0;
        } else {
            offSlimeTicks++;
            if (offSlimeTicks >= resetTicks.get()) {
                bounceCount = 0;
            }
        }

        double yVel = mc.player.getDeltaMovement().y;
        boolean falling = yVel < -0.1;

        if (wasFalling && onSlime && !falling) {

            performBounce(yVel);
        }

        if (autoBounce.get() && onSlime && mc.player.onGround() && yVel == 0) {
            performBounce(-0.5);
        }

        wasFalling = falling;
        wasOnSlime = onSlime;
    }

    private void performBounce(double impactVelocity) {
        bounceCount++;

        double baseBounce = Math.abs(impactVelocity);
        double multiplier = Math.pow(bounceMultiplier.get(), bounceCount);
        double newVel = baseBounce * multiplier;

        newVel = Math.min(newVel, maxVelocity.get());

        Vec3 vel = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(vel.x, newVel, vel.z);

        info("Bounce #" + bounceCount + " • velocity: " + String.format("%.2f", newVel));
    }

    private boolean isOnSlime() {
        BlockPos pos = mc.player.blockPosition();

        return mc.level.getBlockState(pos).is(Blocks.SLIME_BLOCK)
            || mc.level.getBlockState(pos.below()).is(Blocks.SLIME_BLOCK);
    }

    @Override
    public String getInfoString() {
        return "Bounces: " + bounceCount;
    }
}
