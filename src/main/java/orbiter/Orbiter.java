package orbiter;

import orbiter.commands.AutoShopCommand;
import orbiter.commands.EnchCrackCommand;
import orbiter.commands.EnchCrackedAliasCommand;
import orbiter.commands.EnccAliasCommand;
import orbiter.commands.EscapeCommand;
import orbiter.commands.ISellWandCommand;
import orbiter.commands.ExportModuleListCommand;
import orbiter.commands.FixDeathCommand;
import orbiter.commands.GivePresetCommand;
import orbiter.commands.GivePresetItemsCommand;
import orbiter.commands.ItemCrashCommand;
import orbiter.commands.ItemStealerCommand;
import orbiter.commands.MultiCommand;
import orbiter.commands.NbtCommand;
import orbiter.commands.TNTRainCommand;
import orbiter.commands.TransferCommand;
import orbiter.commands.VerifyProtectCommand;
import orbiter.commands.PeakPluginScannerCommand;
import orbiter.commands.UUIDBanCommand;
import orbiter.commands.WorldEditCommand;
import orbiter.commands.HideKeybindCommand;
import orbiter.commands.SetPrefixCommand;
import orbiter.hud.CustomTextHud;
import orbiter.hud.NearestPlayerHud;
import orbiter.hud.RenderDistanceHud;
import orbiter.hud.ServerBrandHud;
import orbiter.hud.ServerDifficultyHud;
import orbiter.hud.ServerIpHud;
import orbiter.hud.ServerPluginsHud;
import orbiter.hud.ServerProtocolHud;
import orbiter.hud.ServerTimeHud;
import orbiter.hud.ServerTpsHud;
import orbiter.hud.ServerPlayersHud;
import orbiter.hud.ServerRealIpHud;
import orbiter.hud.ServerRealVersionHud;
import orbiter.hud.ServerVersionNoteHud;
import orbiter.hud.ServerVersionHud;
import orbiter.hud.WeaponCooldownHud;
import orbiter.modules.render.BlockSpoof;
import orbiter.modules.render.BossbarFlash;
import orbiter.modules.render.Camera360;
import orbiter.modules.render.FireworkShow;
import orbiter.modules.render.ParticleSpam;
import orbiter.modules.render.BlockSpam;
import orbiter.modules.render.ParticleControl;
import orbiter.modules.render.PlaySoundSpam;
import orbiter.modules.render.ViewBlocks;
import orbiter.modules.combat.BowAssist;
import orbiter.modules.combat.CrossbowAssist;
import orbiter.modules.combat.ShieldAssist;
import orbiter.modules.combat.SpearAssist;
import orbiter.modules.combat.TridentAssist;
import orbiter.modules.misc.ItemInfo;
import orbiter.modules.misc.ItemStealer;
import orbiter.modules.misc.ExploitPreventer;
import orbiter.modules.misc.ServerProtect;
import orbiter.modules.misc.PeakPluginScanner;
import orbiter.modules.misc.SpamPlus;
import orbiter.modules.misc.EnchCracker;
import orbiter.modules.world.WorldDownloader;
import orbiter.modules.world.ControlPlayer;
import orbiter.util.ConfigModifier;
import com.mojang.logging.LogUtils;
import orbiter.modules.movement.SlimeJump;
import orbiter.modules.movement.JumpA;
import orbiter.modules.misc.InfiniReach;
import orbiter.modules.misc.ISellWand;
import orbiter.modules.world.UUIDBan;
import orbiter.modules.*;
import orbiter.modules.world.DeathOverride;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public class Orbiter extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    private static ItemStack safeIcon(Item item) {
        try {
            return item.getDefaultInstance();
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    public static final Category CATEGORY = new Category("Orbiter Survival", () -> safeIcon(Items.DIAMOND_BLOCK));
    public static final Category CATEGORY_VANILLA = new Category("Orbiter Vanilla", () -> safeIcon(Items.NETHERITE_SWORD));
    public static final Category CATEGORY_OP = new Category("Orbiter Creative/OP", () -> safeIcon(Items.DIAMOND_BLOCK));
    public static final Category CATEGORY_STUPID = new Category("Orbiter Stupid", () -> safeIcon(Items.SLIME_BLOCK));
    public static final HudGroup HUD_GROUP = new HudGroup("Orbiter");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Orbiter 1.0.2");

        ConfigModifier.get();

        Modules modules = Modules.get();
        if (modules == null) {
            LOG.warn("Modules system not available, skipping module registration");
            return;
        }

        modules.add(new AutoCraftPlus());
        modules.add(new AutoShop());
        modules.add(new AntiStaff());
        modules.add(new AntiKnockback());
        modules.add(new AntiPush());
        modules.add(new PingSpoof());
        modules.add(new PrecisionShot());
        modules.add(new BowAssist());
        modules.add(new CrossbowAssist());
        modules.add(new ShieldAssist());
        modules.add(new TridentAssist());
        modules.add(new AimAssistPlus());
        modules.add(new MaceAssist());
        modules.add(new SpearAssist());
        modules.add(new OutOfReach());
        modules.add(new NoFriendHit());
        modules.add(new MessageFormatter());
        modules.add(new ViewBlocks());
        modules.add(new BlockSpoof());
        modules.add(new Camera360());
        modules.add(new ClientSideThings());
        modules.add(new ClientSideMine());
        modules.add(new ForceInvisibility());
        modules.add(new SlimeJump());
        modules.add(new JumpA());
        modules.add(new InfiniReach());
        modules.add(new Actions());
        modules.add(new AutoFind());
        modules.add(new AutoClutch());
        modules.add(new AutoFarming());
        modules.add(new Restock());
        modules.add(new LeaveMessage());
        modules.add(new SpamPlus());
        modules.add(new EnchCracker());
        modules.add(new ItemStealer());
        modules.add(new ItemInfo());
        modules.add(new ServerProtect());
        modules.add(new PeakPluginScanner());
        modules.add(new ExploitPreventer());
        modules.add(new ISellWand());

        modules.add(new ItemGenerator());
        modules.add(new CommandBlockPlacer());
        modules.add(new ItemCreator());
        modules.add(new WorldEraser());
        modules.add(new NBTLecternCrasher());
        modules.add(new WorldEditModule());
        modules.add(new ParticleControl());
        modules.add(new BlockSpam());
        modules.add(new ControlPlayer());
        modules.add(new ParticleSpam());
        modules.add(new PlaySoundSpam());
        modules.add(new RNGSpammer());
        modules.add(new BossbarFlash());
        modules.add(new EntitySpammer());
        modules.add(new TNTRain());
        modules.add(new DeathOverride());
        modules.add(new FireworkShow());
        modules.add(new OperatorNuker());
        modules.add(new WorldDownloader());
        modules.add(new UUIDBan());

        try {
            Commands.add(new ItemCrashCommand());
            Commands.add(new TNTRainCommand());
            Commands.add(new EnchCrackCommand());
            Commands.add(new EnchCrackedAliasCommand());
            Commands.add(new EnccAliasCommand());
            Commands.add(new WorldEditCommand());
            Commands.add(new AutoShopCommand());
            Commands.add(new ISellWandCommand());
            Commands.add(new FixDeathCommand());
            Commands.add(new EscapeCommand());
            Commands.add(new ExportModuleListCommand());
            Commands.add(new GivePresetItemsCommand());
            Commands.add(new TransferCommand());
            Commands.add(new MultiCommand());
            Commands.add(new ItemStealerCommand());
            Commands.add(new NbtCommand());
            Commands.add(new GivePresetCommand());
            Commands.add(new VerifyProtectCommand());
            Commands.add(new PeakPluginScannerCommand());
            Commands.add(new UUIDBanCommand());
            Commands.add(new HideKeybindCommand());
            Commands.add(new SetPrefixCommand());

            HideKeybindCommand.loadAndApplyOnStartup();
        } catch (Exception e) {
            LOG.warn("Failed to register commands", e);
        }

        try {
            Hud hud = Hud.get();
            if (hud != null) {
                hud.register(CustomTextHud.INFO);
                hud.register(WeaponCooldownHud.INFO);
                hud.register(RenderDistanceHud.INFO);
                hud.register(NearestPlayerHud.INFO);
                hud.register(ServerTpsHud.INFO);
                hud.register(ServerPlayersHud.INFO);
                hud.register(ServerRealIpHud.INFO);
                hud.register(ServerRealVersionHud.INFO);
                hud.register(ServerVersionNoteHud.INFO);
                hud.register(ServerIpHud.INFO);
                hud.register(ServerBrandHud.INFO);
                hud.register(ServerVersionHud.INFO);
                hud.register(ServerProtocolHud.INFO);
                hud.register(ServerDifficultyHud.INFO);
                hud.register(ServerTimeHud.INFO);
                hud.register(ServerPluginsHud.INFO);
            }
        } catch (Exception e) {
            LOG.warn("Failed to register HUD elements", e);
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(CATEGORY_VANILLA);
        Modules.registerCategory(CATEGORY_OP);
        Modules.registerCategory(CATEGORY_STUPID);
    }

    @Override
    public String getPackage() {
        return "orbiter";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("player19425", "Meteor-Orbiter");
    }
}
