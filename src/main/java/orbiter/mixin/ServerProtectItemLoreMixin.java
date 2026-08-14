package orbiter.mixin;

import orbiter.modules.misc.ServerProtect;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class ServerProtectItemLoreMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void orbiter$sanitizeLore(Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        ServerProtect module = ServerProtect.get();

        if (module == null || !module.isActive()) return;

        ItemStack self = (ItemStack) (Object) this;
        if (self.isEmpty()) return;

        if (module.shouldSanitizeCopyForTooltip()) {
            List<Text> safeView = ServerProtect.createSafeItemTooltip(self);
            if (!safeView.isEmpty()) {
                cir.setReturnValue(safeView);
                return;
            }
        }

        if (module.shouldValidateEntityData() && module.shouldSanitizeCopyForTooltip()) {
            if (ServerProtect.isMaliciousEntityDataOnly(self)) {
                cir.setReturnValue(List.of(
                    Text.literal("\u00a7c[ServerProtect] Malicious entity data detected"),
                    Text.literal("\u00a77Tooltip hidden; the live item was not modified.")
                ));
                return;
            }
        }

        WrittenBookContentComponent bookContent = self.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (bookContent != null && bookContent.pages() != null) {
            List<RawFilteredPair<Text>> pages = bookContent.pages();
            int totalLen = 0;
            boolean abusivePage = false;
            for (RawFilteredPair<Text> page : pages) {
                Text raw = page.raw();
                totalLen += raw.getString().length();
                if (ServerProtect.isAbusiveText(raw)) { abusivePage = true; break; }
            }
            if (abusivePage || totalLen > 1800 || pages.size() > 50) {
                cir.setReturnValue(List.of(
                    Text.literal("\u00a7c[ServerProtect] Book content stripped"),
                    Text.literal("\u00a77Total: " + totalLen + " chars, " + pages.size() + " pages")
                ));
                return;
            }
        }

        Text customName = self.get(DataComponentTypes.CUSTOM_NAME);
        if (module.shouldSanitizeNames() && customName != null) {
            if (ServerProtect.isAbusiveText(customName)
                || customName.getString().length() > module.getMaxNameLength()) {

                List<Text> original = cir.getReturnValue();
                List<Text> sanitized = new ArrayList<>();
                sanitized.add(Text.literal("\u00a7c[ServerProtect] Abusive custom name hidden"));
                if (original != null) {
                    for (Text line : original) {
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

        List<Text> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        int maxLen = module.getMaxLoreLength();
        boolean needsFilter = false;
        int totalLen = 0;

        for (Text line : original) {

            if (ServerProtect.isAbusiveText(line)) { needsFilter = true; break; }
            String raw = line.getString();
            totalLen += raw.length();
            if (totalLen > maxLen) { needsFilter = true; break; }
            if (module.shouldRemoveObfuscated() && raw.contains("\u00A7k")) { needsFilter = true; break; }
            if (module.shouldRemoveChinese() && ServerProtect.hasExcessiveCjk(raw)) { needsFilter = true; break; }
        }

        if (!needsFilter) return;

        List<Text> sanitized = new ArrayList<>();
        sanitized.add(Text.literal("\u00a7c[ServerProtect] Lore stripped - too large/abusive"));
        for (Text line : original) {
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
