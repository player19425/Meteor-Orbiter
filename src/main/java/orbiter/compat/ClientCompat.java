package orbiter.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

public final class ClientCompat {
    private ClientCompat() {}

    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static ToastManager toastManager() {
        return Minecraft.getInstance().gui.toastManager();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static Camera mainCamera() {
        return Minecraft.getInstance().gameRenderer.mainCamera();
    }

    public static void invalidateCompiledGeometry() {
        Minecraft mc = Minecraft.getInstance();
        mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
    }
}
