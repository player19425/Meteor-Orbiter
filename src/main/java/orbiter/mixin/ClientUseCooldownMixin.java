package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Minecraft.class)
public abstract class ClientUseCooldownMixin {
    @ModifyConstant(method = "startUseItem", constant = @Constant(intValue = 4))
    private int orbiter$customItemUseCooldown(int original) {
        ClientSideThings module = ClientSpoofState.module();
        Minecraft client = (Minecraft) (Object) this;
        if (module == null || client.player == null) return original;
        if (!client.player.getMainHandItem().isEmpty() && module.shouldOverrideUseCooldown(client.player.getMainHandItem().getItem())) return module.getCustomUseCooldownTicks();
        if (!client.player.getOffhandItem().isEmpty() && module.shouldOverrideUseCooldown(client.player.getOffhandItem().getItem())) return module.getCustomUseCooldownTicks();
        return original;
    }
}
