# Orbiter V2.1 Safe Continuation Plan

## Plan Rules

- Implement only the scope in `docs/compose/specs/orbiter-safe-plan.md`.
- Every task must preserve the mandatory safety boundary.
- A task is complete only when its acceptance checks pass or its unresolved issue is recorded honestly.
- Do not run Minecraft, connect to a server, perform 253-mod testing, or send destructive commands.
- `dependsOn` is authoritative. Tasks with an empty list may proceed independently.

## Tasks

### SAFE-001 — Establish static baseline and prohibited-feature guardrail

- **description:** Record current source/build state, available JDK invocation, existing mixin entries, registrations, and prohibited module/command patterns. Add a non-executing verification checklist or test fixture that fails if newly introduced registrations/templates violate the safe scope.
- **acceptance:** Baseline commands and results are recorded; every mixin entry and current registration is inventoried; guardrail checks cover crashers, mass op/deop/kill/permission removal, UUID harassment/persistence, server wiping, infinite reach, and direct safety bypasses; no production behavior changes.
- **files:** `docs/compose/reports/orbiter-safe-plan.md`, test/build-support files as appropriate
- **dependsOn:** []

### SAFE-002 — Audit mixin declarations and exact descriptors

- **description:** Compare `orbiter.mixins.json` with live source classes and Minecraft 1.21.11 mapped signatures. Remove only proven stale entries, correct partial client-spoof signatures, and make optional hooks tolerant only where omission is safe and diagnosed.
- **acceptance:** Every configured mixin resolves to a compiled source class; changed injections use exact mapped descriptors; no hook gains priority intended to suppress another mod; static mixin audit has no missing-class error.
- **files:** `src/main/resources/orbiter.mixins.json`, `src/main/java/orbiter/mixin/*.java`
- **dependsOn:** []

### SAFE-003 — Refine CrashFixer and packet-protection ownership

- **description:** Replace coarse prefix-only coexistence logic with an explicit overlap table for CrashFixer and confirmed PacketFixer/ExploitPreventer hooks while keeping distinct ServerProtect hooks active. Add one bounded compatibility summary.
- **acceptance:** Only confirmed duplicate interception points are skipped; unrelated dialog/item/packet protections remain enabled; compatibility decisions are deterministic and unit-testable where possible; startup log emits one concise summary; no runtime-compatibility claim is made.
- **files:** `src/main/java/orbiter/mixin/OrbiterMixinPlugin.java`, `src/main/resources/orbiter.mixins.json`, related compatibility helper/tests
- **dependsOn:** [SAFE-002]

### SAFE-004 — Implement immutable server capability snapshots

- **description:** Add lifecycle-managed capability snapshots from Brigadier roots and bounded PeakPluginScanner evidence. Normalize namespaced roots and represent availability independently from authorization.
- **acceptance:** States are `AVAILABLE`, `UNAVAILABLE`, or `UNKNOWN`; hidden/absent command trees do not imply denied permission; snapshots clear on disconnect; no background thread reads Minecraft state; normalization tests cover vanilla, Essentials, WorldEdit, and ambiguous roots.
- **files:** `src/main/java/orbiter/util/ServerCapabilities.java`, command-tree integration files, tests
- **dependsOn:** []

### SAFE-005 — Implement the safe command adapter

- **description:** Add typed descriptors/refusals for safe give, fill, setblock, and WorldEdit routing. Prefer namespaced vanilla commands when aliases are overridden and keep permission uncertainty visible.
- **acceptance:** Adapter selects `minecraft:give` when supported, recognizes explicit Essentials/WorldEdit paths, validates arguments/components, returns status instead of guessing, and exposes no mass authority-change, wiping, harassment, or bypass APIs.
- **files:** `src/main/java/orbiter/util/ServerCommandAdapter.java`, `src/main/java/orbiter/util/CommandUtils.java`, tests
- **dependsOn:** [SAFE-004]

### SAFE-006 — Implement bounded owner-aware command scheduling

