package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HungerManager.class)
public abstract class HungerManagerSpoofMixin {
    @Inject(method = "getFoodLevel", at = @At("HEAD"), cancellable = true)
    private void orbiter$getFoodLevel(CallbackInfoReturnable<Integer> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeHunger()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player.getHungerManager()) return;

        cir.setReturnValue(module.getFakeHunger());
    }

    @Inject(method = "getSaturationLevel", at = @At("HEAD"), cancellable = true)
    private void orbiter$getSaturationLevel(CallbackInfoReturnable<Float> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeHunger()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player.getHungerManager()) return;

        cir.setReturnValue(module.getFakeSaturation());
    }
}
