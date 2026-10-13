# Ревью Tactical NPC AI 0.2.0 перед альфой

Дата: 11 октября 2026. Проверен текущий рабочий каталог, а не отдельный Git diff. Production Java не изменялся. План: [ALPHA-DEVELOPMENT-PLAN](ALPHA-DEVELOPMENT-PLAN.md). Промпт: [ALPHA-IMPLEMENTATION-PROMPT](ALPHA-IMPLEMENTATION-PROMPT.md).

## Вывод

Развивать существующее ядро имеет смысл: серверная интеграция, native TaCZ lifecycle, immutable snapshots, arbitration, отдельный NPCs adapter и проверка оружия уже представляют значительный объём полезной работы. Переписывать их вместе с ИИ не нужно.

Основной долг — поведение между выстрелами: удержание контакта, восстановление после неудачного пути, приоритет аварийных действий, содержательный выбор манёвра и завершение приказов. Больше информации о цели поможет преследованию, но само по себе не исправит эти состояния.

Для быстрой альфы рекомендую одну боевую политику и два runtime: собственный `tacticalnpcai:npc` плюс уже существующий адаптер NPCs 2.1.0. Если нужны готовые скины, loadouts и спавн, адаптер даёт их без нового форка. Переход целиком на другой мод имеет смысл только после сравнительного игрового испытания.

## Проверенный стек

Minecraft 1.20.1; Forge 47.4.20; Microsoft OpenJDK 17.0.19+10; TaCZ 1.1.8-hotfix2; optional NPCs 2.1.0; ForgeGradle 6.0.54; wrapper Gradle 8.8; мод 0.2.0.

SHA-256 локальных зависимостей перепроверены и совпали с INTEGRATION-AUDIT:

- `forge-addon/libs/tacz-1.20.1-1.1.8-hotfix2.jar`: `2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2`.
- `forge-addon/libs/tacznpcs-2.1.0-1.20.1.jar`: `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE`.

## Находки по приоритету

### R1 — P1: цикл укрытия отменяет аварийное отступление

`ai-core/.../MovementPlanner.java:14–23`; входная аварийная политика: `CombatBrain.java:37–40`.

При healthRatio=0.1, доступном укрытии и предыдущем PEEK `CombatBrain` выдаёт RETREAT. Затем `MovementPlanner` выбирает ветку предыдущего PEEK, проверяет лишь пустой магазин, текущий damage и возраст >=60, возвращая движение к peek. Низкое здоровье в этой ветке не проверяется. Аналогично аварийный смысл нового решения может уступить старому циклу укрытия.

Следствие: раненый NPC продолжает выходить к противнику. Требуется явный приоритет аварийных переходов перед продолжением commitment; обычные манёвры могут сохранять commitment, экстренные должны его прерывать. Воспроизведено Java probe.

### R2 — P1: REPOSITION без укрытия означает остановку

`ai-core/.../CombatBrain.java:41`; `CombatDecision.java:15–30`; `MovementPlanner.java:7`; `forge-addon/.../MovementExecutor.java:34–49`.

При заблокированном маршруте и отсутствии cover выбирается REPOSITION, но `bind()` не задаёт для него движение. Planner сразу возвращает результат, потому что список covers пуст. На ровной площадке без укрытий восстановление маршрута поэтому может превращаться в HOLD до завершения backoff. Backoff фиксирован на 160 ticks, то есть 8 секунд при 20 TPS.

Нужен recovery с ограниченным числом альтернатив: соседняя доступная точка, другой угол подхода, промежуточная точка, ограниченный повтор, затем явный статус недостижимости. Отказ одного destination не должен запрещать все движения. Воспроизведено Java probe.

### R3 — P1: повторный peek получает полный цикл обнаружения и реакции

`forge-addon/.../TacticalCombatGoal.java:146–159,216–217`; `config/ServerConfig.java:19,25`.

