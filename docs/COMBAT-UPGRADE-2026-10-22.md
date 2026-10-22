# Усиление боевого AI — 2026-10-22

> Команды в этом датированном отчёте относятся к прежнему интерфейсу. Актуальные команды, постоянный Hard и таблица миграции: [COMMANDS](COMMANDS.md).

Закреплено: **Minecraft 1.20.1 / Forge 47.4.20 / Java 17.0.19 / TaCZ 1.1.8-hotfix2**;
optional adapter **[TACZ] NPCs 2.1.0**. Новых зависимостей, AI-библиотек и Mixins нет.
Это отчёт о реализации и измерениях, не человеческая приёмка PvP-баланса.

## Реализованные этапы

1. **Обнаружение128.** Detection128, hearing128, activation не меньше sight/hearing,
   memory160 блоков /320тиков. Нижняя граница памяти — max(sight, hearing)+16, чтобы звук у края не исчезал сразу. Поиск по прежним страницам16 UUID, максимум4 LOS на вызов.
   FOLLOW_RANGE128; дальние перемещения остаются короткими локальными отрезками.
   Перед visibility/cover ray выполняется ограниченный DDA по пересекаемым загруженным чанкам,
   включая тонкие угловые пересечения. Поиск не добавляет chunk tickets и не вызывает загрузку чанков.
2. **Дистанции и упреждение.** Предпочтительная дистанция класса, native damage falloff и
   кинетическая дальность разделены. Авторизованный reach ограничен512 блоками; это выше текущих sight/memory/hint радиусов, без запрета на огонь по damage falloff. Live cached speed с native global multiplier переводится
   в блоки/тик; учтены проверенные gravity/drag/lifetime. Скорость цели берётся только из двух
   последовательных визуальных samples; разрыв видимости обнуляет её. Горизонт упреждения12тиков,
   подтверждённая скорость ограничена.5 блока/тик, поворот17°/тик, коррелированная ошибка.35°
   плюс неизменённый TaCZ spread. Мобильное оружие сближается с ADS/огнём; отход использует короткие
   проверяемые пути. Нулевая скорость движения означает неподвижный NPC, а не ошибку маршрута.
3. **Продолжительное давление.** Настраиваются очередь8, пауза12тиков, длительность160тиков,
   бюджет48 патронов на непрерывный скрытый контакт. Новый hidden report не пополняет бюджет.
   Один verified GunFireEvent token, подтверждённый реальным projectile join, отличает патрон
   от дробин; canceled join не считается выстрелом. Проверяются известная точка и боковые
   смещения±2/4/6; заблокированные сектора сменяются, после16тиков без доступной точки запрашивается
   reposition. Между очередями сохраняется позиция. Координаты скрытой цели в Hard не читаются
   для прицеливания. Каждый native round заново проверяет препятствия, направление ствола,
   допустимость противника, состояние оружия и союзников. Intended hostile исключён из своего
   сектора по UUID без считывания скрытой позиции. Дальше сектора союзников защищает native pre-damage guard.
4. **Обход и роли.** Прикрывающий anchor, один flanker, suppressors; native team задаёт отряд.
   Обход — lateral→advance→inward с настоящими waypoint, native path validation, claims, deadline240,
   backoff и сменой стороны. Utility имеет margin10 и commitment80; урон, здоровье, обслуживание
   оружия и провал маршрута прерывают его. Deferred cover ограничивает только решения, использующие
   это укрытие. После12тиков реально занятого огневого сектора поиск продолжает следующие страницы:
   новая цель должна быть видимой и хотя бы на10% ближе, смена не чаще40тиков. DEFERRED не считается
   доказательством занятого сектора. Это устраняет удержание неудобной дальней цели при наличии ближней.
5. **Hard / опциональный Unfair.** Hard сохраняет легитимные наблюдения и сохранённую ammo policy.
   Unfair: hearing192, derived contact memory208, reaction1, squad delay2/radius128, бесконечный резерв с обычной native reload,
   отдельная подсказка каждые60тиков. Подсказка — область с шагом4, uncertainty5 и нулевой скоростью;
   отдельная transient memory `UNFAIR_HINT`, никогда VISION или визуальный squad report. Она может
   заменить устаревшую скрытую информацию. Hard очищает подсказки, отменяет незавершённую reload
   при смене политики и инвалидирует старый frame. `/npc info` показывает профиль/источник,
   diagnostics считает hints отдельно от visual first-shot/reacquire.

