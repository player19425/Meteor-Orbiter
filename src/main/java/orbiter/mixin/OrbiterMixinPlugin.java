package orbiter.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public final class OrbiterMixinPlugin implements IMixinConfigPlugin {
    private static final String CRASH_FIXER_PREFIX = "orbiter.mixin.CrashFixer";
    private static final Set<String> CRASH_FIXER_IDS = Set.of("crashfixer");
    private static final Set<String> PACKET_FIXER_IDS = Set.of("packetfixer", "packet-fixer");
    private static final Set<String> EXPLOIT_PREVENTER_IDS = Set.of("exploitpreventer", "exploit-preventer");

    private boolean delegateCrashProtection;
    private boolean delegatePacketProtection;
    private static volatile boolean crashFixerDelegated;
    private static volatile boolean externalPacketProtection;

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        delegateCrashProtection = CRASH_FIXER_IDS.stream().anyMatch(loader::isModLoaded);
        delegatePacketProtection = PACKET_FIXER_IDS.stream().anyMatch(loader::isModLoaded)
            || EXPLOIT_PREVENTER_IDS.stream().anyMatch(loader::isModLoaded);
        crashFixerDelegated = delegateCrashProtection;
        externalPacketProtection = delegatePacketProtection;

        List<String> ownership = new ArrayList<>();
        if (delegateCrashProtection) ownership.add("CrashFixer owns crash-text protections");
        if (delegatePacketProtection) ownership.add("external packet protection detected; Orbiter keeps distinct guards only");
        if (ownership.isEmpty()) ownership.add("Orbiter owns all configured protections");
        System.out.println("[Orbiter] compatibility: " + String.join("; ", ownership));
    }

    public static boolean isCrashFixerDelegated() { return crashFixerDelegated; }
    public static boolean isExternalPacketProtectionPresent() { return externalPacketProtection; }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (delegateCrashProtection && mixinClassName.startsWith(CRASH_FIXER_PREFIX)) return false;

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
