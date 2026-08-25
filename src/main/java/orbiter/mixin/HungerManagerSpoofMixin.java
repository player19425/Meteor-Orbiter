package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FoodData.class)
public abstract class HungerManagerSpoofMixin {
    @Inject(method = "getFoodLevel", at = @At("HEAD"), cancellable = true)
    private void orbiter$getFoodLevel(CallbackInfoReturnable<Integer> cir) {
        if (!ClientSpoofState.isHudRenderScope()) return;
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeHunger()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player.getFoodData()) return;

        cir.setReturnValue(module.getFakeHunger());
    }

    @Inject(method = "getSaturationLevel", at = @At("HEAD"), cancellable = true)
    private void orbiter$getSaturationLevel(CallbackInfoReturnable<Float> cir) {
        if (!ClientSpoofState.isHudRenderScope()) return;
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeHunger()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player.getFoodData()) return;

        cir.setReturnValue(module.getFakeSaturation());
    }
}
