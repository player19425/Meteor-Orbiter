package orbiter.mixin;

import orbiter.modules.ClientSideThings;
import orbiter.util.ClientSpoofState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudHotbarMixin {
    @Unique
    private ItemStack orbiter$renderStack;

    @Inject(method = "renderHotbarItem", at = @At("HEAD"), cancellable = true)
    private void orbiter$onRenderHotbarItemHead(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack original, int slot, CallbackInfo ci) {
        orbiter$renderStack = getRenderStack(original, slot);

        ClientSideThings module = ClientSpoofState.module();
        if (module == null || orbiter$renderStack == null || orbiter$renderStack.isEmpty()) return;

        if ((original == null || original.isEmpty()) && module.isFakeHotbarItemsEnabled() && module.hasConfiguredFakeHotbarItem(slot)) {
            context.drawItem(player, orbiter$renderStack, x, y, slot);

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            if (textRenderer != null) context.drawStackOverlay(textRenderer, orbiter$renderStack, x, y);
            orbiter$renderStack = null;
            ci.cancel();
        }
    }

    @Inject(method = "renderHotbarItem", at = @At("RETURN"))
    private void orbiter$onRenderHotbarItemReturn(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack original, int slot, CallbackInfo ci) {
        orbiter$renderStack = null;
    }

    @Redirect(
        method = "renderHotbarItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V")
    )
    private void orbiter$redirectDrawItem(DrawContext instance, LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        ItemStack render = orbiter$renderStack != null ? orbiter$renderStack : stack;
        instance.drawItem(entity, render, x, y, seed);
    }

    @Redirect(
        method = "renderHotbarItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;II)V")
    )
    private void orbiter$redirectDrawStackOverlay(DrawContext instance, TextRenderer textRenderer, ItemStack stack, int x, int y) {
        ItemStack render = orbiter$renderStack != null ? orbiter$renderStack : stack;
        instance.drawStackOverlay(textRenderer, render, x, y);
    }

    private ItemStack getRenderStack(ItemStack incoming, int slot) {
        ClientSideThings module = ClientSpoofState.module();
        if (module == null) return incoming;

        ItemStack original = incoming == null ? ItemStack.EMPTY : incoming;
        ItemStack spoof = module.getFakeHotbarStack(slot, original);
        if (spoof == null || spoof.isEmpty()) spoof = original;

        ItemStack out = spoof.copy();
        int fallback = original.isEmpty() ? out.getCount() : original.getCount();
        int fake = module.getHotbarSpoofCount(slot, fallback);
        out.setCount(Math.min(module.getMaxFakeHotbarCount(), Math.max(1, fake)));

        return out;
    }
}
