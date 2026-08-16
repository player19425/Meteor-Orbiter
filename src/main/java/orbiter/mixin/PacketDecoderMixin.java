package orbiter.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import orbiter.modules.misc.RawPacketCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin<T extends PacketListener> {

    @Shadow
    private ProtocolInfo<T> protocolInfo;

    @Inject(method = "decode", at = @At("HEAD"))
    private void orbiter$captureRawBytes(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out, CallbackInfo ci) {
        try {
            int readable = buf.readableBytes();
            if (readable <= 0 || readable > 65536) return;

            int checkLimit = Math.min(readable, 256);
            int startIdx = buf.readerIndex();
            boolean mightHaveChannel = false;
            for (int i = 0; i < checkLimit; i++) {
                if (buf.getByte(startIdx + i) == (byte) ':') {
                    mightHaveChannel = true;
                    break;
                }
            }

            if (mightHaveChannel) {
                byte[] raw = new byte[readable];
                buf.getBytes(startIdx, raw);
                RawPacketCapture.enqueue(raw);
            }
        } catch (Exception ignored) {

        }
    }
}
