package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.ClientSpoofState;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ClientSideThings extends Module {

    public enum WeatherMode {
        Server,
        Clear,
        Rain,
        Snow
    }

    public enum ChaosIntensity {
        Smooth,
        Random
    }

    public enum CrosshairStyle {
        Default,
        Dot,
        Cross,
        Circle,
        Thin,
        None
    }

    public enum OverlayMode { Server, ForceOn, ForceOff }
    public enum DimensionSky { Overworld, Nether, End }

    public enum FakeGameMode {
        Server(null),
        Survival(GameMode.SURVIVAL),
        Creative(GameMode.CREATIVE),
        Adventure(GameMode.ADVENTURE),
        Spectator(GameMode.SPECTATOR);

        public final GameMode mode;
        FakeGameMode(GameMode mode) { this.mode = mode; }
    }

    private final SettingGroup sgHud          = settings.getDefaultGroup();
    private final SettingGroup sgInventory    = settings.createGroup("Inventory & NBT");
    private final SettingGroup sgVisuals      = settings.createGroup("Visuals");
    private final SettingGroup sgWeather      = settings.createGroup("Weather");
    private final SettingGroup sgEquipment    = settings.createGroup("Fake Equipment");
    private final SettingGroup sgChaos        = settings.createGroup("Full Chaos");
    private final SettingGroup sgSpoof        = settings.createGroup("Spoof");
    private final SettingGroup sgOverlay      = settings.createGroup("Overlay Overrides");
    private final SettingGroup sgCrosshair    = settings.createGroup("Crosshair Override");
    private final SettingGroup sgFog          = settings.createGroup("Fog Override");
    private final SettingGroup sgBossbar      = settings.createGroup("Bossbar Override");
    private final SettingGroup sgFakeDeath    = settings.createGroup("Fake Death Screen");

    private final Setting<Boolean> fakeHealthEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-health-enabled")
        .defaultValue(false)
        .build());

    private final Setting<Double> fakeHealth = sgHud.add(new DoubleSetting.Builder()
        .name("fake-health")
        .defaultValue(20.0)
        .min(0.0).max(2048.0).sliderRange(0.0, 20.0)
        .visible(fakeHealthEnabled::get)
        .build());

    private final Setting<Boolean> fakeArmorEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-armor-enabled")
        .defaultValue(false)
        .build());

    private final Setting<Integer> fakeArmor = sgHud.add(new IntSetting.Builder()
        .name("fake-armor")
        .description("Armor bar value. 20 = full armor bar.")
        .defaultValue(20)
        .min(0).max(40).sliderRange(0, 20)
        .visible(fakeArmorEnabled::get)
        .build());

    private final Setting<Boolean> fakeAbsorptionEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-absorption-enabled")
        .defaultValue(false)
        .build());

    private final Setting<Integer> fakeAbsorption = sgHud.add(new IntSetting.Builder()
        .name("fake-absorption")
        .description("Absorption hearts value (0-2048). Each 2 = one heart row.")
        .defaultValue(20)
        .min(0).max(2048).sliderRange(0, 40)
        .visible(fakeAbsorptionEnabled::get)
        .build());

    private final Setting<Boolean> fakeHungerEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-hunger-enabled")
        .defaultValue(false)
        .build());

    private final Setting<Integer> fakeHunger = sgHud.add(new IntSetting.Builder()
        .name("fake-hunger")
        .defaultValue(20)
        .min(0).max(20).sliderRange(0, 20)
        .visible(fakeHungerEnabled::get)
        .build());

    private final Setting<Double> fakeSaturation = sgHud.add(new DoubleSetting.Builder()
        .name("fake-saturation")
        .defaultValue(20.0)
        .min(0.0).max(20.0).sliderRange(0.0, 20.0)
        .visible(fakeHungerEnabled::get)
        .build());

    private final Setting<Boolean> fakeXpEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-xp-enabled")
        .defaultValue(false)
        .build());

    private final Setting<Integer> fakeXpLevel = sgHud.add(new IntSetting.Builder()
        .name("fake-xp-level")
        .defaultValue(100)
        .min(0).max(5000).sliderRange(0, 250)
        .visible(fakeXpEnabled::get)
        .build());

    private final Setting<Double> fakeXpProgress = sgHud.add(new DoubleSetting.Builder()
        .name("fake-xp-progress")
        .defaultValue(0.75)
        .min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(fakeXpEnabled::get)
        .build());

    private final Setting<Boolean> fakeTimeEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-time-enabled")
        .defaultValue(false)
        .build());

    private final Setting<Integer> fakeTime = sgHud.add(new IntSetting.Builder()
        .name("fake-time")
        .description("0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.")
        .defaultValue(6000)
        .min(0).max(24000).sliderRange(0, 24000)
        .visible(fakeTimeEnabled::get)
        .build());

    private final Setting<Boolean> visualCreative = sgHud.add(new BoolSetting.Builder()
        .name("visual-creative-gamemode")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> creativeInventory = sgHud.add(new BoolSetting.Builder()
        .name("creative-inventory")
        .description("Allows opening Creative inventory UI locally when visual creative mode is on.")
        .defaultValue(false)
        .visible(visualCreative::get)
        .build());

    private final Setting<Boolean> hideItemDamage = sgHud.add(new BoolSetting.Builder()
        .name("hide-item-damage")
        .description("Hides durability bars and damaged state rendering locally.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> fakeBreatheEnabled = sgHud.add(new BoolSetting.Builder()
        .name("fake-breathe")
        .description("Always show max bubble bar (air) regardless of actual air level.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> fakeCountEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("fake-hotbar-counts")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxFakeHotbarCount = sgInventory.add(new IntSetting.Builder()
        .name("max-fake-hotbar-count")
        .description("Normal slider range is up to 128, direct input supports up to 2048.")
        .defaultValue(128)
        .min(1).max(2048).sliderRange(1, 128)
        .visible(fakeCountEnabled::get)
        .build());

    private final Setting<Integer> countEditStep = sgInventory.add(new IntSetting.Builder()
        .name("count-edit-step")
        .defaultValue(16)
        .min(1).max(512).sliderRange(1, 128)
        .visible(fakeCountEnabled::get)
        .build());

    private final Setting<Integer> hotbarCount1 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-1").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount2 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-2").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount3 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-3").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount4 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-4").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount5 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-5").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount6 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-6").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount7 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-7").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount8 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-8").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());
    private final Setting<Integer> hotbarCount9 = sgInventory.add(new IntSetting.Builder().name("hotbar-count-9").defaultValue(64).min(1).max(2048).sliderRange(1, 128).visible(fakeCountEnabled::get).build());

    private final Setting<Boolean> fakeHotbarItems = sgInventory.add(new BoolSetting.Builder()
        .name("fake-hotbar-items")
        .description("Spoofs only the rendered hotbar item icons client-side.")
        .defaultValue(false)
        .build());

    private final Setting<Item> hotbar1 = sgInventory.add(new ItemSetting.Builder().name("hotbar-1").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar2 = sgInventory.add(new ItemSetting.Builder().name("hotbar-2").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar3 = sgInventory.add(new ItemSetting.Builder().name("hotbar-3").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar4 = sgInventory.add(new ItemSetting.Builder().name("hotbar-4").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar5 = sgInventory.add(new ItemSetting.Builder().name("hotbar-5").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar6 = sgInventory.add(new ItemSetting.Builder().name("hotbar-6").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar7 = sgInventory.add(new ItemSetting.Builder().name("hotbar-7").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar8 = sgInventory.add(new ItemSetting.Builder().name("hotbar-8").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());
    private final Setting<Item> hotbar9 = sgInventory.add(new ItemSetting.Builder().name("hotbar-9").defaultValue(Items.AIR).visible(fakeHotbarItems::get).build());

    private final Setting<Boolean> fakeNbtEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("fake-nbt")
        .description("Overrides client-only item display name and lore.")
        .defaultValue(false)
        .build());

    private final Setting<String> fakeName = sgInventory.add(new StringSetting.Builder()
        .name("fake-name")
        .defaultValue("")
        .visible(fakeNbtEnabled::get)
        .build());

    private final Setting<String> fakeLore = sgInventory.add(new StringSetting.Builder()
        .name("fake-lore")
        .description("Use | to split lines.")
        .defaultValue("")
        .visible(fakeNbtEnabled::get)
        .build());

    private final Setting<Double> itemScale = sgVisuals.add(new DoubleSetting.Builder()
        .name("item-scale")
        .defaultValue(1.0)
        .min(0.1).max(4.0).sliderRange(0.1, 3.0)
        .build());

    private final Setting<Double> glintMultiplier = sgVisuals.add(new DoubleSetting.Builder()
        .name("glint-multiplier")
        .defaultValue(1.0)
        .min(0.1).max(25.0).sliderRange(0.1, 10.0)
        .build());

    private final Setting<WeatherMode> weatherMode = sgWeather.add(new EnumSetting.Builder<WeatherMode>()
        .name("weather")
        .defaultValue(WeatherMode.Server)
        .build());

    private final Setting<Boolean> fakeEquipmentEnabled = sgEquipment.add(new BoolSetting.Builder()
        .name("enabled")
        .defaultValue(false)
        .build());

    private final Setting<Item> fakeHelmet = sgEquipment.add(new ItemSetting.Builder().name("helmet").defaultValue(Items.NETHERITE_HELMET).visible(fakeEquipmentEnabled::get).build());
    private final Setting<Item> fakeChest = sgEquipment.add(new ItemSetting.Builder().name("chestplate").defaultValue(Items.NETHERITE_CHESTPLATE).visible(fakeEquipmentEnabled::get).build());
    private final Setting<Item> fakeLegs = sgEquipment.add(new ItemSetting.Builder().name("leggings").defaultValue(Items.NETHERITE_LEGGINGS).visible(fakeEquipmentEnabled::get).build());
    private final Setting<Item> fakeBoots = sgEquipment.add(new ItemSetting.Builder().name("boots").defaultValue(Items.NETHERITE_BOOTS).visible(fakeEquipmentEnabled::get).build());
    private final Setting<Item> fakeOffhand = sgEquipment.add(new ItemSetting.Builder().name("offhand").defaultValue(Items.TOTEM_OF_UNDYING).visible(fakeEquipmentEnabled::get).build());

    private final Setting<Boolean> chaosEnabled = sgChaos.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Randomly or smoothly mutates fake hearts/xp/hunger/xpbar/armor values.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> chaosDelay = sgChaos.add(new IntSetting.Builder()
        .name("delay")
        .defaultValue(5)
        .min(1).max(100).sliderRange(1, 40)
        .visible(chaosEnabled::get)
        .build());

    private final Setting<ChaosIntensity> chaosIntensity = sgChaos.add(new EnumSetting.Builder<ChaosIntensity>()
        .name("intensity")
        .defaultValue(ChaosIntensity.Smooth)
        .visible(chaosEnabled::get)
        .build());

    private final Setting<FakeGameMode> fakeGamemode = sgSpoof.add(new EnumSetting.Builder<FakeGameMode>()
        .name("fake-gamemode")
        .description("Spoof displayed gamemode client-side. Server = no spoof.")
        .defaultValue(FakeGameMode.Server)
        .build());

    private final Setting<Integer> fakePing = sgSpoof.add(new IntSetting.Builder()
        .name("fake-ping")
        .description("Spoof displayed latency. 0 = no spoof.")
        .defaultValue(0)
        .min(0).max(999).sliderRange(0, 999)
        .build());

    private final Setting<OverlayMode> fireOverlayMode = sgOverlay.add(new EnumSetting.Builder<OverlayMode>()
        .name("fire-overlay-mode")
        .description("Server preserves vanilla; ForceOn shows fire locally; ForceOff hides it.")
        .defaultValue(OverlayMode.Server)
        .build());

    private final Setting<Double> fireOverlayHeight = sgOverlay.add(new DoubleSetting.Builder()
        .name("fire-overlay-height")
        .description("Fire overlay height (0.0 = no overlay, 1.0 = default, higher = taller).")
        .defaultValue(1.0)
        .min(0.0).max(2.0).sliderRange(0.0, 1.5)
        .visible(() -> fireOverlayMode.get() == OverlayMode.ForceOn)
        .build());

    private final Setting<OverlayMode> pumpkinOverlayMode = sgOverlay.add(new EnumSetting.Builder<OverlayMode>()
        .name("pumpkin-overlay-mode")
        .description("Server preserves vanilla; ForceOff hides the pumpkin overlay.")
        .defaultValue(OverlayMode.Server)
        .build());

    private final Setting<Boolean> replacePumpkinOverlay = sgOverlay.add(new BoolSetting.Builder()
        .name("replace-pumpkin-overlay")
        .description("Replace the pumpkin blur with a transparent overlay instead of fully removing it.")
        .defaultValue(false)
        .visible(() -> pumpkinOverlayMode.get() == OverlayMode.ForceOff)
        .build());

    private final Setting<OverlayMode> waterOverlayMode = sgOverlay.add(new EnumSetting.Builder<OverlayMode>()
        .name("water-overlay-mode")
        .description("Server preserves vanilla; ForceOn/ForceOff control the local underwater overlay.")
        .defaultValue(OverlayMode.Server)
        .build());

    private final Setting<OverlayMode> skyOverlayMode = sgOverlay.add(new EnumSetting.Builder<OverlayMode>()
        .name("sky-mode")
        .description("Server preserves vanilla; ForceOn uses the configured local sky.")
        .defaultValue(OverlayMode.Server)
        .build());

    private final Setting<DimensionSky> sky = sgOverlay.add(new EnumSetting.Builder<DimensionSky>()
        .name("sky")
        .description("Sky appearance used locally in ForceOn mode.")
        .defaultValue(DimensionSky.End)
        .visible(() -> skyOverlayMode.get() == OverlayMode.ForceOn)
        .build());

    private final Setting<Boolean> spoofBurning = sgOverlay.add(new BoolSetting.Builder()
        .name("spoof-burning")
        .description("Render the local fire state without changing server fire or damage state.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> spoofPositionEnabled = sgSpoof.add(new BoolSetting.Builder()
        .name("spoof-position")
        .description("Offset only the local player model and camera. Server movement is unchanged.")
        .defaultValue(false)
        .build());

    private final Setting<Double> spoofPositionX = sgSpoof.add(new DoubleSetting.Builder().name("position-offset-x").defaultValue(0.0).min(-64.0).max(64.0).sliderRange(-16.0, 16.0).visible(spoofPositionEnabled::get).build());
    private final Setting<Double> spoofPositionY = sgSpoof.add(new DoubleSetting.Builder().name("position-offset-y").defaultValue(0.0).min(-64.0).max(64.0).sliderRange(-16.0, 16.0).visible(spoofPositionEnabled::get).build());
    private final Setting<Double> spoofPositionZ = sgSpoof.add(new DoubleSetting.Builder().name("position-offset-z").defaultValue(0.0).min(-64.0).max(64.0).sliderRange(-16.0, 16.0).visible(spoofPositionEnabled::get).build());

    private final Setting<Boolean> spoofBiomeEnabled = sgSpoof.add(new BoolSetting.Builder().name("spoof-biome").description("Spoof biome-dependent client visuals only.").defaultValue(false).build());
    private final Setting<String> spoofBiome = sgSpoof.add(new StringSetting.Builder().name("spoof-biome-id").description("Registry ID used for the local biome label/visual adapter.").defaultValue("minecraft:the_end").visible(spoofBiomeEnabled::get).build());

    private final Setting<Boolean> spoofTickRateEnabled = sgSpoof.add(new BoolSetting.Builder().name("spoof-client-tick-rate").description("Scale local client tick processing; server tick rate is unaffected.").defaultValue(false).build());
    private final Setting<Double> spoofTickRate = sgSpoof.add(new DoubleSetting.Builder().name("client-tick-rate").defaultValue(1.0).min(0.05).max(4.0).sliderRange(0.25, 2.0).visible(spoofTickRateEnabled::get).build());

    private final Setting<Boolean> customUseCooldownEnabled = sgSpoof.add(new BoolSetting.Builder().name("custom-use-cooldown").description("Use a local cooldown for selected items; server cooldowns are not bypassed.").defaultValue(false).build());
    private final Setting<List<Item>> customCooldownItems = sgSpoof.add(new meteordevelopment.meteorclient.settings.ItemListSetting.Builder().name("cooldown-items").defaultValue(List.of(Items.ENDER_PEARL)).visible(customUseCooldownEnabled::get).build());
    private final Setting<Integer> customUseCooldownTicks = sgSpoof.add(new IntSetting.Builder().name("cooldown-ticks").defaultValue(20).min(0).max(200).sliderRange(0, 100).visible(customUseCooldownEnabled::get).build());

    private final Setting<CrosshairStyle> crosshairStyle = sgCrosshair.add(new EnumSetting.Builder<CrosshairStyle>()
        .name("crosshair-style")
        .description("Override the crosshair rendering style.")
        .defaultValue(CrosshairStyle.Default)
        .build());

    private final Setting<Double> crosshairScale = sgCrosshair.add(new DoubleSetting.Builder()
        .name("crosshair-scale")
        .description("Scale multiplier for crosshair rendering.")
        .defaultValue(1.0)
        .min(0.1).max(5.0).sliderRange(0.1, 3.0)
        .visible(() -> crosshairStyle.get() != CrosshairStyle.Default)
        .build());

    private final Setting<Integer> crosshairThickness = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-thickness")
        .description("Line thickness for cross/none styles.")
        .defaultValue(1)
        .min(1).max(6)
        .visible(() -> crosshairStyle.get() == CrosshairStyle.Cross
            || crosshairStyle.get() == CrosshairStyle.Thin)
        .build());

    private final Setting<Boolean> crosshairDebugHide = sgCrosshair.add(new BoolSetting.Builder()
        .name("crosshair-debug-hide")
        .description("Also hide crosshair in F3 debug screen.")
        .defaultValue(false)
        .visible(() -> crosshairStyle.get() == CrosshairStyle.None)
        .build());

    private final Setting<Boolean> fogOverrideEnabled = sgFog.add(new BoolSetting.Builder()
        .name("fog-override-enabled")
        .description("Override per-dimension fog distances.")
        .defaultValue(false)
        .build());

    private final Setting<Double> fogOverworldStart = sgFog.add(new DoubleSetting.Builder()
        .name("fog-overworld-start")
        .description("Fog start distance in overworld.")
        .defaultValue(0.0)
        .min(0.0).max(65536.0).sliderRange(0.0, 2048.0)
        .visible(fogOverrideEnabled::get)
        .build());

    private final Setting<Double> fogOverworldEnd = sgFog.add(new DoubleSetting.Builder()
        .name("fog-overworld-end")
        .description("Fog end distance in overworld.")
        .defaultValue(65536.0)
        .min(0.0).max(65536.0).sliderRange(0.0, 65536.0)
        .visible(fogOverrideEnabled::get)
        .build());

    private final Setting<Double> fogNetherStart = sgFog.add(new DoubleSetting.Builder()
        .name("fog-nether-start")
        .description("Fog start distance in the Nether.")
        .defaultValue(0.0)
        .min(0.0).max(65536.0).sliderRange(0.0, 2048.0)
        .visible(fogOverrideEnabled::get)
        .build());

    private final Setting<Double> fogNetherEnd = sgFog.add(new DoubleSetting.Builder()
        .name("fog-nether-end")
        .description("Fog end distance in the Nether.")
        .defaultValue(65536.0)
        .min(0.0).max(65536.0).sliderRange(0.0, 65536.0)
        .visible(fogOverrideEnabled::get)
        .build());

    private final Setting<Double> fogEndStart = sgFog.add(new DoubleSetting.Builder()
        .name("fog-the-end-start")
        .description("Fog start distance in The End.")
        .defaultValue(0.0)
        .min(0.0).max(65536.0).sliderRange(0.0, 2048.0)
        .visible(fogOverrideEnabled::get)
        .build());

    private final Setting<Double> fogEndEnd = sgFog.add(new DoubleSetting.Builder()
        .name("fog-the-end-end")
        .description("Fog end distance in The End.")
        .defaultValue(65536.0)
        .min(0.0).max(65536.0).sliderRange(0.0, 65536.0)
        .visible(fogOverrideEnabled::get)
        .build());

    private final Setting<Boolean> bossbarOverrideEnabled = sgBossbar.add(new BoolSetting.Builder()
        .name("bossbar-override-enabled")
        .description("Override bossbar text and percent display.")
        .defaultValue(false)
        .build());

    private final Setting<String> bossbarText = sgBossbar.add(new StringSetting.Builder()
        .name("bossbar-text")
        .description("Text to replace all bossbar titles with. Empty = no text change.")
        .defaultValue("")
        .visible(bossbarOverrideEnabled::get)
        .build());

    private final Setting<Double> bossbarPercent = sgBossbar.add(new DoubleSetting.Builder()
        .name("bossbar-percent")
        .description("Override bossbar percentage. -1 = no override.")
        .defaultValue(-1.0)
        .min(-1.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(bossbarOverrideEnabled::get)
        .build());

    private final Setting<Boolean> bossbarHideAll = sgBossbar.add(new BoolSetting.Builder()
        .name("bossbar-hide-all")
        .description("Hide all boss bars completely.")
        .defaultValue(false)
        .visible(bossbarOverrideEnabled::get)
        .build());

    private final Setting<Boolean> bossbarColorOverride = sgBossbar.add(new BoolSetting.Builder()
        .name("bossbar-color-override")
        .description("Override boss bar color.")
        .defaultValue(false)
        .visible(bossbarOverrideEnabled::get)
        .build());

    private final Setting<BossBar.Color> bossbarColor = sgBossbar.add(new EnumSetting.Builder<BossBar.Color>()
        .name("bossbar-color")
        .description("Color to override boss bars with.")
        .defaultValue(BossBar.Color.RED)
        .visible(() -> bossbarOverrideEnabled.get() && bossbarColorOverride.get())
        .build());

    private final Setting<Boolean> fakeDeathScreenEnabled = sgFakeDeath.add(new BoolSetting.Builder()
        .name("fake-death-screen")
        .description("Show a fake death screen overlay without actually dying.")
        .defaultValue(false)
        .build());

    private final Setting<String> fakeDeathMessage = sgFakeDeath.add(new StringSetting.Builder()
        .name("fake-death-message")
        .description("Custom death message. Empty uses default 'You died!'.")
        .defaultValue("")
        .visible(fakeDeathScreenEnabled::get)
        .build());

    private final Setting<Integer> fakeDeathScreenOpacity = sgFakeDeath.add(new IntSetting.Builder()
        .name("fake-death-opacity")
        .description("Background opacity of the fake death screen (0-255).")
        .defaultValue(100)
        .min(0).max(255).sliderRange(0, 255)
        .visible(fakeDeathScreenEnabled::get)
        .build());

    private final Setting<Double> fakeDeathScreenDuration = sgFakeDeath.add(new DoubleSetting.Builder()
        .name("fake-death-duration")
        .description("Seconds to show fake death screen before auto-dismissing (0 = forever).")
        .defaultValue(0.0)
        .min(0.0).max(30.0).sliderRange(0.0, 10.0)
        .visible(fakeDeathScreenEnabled::get)
        .build());

    private final Random random = new Random();

    private int backupXpLevel;
    private float backupXpProgress;
    private int latestRealXpLevel;
    private float latestRealXpProgress;
    private volatile boolean xpSnapshotValid;
    private volatile boolean xpOverridden;
    private final Object xpLock = new Object();

    private int chaosTimer;
    private float smoothT;
    private float chaosHealth;
    private int chaosHunger;
    private float chaosSat;
    private int chaosArmorVal;
    private int chaosXpLvl;
    private float chaosXpProg;
    private int chaosAbsorption;

    private GameMode realGameMode;
    private int realLatency = -1;

    private boolean showingFakeDeath = false;
    private boolean fakeDeathSettingWasEnabled = false;
    private long fakeDeathStartTimeMs = 0;
    private float fakeDeathFadeAlpha = 0.0f;

    private static Field resolveField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static final Field currentGameModeField = resolveField(
        net.minecraft.client.network.ClientPlayerInteractionManager.class,
        "currentGameMode", "gameMode", "field_2606"
    );

    private static final Field latencyField = resolveField(
        PlayerListEntry.class,
        "latency", "ping", "field_3725"
    );

    private static final Field absorptionField = resolveField(
        net.minecraft.entity.player.PlayerEntity.class,
        "absorptionAmount", "field_13189"
    );

    public ClientSideThings() {
        super(Orbiter.CATEGORY, "client-side-things",
            "Local visual spoof system for HUD, inventory, weather, equipment, overlays, fog, crosshair, and bossbar.");
    }

    @Override
    public void onActivate() {
        ClientSpoofState.clearAll();
        chaosTimer = 0;
        smoothT = 0.0f;
        xpSnapshotValid = false;
        xpOverridden = false;
        realGameMode = null;
        realLatency = -1;
        showingFakeDeath = false;
        fakeDeathSettingWasEnabled = false;
        fakeDeathFadeAlpha = 0.0f;

        if (mc.player != null) {
            synchronized (xpLock) {
                latestRealXpLevel = mc.player.experienceLevel;
                latestRealXpProgress = mc.player.experienceProgress;
                backupXpLevel = latestRealXpLevel;
                backupXpProgress = latestRealXpProgress;
                xpSnapshotValid = true;
            }

            if (mc.interactionManager != null && currentGameModeField != null) {
                try {
                    realGameMode = (GameMode) currentGameModeField.get(mc.interactionManager);
                } catch (IllegalAccessException ignored) {
                    realGameMode = mc.interactionManager.getCurrentGameMode();
                }
            }

            if (mc.player.networkHandler != null) {
                PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
                if (entry != null) {
                    realLatency = entry.getLatency();
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        restoreXp();
        restoreSpoof();
        ClientSpoofState.clearAll();
        showingFakeDeath = false;
        fakeDeathFadeAlpha = 0.0f;
    }

    private void restoreSpoof() {

        if (mc.interactionManager != null && realGameMode != null && currentGameModeField != null) {
            try {
                currentGameModeField.set(mc.interactionManager, realGameMode);
            } catch (IllegalAccessException ignored) {}
        }

        if (mc.player != null && mc.player.networkHandler != null && realLatency >= 0 && latencyField != null) {
            restorePlayerLatency();
        }
    }

    private void restorePlayerLatency() {
        if (mc.player == null || mc.player.networkHandler == null || realLatency < 0) return;

        PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
        if (entry == null || latencyField == null) return;

        try {
            latencyField.setInt(entry, realLatency);
        } catch (IllegalAccessException ignored) {}
    }

    private void restoreXp() {
        if (mc.player == null) return;

        synchronized (xpLock) {
            if (!xpSnapshotValid) {
                xpOverridden = false;
                return;
            }

            int level = latestRealXpLevel;
            float progress = latestRealXpProgress;

            if (level <= 0 && progress <= 0.0f && (backupXpLevel > 0 || backupXpProgress > 0.0f)) {
                level = backupXpLevel;
                progress = backupXpProgress;
            }

            mc.player.experienceLevel = level;
            mc.player.experienceProgress = progress;
            xpOverridden = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (chaosEnabled.get()) {
            updateChaos();
        }

        if (shouldSpoofXp()) {
            synchronized (xpLock) {
                if (!xpSnapshotValid) {
                    backupXpLevel = mc.player.experienceLevel;
                    backupXpProgress = mc.player.experienceProgress;
                    xpSnapshotValid = true;
                }
                mc.player.experienceLevel = getEffectiveXpLevel();
                mc.player.experienceProgress = getEffectiveXpProgress();
                xpOverridden = true;
            }
        } else if (xpOverridden) {
            restoreXp();
        } else {
            synchronized (xpLock) {
                latestRealXpLevel = mc.player.experienceLevel;
                latestRealXpProgress = mc.player.experienceProgress;
                backupXpLevel = latestRealXpLevel;
                backupXpProgress = latestRealXpProgress;
                xpSnapshotValid = true;
            }
        }

        if (mc.interactionManager != null && fakeGamemode.get() != FakeGameMode.Server && currentGameModeField != null) {
            GameMode target = fakeGamemode.get().mode;
            if (target != null) {
                try {
                    currentGameModeField.set(mc.interactionManager, target);
                } catch (IllegalAccessException ignored) {}
            }
        }

        if (mc.player != null && mc.player.networkHandler != null && fakePing.get() > 0 && latencyField != null) {
            PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                try {
                    latencyField.setInt(entry, fakePing.get());
                } catch (IllegalAccessException ignored) {}
            }
        }

        if (fakeBreatheEnabled.get() && mc.player != null) {
            mc.player.setAir(mc.player.getMaxAir());
        }

        updateFakeDeathScreen();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ExperienceBarUpdateS2CPacket packet) {
            synchronized (xpLock) {
                latestRealXpLevel = packet.getExperienceLevel();
                latestRealXpProgress = packet.getBarProgress();
                if (!xpOverridden) {
                    backupXpLevel = latestRealXpLevel;
                    backupXpProgress = latestRealXpProgress;
                    xpSnapshotValid = true;
                }
            }
            return;
        }

        if (weatherMode.get() == WeatherMode.Server) return;
        if (!(event.packet instanceof GameStateChangeS2CPacket packet)) return;

        GameStateChangeS2CPacket.Reason reason = packet.getReason();
        if (reason == GameStateChangeS2CPacket.RAIN_STARTED
            || reason == GameStateChangeS2CPacket.RAIN_STOPPED
            || reason == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED
            || reason == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED) {
            event.cancel();
        }
    }

    private void updateChaos() {
        chaosTimer--;
        if (chaosTimer > 0) return;

        chaosTimer = Math.max(1, chaosDelay.get());

        if (chaosIntensity.get() == ChaosIntensity.Random) {
            chaosHealth = random.nextFloat() * 2048.0f;
            chaosHunger = random.nextInt(21);
            chaosSat = random.nextFloat() * 20.0f;
            chaosArmorVal = random.nextInt(41);
            chaosXpLvl = random.nextInt(5001);
            chaosXpProg = random.nextFloat();
            chaosAbsorption = random.nextInt(2049);
            return;
        }

        smoothT += 0.35f;
        chaosHealth = (float) (1024.0 + Math.sin(smoothT) * 1024.0);
        chaosHunger = MathHelper.clamp((int) Math.round(10 + Math.sin(smoothT * 1.3f) * 10), 0, 20);
        chaosSat = MathHelper.clamp((float) (10.0 + Math.cos(smoothT * 1.1f) * 10.0), 0.0f, 20.0f);
        chaosArmorVal = MathHelper.clamp((int) Math.round(20 + Math.sin(smoothT * 0.9f) * 20), 0, 40);
        chaosXpLvl = MathHelper.clamp((int) Math.round(2500 + Math.sin(smoothT * 0.6f) * 2500), 0, 5000);
        chaosXpProg = MathHelper.clamp((float) ((Math.sin(smoothT * 2.2f) + 1.0) / 2.0), 0.0f, 1.0f);
        chaosAbsorption = MathHelper.clamp((int) Math.round(1024 + Math.sin(smoothT * 0.7f) * 1024), 0, 2048);
    }

    private void updateFakeDeathScreen() {
        if (!fakeDeathScreenEnabled.get()) {
            fakeDeathSettingWasEnabled = false;
            if (showingFakeDeath) {
                showingFakeDeath = false;
                fakeDeathFadeAlpha = 0.0f;
            }
            return;
        }

        if (!fakeDeathSettingWasEnabled) {
            fakeDeathSettingWasEnabled = true;
            showingFakeDeath = true;
            fakeDeathStartTimeMs = System.currentTimeMillis();
            fakeDeathFadeAlpha = 0.0f;
        } else if (showingFakeDeath) {

            fakeDeathFadeAlpha = Math.min(1.0f, fakeDeathFadeAlpha + 0.02f);

            if (fakeDeathScreenDuration.get() > 0.0) {
                long elapsed = System.currentTimeMillis() - fakeDeathStartTimeMs;
                double durationMs = fakeDeathScreenDuration.get() * 1000.0;
                if (elapsed >= durationMs) {
                    showingFakeDeath = false;
                    fakeDeathFadeAlpha = 0.0f;
                }
            }
        }
    }

    public void triggerFakeDeath() {
        showingFakeDeath = true;
        fakeDeathStartTimeMs = System.currentTimeMillis();
        fakeDeathFadeAlpha = 0.0f;
    }

    public void dismissFakeDeath() {
        showingFakeDeath = false;
        fakeDeathFadeAlpha = 0.0f;
    }

    public boolean isShowingFakeDeath() {
        return fakeDeathScreenEnabled.get() && showingFakeDeath;
    }

    public float getFakeDeathAlpha() {
        return fakeDeathFadeAlpha;
    }

    public String getFakeDeathMessageText() {
        String msg = fakeDeathMessage.get();
        return (msg == null || msg.isBlank()) ? "You died!" : msg;
    }

    public int getFakeDeathBgOpacity() {
        return fakeDeathScreenOpacity.get();
    }

    private boolean shouldSpoofXp() {
        return fakeXpEnabled.get() || chaosEnabled.get();
    }

    public boolean shouldFakeHealth() {
        return fakeHealthEnabled.get() || chaosEnabled.get();
    }

    public float getFakeHealth() {
        if (chaosEnabled.get()) return chaosHealth;
        return fakeHealth.get().floatValue();
    }

    public boolean shouldFakeArmor() {
        return fakeArmorEnabled.get() || chaosEnabled.get();
    }

    public int getFakeArmor() {
        if (chaosEnabled.get()) return chaosArmorVal;
        return fakeArmor.get();
    }

    public boolean shouldFakeAbsorption() {
        return fakeAbsorptionEnabled.get() || chaosEnabled.get();
    }

    public float getFakeAbsorption() {
        if (chaosEnabled.get()) return (float) chaosAbsorption;
        return (float) fakeAbsorption.get();
    }

    public boolean shouldFakeHunger() {
        return fakeHungerEnabled.get() || chaosEnabled.get();
    }

    public int getFakeHunger() {
        if (chaosEnabled.get()) return chaosHunger;
        return fakeHunger.get();
    }

    public float getFakeSaturation() {
        if (chaosEnabled.get()) return chaosSat;
        return fakeSaturation.get().floatValue();
    }

    public boolean shouldFakeBreathe() {
        return fakeBreatheEnabled.get();
    }

    public int getEffectiveXpLevel() {
        if (chaosEnabled.get()) return chaosXpLvl;
        return fakeXpLevel.get();
    }

    public float getEffectiveXpProgress() {
        if (chaosEnabled.get()) return chaosXpProg;
        return fakeXpProgress.get().floatValue();
    }

    public boolean shouldFakeTime() {
        return fakeTimeEnabled.get();
    }

    public long getFakeTimeOfDay() {
        return fakeTime.get();
    }

    public boolean visualCreativeEnabled() {
        return visualCreative.get();
    }

    public boolean creativeInventoryEnabled() {
        return visualCreative.get() && creativeInventory.get();
    }

    public boolean hideItemDamageEnabled() {
        return hideItemDamage.get();
    }

    public WeatherMode getWeatherMode() {
        return weatherMode.get();
    }

    public float getForcedRainGradient() {
        if (weatherMode.get() == WeatherMode.Clear) return 0.0f;
        if (weatherMode.get() == WeatherMode.Server) return -1.0f;
        return 1.0f;
    }

    public float getForcedThunderGradient() {
        if (weatherMode.get() == WeatherMode.Server) return -1.0f;
        if (weatherMode.get() == WeatherMode.Clear) return 0.0f;
        return weatherMode.get() == WeatherMode.Rain ? 0.35f : 0.0f;
    }

    public double getItemScale() {
        return itemScale.get();
    }

    public double getGlintMultiplier() {
        return glintMultiplier.get();
    }

    public boolean shouldOverrideFireOverlay() {
        return fireOverlayMode.get() != OverlayMode.Server;
    }

    public boolean shouldForceFireOverlay() { return fireOverlayMode.get() == OverlayMode.ForceOn; }
    public boolean shouldForceOffFireOverlay() { return fireOverlayMode.get() == OverlayMode.ForceOff; }

    public double getFireOverlayHeight() {
        return fireOverlayHeight.get();
    }

    public boolean shouldDisablePumpkinOverlay() {
        return pumpkinOverlayMode.get() == OverlayMode.ForceOff;
    }

    public boolean shouldReplacePumpkinOverlay() {
        return pumpkinOverlayMode.get() == OverlayMode.ForceOff && replacePumpkinOverlay.get();
    }

    public boolean shouldDisableWaterOverlay() {
        return waterOverlayMode.get() == OverlayMode.ForceOff;
    }

    public boolean shouldForceWaterOverlay() { return waterOverlayMode.get() == OverlayMode.ForceOn; }
    public boolean shouldForceOffWaterOverlay() { return waterOverlayMode.get() == OverlayMode.ForceOff; }

    public boolean shouldSpoofSky() { return skyOverlayMode.get() == OverlayMode.ForceOn; }
    public DimensionSky getSkyMode() { return sky.get(); }
    public boolean shouldSpoofBurning() { return spoofBurning.get(); }
    public boolean shouldSpoofPosition() { return spoofPositionEnabled.get(); }
    public double getPositionOffsetX() { return spoofPositionX.get(); }
    public double getPositionOffsetY() { return spoofPositionY.get(); }
    public double getPositionOffsetZ() { return spoofPositionZ.get(); }
    public boolean shouldSpoofBiome() { return spoofBiomeEnabled.get(); }
    public String getSpoofBiomeId() { return spoofBiome.get(); }
    public boolean shouldSpoofClientTickRate() { return spoofTickRateEnabled.get(); }
    public float getSpoofClientTickRate() { return spoofTickRate.get().floatValue(); }
    public boolean shouldOverrideUseCooldown(Item item) { return customUseCooldownEnabled.get() && customCooldownItems.get().contains(item); }
    public int getCustomUseCooldownTicks() { return MathHelper.clamp(customUseCooldownTicks.get(), 0, 200); }

    public CrosshairStyle getCrosshairStyle() {
        return crosshairStyle.get();
    }

    public double getCrosshairScale() {
        return crosshairScale.get();
    }

    public int getCrosshairThickness() {
        return crosshairThickness.get();
    }

    public boolean shouldHideCrosshairDebug() {
        return crosshairStyle.get() == CrosshairStyle.None && crosshairDebugHide.get();
    }

    public boolean shouldOverrideFog() {
        return fogOverrideEnabled.get();
    }

    public double getFogStart() {
        if (mc.player == null || mc.world == null) return 0.0;
        var dimKey = mc.world.getRegistryKey();
        if (dimKey == net.minecraft.world.World.OVERWORLD) return fogOverworldStart.get();
        if (dimKey == net.minecraft.world.World.NETHER) return fogNetherStart.get();
        if (dimKey == net.minecraft.world.World.END) return fogEndStart.get();
        return fogOverworldStart.get();
    }

    public double getFogEnd() {
        if (mc.player == null || mc.world == null) return 65536.0;
        var dimKey = mc.world.getRegistryKey();
        if (dimKey == net.minecraft.world.World.OVERWORLD) return fogOverworldEnd.get();
        if (dimKey == net.minecraft.world.World.NETHER) return fogNetherEnd.get();
        if (dimKey == net.minecraft.world.World.END) return fogEndEnd.get();
        return fogOverworldEnd.get();
    }

    public boolean shouldOverrideBossbar() {
        return bossbarOverrideEnabled.get();
    }

    public boolean shouldHideAllBossbars() {
        return bossbarOverrideEnabled.get() && bossbarHideAll.get();
    }

    public String getBossbarTextOverride() {
        String text = bossbarText.get();
        return (text == null || text.isBlank()) ? null : text;
    }

    public boolean shouldOverrideBossbarPercent() {
        return bossbarOverrideEnabled.get() && bossbarPercent.get() >= 0.0;
    }

    public float getBossbarPercentOverride() {
        double pct = bossbarPercent.get();
        return pct < 0.0 ? -1.0f : (float) MathHelper.clamp(pct, 0.0, 1.0);
    }

    public boolean shouldOverrideBossbarColor() {
        return bossbarOverrideEnabled.get() && bossbarColorOverride.get();
    }

    public BossBar.Color getBossbarColorOverride() {
        return bossbarColor.get();
    }

    public boolean isFakeCountEnabled() {
        return fakeCountEnabled.get();
    }

    public int getCountEditStep() {
        return countEditStep.get();
    }

    public int getMaxFakeHotbarCount() {
        return MathHelper.clamp(maxFakeHotbarCount.get(), 1, 2048);
    }

    public boolean isFakeHotbarItemsEnabled() {
        return fakeHotbarItems.get();
    }

    public boolean hasConfiguredFakeHotbarItem(int hotbarSlot) {
        Item item = getConfiguredHotbarItem(hotbarSlot);
        return item != null && item != Items.AIR;
    }

    public int getHotbarSpoofCount(int hotbarSlot, int fallbackCount) {
        if (!fakeCountEnabled.get()) return fallbackCount;

        int configured = switch (hotbarSlot) {
            case 0 -> hotbarCount1.get();
            case 1 -> hotbarCount2.get();
            case 2 -> hotbarCount3.get();
            case 3 -> hotbarCount4.get();
            case 4 -> hotbarCount5.get();
            case 5 -> hotbarCount6.get();
            case 6 -> hotbarCount7.get();
            case 7 -> hotbarCount8.get();
            case 8 -> hotbarCount9.get();
            default -> fallbackCount;
        };

        return MathHelper.clamp(configured, 1, getMaxFakeHotbarCount());
    }

    public int adjustHotbarCount(int hotbarSlot, int delta) {
        int current = getHotbarSpoofCount(hotbarSlot, 64);
        int value = MathHelper.clamp(current + delta, 1, getMaxFakeHotbarCount());
        setHotbarCount(hotbarSlot, value);
        return value;
    }

    public void setHotbarCount(int hotbarSlot, int value) {
        int clamped = MathHelper.clamp(value, 1, getMaxFakeHotbarCount());
        switch (hotbarSlot) {
            case 0 -> hotbarCount1.set(clamped);
            case 1 -> hotbarCount2.set(clamped);
            case 2 -> hotbarCount3.set(clamped);
            case 3 -> hotbarCount4.set(clamped);
            case 4 -> hotbarCount5.set(clamped);
            case 5 -> hotbarCount6.set(clamped);
            case 6 -> hotbarCount7.set(clamped);
            case 7 -> hotbarCount8.set(clamped);
            case 8 -> hotbarCount9.set(clamped);
        }
    }

    public ItemStack getFakeHotbarStack(int hotbarSlot, ItemStack original) {
        if (!fakeHotbarItems.get() && !fakeCountEnabled.get()) return original;

        Item item = fakeHotbarItems.get() ? getConfiguredHotbarItem(hotbarSlot) : Items.AIR;

        ItemStack stack;
        if (item != null && item != Items.AIR) {
            stack = item.getDefaultStack();
        } else if (original != null && !original.isEmpty()) {
            stack = original.copy();
        } else {
            stack = ItemStack.EMPTY;
        }

        if (stack.isEmpty()) return stack;

        int fallback = original == null || original.isEmpty() ? 1 : original.getCount();
        int wanted = getHotbarSpoofCount(hotbarSlot, fallback);
        int allowed = stack.getMaxCount() <= 1 ? 1 : getMaxFakeHotbarCount();
        stack.setCount(MathHelper.clamp(wanted, 1, allowed));
        return stack;
    }

    private Item getConfiguredHotbarItem(int hotbarSlot) {
        return switch (hotbarSlot) {
            case 0 -> hotbar1.get();
            case 1 -> hotbar2.get();
            case 2 -> hotbar3.get();
            case 3 -> hotbar4.get();
            case 4 -> hotbar5.get();
            case 5 -> hotbar6.get();
            case 6 -> hotbar7.get();
            case 7 -> hotbar8.get();
            case 8 -> hotbar9.get();
            default -> Items.AIR;
        };
    }

    public Text getGlobalFakeName(ItemStack stack) {
        if (!fakeNbtEnabled.get() || stack == null || stack.isEmpty()) return null;
        String name = fakeName.get();
        if (name == null || name.isBlank()) return null;

        return orbiter.util.LegacyTextFormatter.parse(name);
    }

    public List<Text> getGlobalFakeLore(ItemStack stack) {
        if (!fakeNbtEnabled.get() || stack == null || stack.isEmpty()) return List.of();
        String lore = fakeLore.get();
        if (lore == null || lore.isBlank()) return List.of();

        List<Text> lines = new ArrayList<>();
        for (String line : lore.split("\\|")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(orbiter.util.LegacyTextFormatter.parse(trimmed));
        }

        return lines;
    }

    public ItemStack getFakeEquipmentStack(EquipmentSlot slot, ItemStack original) {
        if (!fakeEquipmentEnabled.get()) return original;

        Item item = switch (slot) {
            case HEAD -> fakeHelmet.get();
            case CHEST -> fakeChest.get();
            case LEGS -> fakeLegs.get();
            case FEET -> fakeBoots.get();
            case OFFHAND -> fakeOffhand.get();
            default -> null;
        };

        if (item == null || item == Items.AIR) return original;
        return item.getDefaultStack();
    }

    public FakeGameMode getFakeGamemodeSetting() {
        return fakeGamemode.get();
    }

    public int getFakePingSetting() {
        return fakePing.get();
    }

    public boolean isFireOverlayOverrideActive() {
        return fireOverlayMode.get() != OverlayMode.Server;
    }

    public boolean isPumpkinOverlayDisabled() {
        return pumpkinOverlayMode.get() == OverlayMode.ForceOff;
    }

    public boolean isWaterOverlayDisabled() {
        return waterOverlayMode.get() == OverlayMode.ForceOff;
    }

    public boolean isFogOverrideActive() {
        return fogOverrideEnabled.get();
    }

    public boolean isBossbarOverrideActive() {
        return bossbarOverrideEnabled.get();
    }

    public boolean isCrosshairOverrideActive() {
        return crosshairStyle.get() != CrosshairStyle.Default;
    }

    @Override
    public String getInfoString() {
        List<String> parts = new ArrayList<>();
        if (fakeHealthEnabled.get() || chaosEnabled.get()) parts.add("HP");
        if (fakeAbsorptionEnabled.get()) parts.add("Abs");
        if (fakeHungerEnabled.get() || chaosEnabled.get()) parts.add("Hunger");
        if (fakeXpEnabled.get() || chaosEnabled.get()) parts.add("XP");
        if (shouldOverrideFireOverlay()) parts.add("Fire");
        if (shouldDisablePumpkinOverlay()) parts.add("Pumpkin");
        if (shouldDisableWaterOverlay()) parts.add("Water");
        if (shouldOverrideFog()) parts.add("Fog");
        if (isCrosshairOverrideActive()) parts.add("Cross");
        if (isBossbarOverrideActive()) parts.add("Boss");
        if (isShowingFakeDeath()) parts.add("Death");
        if (shouldFakeBreathe()) parts.add("Breathe");
        return parts.isEmpty() ? null : String.join(",", parts);
    }
}
