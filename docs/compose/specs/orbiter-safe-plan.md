# Orbiter V2.1 Safe Continuation Specification

## 1. Purpose

Continue Orbiter V2.1 only within a safe, non-destructive scope. The implementation must improve startup reliability, defensive client behavior, bounded command infrastructure, rendering correctness/performance, and validation of operator-oriented inputs without adding server sabotage, authority bypasses, harassment, persistence, or destructive execution shortcuts.

This specification supersedes unsafe portions of `.mimocode/plans/1784487435698-playful-falcon.md`. It is based on the current Minecraft 1.21.11 Fabric/Meteor source tree and its partially implemented ServerProtect, CrashFixer coexistence, ViewBlocks, BossbarFlash, WorldEraser, WorldEdit, and ClientSideThings code.

## 2. Verified Baseline

- Java 21, Minecraft 1.21.11, Yarn `1.21.11+build.3`, Fabric Loader dependency `0.18.2`, and Meteor `1.21.11-SNAPSHOT` are configured in the live tree.
- `OrbiterMixinPlugin` exists and skips Orbiter CrashFixer-prefixed mixins when mod ID `crashfixer` is loaded, but ownership is coarse and lacks a concise compatibility report.
- `ServerProtect` already has broad packet, item, text, and dialog defenses. Its legacy entity-data removal defaults to false, but inspection, mutation boundaries, component coverage, and fixtures need hardening.
- Dialog packet and screen mixins exist, but the emergency UI, timeout, per-server suppression behavior, and bounded validation need completion.
- `ViewBlocks` exists with known incremental-scan, furthest-drop, forced-chunk-access, allocation, and budget issues.
- `BossbarFlash` exists but repeatedly emits unchanged state and lacks a shared bounded scheduler.
- `WorldEraser` and `WorldEditModule` exist and can build eager command lists. They require checked preflight validation and lazy preview/execution descriptors.
- `PeakPluginScanner` and `PluginDatabase` contain Essentials and WorldEdit evidence, but there is no shared immutable capability snapshot or command adapter.
- `BeaconOptimizer`, shared `CommandBatcher`, `ServerCapabilities`, `ServerCommandAdapter`, `SafeRegionMath`, and `FillCommandIterator` do not exist.
- Client-side spoof work is partially implemented. New settings/mixins require exact-signature correction, registration, lifecycle cleanup, and static/build verification.
- The current README remains the generic addon-template documentation.
- No project-owned automated test suite was found.
- Linux Java is unavailable in the recorded environment; Windows JDK 21 is available under `/mnt/c/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot/`.

## 3. Mandatory Safety Boundary

### 3.1 Allowed work

- Defensive client validation, bounded previews, status reporting, and local presentation-only spoofing.
- Startup/mixin compatibility fixes that avoid interception conflicts rather than attempting to override other mods.
- Capability observation from server command trees and scanner evidence, while representing permission as unknown unless directly established.
- Bounded scheduling of legitimate user-authorized commands already supported by safe modules.
- Checked arithmetic, input rejection, lazy descriptors, cancellation, and progress reporting.
- Performance optimizations that preserve visible/gameplay-relevant output.
- Static analysis, unit tests, compilation, resource validation, JAR build, and inspection of build/mixin diagnostics.

### 3.2 Prohibited work

Do not implement, register, activate, restore, or test:

- Crashers, crash items, malformed payload generators, or intentional resource-exhaustion tools.
- Server/world wiping or execution paths intended to erase large regions without ordinary bounded safety controls.
- Mass deop, op, kill, permission/group removal, or wildcard-permission takeover.
- UUID-ban harassment, duplicate-UUID abuse, re-summon persistence, remote harassment markers, or equivalent behavior.
- Infinite reach, attack-range bypasses, or any claim that client state overrides server authority.
- Features intended to sabotage a server, evade server authority, conceal unauthorized actions, or persist after the client leaves.
- Any bypass of validation, confirmation, scheduler budgets, ownership cancellation, or capability checks.

Unsafe requested modules such as UUIDBan, DestroyNow, and InfiniReach must remain absent/unregistered. If discoverability is useful, expose only a non-executing status/preview diagnostic stating that Orbiter does not implement the unsafe action and explaining the server-authoritative limitation. Existing destructive operations outside this scope must not be expanded or made easier to invoke.

