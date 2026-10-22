package orbiter.compat;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;

public final class EntityCompat {
    private EntityCompat() {}

    public static boolean isKeyDown(int keyCode) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.getItem() instanceof AxeItem;
    }

    public static boolean isHoe(ItemStack stack) {
        return stack.getItem() instanceof HoeItem;
    }

    public static boolean isSwinging(LivingEntity entity) {
        return entity.swinging;
    }

    public static void swing(Player player, InteractionHand hand) {
        player.swing(hand);
    }

    public static void swingWithAttackAnimation(Player player, InteractionHand hand) {
        player.swing(hand);
    }

    public static boolean dropKeeping(Player player, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || !(player instanceof LocalPlayer local)) return false;
        if (mc.getConnection() == null) return false;

        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) return false;
        if (inventory.getItem(slot).isEmpty()) return false;
        if (!Inventory.isHotbarSlot(slot)) return false;

        int previous = inventory.getSelectedSlot();
        if (previous != slot) {
            inventory.setSelectedSlot(slot);
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }

        local.drop(true);

        if (previous != slot) {
            inventory.setSelectedSlot(previous);
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(previous));
        }

        return true;
    }

    public static void openUri(java.net.URI uri) {
        net.minecraft.util.Util.getPlatform().openUri(uri);
    }
}
