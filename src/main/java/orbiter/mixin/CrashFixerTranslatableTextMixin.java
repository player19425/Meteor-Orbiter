package orbiter.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.TranslatableTextContent;
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

@Mixin(TranslatableTextContent.class)
public class CrashFixerTranslatableTextMixin {

    @Unique
    private static final Pattern formatPattern = Pattern.compile("%(?:(\\d+)\\$)?([%s])");

    @Unique
    private static final ThreadLocal<Integer> recursionDepth = ThreadLocal.withInitial(() -> 0);
    @Unique
    private static final ThreadLocal<Boolean> isPoisoned = ThreadLocal.withInitial(() -> false);
    @Unique
    private static final AtomicBoolean logged = new AtomicBoolean(false);

    @Inject(method = "visit(Lnet/minecraft/text/StringVisitable$Visitor;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void orbiter$guardPayload1(StringVisitable.Visitor<T> visitor, CallbackInfoReturnable<Optional<T>> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldPayloadGuard() || isPayloadSafe(mod)) return;
        cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "visit(Lnet/minecraft/text/StringVisitable$StyledVisitor;Lnet/minecraft/text/Style;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void orbiter$guardPayload2(StringVisitable.StyledVisitor<T> visitor, Style style, CallbackInfoReturnable<Optional<T>> cir) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldPayloadGuard() || isPayloadSafe(mod)) return;
        cir.setReturnValue(Optional.empty());
    }

    @Redirect(method = "visit(Lnet/minecraft/text/StringVisitable$Visitor;)Ljava/util/Optional;",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/text/StringVisitable;visit(Lnet/minecraft/text/StringVisitable$Visitor;)Ljava/util/Optional;"))
    private <T> Optional<T> orbiter$redirectVisit1(StringVisitable instance, StringVisitable.Visitor<T> visitor) {
        ServerProtect mod = Modules.get() == null ? null : Modules.get().get(ServerProtect.class);
        if (mod == null || !mod.isActive() || !mod.shouldRecursionGuard()) return instance.visit(visitor);
        return guarded(() -> instance.visit(visitor), mod);
    }

    @Redirect(method = "visit(Lnet/minecraft/text/StringVisitable$StyledVisitor;Lnet/minecraft/text/Style;)Ljava/util/Optional;",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/text/StringVisitable;visit(Lnet/minecraft/text/StringVisitable$StyledVisitor;Lnet/minecraft/text/Style;)Ljava/util/Optional;"))
    private <T> Optional<T> orbiter$redirectVisit2(StringVisitable instance, StringVisitable.StyledVisitor<T> visitor, Style style) {
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
        TranslatableTextContent contents = (TranslatableTextContent) (Object) this;
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