- **description:** Add a client-thread scheduler with typed command, delay, and callback steps; hard queue and per-tick limits; owner keys; deduplication; progress; and lifecycle cancellation.
- **acceptance:** Queue overflow rejects immediately; one owner cannot cancel another; disconnect/deactivation clears applicable work; callbacks run on the client thread; no magic pseudo-command strings are required; tests prove budgets, ordering, dedupe, cancellation, and reset.
- **files:** `src/main/java/orbiter/util/CommandBatcher.java`, lifecycle registration files, tests
- **dependsOn:** []

### SAFE-007 — Implement checked region math

- **description:** Add checked/saturating `long` helpers and immutable region preflight results for radius, dimensions, legal coordinates, volume, estimated blocks, and command budgets.
- **acceptance:** Invalid/overflowing inputs fail before loops or proportional allocation; legal-boundary cases are deterministic; typed radius `943592592594` is rejected; unit tests cover overflow, negative dimensions, coordinate limits, and caps.
- **files:** `src/main/java/orbiter/util/SafeRegionMath.java`, tests
- **dependsOn:** []

### SAFE-008 — Implement lazy bounded fill descriptors

- **description:** Add iterative cuboid splitting and resumable descriptor iterators that replenish only bounded scheduler capacity.
- **acceptance:** No recursive or complete command-list materialization; every emitted cuboid stays under the configured fill-volume cap; cancellation halts further generation; hard command/block caps are enforced; tests cover exact coverage, boundaries, and early termination.
- **files:** `src/main/java/orbiter/util/FillCommandIterator.java`, related descriptor types, tests
- **dependsOn:** [SAFE-006, SAFE-007]

### SAFE-009 — Centralize bounded text and obfuscation inspection

- **description:** Implement reusable text complexity accounting for total characters, depth, nodes, style nodes, and actual obfuscated characters across supported text contexts.
- **acceptance:** Inspector has hard budgets and no unbounded recursion; detects styled obfuscation without relying only on `§k`; preserves legitimate short formatting under policy; tests cover nested translations, hover text, obfuscated styles, and ordinary text.
- **files:** `src/main/java/orbiter/modules/misc/ServerProtect.java` or a focused utility, related mixins, tests
- **dependsOn:** []

### SAFE-010 — Harden Dialog API packet validation and suppression state

- **description:** Complete bounded dialog validation, per-server expiring suppression, clear-dialog allowance, auto-close state, repeated-fingerprint escalation, and bounded diagnostics.
- **acceptance:** Normal closable dialogs pass; malformed/over-budget/provably unclosable dialogs fail before unsafe rendering where mappings permit; clear behavior always passes; suppression expires and resets as configured; only bounded metadata is retained; tests cover expiry and repeated fingerprint behavior.
- **files:** `src/main/java/orbiter/modules/misc/ServerProtect.java`, `src/main/java/orbiter/mixin/ServerProtectDialogPacketMixin.java`, tests
- **dependsOn:** [SAFE-002, SAFE-009]

### SAFE-011 — Complete safe dialog emergency controls

- **description:** Add non-overlapping `Close once` and dynamically labeled close-and-suppress controls plus timeout closure that never invokes server-provided actions.
- **acceptance:** Labels reflect configured duration; controls return to a safe parent/game screen; closure sends no dialog action; controls do not cover server inputs/actions under bounded layouts; timeout/reopen handling cannot form an unbounded loop; exact mapped screen signatures compile.
- **files:** `src/main/java/orbiter/mixin/ServerProtectDialogScreenMixin.java`, `src/main/java/orbiter/modules/misc/ServerProtect.java`, tests for extracted state logic
- **dependsOn:** [SAFE-010]

### SAFE-012 — Build bounded data-component visitor

- **description:** Refactor item inspection into a budgeted visitor with identity cycle protection and explicit safe/suspicious/malicious outcomes across text, books, nested items, entity/block data, attributes, and large collections supported by 1.21.11 APIs.
- **acceptance:** Depth/node/character/list/size budgets are enforced; non-finite/extreme numeric values and recursive graphs are rejected; component presence alone is not malicious; visitor never mutates its input; representative tests cover each supported component family.
- **files:** `src/main/java/orbiter/modules/misc/ServerProtect.java` and/or focused scanner classes, tests
- **dependsOn:** [SAFE-009]

### SAFE-013 — Preserve spawn eggs and isolate legacy destructive sanitization

