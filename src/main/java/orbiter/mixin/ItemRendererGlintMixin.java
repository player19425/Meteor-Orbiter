package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererGlintMixin {
    @Inject(method = "getGlintRenderLayers", at = @At("RETURN"), cancellable = true)
    private static void orbiter$getGlintRenderLayers(RenderLayer layer, boolean solid, boolean glint, CallbackInfoReturnable<List<RenderLayer>> cir) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        double multiplier = module.getGlintMultiplier();
        if (multiplier <= 1.01 || !glint) return;

        List<RenderLayer> base = cir.getReturnValue();
        if (base == null || base.isEmpty()) return;

        int repeats = Math.max(1, (int) Math.ceil(multiplier * 12.0));
        List<RenderLayer> out = new ArrayList<>(base.size() * repeats);
        for (int i = 0; i < repeats; i++) out.addAll(base);

        cir.setReturnValue(out);
    }
}
