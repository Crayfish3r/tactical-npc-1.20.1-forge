# Обновление team/команд — 2026-10-20

> Команды в этом датированном отчёте относятся к прежнему интерфейсу. Актуальные команды, постоянный Hard и таблица миграции: [COMMANDS](COMMANDS.md).

**WAITING_PLAYTEST.** Владелец согласовал отдельное обновление пункта1 после пакета08–14. Разработка интеллекта/новых тактик не входит в объём. HEAD `3f9e810d0ce7684d78f438c91819c71e4db47539`, исходные рабочие изменения08–14 сохранены; commit/reset/push/merge не выполнялись. Minecraft1.20.1/Forge47.4.20/Java17/TaCZ1.1.8-hotfix2, optional NPCs2.1.0.

## Новые команды

```mcfunction
/spawn npc
/spawn npc team red
/spawn npc team "Красная команда" count 4
/spawn npc team blue count 4
/npc team red
/npc team red @s
/npc team clear
/npc info
```

Новые независимые NPC сразу в ATTACK, с native AK47 и прежним ammo policy. NPC без team — одиночка: другой NPC без team ему не союзник. Survival/adventure игроки — враги кроме членов той же team; creative/spectator не атакуются. Одинаковая team даёт союз и автоматическую существующую squad-координацию. Имена:1–64 символа, кавычки для пробелов/кириллицы, без управляющих/внешних пробельных символов. Для назначения team с именем `clear` используйте кавычки.

Count по умолчанию1, диапазон1–16. Максимум49 проверок позиций на NPC: одиночный spawn использует сетку ±6 блоков, count>1 — ±12 с интервалом4 блока, только загруженные безопасные позиции/граница мира/свободные тела. При нехватке места создаётся только безопасная часть, команда возвращает и сообщает фактическое число. Оружие/патроны и native gates не обходятся.

У `/npc team` и `/npc info` без targets выбирается NPC в прицеле до32 при LOS; консоль требует targets. Team с targets принимает живых независимых NPC и игроков, проверяет весь список до изменения. Info максимум32 NPC, без draw/reload side effects. Permission2; чужая ветка `/spawn` сохраняется. `/tacticalnpc spawn` — тот же новый путь; spawnlegacy/optional spawn не менялись.

Команды `attack`, `defend 4..128`, `stand`, отдельный `PATROL`, equip/ammo/hard/diagnostics сохранены. Legacy `/tacticalnpc faction <npc> <name>` назначает native team, `/tacticalnpc squad <targets> <UUID|clear>` назначает team с именем UUID либо убирает team, с сообщением о совместимости. Отдельной mutable faction/squad больше нет.

## Принадлежность и сохранения

Штатный Minecraft scoreboard — единственный источник принадлежности и её сохранения (`scoreboard.dat`, UUID entry NPC / имя игрока). `/team join`, `/team leave`, `/team remove` согласованы с новым интерфейсом. Внутренний squad UUID связан с объектом native team, не показывается/не настраивается пользователем; радио остаётся per-level, прежние48 blocks/10tick delay/TTL/budgets/roles/reservations сохранены. Удаление и создание одноимённой team не наследует старые reports.

Изменение собственной team сбрасывает старые frames/target/memory/claims до нового native round, в том числе при `/team` до следующего entity tick. Текущая принадлежность цели проверяется как для DIRECT, так и для suppression; уже созданные tactical projectiles сохраняют native pre-damage ally guard.

Schema5 сохраняет order/anchor/radius128/standPhase/serviceId и native ItemStack/ammo. Team не дублируется в entity NBT как второй источник истины. Schema1–4: прежняя faction становится team только при отсутствии native membership; существующая native team имеет приоритет, конфликт явно диагностируется. Исходные faction/squad UUID архивируются в `legacyFaction`/`legacySquad`, старые order/neutral сохраняются. Группы старой одной faction теперь координируются вместе; архивный squad больше не отделяет союзников. Native team mutation выполняется при server activation, не в асинхронном NBT decoder; pending migration переживает resave. У schema5 удалённая vanilla team не восстанавливается из устаревшей entity-копии.

У старых сохранённых NEUTRAL режим сохраняется до явного `/npc mode attack ...`; новый spawn всегда ATTACK. Optional NPCs adapter пока сохраняет собственные spawn/equipment правила; новый `/npc team` ориентирован на independent NPC/players. Установка нового distribution JAR в отдельный клиент/modpack и human PvP требуют ручной проверки.

## Изменённые файлы

