package orbiter.compat;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

public final class EntityCompat {
    private EntityCompat() {}

    public static boolean isKeyDown(int keyCode) {
        return InputConstants.isKeyDown(keyCode);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.is(ItemTags.AXES);
    }

    public static boolean isHoe(ItemStack stack) {
        return stack.is(ItemTags.HOES);
    }

    public static boolean isSwinging(LivingEntity entity) {
        return entity.isSwinging();
    }

    public static void swing(Player player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, false);
    }

    public static void swingWithAttackAnimation(Player player, InteractionHand hand) {
        player.swing(hand, player.getMainHandItem().getAttackAnimation(), false);
    }

    public static boolean dropKeeping(Player player, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || !(player instanceof LocalPlayer local)) return false;

        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) return false;
        if (inventory.getItem(slot).isEmpty()) return false;
        if (!Inventory.isHotbarSlot(slot)) return false;

        int previous = inventory.getSelectedSlot();
        ClientPacketListener connection = mc.getConnection();

        if (previous != slot) {
            inventory.setSelectedSlot(slot);
            if (connection != null) connection.send(new ServerboundSetCarriedItemPacket(slot));
        }

        mc.gameMode.dropItem(local, true);

        if (previous != slot) {
            inventory.setSelectedSlot(previous);
            if (connection != null) connection.send(new ServerboundSetCarriedItemPacket(previous));
        }

        return true;
    }

    public static void openUri(java.net.URI uri) {
        com.mojang.blaze3d.Blaze3D.openUri(uri);
    }
}