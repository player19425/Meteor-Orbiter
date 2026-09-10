# Changelog

## 1.0.6 (2026-09-10)

- Noclip: walks through walls for real, survival included after a falling sand or gravel block lands on your head. Dips a bit every few seconds so the vanilla floating kick never fires.
- Combat engine: every assist now shares one rotation engine with silent aim and a fight arbiter. Aim assist plus, bow, crossbow, trident, mace, spear, precision shot, shield assist, out of reach and no friend hit all run on it.
- Auto Totem Plus: keeps a totem in your offhand with a real inventory session, input freezing and priority modes.
- Auto Login: auto answers login and register prompts with your saved credentials.
- Bed Defender: auto places a protective ring of blocks around beds.
- Ghost blocks: .ghostblock places client-only blocks, .copy and .copypos clone items and positions.
- Enchantment cracker: reworked core with fewer false steps and fixed commands.
- Server protect: stripped down internals and removed dead utility classes left over from older versions.
- Bugfix passes: raw packet capture, auto find, anti staff, infini reach, item stealer, auto shop, leave message, plugin scanner, hud text and the mojang api helper.
- Crashes: extra guards in the packet decoder, disconnect and client stop paths so a dying server stops taking the client with it.

## 1.0.0 (2026-08-16)

- UUID ban: fixed entity NBT format for MC 26.2 (modern int-array UUID codec) and real spawn-egg entity types.
- Plugin scanner: persistent per-server cache, probe retries, and own-command observation.
- Server HUDs: added players, real IP, real version, and version bridge note; fixed version, protocol, and difficulty HUDs.
- Assists: entity-type targeting on all assist modules.
- TNT Rain: random force and rotation per TNT, plus more continuous-mode options.
- NBT Lectern Crasher: sanitized command payloads to avoid server kicks.
- Command Block Placer: dynamic command slots (1-100) and amount limit.
- Camera 360: movement packets now send legal wrapped/clamped rotations.
- Removed the DestroyNow module.
- Licensed under the MIT License.
