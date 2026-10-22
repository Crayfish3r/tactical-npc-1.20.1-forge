---
name: forge-compat-hook
description: Extend the existing Forge adapter and optional NPCs compatibility source set with verified APIs and a single owner of combat control.
---

# forge-compat-hook

Inputs: affected runtime/source set, [existing build](../../../forge-addon/build.gradle), [adapter guide](../../../forge-addon/README.md) and scoped audit evidence. Apply [project rules](../../../AGENTS.md).

## Procedure
1. Inspect the current architecture and main/compat/compatTest boundaries. Extend this ForgeGradle project; do not regenerate it or replace its verified mappings, resources or run configurations.
2. Verify the APIs and versions actually touched using [tacz-npc-audit](../tacz-npc-audit/SKILL.md). Require NPCs binaries only for its classes/signatures or adapter tasks. Standalone means the independent mod with TaCZ, without NPCs at runtime.
3. Extend NpcRuntimeAdapter only where the boundary needs it. Keep gameplay objects/interactions in Forge, immutable decisions in core and mutations on the server thread.
4. Prove single ownership before enabling custom movement/fire. Disable or replace competing base behavior and preserve a safe unsupported-version/entity fallback.
5. Keep generation/TTL validation and per-level cleanup on removal, death, unload, order/target change and dimension transfer. Avoid strong world/entity references in long-lived caches.
6. Check dedicated-server class loading. Use a Mixin only under the documented exception in AGENTS.md.

## Verify
Before running, identify changed files, behavior and the minimum affected compile/lifecycle/ownership/native-fire checks using [testing profiles](../../../docs/TESTING-PROFILES.md). Include ordinary dedicated save/restart for persistence/migration/lifecycle/config-loading/packaging risks. Select combined checks for optional-adapter, shared-integration or mixed-runtime risks, or an explicit request for that environment; do not repeat the full standalone set after each fix. FULL follows the shared owner-request/release/insufficient-selective-coverage rule. Unavailable optional dependencies do not invalidate independent results.

Diagnose FAIL first, then repeat the failing test and necessary related regressions. Reuse applicable final-input results for one final STAGE assessment, considering shared fixtures/helpers, resources, config, dependencies and runtime as well as source/test changes. Explain each additional run; preserve original FAIL and disclose coverage from multiple runs. Record actual commands and duplicate-control/fallback evidence.
