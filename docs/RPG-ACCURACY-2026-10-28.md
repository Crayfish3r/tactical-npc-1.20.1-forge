# RPG and per-NPC accuracy — 2026-10-28

Local implementation on `feature`, base `5aa22772da5b4c2d3c7c06294c5b9450f4510c2d`.
Not a published commit or release. Minecraft 1.20.1 / Forge 47.4.20 /
Microsoft Java 17.0.19 / TaCZ 1.1.8-hotfix2; optional NPCs 2.1.0.
Pinned API/resource evidence: [integration audit](INTEGRATION-AUDIT.md).

## Behavior

New verified weapon ID: `tacz:rpg7`, ammunition `tacz:rpg_rocket`.
GunSupport distinguishes KINETIC / EXPLOSIVE / UNSUPPORTED. Compatible native RPG mechanisms
require rpg type, rocket ammo, SEMI, one projectile, open bolt and audited script/item behavior;
arbitrary scripted shooting/property hooks, charge, inventory feeding, incendiary projectiles,
heat and incompatible live attachments remain rejected with a reason. Unknown packs are not certified.
Metadata/rejection caching follows weapon identity, fire mode, attachment cache identity and resource
generation. Live ammo and attachment explosion/ignition properties are revalidated.

RPG has its own profile: one native request per volley, ordinary native reload/cooldown,
minimum `max(12,2*liveRadius+2)` blocks (12 for the default rocket), preferred maximum 64.
These are code-owned safety/behavior limits, not new configuration settings. Indexed ballistic
gravity/drag/lifetime and live modified speed feed the existing predictor. Rockets never perform
ordinary suppression. Too-close DIRECT fire is forbidden even while retreating, critically hurt,
blocked by an order boundary or in STAND_WAIT. The existing order overlay can hold movement;
no artificial first shot or order transition was added.

Only an actual explosive fire event performs the additional bounded local query around intended
impact/barrel endpoints. It consumes the existing active query budget, checks loaded chunks without
loading them, caps predicate work, and fails closed. Native Detonate filters our tagged projectile's
victim list before both damage and independent knockback writes. Missing/dead/removed owners and
invalid/inactive match authority fail closed. Player projectiles and block destruction policy are
unchanged; no production rocket, explosion or damage implementation was added.

`NpcDifficulty.HARD` retains the original correlated aiming formula and 0.35-degree amplitude.
`DEFAULT` uses the same formula with the new `defaultAimErrorMultiplier` (initially 3), hence
1.05-degree amplitude at current settings. The aligned threshold still includes the actual error
amplitude; native barrel/ally checks remain mandatory. All other combat/native settings are shared.
No active UNFAIR or hints were added. The multiplier is outside legacy normalizeHard resets.

Command: `/npc difficulty <target> hard|default`, with existing name/radius/selector resolution,
permission 2 and literal completions. Both info commands show the actual named level. The setter
only changes that server field, preserving native reload, ammo, order, STAND phase and knowledge.
NBT saves the enum name; absent/unknown values use HARD, unknown values warn.
The old five-argument SpawnMemberSpec constructor is retained exactly and defaults HARD;
the sixth argument selects each member's accuracy independently. Mixed API groups are supported.
TacticalTablet was not changed. See [API usage](MATCH-NPC-API.md).

## Validation and preserved failures

Core command: `bash ai-core/run-tests.sh policy temporal combat` (Git Bash):
75 policy, 94 temporal, 110 combat checks passed. Full fresh core compilation; other suites not selected.
Initial sandbox execution could not access the Gradle lock / MSYS shared objects; executed zero tests.
The authorized local runs below used the installed cache.

GameTest launcher commands use the bundled Python interpreter, `--offline`, namespace tacticalnpcai,
isolated runtime/output directories and the selections in each JSON receipt under
`forge-addon/build/test-runs/`. Test-only finite chunk tickets come from ScenarioIsolation.

| Receipt timestamp (UTC) | Scope and outcome |
|---|---|
| 20261028T084830.154145Z | STAGE: sandbox Gradle lock denial, zero execution, FAIL |
| 20261028T084855.029488Z | Same selection: compile error LivingEntity vs PathfinderMob in new event guard, zero execution, FAIL; corrected via runtime entity |
| 20261028T085024.140106Z | 13 selected/completed, 10 passed, 3 failed: accuracy, close RPG, boundary recovery fixture |
| 20261028T085520.756534Z | 8 selected, interrupted by assertion thrown from a test event listener; no complete invocation receipt; FAIL |
| 20261028T085901.623053Z | 8 selected/completed, 4 passed, 4 failed: orphan, inactive snapshot, mixed API order assertion, ally blast fixture |
| 20261028T090233.638966Z | 3 selected/completed: mixed API passed; two lifecycle blast assertions failed |
| 20261028T090513.832925Z | 2 selected/completed/passed: both native block-hit lifecycle scenarios; PASS |
| 20261028T091330.608755Z | Combined: 2 selected/completed/passed, ally native RPG splash and optional NPCs native BURST; PASS |

