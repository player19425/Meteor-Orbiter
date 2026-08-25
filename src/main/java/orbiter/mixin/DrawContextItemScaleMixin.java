package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiRenderer.class)
public abstract class DrawContextItemScaleMixin {
    @WrapMethod(method = "submitBlitFromItemAtlas(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;Lnet/minecraft/client/gui/render/GuiItemAtlas$SlotView;)V")
    private void orbiter$scaleItem(GuiItemRenderState state, GuiItemAtlas.SlotView slot, Operation<Void> original) {
        ClientSideThings module = ClientSpoofState.module();

        double scale = module == null ? 1.0 : module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) {
            original.call(state, slot);
            return;
        }

        Matrix3x2f pose = state.pose();
        Matrix3x2f backup = new Matrix3x2f(pose);
        pose.scaleAround((float) scale, (float) scale, state.x() + 8.0f, state.y() + 8.0f);
        try {
            original.call(state, slot);
        } finally {
            pose.set(backup);
        }
    }
}
