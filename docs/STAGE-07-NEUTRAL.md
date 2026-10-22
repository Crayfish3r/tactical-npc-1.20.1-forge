# Этап 07 — нейтральный spawn и подтверждённый damage

> Команды в этом датированном отчёте относятся к прежнему интерфейсу. Актуальные команды, постоянный Hard и таблица миграции: [COMMANDS](COMMANDS.md).

База: `main`, HEAD `eb1f04eefb2141adaefd91c90fb82b56a0d9c030`, без reset/checkout/push.
Владелец явно принял 06 в текущем чате 2026-10-19. Работа только над 07; 08–14 не начаты.
Предшествующий dirty diff аудита тестирования сохранён; stage-only receipts и pre-stage копии
находятся локально в `forge-addon/build/stage07-*`. Принятие 07 не предполагается.

Стек: Minecraft 1.20.1, Forge 47.4.20, Java 17 (Microsoft 17.0.19), TaCZ 1.1.8-hotfix2,
optional NPCs 2.1.0. Зависимости не обновлялись.

## Изменения

- `DebugCommands`: permission-2 `/spawn npc` добавляет только дочерний literal, не заменяет
  executor/permission чужого `/spawn`. Оба independent spawn-алиаса используют один путь:
  до четырёх соседних XZ candidates по 3 блока, до трёх высот через существующий safeStanding;
  только loaded chunks, подходящий floor без перечисленных hazards, collision/body и world border.
  Нет подходящей позиции — команда отклоняется. AK-47/ammo, spawnlegacy и optional factory сохранены.
- `TacticalNpcEntity`: независимая persisted neutral policy, transient attacker UUID/TTL.
  После super.actuallyHurt проверяется реальная потеря health/absorption; cancelled/zero damage
  не даёт hostility. Поддерживаемый living source, для projectile достоверный owner; self/mob/
  orphan не подходят. Обычные ограничения creative/spectator сохраняются.
- `CombatTargets`: базовые type/lifecycle checks отделены от alliance policy. Только текущий
  attacker нейтрального допускается персонально, включая фактически повредившего союзника;
  faction/team не меняются, третьи лица защищены. Эта же policy действует в native pre-damage guard.
- `TacticalCombatGoal`: нейтральный не acquire-ит соседей и не принимает слух/squad report как
  контакт. Реальный hit немедленно инвалидирует прежние команды и задаёт только candidate UUID.
  Повторный реальный hit того же источника продлевает контакт без сброса памяти/ADS;
  смена attacker инвалидирует старую generation. Для этого источника perception/reaction/acquisition wait отсутствуют; точный aim возникает
  лишь после бюджетного CLEAR LOS, поворот ограничен прежним turn rate. DEFERRED не даёт видимость.
  Разрешённая память остаётся frozen; после её завершения/смерти/unload/инвалидации attacker —
  возврат к neutral ожиданию. Overlay policy проверяется перед arbitration и на каждом native round.
  Native reload/bolt/ADS/cooldown/ammo и уже созданные projectile остаются в TaCZ.
- Explicit mode/order снимает neutral policy через прежний `setOrder` и тот же controller.
  Equip/ammo/faction/squad сами по себе neutral не активируют. Создание через низкоуровневую
  entity factory сохраняет прежний active default; NEUTRAL явно задаётся двумя spawn-командами.

NBT schema **3** добавляет `tacticalnpc.neutral`. Schema 1/2 мигрируют как прежние active NPC,
даже если поля neutral нет; schema 2/3 сохраняют anchor. UUID/serviceId/faction/squad/order,
radius 128 и native ItemStack/chamber/reserve не пересоздаются. Сохранение во время retaliation
после загрузки даёт neutral без live target/памяти/пути. Будущая неизвестная schema отключает AI.
Две старые GameTest-проверки номера schema обновлены до 3, проверки order/anchor/radius сохранены.

Новые `NeutralTests` — 12 сценариев: spawn/старые equip-ammo/permission; proximity/hearing/report;
реальный melee и death; personal allied source/replacement; cancellation/zero/unknown/self/mob;
физическая arrow collision; hidden source/TTL; native reload; uncreated BURST rounds; migration;
реальное попадание TaCZ-пули от NPC; frozen visual memory/expiry. `NeutralOptionalTests` проверяет
подтверждённый NPCs runtime как actual damage source. Human PvP ими не подменяется.

## Проверки

