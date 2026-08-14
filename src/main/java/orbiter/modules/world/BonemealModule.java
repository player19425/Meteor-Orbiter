package orbiter.modules.world;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class BonemealModule extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlacement = settings.createGroup("Placement");

    private final Setting<Integer> reach = sgGeneral.add(new IntSetting.Builder()
        .name("reach")
        .description("How far to place bonemeal.")
        .defaultValue(200)
        .min(1)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> throughBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("through-blocks")
        .description("Place through blocks without line of sight.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Radius around cursor to apply bonemeal.")
        .defaultValue(5)
        .min(0)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> maxPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-per-tick")
        .description("Maximum bonemeal applications per tick.")
        .defaultValue(50)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<SettingColor> previewColor = sgGeneral.add(new ColorSetting.Builder()
        .name("preview-color")
        .description("Color for the placement preview.")
        .defaultValue(new SettingColor(50, 255, 50, 80))
        .build()
    );

    private final Setting<Boolean> showPreview = sgGeneral.add(new BoolSetting.Builder()
        .name("show-preview")
        .description("Show a preview of where bonemeal will be applied.")
        .defaultValue(true)
        .build()
    );

    private final Set<BlockPos> appliedThisSession = new HashSet<>();
    private BlockPos lastTargetPos = null;
    private int appliedCount = 0;

    public BonemealModule() {
        super(Orbiter.CATEGORY, "bonemeal-painter",
            "Paints the world with bonemeal. Creative/OP only.");
    }

    @Override
    public void onActivate() {
        if (!canUse()) {
            error("Bonemeal Painter requires Creative mode or OP status.");
            toggle();
            return;
        }
        appliedThisSession.clear();
        appliedCount = 0;
    }

    @Override
    public void onDeactivate() {
        appliedThisSession.clear();
        lastTargetPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!canUse()) return;

        if (!mc.options.useKey.isPressed()) {
            lastTargetPos = null;
            return;
        }

        ClientPlayerEntity player = mc.player;
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);

        Vec3d targetPoint;
        if (throughBlocks.get()) {

            double dist = Math.min(reach.get(), 256);
            targetPoint = eyePos.add(lookVec.multiply(dist));
        } else {

            Vec3d end = eyePos.add(lookVec.multiply(reach.get()));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eyePos, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) return;
            targetPoint = hit.getPos();
        }

        BlockPos centerPos = BlockPos.ofFloored(targetPoint);
        if (centerPos.equals(lastTargetPos)) return;
        lastTargetPos = centerPos;

        int r = radius.get();
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = centerPos.add(dx, dy, dz);
                    if (applyBonemeal(pos)) {
                        count++;
                        appliedCount++;
                        if (count >= maxPerTick.get()) return;
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!showPreview.get() || mc.player == null || mc.world == null) return;
        if (!mc.options.useKey.isPressed()) return;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d targetPoint = eyePos.add(lookVec.multiply(Math.min(reach.get(), 256)));
        BlockPos centerPos = BlockPos.ofFloored(targetPoint);
        int r = radius.get();

        SettingColor c = previewColor.get();
        meteordevelopment.meteorclient.utils.render.color.Color fill =
            new meteordevelopment.meteorclient.utils.render.color.Color(c.r, c.g, c.b, c.a);
        meteordevelopment.meteorclient.utils.render.color.Color line =
            new meteordevelopment.meteorclient.utils.render.color.Color(c.r, c.g, c.b, 255);

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = centerPos.add(dx, dy, dz);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() instanceof Fertilizable) {
                        event.renderer.box(pos, fill, line, ShapeMode.Both, 0);
                    }
                }
            }
        }
    }

    private boolean applyBonemeal(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;
        if (appliedThisSession.contains(pos)) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof Fertilizable fertilizable)) return false;
        if (!fertilizable.isFertilizable(mc.world, pos, state)) return false;

        if (!hasBonemeal()) return false;

        Vec3d hitVec = Vec3d.ofCenter(pos);
        Direction side = Direction.UP;
        BlockHitResult hitResult = new BlockHitResult(hitVec, side, pos, false);

        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.MAIN_HAND, hitResult, 0));

        appliedThisSession.add(pos);
        return true;
    }

    private boolean hasBonemeal() {
        if (mc.player == null) return false;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isOf(Items.BONE_MEAL)) return true;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.BONE_MEAL)) {
                return true;
            }
        }

        return mc.player.getAbilities().creativeMode;
    }

    private boolean canUse() {
        if (mc.player == null) return false;
        return mc.player.getAbilities().creativeMode;
    }

    @Override
    public String getInfoString() {
        return appliedCount + " applied";
    }
}