Любой провал FOV/LOS сбрасывает `targetId`, `candidateId` и `seenSince`. Возвращение того же игрока обрабатывается как новое знакомство. С настройками по умолчанию задержки acquisition (до 9 ticks), perception (4) и reaction (6) складываются; очередной planning tick и поворот могут добавить время. Для уже направленного и готового NPC базовый путь составляет примерно 10–19 ticks до дополнительных задержек, а не только заявленные reactionTicks=6.

Особенно легко эксплуатировать короткие выходы из-за угла: NPC сохраняет last-known, но готовность отвечать заново теряется. Нужно отделить идентичность контакта от текущей видимости; при свежем повторном контакте использовать короткую reacquire-задержку. Потеря LOS немедленно запрещает DIRECT, но не должна обнулять всю готовность. Проверено по коду; измерения человек/NPC в этой работе не выполнялись.

### R4 — P2: текущая готовность оружия остаётся заблокированной старым решением

`ai-core/.../CombatBrain.java:43,51–53`; `ActionArbiter.java:29–33`; `forge-addon/.../TacticalCombatGoal.java:196–201`.

`cooldownReady=false` записывается в `firePermitted=false` и weaponAction=NONE. Runtime пересчитывает политику раз в 4 ticks либо при изменении rounds/busy, но переход cooldown false→true не является триггером. Поэтому даже свежий Safety с ready=true не разрешит стрельбу до нового решения. Подобная задержка встречается после окончания паузы очереди.

Решение: политика выражает намерение стрелять, arbiter/native API проверяют текущую техническую готовность; либо добавить корректный cooldown-edge trigger. Кэширование геометрии/тактики допустимо, устаревшая готовность не должна задерживать выстрел. Воспроизведено Java probe.

### R5 — P2: HOLD без peek не имеет выхода по таймауту

`ai-core/.../MovementPlanner.java:16–18`; `forge-addon/.../CoverSensor.java:38–44`.

Если cover выбран, но peek не найден, HOLD продолжается независимо от возраста состояния, готовности оружия и нового базового решения. CoverSensor может успешно продлевать такое укрытие каждые 20 ticks; это не гарантирует, что peek когда-либо появится. В сценарии с непроходимыми боковыми выходами NPC способен оставаться пассивным, пока жива память контакта, а при новых наблюдениях — ещё дольше.

Требуется различать временное укрытие для reload и боевую позицию с выходом; добавить срок HOLD, выбор другого cover/угла или возврат к задаче. Воспроизведено Java probe. Это не утверждение, что любое укрытие всегда вызывает зависание.

### R6 — P2: aim использует координату из кэшированного решения

`forge-addon/.../TacticalCombatGoal.java:169–174,196–205`; `ai-core/.../AimLead.java`.

Зрительное Observation обновляется каждый tick, но `turn()` получает `decision.aim().point()`, обычно пересчитываемый раз в 4 ticks. При ожидании оружия/повороте точка может отставать на 3 ticks; изменения rounds иногда сокращают это окно. `AimLead` существует только как demonstrator и используется тестом, production вызова нет.

Нужно обновлять tracking/aim для того же валидного визуального контакта каждый tick отдельно от частоты смены тактики. Упреждение вводить лишь после подтверждения единиц projectile speed и с ограничением горизонта. DIRECT не должен получать скрытые координаты. Проверено по коду, величина потери точности не измерена.

### R7 — P2: исчерпание ray budget интерпретируется как потеря видимости

`forge-addon/.../VisibilitySensor.java:34–37`; `TacticalCombatGoal.java:146–149`; `AiWorkBudget.java:22–27`.

`clear()` возвращает false и при стене, и при отсутствии вычислительного бюджета. Последний случай сбрасывает контакт/реакцию. Cover и native fire используют тот же бюджет; при насыщении производительность может превращаться в изменение боевого поведения и постоянное повторное обнаружение.

Нужен результат CLEAR/BLOCKED/DEFERRED. DEFERRED запрещает выстрел без свежей проверки, но сохраняет контакт и состояние готовности; LOS для активных контактов получает приоритет перед дополнительным cover sampling. Массовое воспроизведение именно starvation в этой работе не запускалось.