## 4. Functional Requirements

### 4.1 Startup and mixin reliability

1. Audit every class named by `orbiter.mixins.json`; every entry must resolve to a source/generated class.
2. Use exact Minecraft 1.21.11 mapped descriptors for all changed mixins.
3. Refine `OrbiterMixinPlugin` to maintain an explicit table of Orbiter hooks that overlap with CrashFixer, PacketFixer, or ExploitPreventer.
4. Skip only confirmed duplicate hooks. ServerProtect packet/item/dialog hooks targeting distinct methods must remain active.
5. Never resolve a conflict by raising Orbiter priority to suppress another protection mod.
6. Optional compatibility hooks may use tolerant application only when loss of the hook is safe and a diagnostic records the omission.
7. Emit one bounded startup summary describing loaded compatibility mods, delegated hooks, active Orbiter hooks, and skipped optional hooks.
8. Verification is static/build only for this task. No launch or 253-mod compatibility run is permitted, and no claim of runtime compatibility may be made without such a later run.

### 4.2 Shared capability and command infrastructure

Implement reusable infrastructure with these properties:

- `ServerCapabilities`: immutable snapshots of normalized command roots and scanner evidence, refreshed on join/command-tree update and cleared on disconnect.
- Capability states: `AVAILABLE`, `UNAVAILABLE`, `UNKNOWN`; command presence must not be equated with permission.
- `ServerCommandAdapter`: chooses namespaced vanilla commands when available, recognizes Essentials/WorldEdit evidence, validates command components, and returns typed command descriptors or a refusal/status result.
- `CommandBatcher`: bounded queue, per-tick budget, hard queue limit, owner keys, owner-only cancellation, typed delays/actions, optional deduplication keys, progress, and disconnect cleanup.
- Scheduler callbacks and all Minecraft state access remain on the client thread.
- No collection may be allocated in proportion to an untrusted radius, region volume, entity count, or hidden server command tree without a hard cap.
- Adapter/scheduler APIs must not include commands for mass op/deop/kill, permission removal, server wiping, or authority bypass.

### 4.3 Safe region math and lazy work

- Use checked `long` add/multiply and explicit legal coordinate bounds.
- Reject invalid radius, dimensions, volume, coordinate overflow, estimated command overflow, and unsupported over-budget patterns before iteration.
- Split cuboids iteratively and lazily under the server command volume limit; never recursively materialize all subdivisions.
- Expose typed descriptors/iterators that generate only enough work to replenish the bounded scheduler.
- Provide deterministic preflight estimates and hard-stop reasons.
- Cancellation/disconnect must stop unsent work immediately.
- Validation must not have a hidden `force`, `unsafe`, direct-send, or alternate execution bypass.

### 4.4 ServerProtect dialogs

- Validate dialog complexity before unsafe screen construction where the mapped packet handler permits it.
- Bound title/body characters, element count, inputs, actions, nesting depth, estimated payload size, and obfuscated character/style-node counts.
- Block malformed, over-budget, recursive, or provably unclosable dialogs; allow normal closable dialogs.
- Always permit server clear-dialog behavior.
- Add non-overlapping `Close once` and `Close + suppress` controls that invoke no server-provided action.
- The suppression label must reflect the configured duration rather than hardcoding 15 minutes.
- Suppression is per server, expires, and resets on disconnect by default.
- Auto-close must be bounded and avoid a repeated open/close loop by escalating only the repeated dialog fingerprint.
- Keep only bounded hashes/metadata for diagnostics.

### 4.5 ServerProtect items and data components