Server-thread authority, immutable core snapshots, generation/TTL, native TaCZ shot/reload/bolt,
кулдауны, физические препятствия и защита союзников сохранены. AGENTS5/9 изменены только для
разрешённых пользователем исключений Unfair. Старое ограничение навыка6rounds/40ticks заменено
явным запросом на продолжительное настраиваемое давление. Аудит API/хешей: [INTEGRATION-AUDIT](INTEGRATION-AUDIT.md).

## Проверки

- **Pure Java: 818 checks PASS** (72 policy +94 temporal +38 territory +235 patrol +353 attack +26 combat upgrade). [Receipt](testing/combat-upgrade-core-2026-10-22.json).
- **Combined STAGE36/36 PASS**: все новые10 GameTests, полный PerceptionTests, native safety/sector, pressure, detour, alpha tracking, finite reload и optional lifecycle. [Receipt/точная команда](testing/combat-upgrade-stage-2026-10-22.json).
- **Standalone STAGE24/24 PASS без NPCs**: новые10, IndependentNpcTests, pressure, actual detour/tracking и оба perception regressions. [Receipt](testing/combat-upgrade-standalone-2026-10-22.json).
- **Последний FULL: 131 executed,129 PASS,2 FAIL** — `DefendTerritoryTests#externalDisplacementReturnsThroughOutsideNodes` и `CombatGameTests#combatLifecycle`. [Неизменённый FAIL receipt](testing/combat-upgrade-full-2026-10-22.json). Прежние FAIL reaction/perception в этом полном прогоне прошли. В lifecycle raw ray попал в посторонний gravel внутри ожидаемого воздушного коридора (точные координаты/состояние сохранены в логе).
- Затем исправлен только ручной драйвер DEFEND fixture: ожидание реальных grounded/entity-ticking предусловий и один executor call на entity physics tick вместо вызовов во время паузы сущности. Сохранены те же displacement, native-path, knockback и `<2 blocks at tick200` assertions. **Все7 DEFEND PASS**. [Receipt](testing/combat-upgrade-defend-2026-10-22.json). В lifecycle затем явно подготовлен воздушный коридор; строгая raw-ray проверка и реальные movement/reload/projectile assertions сохранены. **Последние lifecycle/native9/9 PASS** ([receipt](testing/combat-upgrade-lifecycle-2026-10-22.json)). Полный каталог после этих test-only правок не повторялся; FULL остаётся FAIL, непрерывный полный green не заявлен. Production AI после измерения не менялся.
- Plain `compileJava compileCompatJava build --offline --rerun-tasks`: **PASS25s,12 executed**,7 deprecation warnings. `test=NO-SOURCE` не засчитан как проверка. [Build receipt](testing/combat-upgrade-build-2026-10-22.json).
- **Production artifact verifier PASS**: Java major61;101 main /4 compat classes, без test/profile fixtures и вложенных зависимостей; core чистый, main без NPCs linkage. [SHA/размеры](testing/combat-upgrade-artifacts-2026-10-22.json).
- Обычный dedicated **startup→reload→save/stop→restart/load/stop PASS102,938s**: native team, UUID, order/schema5, finite policy и actual gun stack сохранились. Исходные properties/EULA неизменны. [Receipt](testing/combat-upgrade-dedicated-2026-10-22.json), [preservation](testing/combat-upgrade-preservation-2026-10-22.json).

Все игровые JVM — ForgeGradle userdev. Обычный dedicated gate не содержит GameTest/profile init или искусственных часов; установка distribution JAR в отдельный launcher этим не подтверждена.
История FULL115/9fail,129/3fail,130/3fail,131/2fail и затронутых repairs сохранена в [machine validation](testing/combat-upgrade-validation-2026-10-22.json), без переименования старых FAIL в PASS. Промежуточный compile-only FAIL с0 executed также не засчитан. Часть ранних ошибок происходила от устаревших falloff/range/suppression ожиданий и стендовых чанков; production fixes включают устранение ложной path failure при speed0 и pressure pause при отключённом suppression.

