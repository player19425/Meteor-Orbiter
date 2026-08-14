package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySpoofMixin {
    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void orbiter$getHealth(CallbackInfoReturnable<Float> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeHealth()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this == mc.player) {
            cir.setReturnValue(module.getFakeHealth());
        }
    }

    @Inject(method = "getArmor", at = @At("HEAD"), cancellable = true)
    private void orbiter$getArmor(CallbackInfoReturnable<Integer> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null || !module.shouldFakeArmor()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this == mc.player) {
            cir.setReturnValue(module.getFakeArmor());
        }
    }

    @Inject(method = "getEquippedStack", at = @At("RETURN"), cancellable = true)
    private void orbiter$getEquippedStack(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if ((Object) this != mc.player) return;

        ItemStack current = cir.getReturnValue();
        ItemStack fake = module.getFakeEquipmentStack(slot, current);
        if (fake != null && fake != current) {
            cir.setReturnValue(fake);
        }
    }
}