- Replace ad hoc traversal with a bounded visitor supporting depth, node, character, list-entry, and estimated-size budgets plus identity cycle detection.
- Treat `ENTITY_DATA`, `BUCKET_ENTITY_DATA`, and `BLOCK_ENTITY_DATA` as inspectable data, not intrinsically malicious data.
- Preserve legitimate spawn eggs and other safe operator items unchanged under normal defaults.
- Reject non-finite/extreme numerics, recursive passenger/item graphs, oversized text/lists, invalid identifiers, and concretely dangerous command-adjacent payloads according to policy.
- Tooltip inspection may use a sanitized copy but must never mutate the live `ItemStack`.
- Packet handling must choose an explicit result: accept unchanged, block packet and warn, record a bounded diagnostic fingerprint, or show a local preview copy.
- Never silently strip a received item and thereby desynchronize client and server.
- Keep the legacy destructive entity-data option disabled by default, clearly deprecated/dangerous, and isolated from normal validation.
- Add regression fixtures proving safe custom spawn-egg component equivalence after inspection.

### 4.6 Obfuscation protection

- Centralize bounded text inspection for names, lore, books, dialogs, hover/translatable content, entities, text displays, bossbars, titles, scoreboards, and teams.
- Count actual obfuscated characters and styled nodes; do not rely only on a raw `§k` substring.
- Apply context-specific outcomes: reject malicious network content, suppress unsafe rendering, or render a bounded local preview. Do not mutate authoritative state.
- Keep legitimate short formatting under configurable conservative limits.

### 4.7 BeaconOptimizer

- Add a render module only if the exact mapped renderer interception can be composed safely.
- Preserve all on-screen beacon beams and near-field quality by default.
- Optimize through cached immutable decisions, update throttling, allocation reduction, conservative off-screen culling, and optional geometry LOD that never removes visible beam presence.
- If renderer compatibility cannot be proven statically, disable the risky hook and report status rather than cancel rendering.
- Register module and mixin only after compile/resource checks pass.

### 4.8 ViewBlocks

- Correct previous/current scan-position ordering.
- Correct furthest-entry eviction.
- Never force-load chunks for scanning.
- Use resumable cursors with strict inspected-block and rendered-block budgets.
- Skip empty/unloaded sections and precompute target-block membership.
- Use O(1) tracked-position lookup/removal and reduce per-match/per-frame allocations.
- Cull before draw submission, prioritize nearest items when over budget, and cache selection until invalidated.
- Keep scan/render statistics bounded and optional.

### 4.9 BossbarFlash

- Maintain per-owned-bar desired and last-sent state.
- Diff fields and enqueue only changes.
- Use a dedicated command budget under the global scheduler budget.
- Spread creation and cleanup over ticks.
- Never delete an unowned bossbar ID.
- Suppress only exact locally attributable feedback; never alter server gamerules.
- Parse rejection/permission feedback distinctly from benign unchanged-state feedback.
- Remove or disable deliberate obfuscation output if it conflicts with Orbiter's defensive text policy.

### 4.10 WorldEraser and WorldEdit safety

- Retain these only as bounded, user-directed administrative tools with mandatory preflight validation and no bypass.
- Add a preview-only mode that computes estimates and representative commands without sending anything.
- Reject absurd typed radii such as `943592592594` immediately without allocation or iteration.
- Replace eager lists and pseudo-command strings with shared typed scheduler steps and lazy iterators.
- Apply coordinate, radius, estimated-block, estimated-volume, and command caps to every shape/pattern/path.
- Reject over-budget checkerboards and similarly sparse-per-block patterns instead of enumerating them.
- Expose pause/resume/cancel/progress for accepted bounded work.
- Prefer detected native WorldEdit only through the adapter; otherwise allow a safe bounded vanilla fallback where semantics match.
- Never add a force-run, direct-send, safety-off, silent truncate, or destructive execution bypass.
- Existing potentially destructive execution must remain gated; tests must exercise only validators, iterators, preview generation, and scheduler behavior, never a live destructive command.

### 4.11 Essentials and WorldEdit detection

- Reuse PeakPluginScanner evidence without duplicating its database.
- Prefer `minecraft:give` when Essentials overrides an alias.
- Treat hidden command trees as `UNKNOWN`, not unavailable.
- Report selected adapter path and permission uncertainty.
- Validate 1.21.11 item component syntax before descriptor creation.
- Do not probe by sending destructive or state-changing commands.

### 4.12 Client-side spoof fixes