First selection was `RpgAccuracyTests,FiringRegressionTests,ModeCommandTests,TransparentSightTests#firstAcquisitionAndNativeBurstThroughGlassThenStoneStopsRounds`.
Subsequent selections narrowed to new/failed/unfinished scenarios; exact commands are in receipts.
No successful unchanged full selection was rerun for confidence. No FULL, JFR, soak or load matrix ran.

Accuracy fixture fixes the NPC UUID, target position at 32 blocks and velocity, uses ordinary AK-47,
equal adjacent sample windows and native fire-event barrel angles relative to the ballistic aim point.
The completed 085901 run measured HARD 0.335721 degrees, DEFAULT 0.998616 degrees, 40/40 native fire
events and 80 actual joined native projectiles. The earlier interrupted measurement was
0.335130 / 1.006102 with 40/41 events and 81 projectiles; retained as partial evidence only.
The initial free-moving target lost contact after 30 rounds, so its result was rejected as an accuracy comparison.
This measures actual angular error and firing activity, not human hit rate, win rate or splash accuracy.

Boundary recovery injection now waits for a real visual observation instead of assuming that fixture
tick 12 guarantees contact. The assertion still requires EXHAUSTED, order clipping, recovery release
and new native projectiles. Mixed API compares the saved command kind; runtime STAND deliberately
maps to a DEFEND_AREA order state. These were fixture corrections, not relaxed production policies.

Lifecycle diagnostics found actual rockets discarded after four ticks by an entity-hit whose Pre was
correctly canceled before native explosion creation. Moving the target off the flight line after launch
forces block-hit splash against a real wall inside the ticking structure. Owner unload uses the native
removal reason. The inactive-match fixture explicitly attaches the persisted match token to an already
launched native projectile and registers inactive authority; this tests the stale projectile authority
path, not a complete TacticalTablet session. Ally splash similarly requires a real native explosion
with a protected living NPC introduced after launch. No fake GunFireEvent counts as a shot.

Ordinary dedicated command: `python tools/testing/run-accuracy-dedicated-check.py`.
Initial receipt `build/command-dedicated-check/20261028T090725/receipt.json` is FAIL: stale ordinary
build classes registered SelectedGameTests without a catalog. Preserved the old classes at
`build/stale-main-before-accuracy-dedicated-20261028`, then recompiled the ordinary output.
Final receipt `build/command-dedicated-check/20261028T090943/receipt.json`: PASS, 122.75 seconds,
two ordinary server processes, save/restart and natural remote chunk unload/load. Exact DEFAULT NBT,
UUID, name, native team, DEFEND128 order and weapon/ammo state compare equal before/after restart.
Real `/reload` and legacy UNFAIR migration preserve `defaultAimErrorMultiplier = 4.0`.
Original server.properties/EULA hashes and their unchanged state are in preservation.json.

Final STAGE assessment reuses final applicable per-scenario results across the preserved runs:
15 standalone scenarios plus 2 combined scenarios (17 unique scenarios in total), and 279 core checks.
The final combined run covers the changed ally fixture on its final inputs and shared optional BURST;
it does not substitute a full combined catalogue. The 085901 completed run covers the final accuracy,
close-distance/reload, NBT/command and boundary fixtures. The 090233 run covers final mixed API;
090513 covers final lifecycle block impacts. Unchanged command/passivity/glass scenario results
from 085024 are retained: the later live inventory read is false on their unchanged ordinary weapons,
and the new loaded-region check applies only to explosive native rounds. Later test edits are local
to the affected fixture methods and do not change these shared inputs. The first failed STAGE and
interrupted/failed FAST receipts remain failed; this is assembled coverage, not a new green full invocation.

Commands, dependency hashes, source/fixture identity and receipt/log hashes are indexed in
[validation manifest](testing/rpg-accuracy-validation-2026-10-28.json). No production JAR is delivered.

## Remaining scope

No human 1v1/1v4, production-JAR playtest, TacticalTablet binary launch, additional external gunpacks,
optional-adapter RPG balance test, owner dimension-transfer/restart of a flying rocket, or dedicated
owner-death combat scenario was run. Owner death uses the same fail-closed guard inspected in source.
Native penetration/destruction behavior is not simulated or guaranteed.