### R8 — P2: единый conservative corridor блокирует огонь из-за любых существ за целью

`forge-addon/.../VisibilitySensor.java:54–76`.

Проверяется луч длиной 128 blocks; в него попадают все LivingEntity, не только союзники, с расширением по максимальному cached spread. Стены между целью и посторонним существом не учитываются. Коровы, второй противник или существо за стеной могут запрещать выстрел. При 32 candidates стрельба также полностью блокируется.

Это осознанная MVP-консервативность, но для альфы она может выглядеть как «NPC видит и не стреляет». Разделить blockers/союзников/прочие цели, ограничить коридор подтверждённой геометрией и баллистикой, сохранить финальный allied damage guard и защиту native BURST rounds. Не удалять проверку союзников ради темпа стрельбы. Частота этого эффекта на пользовательской карте не измерена.

## Пробелы поведения, отдельные от дефектов

- **ASSAULT пока не самостоятельная задача.** `OrderPlanner.java:5` возвращает общий combat без изменений. Видимую цель NPC держит на оружейной дистанции, скрытую ищет по единственной last-known точке. Нет специальной агрессии, перехвата маршрута, завершения штурма или цели штурма как позиции.
- **PATROL не поддерживает требуемое chase-and-return.** `OrderPlanner.java:11–17` выбирает четыре cardinal точки с переключением каждые 160 ticks, независимо от достижения. Patrol leash продолжает действовать и в бою; при выходе за него NPC возвращается к anchor. Нет сохранённого route index и отдельной боевой подзадачи с возвратом после ликвидации/утраты контакта.
- **SUPPRESS уже реализован, но очень узко.** `SuppressionPlanner.java:4–10` целится ровно в `Observation.position()`. Если эту старую точку закрыла стена, огонь запрещён. Если игрок ушёл в сторону, а прежняя точка открыта, native выстрелы возможны: это покрывает существующий SuppressionTests. Нет семантики дверей/окон, области рассеивания, выбора выходов, вероятности тактического решения и роли suppressor с собственной политикой.
- **FLANK не доходит до runtime.** В `TacticalCombatGoal.java:195` routes всегда `List.of()`. Ветка CombatBrain.FLANK тестируется искусственным snapshot, но в бою не активируется. FLANKER меняет порядок cover sectors, SUPPRESSOR пока не меняет политику огня; ANCHOR лишь дольше остаётся HOLD.
- **Все столкновения слишком похожи.** Фиксированные 24/60/120-tick cover timings, четыре патрульные точки и strafing меняют рисунок мало. Случайность направления сама по себе не создаёт тактических альтернатив. Нужны варианты с оценкой пользы, seed, commitment и прерываниями.
- **Sprint отключён принудительно.** `TacticalCombatGoal.java:220` вызывает `setSprinting(false)`; `MovementCommand.navigate` всегда speed=1; `NpcEntities.java:22` даёт attribute .25. Значение .25 нельзя напрямую сравнивать с player attribute .1: контроллеры движения различаются. Нужен замер фактических blocks/second.
- **Runtime-переиспользование ещё неполное.** Orders/squads/faction/ammo commands привязаны к `TacticalNpcEntity` (`DebugCommands.java:68–85`). Optional NPCs runtime получает default ASSAULT и squad=null. Для полноценной альфы на готовой оболочке эти capabilities нужно вынести за runtime interface.

## Что сохранять

1. Pure `ai-core`, Frame/generation/TTL и immutable Observation. Это позволяет тестировать политику без Minecraft.
2. `TaczGunController`, `GunSupport`, `WeaponProfiles`, native draw/reload/bolt/shoot. Не создавать новый gun engine.
3. `ShotSafetyEvents`: финальная защита союзников и native-round revalidation.
4. `MovementExecutor` как единственного исполнителя, бюджеты и lifecycle. Расширять recovery и режимы скорости, а не добавлять второй navigation controller.
5. Per-level `SquadBlackboard`, delayed reports и claims; роли дополнить реальным поведением.
6. `Npc210Adapter`: уже отделяет базовые goals от нашего контроллера и сохраняет оболочку NPCs. Собственная entity также нужна как контролируемый независимый стенд.

