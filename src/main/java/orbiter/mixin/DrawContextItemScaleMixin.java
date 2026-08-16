package orbiter.mixin;

import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class DrawContextItemScaleMixin {
    @Inject(method = "submitBlitFromItemAtlas(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;Lnet/minecraft/client/gui/render/GuiItemAtlas$SlotView;)V", at = @At("HEAD"))
    private void orbiter$scaleItem(GuiItemRenderState state, GuiItemAtlas.SlotView slot, CallbackInfo ci) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return;

        double scale = module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) return;

        state.pose().scaleAround((float) scale, (float) scale, state.x() + 8.0f, state.y() + 8.0f);
    }
}
