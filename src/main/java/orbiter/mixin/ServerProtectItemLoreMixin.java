package orbiter.mixin;

import orbiter.modules.misc.ServerProtect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.server.network.Filterable;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class ServerProtectItemLoreMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void orbiter$sanitizeLore(Item.TooltipContext context, Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
        ServerProtect module = ServerProtect.get();

        if (module == null || !module.isActive()) return;

        ItemStack self = (ItemStack) (Object) this;
        if (self.isEmpty()) return;

        if (module.shouldSanitizeCopyForTooltip()) {
            List<Component> safeView = ServerProtect.createSafeItemTooltip(self);
            if (!safeView.isEmpty()) {
                cir.setReturnValue(safeView);
                return;
            }
        }

        if (module.shouldValidateEntityData() && module.shouldSanitizeCopyForTooltip()) {
            if (ServerProtect.isMaliciousEntityDataOnly(self)) {
                cir.setReturnValue(List.of(
                    Component.literal("\u00a7c[ServerProtect] Malicious entity data detected"),
                    Component.literal("\u00a77Tooltip hidden; the live item was not modified.")
                ));
                return;
            }
        }

        WrittenBookContent bookContent = self.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (bookContent != null && bookContent.pages() != null) {
            List<Filterable<Component>> pages = bookContent.pages();
            int totalLen = 0;
            boolean abusivePage = false;
            for (Filterable<Component> page : pages) {
                Component raw = page.raw();
                totalLen += raw.getString().length();
                if (ServerProtect.isAbusiveText(raw)) { abusivePage = true; break; }
            }
            if (abusivePage || totalLen > 1800 || pages.size() > 50) {
                cir.setReturnValue(List.of(
                    Component.literal("\u00a7c[ServerProtect] Book content stripped"),
                    Component.literal("\u00a77Total: " + totalLen + " chars, " + pages.size() + " pages")
                ));
                return;
            }
        }

        Component customName = self.get(DataComponents.CUSTOM_NAME);
        if (module.shouldSanitizeNames() && customName != null) {
            if (ServerProtect.isAbusiveText(customName)
                || customName.getString().length() > module.getMaxNameLength()) {

                List<Component> original = cir.getReturnValue();
                List<Component> sanitized = new ArrayList<>();
                sanitized.add(Component.literal("\u00a7c[ServerProtect] Abusive custom name hidden"));
                if (original != null) {
                    for (Component line : original) {
                        if (ServerProtect.isAbusiveText(line)) continue;
                        String raw = line.getString();
                        if (raw.length() > 100) continue;
                        if (module.shouldRemoveObfuscated() && raw.contains("\u00A7k")) continue;
                        if (module.shouldRemoveChinese() && ServerProtect.hasExcessiveCjk(raw)) continue;
                        sanitized.add(line);
                    }
                }
                cir.setReturnValue(sanitized);
                return;
            }
        }

        List<Component> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        int maxLen = module.getMaxLoreLength();
        boolean needsFilter = false;
        int totalLen = 0;

        for (Component line : original) {

            if (ServerProtect.isAbusiveText(line)) { needsFilter = true; break; }
            String raw = line.getString();
            totalLen += raw.length();
            if (totalLen > maxLen) { needsFilter = true; break; }
            if (module.shouldRemoveObfuscated() && raw.contains("\u00A7k")) { needsFilter = true; break; }
            if (module.shouldRemoveChinese() && ServerProtect.hasExcessiveCjk(raw)) { needsFilter = true; break; }
        }

        if (!needsFilter) return;

        List<Component> sanitized = new ArrayList<>();
        sanitized.add(Component.literal("\u00a7c[ServerProtect] ItemLore stripped - too large/abusive"));
        for (Component line : original) {
            if (ServerProtect.isAbusiveText(line)) continue;
            String raw = line.getString();
            if (raw.length() > 100) continue;
            if (module.shouldRemoveObfuscated() && raw.contains("\u00A7k")) continue;
            if (module.shouldRemoveChinese() && ServerProtect.hasExcessiveCjk(raw)) continue;
            sanitized.add(line);
        }
        cir.setReturnValue(sanitized);
    }
}