Артефакты: [основной JAR](../forge-addon/build/libs/tacticalnpcai-0.2.0.jar), [optional adapter](../forge-addon/build/libs/tacticalnpcai-0.2.0-npcs-compat.jar). Статус: **WAITING_PLAYTEST**, не ACCEPTED.

## Dedicated before/after

Оба ordinary dedicated профиля: **PASS, 9/9 фаз**, одинаковый fixture SHA, неизменные production source hashes. Все active samples после активации содержат полный заявленный cohort.

| Сцена | Выстрелы до → после | Попадания до → после | Первый выстрел, тики | Первое попадание, тики | MSPT p95 до → после | Стрелявшие до → после |
|---|---:|---:|---:|---:|---:|---:|
| range64 | 0 → 31 | 0 → 18 | — → 9 | — → 12 | 9,71 → 14,29 | 0 → 1 |
| range96 | 0 → 31 | 0 → 9 | — → 9 | — → 32 | 5,64 → 7,27 | 0 → 1 |
| range128 | 0 → 31 | 0 → 7 | — → 8 | — → 33 | 5,78 → 6,91 | 0 → 1 |
| running64 | 0 → 47 | 0 → 21 | — → 9 | — → 12 | 5,82 → 6,44 | 0 → 1 |
| suppression | 9 → 31 | 4 → 9 | 8 → 8 | 19 → 9 | 6,25 → 6,52 | 1 → 1 |
| flank1v4 | 76 → 93 | 63 → 56 | 21 → 8 | 24 → 10 | 8,09 → 8,26 | 4 → 4 |
| load16 | 735 → 752 | 399 → 739 | 8 → 8 | 9 → 9 | 13,61 → 9,97 | 16 → 16 |
| load30 | 1274 → 1357 | 665 → 1353 | 8 → 8 | 9 → 9 | 15,21 → 15,40 | 28 → 30 |
| load50 | 1943 → 2205 | 1009 → 2175 | 8 → 8 | 9 → 9 | 19,60 → 18,37 | 43 → 50 |

Обнаружение после активации: 64/96/128 — **2/3/2 тика**; baseline не находил эти цели за окно сцены (прежний detection40). По бегущей цели — **21/47**, включая все промахи. Это совместное влияние обнаружения, упреждения и настройки ошибки; из этой пары нельзя выделить вклад одного упреждения.

Неподвижное подавление: **0 → 21 actual SUPPRESS_AREA rounds** после постановки стены; всего9 → 31. В1v4: **0 → 36 area rounds**, 3 подтверждённых прибытия lateral/advance/inward, максимальное смещение flanker **25,87 блока**, FLANK отмечен200тиков. Общие попадания1v4 снижаются63 → 56 при большем давлении по области; это не выдано за оптимизацию или рост нанесённого урона.

30/50: **28 → 30** и **43 → 50** реальных стреляющих владельцев; **1274 → 1357** и **1943 → 2205** выстрелов. AI p95: **6,60 → 6,07ms** и **8,65 → 6,58ms**; цель≤2ms не достигнута. При30 p95 MSPT вырос; при50 снизился при большем объёме огня. Общего performance win не заявлено. Сценарий50 не допускает сравнения одинаковой работы по порогу±10% rounds.

JFR sampling до/после: 654/631 measured server CPU samples; goal+safety 15/22, native maintenance 8/4, fixture 6/16, native shoot 0/3, projectile physics 0/0. Нулевые категории означают отсутствие samples, а не отсутствие работы. Weighted allocation estimate goal+safety: 82,76 → 86,63MiB при изменившейся работе, без allocation win. Heap summaries 92/82 и GC events 46/41 относятся ко всей записи. Это одна последовательная пара, без статистического обоснования общего ускорения.

Сохранённые данные: [сравнение](testing/combat-upgrade-comparison-2026-10-22.json), [до CSV](testing/combat-upgrade-before-metrics.csv), [после CSV](testing/combat-upgrade-after-metrics.csv), [JFR до](testing/combat-upgrade-before-jfr.json), [JFR после](testing/combat-upgrade-after-jfr.json), [окружение](testing/combat-upgrade-environment-2026-10-22.json).
Исходные CSV samples, JFR и server logs: `forge-addon/build/upgrade-profile/20261021T231606-before` и `20261022T023750-after`; retained receipts содержат SHA исходников/fixture.

