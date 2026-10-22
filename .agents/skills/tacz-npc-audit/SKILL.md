---
name: tacz-npc-audit
description: Verify pinned Minecraft Forge and TaCZ APIs for the changed layer and audit optional NPCs signatures only when that adapter is involved.
---

# tacz-npc-audit

Apply [project rules](../../../AGENTS.md) to the API surface actually used. Inputs: changed subsystem, pinned dependency/source manifest and [integration evidence](../../../docs/INTEGRATION-AUDIT.md).

## Procedure
1. Classify the work as pure core, independent Minecraft/Forge navigation, TaCZ control or optional NPCs integration. Reuse verified evidence when its version/signature still matches.
2. Inspect relevant JAR metadata, mod IDs, registry IDs, hashes and source/bytecode signatures. Minecraft/Forge and TaCZ evidence does not require NPCs internal classes.
3. For NPCs-specific work, inspect the pinned NPCs 2.1.0 entity, ownership hook and API; older branches are references, not signature evidence. Missing artifacts block only that affected work/check.
4. For TaCZ calls, verify IGunOperator/IGun, entity support, native operation/event semantics and server/client separation. Never guess class or method names.
5. Choose a supported API/interface/event first. Document ownership transfer and fallback; justify a source fork or narrowly gated Mixin only when needed.
6. Update the audit with reproducible commands, signatures, versions and unresolved compatibility risks. Preserve license/attribution evidence if reusing code.

## Verify
Give an explicit go/no-go per affected API/adapter. Choose minimal checks before running under [testing profiles](../../../docs/TESTING-PROFILES.md); API/instruction documentation alone needs static links/frontmatter/diff checks, not game JVMs. Reuse verified signatures and validation receipts only while their relevant source/test/helper/resource/config/dependency/runtime inputs remain applicable; changes invalidate affected evidence, not all history. Explain each additional run, retain original FAIL and disclose multiple-run coverage at the final STAGE assessment.

Continue independent work when only the optional NPCs audit is unavailable; mark optional compilation/runtime checks NOT RUN or BLOCKED rather than inventing a result. Numerical behavior comes from the source owners in AGENTS.md; historical audits do not impose new FULL runs.
