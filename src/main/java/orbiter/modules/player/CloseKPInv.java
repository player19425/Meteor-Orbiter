package orbiter.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import orbiter.Orbiter;

public class CloseKPInv extends Module {

    private final SettingGroup sgSlots = settings.createGroup("Slots to Keep");

    private final Setting<Boolean> useCrafting = sgSlots.add(new BoolSetting.Builder()
        .name("use-crafting")
        .description("Keep items in the crafting grid and result slot.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useArmor = sgSlots.add(new BoolSetting.Builder()
        .name("use-armor")
        .description("Keep items in armor slots.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useOffhand = sgSlots.add(new BoolSetting.Builder()
        .name("use-offhand")
        .description("Keep items in offhand slot.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> carryCursor = sgSlots.add(new BoolSetting.Builder()
        .name("carry-cursor")
        .description("Remember cursor stack across inventory open/close cycles.")
        .defaultValue(true)
        .build());

    private final SettingGroup sgMouse = settings.createGroup("Mouse Position");

    private final Setting<Boolean> restoreMousePos = sgMouse.add(new BoolSetting.Builder()
        .name("restore-mouse-pos")
        .description("Save the mouse position when the inventory closes and warp it back to the same spot when it reopens, instead of snapping to center.")
        .defaultValue(true)
        .build());

    private static ItemStack savedCursor = ItemStack.EMPTY;

    public CloseKPInv() {
        super(Orbiter.CATEGORY, "close-kp-inv",
            "Keeps items in crafting/armor/offhand when closing inventory.");
    }

    public static CloseKPInv get() {
        return Modules.get().get(CloseKPInv.class);
    }

    public boolean shouldKeepCrafting() { return isActive() && useCrafting.get(); }
    public boolean shouldKeepArmor() { return isActive() && useArmor.get(); }
    public boolean shouldKeepOffhand() { return isActive() && useOffhand.get(); }
    public boolean shouldKeepCursor() { return isActive() && carryCursor.get(); }
    public boolean shouldRestoreMousePos() { return isActive() && restoreMousePos.get(); }

    public static void saveCursor(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            savedCursor = stack.copy();
        }
    }

    public static ItemStack getSavedCursor() {
        ItemStack s = savedCursor;
        savedCursor = ItemStack.EMPTY;
        return s;
    }

    public static boolean hasSavedCursor() {
        return savedCursor != null && !savedCursor.isEmpty();
    }

    private double savedMouseX = -1;
    private double savedMouseY = -1;

    private boolean pendingMouseRestore;

    private boolean wasInventoryOpen;

    @Override
    public void onActivate() {
        wasInventoryOpen = false;
        pendingMouseRestore = false;
        savedMouseX = -1;
        savedMouseY = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        boolean inventoryOpen = mc.currentScreen instanceof HandledScreen<?>;

        if (inventoryOpen && mc.getWindow() != null) {
            long handle = mc.getWindow().getHandle();
            if (handle != 0L) {
                double[] x = new double[1];
                double[] y = new double[1];
                org.lwjgl.glfw.GLFW.glfwGetCursorPos(handle, x, y);
                savedMouseX = x[0];
                savedMouseY = y[0];
            }
        }

        if (wasInventoryOpen && !inventoryOpen && shouldRestoreMousePos()) {

            pendingMouseRestore = true;
        }
        wasInventoryOpen = inventoryOpen;

        if (!pendingMouseRestore) return;
        pendingMouseRestore = false;

        if (savedMouseX < 0 || savedMouseY < 0) return;
        if (mc.getWindow() == null) return;

        long handle = mc.getWindow().getHandle();
        if (handle != 0L) {

            org.lwjgl.glfw.GLFW.glfwSetCursorPos(handle, savedMouseX, savedMouseY);
        }
    }
}
