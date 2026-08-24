package orbiter.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import orbiter.modules.misc.EnchCracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class EnchantTableUseMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void orbiter$captureTablePos(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = hitResult.getBlockPos();
        if (player.level().getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
            EnchCracker.onTableUsed(pos);
        }
    }
}