- `runtime/NpcTeams.java`: единая native membership, ally policy и transient squad identity.
- `entity/TacticalNpcEntity.java`: schema5, server activation migration, team lifecycle; старые режимы/оружие сохраняются.
- `debug/DebugCommands.java`: короткий spawn/team/info, безопасный count, групповой интервал4, явные player targets; старые алиасы.
- `combat/TacticalCombatGoal.java`: проверка текущей team перед каждым native round и eligibility для suppression.
- `TeamTests`/`TeamCommandTests` и затронутые legacy fixtures: фактические team/NBT/native BURST, сохранены уникальные LOS/friendly-fire/NEUTRAL/DEFEND/STAND/DEFERRED/слух/укрытия.
- `ScenarioIsolation`, `run-gametests.py`, `build.gradle`: свежий runtime на receipt, непрерывные tickets общих fixture chunks; без нового framework.
- `TeamCommandProfile`, `run-team-profile.py`, JFR summarizer/verifier и документация. Production ai-core/optional adapter и зависимости в этом обновлении не менялись; прежний рабочий пакет08–14 сохранён.

## Фактические проверки

| Проверка | Фактически выполнено | Итог |
|---|---:|---|
| `bash ai-core/run-tests.sh` |72+94+38+235+353 =792 checks|PASS|
| STAGE new team/native/NBT/range/legacy reload без optional |12 GameTests,105.031s|PASS|
| STAGE placement/permissions/partial/aliases после интервала4 |5 GameTests,72.735s|PASS|
| Финальный STAGE после запрета implicit player |5 GameTests,75.297s|PASS|
| Ordinary dedicated,4×4 через реальные команды и обычные позиции |8 NPC,600 samples,63 projectile;3 red/1 blue shooter|PASS функционального fixture, не human PvP|
| Ordinary dedicated,30 NPC на разнесённых линиях |30 controllers,600 samples,119 projectile;1 red/1 blue shooter|PASS ограниченного fixture, не30 одновременно стреляющих|
| Реальная JFR/streaming analysis |145 measured server CPU samples; CPU/alloc scopes раздельны|PASS анализа, sampling имеет ограниченную точность|
| Plain production `build --offline --console=plain` + verifier |18s,6 executed/6 UP-TO-DATE;94+4 classes,Java61|PASS|
| Plain dedicated reload/save/stop/restart/load/stop |102.171s, точное сравнение policy/UUID/native stack/team; properties/EULA неизменны|PASS|
| Human1v1/4v4, установка нового distribution JAR в клиент/modpack |не выполнялись|NOT RUN|

Команды из корня (`python` — рабочий Python3, `bash` — Git Bash):

```powershell
python tools/testing/run-gametests.py STAGE --offline --tests TeamTests,TeamCommandTests,ModeCommandTests#aimRangeAndUnsupportedFrontEntity,NeutralTests#retaliationCannotBypassNativeReload
python tools/testing/run-gametests.py STAGE --offline --tests TeamCommandTests,NeutralTests#aliasesSafePlacementPermissionsAndOldEquipment
python tools/testing/run-team-profile.py --per-team 4
python tools/testing/run-team-profile.py
python tools/testing/summarize-batch-jfr.py forge-addon/build/team-profile/20261020T082611/profile.jfr
# После всех test/profile запусков, из forge-addon:
.\gradlew.bat build --offline --console=plain
# Снова из корня:
python tools/verify-production-artifacts.py
python tools/testing/run-dedicated-check.py
```

Полные команды/selection/task outcomes/raw-log paths/исходные receipts и SHA: [machine proof](testing/team-validation-2026-10-20.json). Отдельные compileJava/compileCompatJava повторно не запускались: они уже входят в GameTest/build graph. NO-SOURCE,0 GameTests и FakePlayer не засчитываются как человеческий сценарий.

### История неуспешных запусков

Первый STAGE33:32PASS/1FAIL (aim fixture); неправильное имя NBT selection:0/0 FAIL, не PASS. Safety STAGE9:7PASS/2FAIL (aim/BURST), затем2/2 PASS. Первый FULL standalone109:105PASS/4FAIL (aim, non-ticking lifecycle fixture, idle patrol, hearing с устаревшим предположением о союзе teamless); affected13/13 PASS. Первый combined FULL121:118PASS/3FAIL (range/lifecycle/approach-ADS); affected17:16PASS/1FAIL (range). Отдельный range1/1 и весь ModeCommandTests5/5 PASS; fixture сохраняет строгие ray/hitbox/LOS/native-index проверки.

Свежий combined FULL121:120PASS/1FAIL в legacy reload fixture. В нём отделена проверка reload от случайного движения; последующие reload проверяют отсутствие новых запросов/projectile внутри busy интервала, а не нулевой lifetime counter. Реальный ответ после reload/ADS/cooldown обязателен. Его повтор вошёл в12/12 PASS. Первопричина каждого transient не объявляется доказанным production-дефектом. FULL receipts остаются **FAIL**; минимальные правки стенда и scoped повторы указаны отдельно. После последних изменений placement и implicit targeting выполнены затронутые5/5; новая полная матрица не повторялась без риска для остальных подсистем.

