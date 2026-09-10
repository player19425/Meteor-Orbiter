package orbiter.util;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class CameraPosition {
    private CameraPosition() {
    }

    private static Freecam freecam() {
        Modules modules = Modules.get();
        return modules == null ? null : modules.get(Freecam.class);
    }

    private static Freecam activeFreecam() {
        Freecam fc = freecam();
        return fc != null && fc.isActive() ? fc : null;
    }

    public static Vec3 pos() {
        Freecam fc = activeFreecam();
        if (fc != null) return new Vec3(fc.pos.x, fc.pos.y, fc.pos.z);
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    public static float yaw() {
        Freecam fc = activeFreecam();
        if (fc != null) return Mth.wrapDegrees(fc.yaw);
        return Mth.wrapDegrees(Minecraft.getInstance().gameRenderer.mainCamera().yRot());
    }

    public static float pitch() {
        Freecam fc = activeFreecam();
        if (fc != null) return Mth.wrapDegrees(fc.pitch);
        return Mth.wrapDegrees(Minecraft.getInstance().gameRenderer.mainCamera().xRot());
    }

    public static String coords() {
        Vec3 p = pos();
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", p.x, p.y, p.z);
    }

    public static String coordsWithLook() {
        Vec3 p = pos();
        return String.format(Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f", p.x, p.y, p.z, yaw(), pitch());
    }

    public static String blockCoords() {
        BlockPos bp = BlockPos.containing(pos());
        return bp.getX() + " " + bp.getY() + " " + bp.getZ();
    }
}
