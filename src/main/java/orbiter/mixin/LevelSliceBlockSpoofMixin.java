package orbiter.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import orbiter.modules.render.BlockSpoof;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
public abstract class LevelSliceBlockSpoofMixin {
    @ModifyReturnValue(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private BlockState orbiter$spoofBlockState(BlockState original, BlockPos pos) {
        return BlockSpoof.applySpoof(original, pos);
    }

    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private BlockState orbiter$spoofBlockState3(BlockState original, int x, int y, int z) {
        return BlockSpoof.applySpoof(original, null);
    }
}