- **description:** Route tooltip and packet decisions through non-mutating inspection, preserve safe entity/block data unchanged, clearly deprecate the legacy destructive option, and extend local protection diagnostics.
- **acceptance:** Safe custom egg fixture retains component equivalence after inspection; vanilla egg, bucket data, bee/block entity data, and safe armor-stand data pass; recursive passengers/non-finite attributes fail; tooltip uses a copy and never alters the live stack; normal defaults cannot silently strip entity data; diagnostic reports legacy-risk status.
- **files:** `src/main/java/orbiter/modules/misc/ServerProtect.java`, `src/main/java/orbiter/mixin/ServerProtectItemLoreMixin.java`, `src/main/java/orbiter/commands/VerifyProtectCommand.java`, tests
- **dependsOn:** [SAFE-012]

### SAFE-014 — Apply bounded obfuscation policy across receive/render contexts

- **description:** Reuse the central inspector in item, book, dialog, entity/text display, bossbar, title, scoreboard, team, hover, and translation paths where the current APIs expose those contexts safely.
- **acceptance:** Covered contexts use one bounded policy; outcomes are context-specific and non-mutating; no global server gamerule or authoritative state is changed; unsupported contexts are documented rather than hooked speculatively.
- **files:** `src/main/java/orbiter/modules/misc/ServerProtect.java`, relevant `src/main/java/orbiter/mixin/*.java`, tests
- **dependsOn:** [SAFE-003, SAFE-009, SAFE-012]

### SAFE-015 — Implement conservative BeaconOptimizer

- **description:** Add cached render-state/update decisions, allocation reduction, optional conservative off-screen culling, and beam-preserving LOD only at statically verified renderer points.
- **acceptance:** Default behavior never hides an on-screen beam or shortens gameplay-visible meaning; near beams remain full quality; uncertain compatibility disables optimization with a diagnostic instead of canceling rendering; module/mixin compile and are registered only after static checks; pure cache policy has tests.
- **files:** `src/main/java/orbiter/modules/render/BeaconOptimizer.java`, `src/main/java/orbiter/mixin/BeaconBlockEntityRendererOptimizerMixin.java`, `src/main/resources/orbiter.mixins.json`, tests
- **dependsOn:** [SAFE-002]

### SAFE-016 — Correct ViewBlocks scanning and eviction

- **description:** Fix scan-position sequencing, furthest eviction, unloaded-chunk handling, and tracked-position data structures before deeper optimization.
- **acceptance:** Movement delta uses the previous completed scan position; furthest entries are actually removed; scanning never requests an unloaded chunk; tracked removals are O(1); focused helper tests cover movement and eviction behavior.
- **files:** `src/main/java/orbiter/modules/render/ViewBlocks.java`, extracted helper/tests if needed
- **dependsOn:** []

### SAFE-017 — Bound ViewBlocks scan/render work

- **description:** Add resumable scan cursors, empty-section skipping, precomputed block membership, allocation reduction, independent scan/render budgets, nearest-first render selection, and cache invalidation.
- **acceptance:** Per-tick inspected blocks and per-frame rendered blocks never exceed settings/hard caps; no full-volume list is built; unloaded/empty sections are skipped; render subset is cached until relevant change; bounded optional stats expose inspected/tracked/rendered counts.
- **files:** `src/main/java/orbiter/modules/render/ViewBlocks.java`, helper/tests
- **dependsOn:** [SAFE-016]

### SAFE-018 — Add BossbarFlash state diffing

- **description:** Introduce owned-bar desired/last-sent state and pure diff generation, avoiding repeated unchanged fields and repeated random values when a visual change is requested.
- **acceptance:** Unchanged state produces no descriptor; only changed fields are emitted; ownership is explicit; random change selection avoids the previous value when alternatives exist; unit tests cover creation, updates, no-op cycles, and ownership.
- **files:** `src/main/java/orbiter/modules/render/BossbarFlash.java`, state/diff helper, tests
- **dependsOn:** []

### SAFE-019 — Route BossbarFlash through bounded scheduling

