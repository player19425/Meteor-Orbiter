package orbiter.mixin;

import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ClientboundCommandsPacket.class)
public interface ServerProtectCommandTreeAccessor {

    @Accessor("entries")
    List<?> orbiter$getEntries();
}
