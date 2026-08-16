# Changelog

## 1.0.0 (2026-08-16)

- UUID ban: fixed entity NBT format for MC 26.2 (modern int-array UUID codec) and real spawn-egg entity types.
- Plugin scanner: persistent per-server cache, probe retries, and own-command observation.
- Added `.scanplayerlist` to enumerate online players via command autocomplete and copy to clipboard.
- Server HUDs: added players, real IP, real version, and version bridge note; fixed version, protocol, and difficulty HUDs.
- Assists: entity-type targeting on all assist modules.
- TNT Rain: random force and rotation per TNT, plus more continuous-mode options.
- NBT Lectern Crasher: sanitized command payloads to avoid server kicks.
- Command Block Placer: dynamic command slots (1-100) and amount limit.
- Camera 360: movement packets now send legal wrapped/clamped rotations.
- Removed the DestroyNow module.
- Licensed under the MIT License.