- **description:** Queue creation, changed fields, and cleanup through the shared scheduler with a module-specific sub-budget and exact attributable feedback handling.
- **acceptance:** Outgoing bossbar descriptors cannot exceed global or module budget; creation/cleanup spread over ticks; unowned IDs are never deleted; clean-all is cancellable; no gamerule changes; deliberate obfuscation output is removed or safely disabled.
- **files:** `src/main/java/orbiter/modules/render/BossbarFlash.java`, feedback integration files, tests
- **dependsOn:** [SAFE-006, SAFE-009, SAFE-018]

### SAFE-020 — Add WorldEraser validation and preview

- **description:** Apply shared checked preflight to every shape/pattern and add a non-sending preview with estimates, truncation status, hard-stop reasons, and bounded representative descriptors.
- **acceptance:** Absurd/overflowing inputs reject immediately; over-budget checkerboard/sparse patterns reject without enumeration; preview sends nothing and allocates only bounded data; there is no force/unsafe/direct-send bypass; tests cover all shapes and patterns without world modification.
- **files:** `src/main/java/orbiter/modules/world/WorldEraser.java`, validation/preview tests
- **dependsOn:** [SAFE-007, SAFE-008]

### SAFE-021 — Convert accepted WorldEraser work to lazy scheduling

- **description:** Replace eager command storage and recursive splitting with lazy descriptors, bounded queue replenishment, progress, pause/resume, cancel, and disconnect cleanup while preserving existing user confirmation.
- **acceptance:** Accepted operations never materialize all commands; every descriptor passed through adapter and scheduler limits; cancellation stops unsent work; validation cannot be bypassed; tests exercise descriptors/scheduler only and never send a destructive command.
- **files:** `src/main/java/orbiter/modules/world/WorldEraser.java`, tests
- **dependsOn:** [SAFE-005, SAFE-006, SAFE-020]

### SAFE-022 — Add WorldEdit validation, capability status, and preview

- **description:** Apply shared preflight to selection/shape/clipboard operations, replace implicit command assumptions with adapter status, and provide non-sending previews for large or destructive operations.
- **acceptance:** Coordinate/volume/radius/command limits apply before generation; native WorldEdit, bounded vanilla fallback, and unknown capability are distinguished; permission remains unknown unless established; preview is bounded and non-sending; no destructive bypass exists.
- **files:** `src/main/java/orbiter/modules/world/WorldEditModule.java`, `src/main/java/orbiter/commands/WorldEditCommand.java`, tests
- **dependsOn:** [SAFE-004, SAFE-005, SAFE-007, SAFE-008]

### SAFE-023 — Convert WorldEdit steps to typed lazy scheduling

- **description:** Replace eager pending lists and `__WAIT__`/`__VERIFY_TP__` strings with typed scheduler steps, bounded iterators, ownership cancellation, and safe fallback routing.
- **acceptance:** No magic pseudo-command is sent; large accepted operations replenish bounded queues lazily; disconnect/deactivation cancels pending work; destructive confirmations remain mandatory; tests cover planning and cancellation without server execution.
- **files:** `src/main/java/orbiter/modules/world/WorldEditModule.java`, `src/main/java/orbiter/commands/WorldEditCommand.java`, tests
- **dependsOn:** [SAFE-006, SAFE-022]

### SAFE-024 — Migrate give producers to capability-aware routing

- **description:** Route existing legitimate give operations through the shared adapter, preserving creative insertion as a separately reported method and validating 1.21.11 component syntax.
- **acceptance:** Namespaced vanilla give is preferred when available; Essentials syntax is used only in explicit detected/configured mode; selected path is reported; malformed components are rejected before scheduling; no unsafe preset/crash payload is added.
- **files:** `src/main/java/orbiter/modules/world/ItemCreator.java`, `src/main/java/orbiter/modules/world/ItemGenerator.java`, `src/main/java/orbiter/commands/GivePresetCommand.java`, `src/main/java/orbiter/commands/GivePresetItemsCommand.java`, related helpers/tests
- **dependsOn:** [SAFE-005, SAFE-006]

### SAFE-025 — Complete requested local spoof renderer hooks

