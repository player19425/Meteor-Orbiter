package orbiter.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

public final class ClientCompat {
    private ClientCompat() {}

    public static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    public static ToastManager toastManager() {
        return Minecraft.getInstance().getToastManager();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static Camera mainCamera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static void invalidateCompiledGeometry() {
        Minecraft.getInstance().levelRenderer.allChanged();
    }
}
