---
name: forge-compat-hook
description: Build or review the Minecraft Forge 1.20.1 compatibility adapter for TACZ NPCs 2.1.0; prevent duplicate combat AI and unsafe Mixins.
---

# forge-compat-hook

## Inputs
Completed audit, exact JAR versions, desired hook behavior, Forge run configuration.

## Procedure
1. Read `docs/ARCHITECTURE.md`, `forge-addon/README.md` and audit signatures.
2. Generate a minimal ForgeGradle 6 / Java 17 project pinned to the verified versions; write `mods.toml` using verified mod ID, never guesses.
3. Implement `NpcRuntimeAdapter` to expose immutable snapshots and apply commands only on the server thread.
4. Establish a single owner for vanilla/base movement and shooting: disable or replace an original behavior before custom control starts. Ensure fallback on unsupported entity/version.
5. Avoid Mixins if possible. If absolutely necessary document target descriptor + injection and version guard. Test dedicated-server client-class separation.
6. Release on remove/unload/death/dimension change; don't retain strong entity references across chunks.

## Verify
Gradle compile; `runServer`; no-mod/base-only/addon smoke; target toggling; no duplicate shots and no crash after entity unload. Record actual commands/results.
