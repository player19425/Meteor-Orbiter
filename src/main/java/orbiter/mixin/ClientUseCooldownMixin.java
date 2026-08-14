package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MinecraftClient.class)
public abstract class ClientUseCooldownMixin {
    @ModifyConstant(method = "doItemUse", constant = @Constant(intValue = 4))
    private int orbiter$customItemUseCooldown(int original) {
        ClientSideThings module = ClientSpoofState.module();
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (module == null || client.player == null) return original;
        if (!client.player.getMainHandStack().isEmpty() && module.shouldOverrideUseCooldown(client.player.getMainHandStack().getItem())) return module.getCustomUseCooldownTicks();
        if (!client.player.getOffHandStack().isEmpty() && module.shouldOverrideUseCooldown(client.player.getOffHandStack().getItem())) return module.getCustomUseCooldownTicks();
        return original;
    }
}
