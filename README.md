<div align="center">
  <img src="src/main/resources/assets/orbiter/icon.png" alt="Orbiter logo" width="20%"/>
  <h1>Meteor Orbiter</h1>
  <p>An addon for <a href="https://meteorclient.com/">Meteor Client</a> that adds 60+ modules, custom commands, and HUD elements for anarchy, griefing, and quality of life.</p>

  
  <a href="https://github.com/player19425/Meteor-Orbiter/releases"><img src="https://img.shields.io/badge/Version-1.0.5-orange" alt="Version"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.2-blue" alt="Minecraft version">
  <img src="https://img.shields.io/badge/Java-25-green" alt="Java version">
  <a href="https://github.com/player19425/Meteor-Orbiter/releases"><img src="https://img.shields.io/github/downloads/player19425/Meteor-Orbiter/total" alt="Downloads"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/stargazers"><img src="https://img.shields.io/github/stars/player19425/Meteor-Orbiter" alt="Stars"></a>
  <a href="https://github.com/player19425/Meteor-Orbiter/commits/26.2"><img src="https://img.shields.io/github/last-commit/player19425/Meteor-Orbiter" alt="Last commit"></a>
  <img src="https://img.shields.io/github/languages/code-size/player19425/Meteor-Orbiter" alt="Code size">
  <img src="https://img.shields.io/github/issues/player19425/Meteor-Orbiter" alt="Issues">
  <img src="https://img.shields.io/github/license/player19425/Meteor-Orbiter" alt="License">
</div>

<hr />

# About

