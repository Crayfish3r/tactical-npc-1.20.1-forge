# Этап 09 — оружейный snapshot

Checkpoint: `3f9e810d0ce7684d78f438c91819c71e4db47539` + непринятые 08–09. Применён tacz-weapon-control. Native signatures проверены `javap` по pinned JAR.

Файлы: `compat/TaczGunController`, `compat/GunSupport`, `runtime/GunController`, `combat/TacticalCombatGoal`, `combat/VisibilitySensor`, финальный `debug/DebugCommands`; `WeaponSnapshotTests`; этот отчёт. Core не менялся.

Snapshot/observe больше не вызывают draw. Единственная tick maintenance обслуживает смену stack/id и resource generation; NOT_DRAW откладывает восстановление до следующего tick. Повторный вызов maintenance в tick ничего не делает. Snapshot между equip и maintenance закрывает fire. Кэшируются только index/profile (stack/id/mode/resource generation/native property object); ammo/busy/cooldown/sprint/reserve, запреты scripts/charge/heat/explosion, mutable attachment spread/range читаются заново. Одинаковый mutable property object не делает range/spread постоянными. Имеющийся WeaponState передаётся в occupied; после SUCCESS профиль повторно не считывается.

Одинаковый server-thread микрозамер, warmup 2000, затем 10000 чтений AK47, ThreadMXBean allocations:

| Метрика | До | После |
|---|---:|---:|
| Adapter index lookup | 20000 | 0 |
| Построения profile | 10000 | 0 |
| Время, ms | 60.9918 | 26.2670 |
| Выделено bytes | 17126792 | 11042320 |

Это один микроповтор, с JIT/host noise; не прогноз server p95. GunSupport/native внутренние lookup не включены в adapter counter. Snapshot сохраняет dynamic support checks. Pre-09 source с manifest сохранён в `forge-addon/build/batch-baseline` для одинакового ordinary dedicated fixture на 14.

Baseline FAST — 1/1 PASS, receipt `20261019T194636.877069Z-FAST.json`. STAGE `WeaponSnapshotTests,WeaponProfileTests,NeutralTests#retaliationCannotBypassNativeReload,NeutralTests#returningNeutralCancelsUncreatedNativeBurstRounds` — 13/13 PASS, 149.422 s, receipt `20261019T195005.928663Z-STAGE.json`. В том же графе compileJava/compileCompatJava. Core не изменялся, актуальные 792 PASS этапа 08. Полный datapack reload проверяется отдельно на 14; вызов существующего resource verifier/generation покрыт узким тестом.

Ручные действия владельца:
1. Equip AK47 → Glock во время reload и после draw; проверять новый draw/профиль и отсутствие выстрела до native readiness.
2. Finite ammo с малым reserve: magazine/chamber/reserve не восполняются чтением diagnostics, reload не дублируется.
3. Проверить AUTO/SEMI/BURST, стену/союзника между отложенными BURST rounds и смену режима.
4. Сменить разрешённые attachments и выполнить `/reload`: range/spread/support должны соответствовать native cache; explosive/charge/heat/script оружие остаётся заблокированным.

Automated PASS; WAITING_PLAYTEST. Production rebuild/verification перед общей передачей пакета на 14.

Итоговая14 проверка `/equip`: tagged tactical runtime больше не делает дополнительный command-side draw перед tick maintenance; для untagged native optional NPC прежнее поведение сохраняется. Реальная equip-команда включена в существующий invalidation regression. Последний combined STAGE `WeaponSnapshotTests,WeaponProfileTests,ModeCommandTests,NeutralTests#retaliationCannotBypassNativeReload,WeaponScenarioTests` — **25/25 PASS**,188.828s, receipt `20261020T043854.136293Z-STAGE-compat.json`. Это affected supplement после FULL, новый полный прогон без сквозного риска не повторялся.
