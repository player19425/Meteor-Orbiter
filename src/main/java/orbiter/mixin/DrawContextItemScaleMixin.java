package orbiter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import orbiter.modules.misc.ClientSideThings;
import orbiter.util.ClientSpoofState;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
public abstract class DrawContextItemScaleMixin {
    @WrapOperation(method = "submitBlitFromItemAtlas", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/state/GuiItemRenderState;pose()Lorg/joml/Matrix3x2f;"), require = 1)
    private Matrix3x2f orbiter$scaleItem(GuiItemRenderState state, Operation<Matrix3x2f> original) {
        ClientSideThings module = ClientSpoofState.module();

        double scale = module == null ? 1.0 : module.getItemScale();
        if (Math.abs(scale - 1.0) < 1.0e-6) return original.call(state);

        return new Matrix3x2f(original.call(state)).scaleAround((float) scale, (float) scale, state.x() + 8.0f, state.y() + 8.0f);
    }
}
