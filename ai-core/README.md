# ai-core / Java 17

Compile and test with `bash run-tests.sh`. This is intentionally a pure Java module with no Forge/TaCZ dependencies; it tests deterministic tactical policy and basic intercept math. Classes here are an **illustrative vertical slice**, not finished PvP bot AI. Include this module in a later ForgeGradle build via Gradle composite/subproject once the actual mod adapter has been verified.

The `firePermitted` flag is *only the policy's authorization*, not a substitute for TaCZ's own cooldown/ammo/aim and entity-safety checks.
