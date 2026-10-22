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
Use the affected compile tasks and scoped lifecycle/ownership/native-fire GameTests from [testing profiles](../../../docs/TESTING-PROFILES.md). Add ordinary dedicated startup for classloading/lifecycle/packaging risks. Run combined checks when the adapter is affected or FULL is requested; unavailable optional dependencies do not invalidate independent results. Record actual commands and duplicate-control/fallback evidence.
