---
name: hardcore-pvp-brain
description: Develop snapshot-based PvP combat policy with useful state commitment and explicit Hard versus authorized Unfair information rules.
---

# hardcore-pvp-brain

Inputs: current core policy, observation provenance, effective profile and combat evidence. Apply [project rules](../../../AGENTS.md).

## Procedure
1. Keep decisions deterministic from immutable snapshots and keep game interaction in the adapter. Validate identity/generation/TTL before execution.
2. In Hard, use only legitimate observations with expiry/uncertainty. In Unfair, accept only the implemented nonvisual UNFAIR_HINT channel described in AGENTS.md; do not invent visual velocity, relay it as VISION or bypass neutral/eligibility restrictions.
3. Keep acquisition, reaction, turn, accuracy and observed-velocity prediction separate. Read effective settings from [ServerConfig](../../../forge-addon/src/main/java/dev/zuma/tacticalnpc/config/ServerConfig.java) and validity caps from their checking classes, rather than adding another numeric profile in this skill.
4. Evaluate useful actions with commitment/hysteresis and explicit emergency interrupts. Separate tactical goal selection from route execution; a routine decision refresh must not restart a committed action without progress.
5. Use actual weapon metadata through the existing weapon adapter/profiles. Preserve physical and allied-fire gates and native cooldown/reload/bolt behavior in both difficulties.
6. Compare range accuracy, first-fire/hit latency, cover use and action success on comparable maps. Human TTK/win rate and counterplay require human playtest; do not manufacture challenge by arbitrary damage or hidden DIRECT tracking.

## Verify
State changed files, behavior and the minimum checks before running. Select affected [core suites](../../../ai-core/run-tests.sh) when core/contracts change and scoped combat GameTests using [testing profiles](../../../docs/TESTING-PROFILES.md). Include expiry/provenance, emergency interruption and profile-switch regressions when touched. Diagnose FAIL before retrying the failing test and necessary related regressions; explain each additional run and retain original FAIL.

Reuse applicable final-input results for one final STAGE assessment under the shared input/dependency rules; disclose multiple-run coverage. FULL, combined and profiling follow the current risk/request policy, not historical stage mandates. Report effective settings and reproducible seeds without promoting historical or scoped results to a new fully green FULL.
