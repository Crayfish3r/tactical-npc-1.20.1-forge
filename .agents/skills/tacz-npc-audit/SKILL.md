---
name: tacz-npc-audit
description: Audit exact TACZ NPCs and TaCZ versions, decompile or inspect JARs, locate verified entity AI hooks and versioned APIs before any Forge integration or Mixin.
---

# tacz-npc-audit

## Inputs
Exact installed JARs, Forge version, mod list, hashes, relevant source links.

## Procedure
1. Read `docs/INTEGRATION-AUDIT.md` and existing audit evidence first.
2. List JAR files; read `META-INF/mods.toml`, exact modIds, versions, mapped package names and entity IDs; record SHA256 hashes.
3. Use legally obtained source or `javap`/decompiler to identify NPC entity, brain tick, AI goals/sensors, shot controller, ownership and registry. Treat legacy Corrinedev code as history only.
4. Confirm the TaCZ `IGunOperator`/`IGun` API version and NPC entity's actual support before writing calls; record all relevant class/method signatures.
5. Choose extension API > verified source fork > narrowly version-gated Mixin. Document how to stop the base AI without duplicate firing.
6. Fill audit table with references and a compatibility risk register.

## Output / validation
An evidence-based compatibility report; no speculative class imports; explicit go/no-go for adapter. Stop implementation when 2.1.0 artifacts are unavailable.