Используется `tools/testing/run-gametests.py`, profiles FAST/STAGE. Точные command/selection,
exit, counts, tasks и время записаны в JSON рядом с логами `forge-addon/build/test-runs/`.
Python этой машины: `C:/Users/makar/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
Gradle wrapper запускается из `forge-addon`; готовый cache разрешает `--offline`.
Core production-код не менялся: новый core-прогон этапа 07 не заявляется; прежний аудит проверил
792 checks, это отдельный исторический receipt.

Начальные FAIL сохраняются: первый FAST не скомпилировал тест из-за отсутствующего import
SquadRuntime (0 tests); после исправления FAST 3/3. Первый STAGE 22/24: reload fixture требовал
busy на фиксированном tick до принятия TaCZ reload; исправлен на damage при фактически начатой
native reload с сохранением busy/projectile assertions. Save/load patrol также упал; диагностика
и окончательные итоги приведены в итоговой записи ниже, автоматического принятия нет.

### Фактически выполненные прогоны 07

Каталог receipts ниже: `forge-addon/build/test-runs/`; соответствующий raw log имеет то же имя
с `.log` вместо `.json`. В JSON сохранена полная точная command/selection. Необработанные
многостраничные логи в отчёт не копируются.

| Запуск | Результат | Receipt |
|---|---|---|
| FAST compile, первый | FAIL, **0 tests**: отсутствовал import в новом тесте | `20261019T045321.433125Z-FAST.json` |
| FAST spawn/cancelled/NBT | **3/3 PASS**, 56.031 s | `20261019T045425.820046Z-FAST.json` |
| STAGE standalone union | **22/24 PASS**, 2 FAIL, 183.719 s | `20261019T045626.405999Z-STAGE.json` |
| FAST reload + save/load patrol | **2/2 PASS**, 89.344 s | `20261019T050106.537731Z-FAST.json` |
| FAST save/load с UUID упавшего NPC | **1/1 PASS**, 78.531 s | `20261019T050355.447377Z-FAST.json` |
| STAGE selected combined, включая same/new attacker | **7/7 PASS**, 90.703 s | `20261019T050703.018535Z-STAGE-compat.json` |

Всего **37 GameTest executions / 32 уникальных сценария**, включая два первых игровых FAIL;
в итоге каждый выбранный сценарий имеет PASS receipt. Из новых — 12 independent + 1 optional.
Это выбранная регрессия, не FULL. `compileJava`/`compileCompatJava` включены в launcher graph;
неизменившиеся задачи корректно UP-TO-DATE. `NO-SOURCE` не засчитывается как тест.

Два исходных игровых FAIL не скрываются:

- Reload fixture исправлен: ожидание фиксированного tick заменено на обнаружение настоящего
  native reload; damage не отменяет busy, до его окончания нет accepted shots/projectiles,
  затем есть native projectile. Production reload/cooldown не менялись.
- Save/load patrol: сериализация order/anchor/radius прошла, дальнейшее движение один раз нет.
  Необъяснённый ранний сбой этого теста уже был в принятом 06 (STAGE-JOURNAL, ограничения 06).
  После повторного PASS тест проверен с UUID исходного NPC
  `2ca0999a-f025-4308-88ba-5e2ec2f39aa8`: **318 entity ticks**, chunkTicking=true, neutral=false,
  несколько destinations и перемещение **14.528 blocks**. Добавлены диагностика и фиксированный
  UUID этого стенда; production navigation не менялась. Причина первоначального FAIL остаётся
  неустановленной; он не объявляется исправленным, ручной save/restart остаётся обязательным.

### Production и обычный dedicated

Из `forge-addon`: `.\gradlew.bat build --offline --console=plain` без test/profile/client flags,
без clean/rerun/exclusions — **PASS**, 18 s, 12 actionable tasks: 6 executed / 6 UP-TO-DATE.
Лог `forge-addon/build/stage07-production-build.log`.
Из корня: `python -B tools/verify-production-artifacts.py` — **PASS**;
`forge-addon/build/stage07-production-artifacts.json`. Java major 61; main без NPCs references,
test classes/helpers/арен/вложенных зависимостей. SHA проверены повторно после dedicated, совпали.

| Artifact | Bytes / classes | SHA-256 |
|---|---|---|
| `forge-addon/build/libs/tacticalnpcai-0.2.0.jar` | 171937 / 83 | `37E108D6C2FAD66294730915A7066BCA631ECD4B0C5ECF6E7D7CE1878F730B07` |
| `forge-addon/build/libs/tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `1BB5A010C5DB5CF57FED1060387C33761A33F7DF73CC0546ABCC740890270AA7` |

Оба pinned dependency SHA повторно совпали с INTEGRATION-AUDIT:
TaCZ `2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2`,
NPCs `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE`.

Финальный обычный `.\gradlew.bat runServer --offline --console=plain` — **PASS**, без init-script
и test/profile/client flags. Новый flat мир `run/standalone/stage07-neutral-smoke`, seed 7007,
127.0.0.1:25586; временный level-name в server.properties, без изменений gameplay-кода.
Done (3.631s), namespace `[]`; `help spawn`, `help npc`, затем:

```mcfunction
execute positioned 0 -60 0 run spawn npc
tag @e[type=tacticalnpcai:npc,limit=1] add stage07smoke
data get entity @e[tag=stage07smoke,limit=1] tacticalnpc
save-all flush
data get entity @e[tag=stage07smoke,limit=1] HandItems[0].tag
tacticalnpc diagnostics
stop
```