Baseline production/dependencies сохранены **до первой production правки** в
`forge-addon/build/combat-upgrade-baseline/baseline-source.json`. Оба варианта используют
одинаковый fixture source, seed8675309, AK-47, UUID/seed NPC и активацию на fixture tick40.
Сравнивается прежний AI репозитория с новым, не показатели старых отчётов и не оригинальный NPCs2.1.0.
Изолированные ordinary dedicated процессы запускаются последовательно без параллельного GameTest.

Время первого выстрела/попадания — от активации, в тиках; для cohort это первый выстрел группы.
Running64 — траектория с максимальной боковой скоростью.28 блока/тик. В1v4 стенка ставится на90тике;
в pressure-сцене стрелок неподвижен, поэтому движение к цели не выдаётся за подавление.
`rounds` — реально присоединённые uncanceled native projectiles (у AK-47 один на патрон).
`hits` — уникальные bullet UUID, дошедшие до damage handler бессмертного FakePlayer: геометрические
попадания в любую hostile FakePlayer цель данной сцены, **не** снятое здоровье/TTK или accuracy по назначенной цели группы. `post_wall_rounds` включает DIRECT после повторного контакта;
только `area_rounds` доказывает подавление. `flank_arrivals` требует реального прибытия, а не запроса пути.
16/30/50 — активные NPC с настоящими выстрелами. Состав active cohort проверяется по каждому sample.

MSPT — wall-clock Forge ServerTick START→END p50/p95 (до profile END I/O); AI timing включает native safety без вложенного двойного подсчёта.
JFR CPU/allocations — sampling estimates; startup/warmup/teardown исключены по фактическим timestamp,
fixture отделён от AI/native/physics. Нулевой sample категории не означает нулевую стоимость.
Меньше снарядов/стреляющих владельцев или поздний первый выстрел не дают performance credit.
Одна синтетическая пара не доказывает общего ускорения; running comparison также включает расширение detection.

Команды из корня (Python3, Git Bash; Windows runtime paths — в TESTING-PROFILES):

```text
bash ai-core/run-tests.sh
python tools/testing/run-gametests.py FULL --offline
python tools/testing/run-gametests.py FULL --legacy --offline
python tools/testing/run-combat-upgrade.py before
python tools/testing/run-combat-upgrade.py after
python tools/testing/compare-combat-upgrade.py <before-output> <after-output> <comparison.json>
python tools/testing/summarize-batch-jfr.py <actual-profile.jfr>
```

## Отдельный человеческий плейтест и ограничения

- Survival1v1/1v4 на реальной карте: ощущение реакции/честности, длинное давление, нерегулярный
  sprint/strafe/jumps, окна отхода/reload и настоящее время до смерти/победы. Автотесты этого не подтверждают.
- Hard/Unfair раздельно; переключение во время reload/за укрытием, источник подсказки и восстановление
  finite reserve. Для старого мира применить `/tacticalnpc hard`; `/tacticalnpc unfair` включает исключения.
  Команды меняют serverconfig мира; Forge47.4.20 сохраняет их через autosave. Ручные значения задаются там же.
- Лестницы, двери, вертикальные укрытия, тонкие углы, ямы, несколько отрядов, союзный игрок в секторе и внешнее отбрасывание у DEFEND/chunk границы.
  Успешный маршрут вокруг конечной стенки не доказывает прохождение любой карты.
- Тестовая арена явно использует fixture tickets. Production поиск их не создаёт. Detection128
  не может тикать выгруженного NPC: проверить подключённым клиентом simulation distance и границы чанков
  реального сервера. GameTest/FakePlayer не подтверждают текущую сетевую/клиентскую приёмку distribution JAR.
- Упреждение ограничено сухим воздухом, подтверждённой скоростью и конечным горизонтом; вода, ускорение
  цели и сторонние gunpack требуют отдельных проверок. Unsupported scripts/charge/heat/explosive/
  incendiary/inventory-fed по-прежнему закрыты. Native physics определяет попадание.
- Короткий профиль30/50 не заменяет многопользовательский soak и повторные тёплые замеры с JFR.
  AI p95≤2ms не объявляется достигнутым без соответствующих данных. Ручная приёмка — WAITING_PLAYTEST.