- Complete only local presentation/state effects already requested: crosshair, fake-death presentation, bossbar presentation, fire overlay/state, sky appearance, position render offset, biome appearance, client tick-rate presentation, and configurable local item-use delay where technically valid.
- Use exact mapped signatures and register only compile-valid mixins.
- Spoofing must not send deceptive movement/action packets, change server-authoritative position/gamemode/health, or claim server-side effect.
- Restore local state on disable, disconnect, world change, or module absence.
- Replace fragile repeated global module lookups/identity maps where practical with bounded lifecycle-managed snapshots.
- If a requested effect cannot be implemented without altering server authority, expose it as unsupported status rather than faking success.

### 4.13 Registration and documentation

- Register only safe completed modules, mixins, and diagnostics.
- Keep unsafe absent modules and commands unregistered.
- Correct stale version text and document actual build metadata.
- Replace the generic README with Orbiter-specific setup, capability semantics, ServerProtect behavior, command budgets, previews, WorldEraser/WorldEdit limits, client-only spoof limitations, and verification scope.
- Document that this task did not perform launch testing or 253-mod compatibility testing.

## 5. Testing and Verification Requirements

### 5.1 Automated tests

Add tests for pure Java or isolated logic where practical:

- Checked region arithmetic and overflow rejection.
- Lazy cuboid splitting and hard command caps.
- Queue limits, budgets, owner cancellation, deduplication, and disconnect reset.
- Capability normalization and adapter selection for vanilla, Essentials, WorldEdit, hidden, and ambiguous roots.
- Bossbar state diffing and ownership-safe cleanup planning.
- Dialog/text complexity counters and suppression expiry logic.
- Item/component classifications, including safe spawn-egg preservation and malicious recursive/non-finite samples.
- ViewBlocks eviction and cursor/budget helper logic.
- Client spoof lifecycle state reset.

Tests must not connect to a server or send destructive commands.

### 5.2 Static/build verification

- Run compile and unit-test tasks with JDK 21.
- Run the Gradle build without launching Minecraft.
- Inspect compiler diagnostics and generated mixin/refmap diagnostics.
- Verify every mixin config entry resolves to a compiled class.
- Verify registration references only existing classes.
- Search generated/source outputs for prohibited newly added module registrations or command templates.
- Record exact commands, exit codes, warnings, failures, skipped checks, and artifact paths.

### 5.3 Explicitly excluded verification

- No `runClient`, launch testing, server connection, or in-game execution.
- No CrashFixer runtime pair launch.
- No 253-mod compatibility testing.
- No live WorldEraser/WorldEdit execution.
- No destructive-feature testing.

## 6. Acceptance Criteria

The safe continuation is accepted when:

1. All changed code compiles and the non-launch Gradle build succeeds.
2. Automated tests cover shared limits, state diffing, safety rejection, and representative ServerProtect fixtures.
3. Mixin configuration contains no missing classes and changed hooks use verified 1.21.11 descriptors.
4. CrashFixer coexistence ownership is precise and statically auditable, with no attempt to out-prioritize another mod.
5. Safe spawn eggs remain component-equivalent after inspection and tooltips never mutate live stacks.
6. Dialog suppression and emergency close behavior are bounded and do not invoke server actions.
7. Command generation, queueing, region splitting, ViewBlocks scanning/rendering, and bossbar updates have hard budgets.
8. WorldEraser/WorldEdit invalid inputs are rejected before proportional allocation/iteration, and tests use preview/validator paths only.
9. Client spoof effects remain local and reset cleanly.
10. Beacon beams remain visible by design; uncertain renderer compatibility degrades to diagnostics, not hidden beams.
11. No prohibited crasher, server wiping, mass authority modification, UUID harassment/persistence, infinite reach, sabotage, or authority-evasion feature is added or registered.
12. Documentation and the final report state exactly what was implemented, what remains unverified, and every verification result without claiming launch compatibility.

## 7. Final Report Contract

The implementation report in `docs/compose/reports/orbiter-safe-plan.md` must contain:

- Implemented tasks by plan ID and exact files changed.
- Safe replacements used for excluded unsafe requests.
- Remaining work, including every runtime-only or modpack-only uncertainty.
- Verification table with command, result, duration when available, warnings, and artifact.
- Explicit statement that no launch test, 253-mod test, live destructive command, or authority-bypass test was performed.
- Any deviation from this specification and its safety rationale.