Реально создан independent UUID `bdfe06bf-c97a-46dd-a9a9-239944868c91`: schema=3, neutral=1b,
anchor=(3.5,-60,0.5), HOLD_POSITION/24, AK-47, INFINITE reserve policy. Save/stop успешны,
все dimensions сохранены, Gradle exit 0 / BUILD SUCCESSFUL за 1m18s.
`forge-addon/build/stage07-dedicated-smoke.log`.
В консольной сцене без игроков native magazine остался 0, projectile/gates samples пусты:
этот smoke доказывает startup/commands/save/stop, **не** native maintenance/бой/latency.
Native reload и projectile проверены отдельными реальными GameTests выше.

Ошибки подготовки dedicated тоже сохранены. Две попытки init-script завершились до Minecraft
(`stage07-dedicated-first.log`, `-second.log`). Следующие попытки ForgeGradle перезаписал на
старый run/standalone: третья остановлена при подготовке региона (`-third-cancelled.log`),
четвёртая достигла Done и штатно остановлена без создания NPC/команд (`-fourth-old-world.log`).
Прежний `independent-smoke` поэтому **загружался и сохранялся**; откат его мира не выполнялся,
неизменность runtime state мира не утверждается. Ошибки helper не объявляются игровыми PASS.
После этого использован обычный runServer с новым level-name, как описано выше.

Forge автоматически обновил прежний server config; сверено, что значения старых ключей не
изменились, добавлялись только reacquireTicks/activeAcquisitionTicks и комментарии.
Исходные properties/TOML восстановлены побайтово из проверенных сохранённых копий:

- `run/standalone/server.properties` — `E0F9353DD613EF3E426B1B1C4CA4097881568C6F6C9BBB78FDE89563C9375658`;
- `run/standalone/independent-smoke/serverconfig/tacticalnpcai-server.toml` —
  `46B775E8556A2966C1876613955FCC3BA29FC8D49533CF89188977166081B53A`;
- EULA не изменена, ранее принятая: `E4DB182AA77B5E31657F4B83F8973C79A8C65217A4383200F9F59F7C94752FAD`.

Production JAR/исходники после production build не менялись. Измерений ускорения в 07 нет,
цели производительности не заявляются. Полная матрица, профиль/JFR/soak/30+ NPC, graphical client,
human 1v1/1v4 и отдельный disk restart — **NOT RUN**. Проверки 07 завершены;
остается приёмка владельца. **WAITING_PLAYTEST — ОСТАНОВКА**.

## Ручная приёмка владельца

Использовать копию мира/сборки, основной production JAR + pinned TaCZ; optional artifact только
вместе с NPCs 2.1.0. Команды требуют OP permission 2. Игроки проверяют в survival.

1. На ровной свободной площадке `/spawn npc`; пометить единственного нового NPC:
   `/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage07`.
   Подойти, стрелять рядом, привлечь другого игрока: NPC не начинает огонь.
   `/data get entity @e[tag=stage07,limit=1] tacticalnpc` показывает schema 3, neutral:true.
2. Нанести этому NPC один реальный melee hit. Он начинает поворот сразу с прежним ограничением
   скорости и отвечает только ударившему survival-игроку. Второй игрок проходит рядом без атаки.
   Второй игрок сам наносит реальный hit — только теперь ответ может переключиться на него.
3. Повторить с projectile; между ударами поставить стену. За стеной нет точного сопровождения
   или прострела. Убрать стену в течение контакта — допустим обычный ответ после LOS/turn/native gates.
   Убить/удалить источник либо дождаться завершения legitimate memory: neutral не выбирает третьего.
4. `/tacticalnpc equip @e[tag=stage07,limit=1] tacz:ak47`, затем
   `/tacticalnpc ammo @e[tag=stage07,limit=1] finite 30`; ударить во время reload.
   До завершения native reload огня нет. Проверить также `/tacticalnpc spawn` и прежний ammo infinite.
5. `/save-all flush`, штатно остановить/перезапустить копию сервера. Проверить native magazine/
   chamber/reserve, serviceId/faction/squad/order/anchor, neutral без прежнего live attacker.
   Настроенный schema 1/2 NPC сохраняет прежний active order; defend 128 не обрезается.
6. Проверить явное управление прежним NPC:
   `/npc mode attack @e[tag=stage07,limit=1]`, затем
   `/tacticalnpc order @e[tag=stage07,limit=1] PATROL 24`.
   Это активирует существующий controller; этап 08 с новой STAND-последовательностью здесь не реализован.

Ограничения: нет human 1v1/1v4, полного release/perf matrix и проверки произвольных gunpacks.
Скрытый damage без LOS даёт только временную identity, без направления поиска по точной позиции.
Безопасное размещение — ограниченный локальный поиск, не поиск места по всему миру.
