package orbiter.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import orbiter.modules.render.Camera360;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class Camera360HandMixin {

    private float orbiter$savedPitch = Float.NaN;

    private boolean orbiter$is360Active() {
        Camera360 mod = (Camera360) Modules.get().get("360-camera");
        return mod != null && mod.isActive();
    }

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), require = 0)
    private void orbiter$applyHandTiltPitch(float tickProgress, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState, CallbackInfo ci) {
        orbiter$savedPitch = Float.NaN;
        if (!orbiter$is360Active()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null) return;

        orbiter$savedPitch = avatar.xRot;
        float prev = Mth.wrapDegrees(player.xRotO);
        float cur = player.getXRot();
        avatar.xRot = Mth.lerp(tickProgress, prev, prev + Mth.wrapDegrees(cur - prev));
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"), require = 0)
    private void orbiter$restoreHandTiltPitch(float tickProgress, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState, CallbackInfo ci) {
        if (Float.isNaN(orbiter$savedPitch)) return;
        playerState.avatarRenderState.xRot = orbiter$savedPitch;
        orbiter$savedPitch = Float.NaN;
    }
}