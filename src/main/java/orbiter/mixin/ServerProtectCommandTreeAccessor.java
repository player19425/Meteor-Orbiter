package orbiter.mixin;

import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CommandTreeS2CPacket.class)
public interface ServerProtectCommandTreeAccessor {

    @Accessor("nodes")
    List<?> orbiter$getNodes();

    @Accessor("rootSize")
    int orbiter$getRootSize();
}