Начальный ordinary harness упал на сборе population непосредственно в ServerStarted; сбор заменён uncancelled native EntityJoin. Плотный15×15 с первоначальным интервалом2:0 projectile/FAIL. Старый4×4:40 projectile только red/FAIL. Групповой spawn изменён на интервал4; новый4×4 дал63 projectile от обеих team. Текущий плотный15×15 после этой правки — **NOT RUN**. Профиль30 использует fixture-only lanes через6 блоков; weapon/ammo/LOS/turn/safety не обходятся. После профиля добавлен только guard implicit-player команды: проверен scoped5/5; измеренный spawn/native combat path не менялся.

## Измерения и ограничения

Новый30-NPC fixture: tick p50/p95/p99=4.661/11.115/13.662ms (server START→telemetry END window); AI=1.344/3.859/5.001ms, TPS19.998. Query denials0, фактически стреляли лишь2/30 NPC. Это **не** профиль30 одновременных стрелков и не доказательство отсутствия starvation у каждого участника. Target AI p95≤2ms не достигнут. Малая сцена4×4: tick p95=7.683ms, AI p95=1.818ms,63 rounds.

Никакого speedup/before-after performance credit: изменены геометрия и фактическое число rounds. Плотность/занятые сектора всё ещё могут приводить к безопасному ожиданию; тактическое освобождение секторов относится к будущему обсуждению пункта2. Крупная матрица14/soak не повторялась. [30 metrics](testing/team-profile-2026-10-20.json), [4×4 metrics](testing/team-four-2026-10-20.json), [CPU/JVM/seed/modlist/config/geometry](testing/team-environment-2026-10-20.json), [JFR hot stacks/allocations/GC](testing/team-jfr-2026-10-20.json). Raw recordings/worlds/logs остаются в ignored build.

## Production JAR

Проверены именно plain reobfuscated distribution JAR, без combatTests/profile/clientSmoke; main не ссылается на NPCs, core без игровых imports, без embedded dependency JAR/арен/fixtures. Для копии сборки заменить прежний основной JAR на клиенте и сервере; optional artifact нужен только вместе с NPCs2.1.0. Human balance/client distribution acceptance ещё не выполнены.

| JAR | Bytes/classes | SHA-256 |
|---|---:|---|
|`forge-addon/build/libs/tacticalnpcai-0.2.0.jar`|198277/94|`615EAA1890D58F573F811FFD1FB01BB06AA28FB997D622A74FCBAEB0D3D52DAD`|
|`forge-addon/build/libs/tacticalnpcai-0.2.0-npcs-compat.jar`|7680/4|`8745810024D5B69F6896BF56F6429112C4BA43676991B7D71272139FEC364D05`|

## Ручная приёмка владельца

1. В копии мира перейти в creative; для воспроизводимого smoke поставить группы вдоль X (восток/запад), лицом друг к другу. Выполнить две команды: `/spawn npc team red count 4`, затем в20–30 блоках `/spawn npc team blue count 4`. Перед созданием второй группы повернуться к первой; наблюдатель должен отойти в сторону от секторов огня. Команды сами создают/экипируют/активируют группы. Проверить реальный бой, защиту своих и отсутствие пересекающихся spawn bodies.
2. Отдельно создать двух `/spawn npc`: они враги друг другу. Присоединить обоих к одной team — прекращают огонь и координируются. `/npc info` показывает фактическую принадлежность и ammo.
3. `/npc team red @s`, затем survival: красные союзники, синие враги. Проверить adventure, затем creative/spectator. Обычный `/team join/leave` должен давать те же отношения.
4. Во время native BURST присоединить текущую цель к союзной team: новые rounds прекращаются; сменить team самого стрелка, проверить отсутствие старых reports/peek/path/claims.
5. Проверить defend4/32/128, stand WAIT→COMBAT6→DEFEND32 и PATROL; смена team не должна сбрасывать выбранный режим/anchor. Перезапустить мир: team/UUID/serviceId/оружие/chamber/reserve/радиусы/фазы сохраняются.
6. Проверить старую копию мира schema1–4: миграционные сообщения/приоритет существующей vanilla team, архив старой faction/squad; прежний NEUTRAL не активируется сам. Сообщать команды, режим, team, weapon и ожидаемое/фактическое поведение при дефекте.

Статус: **WAITING_PLAYTEST**, не ACCEPTED. Пункт2 не начат; commit/push/merge/reset не выполнялись.