- **description:** Finish exact-signature local-only behavior for crosshair, fake-death presentation, bossbar presentation, fire overlay/state, sky, render-position offset, biome appearance, client tick-rate presentation, and configurable local item-use delay where valid.
- **acceptance:** Every implemented effect is presentation/client-state only; no deceptive movement/action packet or server-authoritative mutation is added; exact 1.21.11 signatures compile; unsupported effects report unsupported rather than claiming success.
- **files:** `src/main/java/orbiter/modules/misc/ClientSideThings.java`, `src/main/java/orbiter/mixin/ClientSide*.java`, sky/position/cooldown mixins as needed, `src/main/resources/orbiter.mixins.json`
- **dependsOn:** [SAFE-002]

### SAFE-026 — Harden client spoof lifecycle and state cleanup

- **description:** Ensure local spoof snapshots/maps are bounded and reset on disable, disconnect, world change, or unavailable module state; reduce fragile repeated module lookup where practical.
- **acceptance:** No stale fake item/entity/world state survives lifecycle boundaries; identity-keyed data cannot grow without a bound/reset; restore logic is null-safe and idempotent; extracted lifecycle tests pass.
- **files:** `src/main/java/orbiter/util/ClientSpoofState.java`, `src/main/java/orbiter/modules/misc/ClientSideThings.java`, disconnect/world lifecycle mixins, tests
- **dependsOn:** [SAFE-025]

### SAFE-027 — Register only completed safe features and diagnostics

- **description:** Register safe modules, lifecycle services, mixins, and verification/status commands; correct stale version text; ensure excluded unsafe modules remain absent and unregistered.
- **acceptance:** All registrations resolve; BeaconOptimizer is registered only if SAFE-015 passed; unsafe UUIDBan/DestroyNow/InfiniReach/crasher/wiping/authority-removal features are not added or registered; version log matches project metadata; guardrail check passes.
- **files:** `src/main/java/orbiter/Orbiter.java`, `src/main/resources/orbiter.mixins.json`, `src/main/resources/fabric.mod.json`, diagnostic command files
- **dependsOn:** [SAFE-003, SAFE-011, SAFE-013, SAFE-014, SAFE-015, SAFE-017, SAFE-019, SAFE-021, SAFE-023, SAFE-024, SAFE-026]

### SAFE-028 — Add Orbiter-specific documentation

- **description:** Replace template-only documentation with accurate safe-feature, capability, ServerProtect, command-budget, preview, client-spoof, and verification guidance.
- **acceptance:** README documents server-authority limits, spawn-egg preservation, dialog controls, WorldEraser/WorldEdit caps/previews, Essentials/WorldEdit detection uncertainty, Beacon/ViewBlocks/Bossbar behavior, and prohibited/absent unsafe features; explicitly says launch and 253-mod verification were not performed in this scope.
- **files:** `README.md`, optional `docs/` references
- **dependsOn:** [SAFE-027]

### SAFE-029 — Run unit and static verification

- **description:** Run all non-launch tests, compile checks, resource processing, mixin/refmap inspection, registration/missing-class checks, and prohibited-feature guardrails using JDK 21.
- **acceptance:** Exact commands and exit codes are recorded; tests and build either pass or every failure is preserved with actionable cause; no Minecraft launch task runs; no server is contacted; no destructive command executes; built artifact path and warnings are recorded.
- **files:** build/test configuration as needed, `docs/compose/reports/orbiter-safe-plan.md`
- **dependsOn:** [SAFE-028]

### SAFE-030 — Produce exact implementation and verification report

- **description:** Finalize the required report with completed plan IDs, exact files changed, safe substitutions, remaining work, and all verification results.
- **acceptance:** Report distinguishes implemented, partial, blocked, and excluded work; contains verification command/result table; states that launch, CrashFixer runtime pairing, 253-mod compatibility, live destructive execution, and authority-bypass testing were not performed; makes no unsupported compatibility claim.
- **files:** `docs/compose/reports/orbiter-safe-plan.md`
- **dependsOn:** [SAFE-029]

## Dependency Review

- Independent roots: SAFE-001, SAFE-002, SAFE-004, SAFE-006, SAFE-007, SAFE-009, SAFE-016, SAFE-018.
- Shared infrastructure converges before command-producing module migration.
- Registration waits for every safe feature branch required by the requested scope.
- Documentation waits for final registration so it describes live behavior.
- Verification and reporting are terminal tasks.
- The graph contains no cycles.
