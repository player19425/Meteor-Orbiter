package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import orbiter.modules.misc.ServerProtect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(TranslatableContents.class)
public class CrashFixerTranslatableTextMixin {

    @Unique
    private static final Pattern formatPattern = Pattern.compile("%(?:(\\d+)\\$)?([%s])");

    @Unique
    private static final ThreadLocal<Integer> recursionDepth = ThreadLocal.withInitial(() -> 0);
    @Unique
    private static final ThreadLocal<Boolean> isPoisoned = ThreadLocal.withInitial(() -> false);
    @Unique
    private static final AtomicBoolean logged = new AtomicBoolean(false);

    @Inject(method = "visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void orbiter$guardPayload1(FormattedText.ContentConsumer<T> visitor, CallbackInfoReturnable<Optional<T>> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldPayloadGuard() || isPayloadSafe(mod)) return;
        cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "visit(Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;Lnet/minecraft/network/chat/Style;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void orbiter$guardPayload2(FormattedText.StyledContentConsumer<T> visitor, Style style, CallbackInfoReturnable<Optional<T>> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldPayloadGuard() || isPayloadSafe(mod)) return;
        cir.setReturnValue(Optional.empty());
    }

    @Redirect(method = "visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/FormattedText;visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;"))
    private <T> Optional<T> orbiter$redirectVisit1(FormattedText instance, FormattedText.ContentConsumer<T> visitor) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldRecursionGuard()) return instance.visit(visitor);
        return guarded(() -> instance.visit(visitor), mod);
    }

    @Redirect(method = "visit(Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;Lnet/minecraft/network/chat/Style;)Ljava/util/Optional;",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/FormattedText;visit(Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;Lnet/minecraft/network/chat/Style;)Ljava/util/Optional;"))
    private <T> Optional<T> orbiter$redirectVisit2(FormattedText instance, FormattedText.StyledContentConsumer<T> visitor, Style style) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldRecursionGuard()) return instance.visit(visitor, style);
        return guarded(() -> instance.visit(visitor, style), mod);
    }

    @Unique
    private <T> Optional<T> guarded(java.util.function.Supplier<Optional<T>> call, ServerProtect mod) {
        int maxDepth = mod.getTranslationMaxRecursionDepth();
        if (isPoisoned.get()) return Optional.empty();
        int depth = recursionDepth.get();
        if (depth > maxDepth) {
            if (!logged.getAndSet(true)) {
                mod.warn("Prevented translation recursion (depth=" + depth + ")");
            }
            isPoisoned.set(true);
            return Optional.empty();
        }
        recursionDepth.set(depth + 1);
        try {
            return call.get();
        } catch (Throwable t) {
            isPoisoned.set(true);
            return Optional.empty();
        } finally {
            recursionDepth.set(depth);
            if (depth == 0) {
                isPoisoned.set(false);
            }
        }
    }

    @Unique
    private boolean isPayloadSafe(ServerProtect mod) {
        TranslatableContents contents = (TranslatableContents) (Object) this;
        String template = contents.getFallback() != null ? contents.getFallback() : contents.getKey();

        if (template.length() > mod.getTranslationMaxTemplateChars()) return false;

        Object[] args = contents.getArgs();
        if (args.length > mod.getTranslationMaxArgs()) return false;

        int maxArgChars = mod.getTranslationMaxArgChars();
        for (Object arg : args) {
            if (estimateArgChars(arg, maxArgChars + 1) > maxArgChars) return false;
        }

        int placeholderCount = 0;
        Matcher matcher = formatPattern.matcher(template);
        while (matcher.find()) {
            if (!"s".equals(matcher.group(2))) continue;
            if (++placeholderCount > mod.getTranslationMaxPlaceholders()) return false;
        }
        return template.length() <= mod.getTranslationMaxExpandedChars();
    }

    @Unique
    private static int estimateArgChars(Object arg, int limit) {
        if (arg == null) return Math.min(4, limit);
        if (arg instanceof String s) return Math.min(s.length(), limit);
        return Math.min(String.valueOf(arg).length(), limit);
    }
}