Orbiter is a collection of modules and tools for [Meteor Client](https://meteorclient.com/), originally built for survival, anarchy, and creative/OP.

Everything is organized into five in-game categories:

- **Orbiter Survival** • general purpose and anti-abuse modules
- **Orbiter Vanilla** • modules that only use vanilla mechanics
- **Orbiter Creative/OP** • modules that require Creative mode or operator permissions
- **Orbiter Stupid** • joke / experimental modules (Enable in Meteor Client settings)
# Requirements

- [Java](https://adoptium.net/temurin/releases) 25 or higher
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3+
- [Meteor Client](https://meteorclient.com/) for 26.2

# Installation

1. Download the latest [release](https://github.com/player19425/Meteor-Orbiter/releases) of the mod.
2. Put the `.jar` in your `.minecraft/mods` folder. (varies depending on launcher)
3. Launch the game with Fabric and Meteor Client installed.
4. It should be properly installed by now.

Orbiter can also keep itself up to date: when you join a server or world it checks for a new release and shows you an in-game popup with update / ignore / changelog options, and it can even download and install the new jar for you if you turn that on. Everything lives in Meteor Config → Orbiter, and both toggles are yours to flip (the popup is on by default).

# Modules:

## Combat:

| Module | Description |
|---|---|
| **Aim Assist Plus** | Aim assist with prediction. |
| **Anti Knockback** | No knockback. |
| **Mace Assist** | Auto-aim and strike with the Mace. |
| **No Friend Hit** | Don't hit Meteor friends. |
| **Out Of Reach**

All assists support selecting which entity types to target (players, armor stands, and more).

## Movement:

| Module | Description |
|---|---|
| **Anti Push** | Stops fluid and entity push. |
| **Auto Clutch** | Auto-clutch to avoid fall damage. |
| **Force Invisibility** | Server-side invisibility. |
| **Jump A** | Jumps over walls and reaches blocks. |
| **Slime Jump** | Bounces higher on slime. |

## Player:

| Module | Description |
|---|---|
| **Auto Craft Plus** | Auto-crafts items at max speed. |
| **Client Side Mine** | Instantly breaks blocks client-side. |
| **Restock** | Auto-restocks your hotbar. |

## World:

| Module | Description |
|---|---|
| **Auto Farming** | Auto-farms crops and animals. |
| **Command Block Placer** | Places command blocks. Creative + OP. |
| **Control Player** | Rotates players around you. OP. |
| **Death Override** | Removes DieScreen incase of kill commandblock. |
| **Entity Spammer** | Spawns and animates entities. OP required. |
| **Item Creator** | Creates custom items. Creative only. |
| **Item Generator** | Spawns items with random enchants. Creative. |
| **Operator Nuker** | Nukes blocks with /fill. OP. |
| **RNG Spammer** | Spawns loot tables around players. OP. |
| **TNT Rain** | Rain of TNT. OP. |
| **UUID Ban (W.I.P)** | Locks players out with their UUID. OP. |
| **World Downloader** | Saves the server world locally. |
| **World Edit** | Client-side WorldEdit. `.we <command>` |
| **World Eraser** | Erases blocks in a radius. Toggle twice to trigger. OP. |

## Render:

| Module | Description |
|---|---|
| **Block Spam** | Let the blocks rain. Rain, animate, or setblock blocks around a target. OP. |
| **Block Spoof** | Spoofs block textures client-side. |
| **Bossbar Flash** | Flash random boss bars. OP required. |
| **Camera 360** | Unlimited camera rotation. |
| **Firework Show** | Firework shows with shapes and colors. |
| **Particle Control** | Particle shapes around a target. |
| **Particle Spam** | Spams particles in a radius. OP required. |
| **Playsound Spam** | Spams every sound. OP required. |
| **View Blocks** | ESP for invisible blocks. |

## Misc:

| Module | Description |
|---|---|
| **Actions** | Reactive triggers and actions. |
| **Anti Staff** | Detects staff and auto-leaves. |
| **Auto Find** | Scans for stashes and bases. |
| **Auto Shop** | Buys from server shops automatically. |
| **Client Side Things** | Local visual spoofs. |
| **Enchantment Cracker** | Cracks the hidden enchantment seed from table offers, then places items and clicks for you when your enchant shows up. `.encc get <enchant> [level]` |
| **Exploit Preventer** | Blocks common server exploits. |
| **I Sell Wand** | Auto-sells using a sell wand. |
| **Infini Reach** | Infinite reach. |
| **Item Info** | Extra info in item tooltips. |
| **Item Stealer** | Steals items from GUIs. |
| **Leave Message** | Sends a chat message before leaving. |
| **Message Formatter** | Formats outgoing chat. |
| **Peak Plugin Scanner** | Detects server plugins. |
| **Ping Spoof** | Spoofs your ping. |
| **Server Protect** | Blocks crash packets and malicious items. |
| **Spam Plus** | Chat spam with letter ladders. |

# Commands:

| Command | Description | Aliases |
|---|---|---|
| `.autoshop` | Toggle and control the AutoShop module. | `autoshopdetect` |
| `.escape` | Controls ForceInvisibility escape logic. | |
| `.enchantmentcracker` | Full-auto enchantment farmer: cracks the seed, predicts every row exactly, places items and clicks when your enchant appears. | `enchantcracked`, `encc` |
| `.exportmodulelist` | Exports all module names to clipboard. | |
| `.fixdeath` | Stops fake death loops and force-resyncs client/server state. | |
| `.givepresetitems` | 200+ OP and command-block-only presets. | `gpi` |
| `.hidekeybind` | Hides Meteor keybinds from the Controls screen. | |
| `.isellwand` | Control the ISellWand module. | `sellwand` |
| `.itemcrash` | Overloads your held item with extreme enchantments, attributes, and NBT data. | |
| `.itemstealer` | Save / load / manage cloned items. | `is`, `steal` |
| `.multicommand` | Run commands targeting multiple players via selectors. | |
| `.nbt` | Copies the held item's full NBT to the clipboard. | |
| `.orbitergivepreset` | Gives 120+ special preset items with lore. | `ogp` |
| `.peakscan` | Plugin scanner • detect server plugins. | |
| `.setprefix` | Sets Meteor's chat command prefix. | |
| `.tntrain` | Triggers TNT rain with specified parameters. | |
| `.transfer` | Transfer to another server without disconnecting. | |
| `.uuidban` | Ban a player by summoning a UUID entity. (W.I.P) | |
| `.verify-protect` | Tests ServerProtect crash item detection against known payloads. | |
| `.we` | A worldedit clone but it uses /fill. | `worldedit` |

# HUD:

| Element | Description |
|---|---|
| **Custom Text** | Displays custom text on the HUD with placeholders. |
| **Nearest Player** | Shows the nearest player and their distance. |
| **Render Distance** | Shows current render distance. |
| **Server TPS** | Shows the server's ticks per second. |
| **Server Time** | Shows the in-game day and time. |
| **Server Version Note** | Notes when the server runs a different version (protocol bridge). |
| **Server Difficulty** | Shows the world difficulty. |
| **Server Protocol** | Shows the server protocol version. |
| **Server Players** | Shows the number of online players. |
| **Server Brand** | Shows the server brand. |
| **Server Real Version** | Shows the real server version from the server list. |
| **Server Version** | Shows the server version. |
| **Server Real IP** | Shows the real IP address of the connection. |
| **Server IP** | Shows the server IP (domain). |
| **Server Plugins** | Shows the detected plugin count. |
| **Weapon Cooldown** | Shows current weapon attack cooldown in seconds. |

# Building from Source:

### Prerequisites:
- [JDK](https://adoptium.net/temurin/releases) 25 or higher
- Gradlew

### Steps:
```bash
git clone https://github.com/player19425/Meteor-Orbiter
cd Meteor-Orbiter

./gradlew build
```

The compiled JAR will be in `build/libs/`.

# Safety

- Destructive features require explicit confirmation and should only be tested on disposable local servers with backups.
- Command-producing modules use capability detection and bounded queues where supported. Command availability does not prove permission.
- Any damage caused by this Meteor Client Addon is __not__ the fault of the creator of the mod, and responsibility lies solely with the person who performed the action. 
- The only official way to download this mod from is [from its github.](https://github.com/player19425/Meteor-Orbiter/releases/latest) Downloading this from any other places is __not__ recommended.


# Credits

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) • the client this addon builds on.
- This was made as a personal project but i felt bad to gate keep it ♥️

# License

This project is licensed under the [MIT License](LICENSE).