## SmartBrainLib: подходит ли

Для Forge 1.20.1 опубликована **SmartBrainLib 1.15**, version ID **YkeJNm33**. Это библиотека организации Brain/sensors/activities/behaviours, а не готовый PvP-ИИ. [Точная версия](https://modrinth.com/mod/smartbrainlib/version/1.15), [официальный исходный проект](https://github.com/Tslat/SmartBrainLib).

Документация показывает `SmartBrainOwner`, `SmartBrainProvider`, server-side `tickBrain`, группы core/idle/fight, условные и случайные behaviours. Это удобно для Patrol → Investigate → Engage → Search → Return и приоритетов действий. Большинство стандартных sensors по умолчанию работают примерно раз в секунду; для быстрой реакции нужен собственный интервал и bounded visual sensor. Готовые behaviours для движения напрямую к attack entity также нельзя использовать без контроля знаний о скрытой цели. [Официальный пример](https://github.com/Tslat/SmartBrainLib/wiki/Making-an-Entity-With-SmartBrainLib).

Кандидат зависимости по официальной Maven-схеме: `implementation fg.deobf('net.tslat.smartbrainlib:SmartBrainLib-forge-1.20.1:1.15')`; репозиторий `https://dl.cloudsmith.io/public/tslat/sbl/maven/`. Это предложение для spike, не результат сборки с SBL. [Инструкция разработчика](https://github.com/Tslat/SmartBrainLib/wiki/Getting-Started).

Рекомендую короткий изолированный spike, максимум несколько рабочих дней, после исправления P1. SBL остаётся в Forge layer; core не импортирует её. Brain scheduler вызывает единый tactical pipeline, sensors производят Observation, behaviours предлагают intent. Только существующие arbiters/executors мутируют оружие и navigation. Нельзя одновременно оставить TacticalCombatGoal и подключить SBL MoveToWalkTarget/LookAtTarget как независимых владельцев тех же ресурсов.

Самый простой spike — на собственной entity через extension points, без Mixins. Автоматический перенос на exact AutoGunOperator 2.1.0 не доказан: его конструкцию и Brain hooks нужно отдельно проверить. Современная wiki не заменяет проверку API бинарника 1.15. В этой работе SBL не скачивалась, не подключалась и не тестировалась совместно с TaCZ.

Критерий включения: те же боевые сценарии проходят, владение единственное, budget/latency не ухудшаются, добавление Patrol/Return требует меньше связанного кода. Если преимуществ нет — оставить собственный компактный scheduler. Сам переход библиотек не является вехой качества ИИ.

## Развивать свой мод или готовый

| Вариант | Что экономит | Что остаётся сделать | Решение |
|---|---|---|---|
| Наш core + NPCs 2.1.0 adapter | Loadouts, skins, spawn, оборудование/анимации готового NPC | Те же дефекты поведения; runtime orders/squads | Наиболее дешёвый путь к готовой оболочке сейчас |
| Наша независимая entity | Контроль lifecycle, отсутствие зависимости NPCs, чистый стенд | Собственное оформление/контент при необходимости | Сохранять; для alpha minimal renderer допустим |
| Fork NPCs 2.1.0 | Прямые extension points вместо внешней замены goals | Поддержка чужого кода, атрибуция, обновления | Только если существующий adapter упрётся в проверенный предел |
| Simple Enemy Mod | Готовые фракции, команды, cover/flank/squad по описанию автора | Пин точного JAR/API, проверка TaCZ 1.1.8-hotfix2, владения и реального качества | Сильный кандидат для короткого A/B, не доказанная замена |
| CustomNPCs + TaCZ integrations | Редактор NPC и сценариев/контент | Другая модель AI/состояния, больше зависимостей, лицензии/совместимость | Выгоднее для authoring карт, не автоматически для сильного PvP |

NPCs 2.1.0 для Forge 1.20.1 и его MIT metadata подтверждены pinned binary и [официальной страницей версии](https://modrinth.com/mod/tacz-npcs/version/Nvq2rJvB). Не подменять его старым `tacz_npc`/чужим форком по похожему имени.

Simple Enemy Mod в [репозитории автора](https://github.com/NekoYuni/Simple-Enemy-Mod-Public) заявляет cover/flanking/squad, Commander Menu и recruitment; README указывает Forge 47.4.0+ и TaCZ 1.1.7. Это сведения автора, не проведённый нами тест и не доказательство несовместимости с 1.1.8-hotfix2. Его исходники/ресурсы требуют отдельной проверки лицензий перед переносом. Не копировать код в MIT-проект автоматически.

У [CustomNPCs-TACZ-Compat](https://github.com/kawer95/CustomNPCs-TACZ-Compat-1.20.1) заявлены native TaCZ gun AI и PlayerAnimator, GPL-3.0-or-later. [Dochi's Warfare](https://www.curseforge.com/minecraft/mc-mods/dochis-warfare) заявляет patrol/route/suppression, экспериментальный cover и редакторы; на странице стоит All Rights Reserved. Эти варианты полезны для сравнения/использования как готовых модов; доступность кода и разрешение на его перенос проверяются отдельно. Их качество боя с pinned TaCZ здесь не проверялось.

**Практическое решение:** провести одинаковые ручные 1v1/1v4 с нашей системой, обычным NPCs 2.1.0 и одним перспективным готовым модом. Критерии: повторный peek, преследование за двумя углами, маршрут по этажам, реакция на flank, suppression дверного проёма, длительность пассивного стояния. Смена основы оправдана, если выигрывает именно это, а не только количество заявленных features.

## Проверки этого ревью

- `bash ai-core/run-tests.sh`: **PASS, 72 policy checks**. Первые два запуска Git Bash не нашли `dirname`; успешный запуск использовал `export PATH="/usr/bin:/mingw64/bin:$PATH"` внутри bash. Production script не изменён.
- `forge-addon/gradlew.bat compileJava compileCompatJava --offline`: **BUILD SUCCESSFUL**, четыре задачи UP-TO-DATE. Это проверка согласованности текущих inputs с уже скомпилированными outputs, не полная clean rebuild.
- `tools/review/AlphaReviewProbe.java`: **4/4 воспроизведения текущих проблем** — emergency override, REPOSITION+HOLD, HOLD без peek, stale cooldown veto. Это диагностические проверки существующего дефектного поведения, не alpha acceptance tests.
- `forge-addon/gradlew.bat runGameTestServer --offline -PcombatTests`: **25/25 required tests PASS, BUILD SUCCESSFUL in 3m 51s**, 5 задач выполнено, 6 UP-TO-DATE. NPCs отсутствует. Сервер сохранил мир и штатно завершился. [Сохранённый лог](testing/alpha-review-standalone-2026-10-11.log). Compat GameTests в этом ревью заново не запускались.
- Новые ordinary `runServer`, human PvP, профили 30/50, SBL и сторонние NPC-моды в этом ревью не запускались. Существующий PROFILE-REPORT — предыдущие измерения, не результат этой работы. Он уже показывает превышение внутреннего ориентира AI p95 ≤2 ms; это нужно учитывать при ускорении sensors.

Запуск probes из корня PowerShell:

```powershell
$reviewOut = Join-Path (Get-Location) 'forge-addon/build/alpha-review-probes'
New-Item -ItemType Directory -Force -Path $reviewOut | Out-Null
$reviewSources = @(Get-ChildItem -LiteralPath 'ai-core/src/main/java' -Recurse -Filter '*.java' | ForEach-Object FullName)
javac --release 17 -encoding UTF-8 -d $reviewOut @reviewSources 'tools/review/AlphaReviewProbe.java'
if ($LASTEXITCODE -eq 0) { java -cp $reviewOut AlphaReviewProbe }
```
