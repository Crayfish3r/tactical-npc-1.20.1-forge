# Профили проверок Tactical NPC

Новый боевой срез2026-10-22: [combat upgrade](COMBAT-UPGRADE-2026-10-22.md). Для его before/after используйте `run-combat-upgrade.py before|after` и `compare-combat-upgrade.py`; source baseline сохранён отдельно от batch14. Нижеследующий аудит и исторические пакеты остаются своими checkpoint.

Аудит 2026-10-19: HEAD `eb1f04eefb2141adaefd91c90fb82b56a0d9c030`, начальное дерево чистое.
Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2 / optional NPCs 2.1.0.
Production-логика не менялась. Этапы 07–14 этим аудитом не начинаются и не принимаются.

Команды ниже — из корня репозитория, кроме явно отмеченных Gradle-команд.
`python` означает рабочий Python 3, `bash` — Git Bash на Windows, а не WSL alias.
На машине аудита PATH aliases не работали: использованы
`C:/Users/makar/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`
и `C:/Program Files/Git/bin/bash.exe`. `--offline` допустим только с готовым pinned cache.

Обновление пакета08–14: [план/приёмка](BATCH-08-14.md). Аудит выше — исторический checkpoint; на новом `3f9e810` владелец принял07 и разрешил пакет08–14 с production изменениями. Отдельные STAGE receipts находятся в отчётах08–13. Новый own native BURST fixture есть в NeutralTests: optional-only запуск ради одного названия больше не нужен. Optional FULL на14 сохраняется.

| Профиль | Когда и обязательный объём | Реальная команда / пример |
|---|---|---|
| FAST | После локальной правки: относящиеся core suites или конкретные Forge-методы. Не заменяет STAGE. | `bash ai-core/run-tests.sh territory patrol`; `python tools/testing/run-gametests.py FAST --tests LookControlTests,ModeCommandTests#permissionSuggestionsAndOldCommandSurface --offline` |
| STAGE | Один согласованный набор новых и затронутых регрессий перед передачей этапа; весь core, если менялся ai-core; production compilation; GameTests по рискам ниже. | `bash ai-core/run-tests.sh`; `python tools/testing/run-gametests.py STAGE --tests CoverTests,AlphaResponsivenessTests,DefendTerritoryTests --offline` — пример для cover, расширить новыми тестами этапа 13 |
| FULL | Этап 14 / существенные сквозные изменения: весь core, standalone, combined при работающем adapter, production verification, ordinary dedicated startup/stop; отдельные release/perf/human проверки. | `bash ai-core/run-tests.sh`; `python tools/testing/run-gametests.py FULL --offline`; `python tools/testing/run-gametests.py FULL --legacy --offline` |

Ordinary сопоставимый before/after14: `python tools/testing/run-batch-profile.py before`, затем `python tools/testing/run-batch-profile.py after`; эти команды запускаются последовательно, без параллельной сборки/тестового сервера во время профиля. Требуется уже сохранённый pre-09 source manifest в ignored `build/batch-baseline`; отсутствие baseline — BLOCKED сравнения, нельзя подставить Oct11. По2 повтора и actual native projectile в каждой сцене, отдельные CSV/JFR/receipts. `python tools/testing/summarize-batch-jfr.py <actual profile.jfr>` обрабатывает реальную запись; sampling не выдаётся за точный полный server cost.

Фикстуры обязаны обеспечивать entity ticking всей своей structure: Vanilla GameTest force-load around origin недостаточен для64x64. Test-only ScenarioIsolation добавляет только tickets внутри actual bounds, сохраняет общие с новой fixture tickets непрерывно и снимает свои вне её bounds. Launcher с обновления team создаёт отдельный `build/test-runs/<UTC>-runtime` для каждого receipt: мир/config предыдущего запуска не становятся входными данными следующего. Raw evidence сохраняется, автоматического удаления нет; `--plan` по-прежнему NOT RUN. Память/LOS assertions выполняются до уничтожающего цель teardown: порядок GameTest runnable map не гарантирован. Все native round observers проверяют отмену EntityJoin; fake GunFireEvent без projectile не доказывает выстрел/слух.

Примеры selection не являются автоматическим доказательством достаточности для будущего этапа.
FAST/STAGE требуют явного `--tests` с точными `Class` / `Class#method`, через запятую,
без wildcard. FULL запрещает фильтр. Для проверки только графа добавить `--plan`: **NOT RUN**.
Launcher всегда включает `compileCompatJava`; `compileJava` уже зависимость GameTestServer.
Поэтому отдельный предварительный compile не нужен, кроме самостоятельной FAST compile-проверки:
из `forge-addon` — `.\gradlew.bat compileJava compileCompatJava --offline --console=plain`.

## Как выбирать STAGE 07–14

Новые тесты обязательны там, где механика пока отсутствует; старое имя класса их не заменяет.
Профиль STAGE — явный union релевантных существующих и новых тестов, а не фиксированный лимит.

| Этап / риск | Существующие опорные проверки (расширить тестами требований этапа) | Дополнительный запуск только по этому риску |
|---|---|---|
| 07, neutral/damage/spawn/NBT | TargetTypeTests, IndependentNpcTests, ModeCommandTests; новые actual-damage, projectile owner, personal hostility, NEUTRAL migration | OptionalTargetTests/WeaponScenarioTests при изменении общей damage/safety политики |
| 08, stand/границы/фаза | StandTests, ModeCommandTests, NeutralTests; связанные territory/patrol/NBT при риске | Native BURST при смене mode/generation gates; delayed SquadTests при завершении контакта |
| 09, weapon snapshot | WeaponSnapshotTests, WeaponProfileTests, neutral reload/BURST | WeaponScenarioTests с `--legacy` при общем controller/equip риске; own native BURST уже есть в NeutralTests; lookup/allocations до/после |
| 10, budgets | QueryBudgetTests, ActivationTests, PerceptionTests, neutral native no-fire | Fixed-order native/optional fairness, cursor, deferred memory; сопоставимая перегрузка/30 NPC |
| 11, sector | SectorTests, QueryBudgetTests, alliedDamageEventGuard, shotgunNativePellets, neutral BURST | Optional allyCrossingAndBehindTarget/nativeModesAndBurst при общей safety правке; одинаковая crowded-сцена/projectiles |
| 12, hearing | HearingTests, SuppressionTests, neutral source/TTL/sound | NPC native shooter, invisible Player, bounded actual events/receivers; optional source при adapter риске |
| 13, cover/peek | PeekRevalidationTests, CoverTests, StandTests, DefendTerritoryTests | DEFERRED/cursor/fair optional cadence; dynamic cover, native PEEK projectiles, existing reservations |
| 14 | FULL без фильтра; старые команды/PATROL/spawnlegacy, persistence/restart, все новые этапы | Обычный dedicated профиль 1/4/16/30/50, JFR/allocations/GC/soak, manual 1v1/1v4 и приёмка владельца |

Для точечного optional риска использовать тот же launcher с `--legacy` и только требуемыми
`OptionalTargetTests` / `WeaponScenarioTests#...` / `CombatGameTests` и затронутыми mixed-runtime
сценариями. Не повторять весь standalone union в combined после каждой локальной правки.
При FULL standalone и combined — разные среды, оба обязательны при работающем adapter.
Одноимённая проверка в другой среде не считается полным дублем.

Обычный dedicated startup/stop нужен после lifecycle/packaging/run-config изменений и на FULL,
не после каждой локальной core/selection правки. JFR/soak/крупные сцены — по измерительному риску
и FULL. Для 09–13 нельзя убирать требуемые сопоставимые измерения до/после.
Сравнивать CPU/JVM/seed/modlist/оружие, warmup и фактические projectile/реакцию; исторический
профиль 2026-10-11 не является свежим baseline. FakePlayer не заменяет human playtest.

## Изменённый запуск и защита отчётов

`forge-addon/gradle/testing.gradle` собирает каталог тестовых классов из source roots, включая
compatTest только в legacy-запуске. Все исходники GameTests остаются входами компилятора при
смене FAST/STAGE/FULL/selection. `SelectedGameTests` регистрирует только выбранные методы через
официальный `RegisterGameTestsEvent`; API проверен в pinned Forge 47.4.20 sources/JAR.
Убрана автосканируемая `GameTestHolder`; у каждой `@GameTest` явно сохранён прежний namespace
`tacticalnpcai`. Template, batch, timeout, required, тела, assertions и test-only clock не менялись.
Новые тесты должны использовать тот же namespace и public static void метод; не добавлять
авторегистрацию GameTestHolder/Generator в обход selection. Конфигурация отвергает их.
Ручные source-excluding init scripts больше не нужны; старые локальные receipts не переписываются.

Launcher сохраняет уникальные raw `.log` и JSON receipts в `forge-addon/build/test-runs/`.
В чат выводятся status/count/time/tasks/path; полный лог читать только вокруг FAIL.
PASS требует exit 0, ровно один отчёт selection и завершения, ненулевой count и равенство
selected = completed = required passed. `NO-SOURCE`, skipped, dry-run, ноль/неполный отчёт —
не GameTest PASS. Сейчас все тесты required; если появятся optional tests, парсер нужно
явно расширить, а не объявлять их прошедшими автоматически.

Core: один javac на вызов, пять suite JVM по умолчанию (792 checks на этом HEAD).
FAST может выбрать policy/temporal/territory/patrol/attack; неизвестные/повторные suite — ошибка.
Компиляция core намеренно остаётся полной и свежей в temporary directory: stale cache не добавлен.
`tools/review/run-alpha-probe.sh` — отдельный исторический диагностический probe, не core suite;
его assertions не объявлены дублями. Не включать автоматически в каждый этап.

## Доказанные повторы и сохранённые различия

- Gradle dry-run `build runGameTestServer -PcombatTests` подтвердил, что общие compile/resources
  уже входят в граф ровно один раз. Перечисление `compileJava compileCompatJava build` внутри
  одного invocation не компилирует трижды; экономия здесь только в отдельных лишних invocations.
- Убрана необходимость временных init scripts с source exclusions: смена selection больше
  не меняет source inputs. Первый запуск после правки всё равно обязан скомпилировать изменения.
- Обычный production `build` использует актуальные Gradle inputs/outputs. Не нужны `clean` и
  `--rerun-tasks` на каждой передаче; они принудительно повторяли даже extract/reobf tasks.
  После test flags production compilation, которая действительно нужна для удаления test classes,
  должна выполниться — её нельзя пропускать через `-x` или выдавать test JAR.
- Инвентаризация: **68 standalone + 11 optional = 79 методов, 18 классов**. Полные дубли
  не доказаны, удалено **0 тестов**. Whitespace-normalized body comparison дал 0 кандидатов;
  это дополнительная проверка, а не доказательство семантического равенства любых тестов.
- Native maintenance/reload/LOS у independent и legacy использует разные entity/operator;
  legacy ownership проверяет восстановление исходных goals/look/body. LookControlTests и
  legacyNavigationCannotSnapAim отличаются runtime. Core territory geometry и Forge route/body/NBT
  проверяют разные слои. NPC target paging и Player paging используют разные источники/activation.
  Rifle/shotgun/bolt fixtures различаются оружием/projectiles/pellets. Всё сохранено.
- NEUTRAL и новая STAND-последовательность ещё не реализованы/не покрыты на этом HEAD.
  Аудит не выдаёт эти будущие сценарии за существующий PASS. Явный BURST пока optional —
  существенный coverage risk для будущих этапов оружия, не повод удалить этот тест.

## Передача production JAR

Один вызов после тестов, из `forge-addon`, без combatTests/profile/clientSmoke/legacy flags,
без source filters, `clean`, `--rerun-tasks` и `-x`:

```powershell
.\gradlew.bat build --offline --console=plain
```

Затем из корня:

```powershell
python -B tools/verify-production-artifacts.py
```

Это актуальный production rebuild, включая обе compilation и reobf по графу. `UP-TO-DATE`
допустим при актуальных inputs; он не равен новому запуску тестов. Проверить SHA и verifier
именно передаваемых JAR, не подставлять исторический receipt. При подозрении на повреждённые
outputs сначала установить причину; принудительный rebuild только как диагностическое исключение.

## Результаты аудита

Фактически использованы абсолютные пути Python/Git Bash выше; Gradle — wrapper из forge-addon.
TaCZ SHA-256 `2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2`,
NPCs SHA-256 `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE`:
повторно проверены, совпадают с pinned audit. Java фактического Forge-запуска 17.0.19 Microsoft.

| Выполненная проверка | Реальный результат / локальный лог |
|---|---|
| Полный `bash ai-core/run-tests.sh` | PASS: 72 policy + 94 temporal + 38 territory + 235 patrol + 353 attack = **792 checks**, 5 suite JVM; `forge-addon/build/testing-audit-core.log` |
| FAST core `bash ai-core/run-tests.sh territory` | PASS **38 checks**, 1 suite JVM; `forge-addon/build/testing-audit-fast-core.log` |
| `python tools/testing/test_runner.py` | PASS **7/7** уникальных проверок ложного PASS; повторный прогон после выделения parser также 7/7 |
| Первая новая FAST регистрация | FAIL: selected=2, реально 0. Потерян прежний namespace. Launcher не принял Gradle BUILD SUCCESSFUL за тестовый PASS. Исправлено явным templateNamespace; `forge-addon/build/test-runs/20261019T043536.244245Z-FAST.log` |
| FAST LookControlTests + ModeCommandTests#permissionSuggestionsAndOldCommandSurface | PASS **2/2**, **63.610 s**; `forge-addon/build/test-runs/20261019T043718.729047Z-FAST.json` |
| STAGE --legacy IndependentNpcTests#finiteReserveAndBolt + WeaponScenarioTests#nativeModesAndBurst | PASS **2/2**, **69.156 s**; `forge-addon/build/test-runs/20261019T043915.733296Z-STAGE-compat.json` |
| FULL --plan | Граф валиден, **NOT RUN**, 0 GameTests; `forge-addon/build/test-runs/20261019T044020.027636Z-FULL.json` |
| FAST LookControlTests --plan / MissingTests --plan | Ожидаемые exit **0 / 1**; неизвестный selector отклонён до запуска сервера. Это negative config check, не GameTest FAIL после исправления; `forge-addon/build/testing-audit-LookControlTests.log`, `testing-audit-MissingTests.log` |
| Сверка исходников с HEAD | PASS: 18 тестовых классов = прежний текст за исключением регистрации/namespace; тела/assertions/templates/batches/timeouts сохранены. Production Java diff отсутствует. `git diff --check` PASS |
| Один `.\gradlew.bat build --offline --console=plain` | PASS **28 s**, 12 actionable tasks: **6 executed / 6 UP-TO-DATE**. `test NO-SOURCE` не засчитывается как тест; `forge-addon/build/testing-audit-production.log` |
| `python -B tools/verify-production-artifacts.py` | PASS; `forge-addon/build/testing-audit-production-artifacts.json` |

Успешно выполнено **4 GameTest-сценария** в двух средах, а не 79/147. Обнаруженная ошибка
регистрации исправлена; исходный FAIL-лог сохранён. FULL сейчас содержит 68 standalone /
79 combined сценариев (147 выполнений в двух разных средах), это инвентарь, не результат запуска.
Full GameTests, обычный dedicated startup/stop, client smoke, JFR, soak, большие NPC-сцены,
human playtest — **NOT RUN**, вне необходимого объёма этого аудита.

Измеренная экономия ограничена доказанным пропуском работы: смена FAST selection на STAGE
с optional runtime сохранила `compileJava` и `compileCompatJava` **UP-TO-DATE**, выполнился
только необходимый `compileCompatTestJava`. В production 6/12 задач остались UP-TO-DATE.
В отличие от временных source exclusions, selection не менял source inputs. Для core FAST
запущена одна suite JVM вместо пяти; javac по-прежнему полный. Сравнимый старый/новый
wall-time benchmark не выполнялся: 63.610/69.156 s относятся к разным сценам/runtime и
не доказывают процент ускорения. Число будущих исключённых invocations зависит от правок,
поэтому общий выигрыш по tool calls/времени/размеру логов не выдумывается.

Итоговые production artifacts, Java major 61, без GameTests/helpers/арен/вложенных зависимостей:

| Путь | Bytes | SHA-256 |
|---|---|---|
| `forge-addon/build/libs/tacticalnpcai-0.2.0.jar` | 169443 | `8EA9008CDF065BC7BA13565C1EE9B14E8373D5D17E33CE7C8AF491E56BD349AA` |
| `forge-addon/build/libs/tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 | `2EE1B7A7E8504E05CED89687829F0118B8EE726CD95C58F349D8321C89FDCFB9` |

Риск: новая регистрация проверена узко в standalone/combined, FULL намеренно не выполнялся.
При добавлении новых тестов нужно сохранить namespace и включить их в STAGE union; отдельные
сквозные/производительные claims требуют своих прогонов. Использование этого документа не
принимает ни аудит, ни этапы 02/06/07–14 автоматически. **WAITING_APPROVAL — ОСТАНОВКА**.
Commit/push/merge не выполнялись.

## Заменяющий блок правил для промпта 07–14

> После локальной правки выполняй FAST: относящиеся core suites / конкретные GameTests.
> Перед завершением этапа — один STAGE union новых и затронутых регрессий; весь core при
> изменении ai-core. GameTests и compileCompatJava запускай одним launcher invocation;
> compileJava уже в графе. Выбор Class/методов делай через runtime selection, без source filters.
> Повторяй только проверки, чьи inputs/поведение затронуты новой правкой или FAIL; записывай
> scope и актуальный receipt. Не используй -x, clean/--rerun-tasks без установленной причины.
> Optional integration, обычный dedicated startup, JFR/soak/крупные сцены — по риску; FULL
> standalone+combined и release matrix — на 14/сквозных изменениях. Измерительные требования
> оптимизационных этапов сохраняй, включая сопоставимые projectile/реакцию до/после.
> Перед каждой передачей JAR — один production build без тестовых flags и artifact verifier.
> Ноль GameTests, skipped, NO-SOURCE, dry-run и FakePlayer не подменяют PASS/ручную приёмку.
> Логи сохраняй на диск; сообщай команду, count, status, краткую причину FAIL и путь.
> Обязательная проверка недоступна/дефект не исправлен — BLOCKED. После этапа WAITING_PLAYTEST;
> следующий этап только после явного принятия владельцем. Аудит тестов — WAITING_APPROVAL.

Исключение для текущего пакета 2026-10-19: владелец явно принял07 и разрешил подряд08–14. Промежуточная ручная приёмка не блокирует выполнение пакета; отчёты и ручные шаги остаются отдельными для каждого этапа. Это разрешение не означает ACCEPTED для08–14. Перед общей передачей остаются один plain production build/verifier и обязательные проверки14.

Фактический итог пакета08–14,2026-10-20: [20 GameTest receipts/команды](testing/stage14-validation-2026-10-20.json), полный core792, standalone FULL98/98, optional FULL110/110; после минимальных найденных правок только affected14/14 и25/25, без ещё одного незатронутого FULL. Production build5 executed/7 UP-TO-DATE, обычные runServer после него сохраняют compilation UP-TO-DATE. Физически уникальные тесты не удалялись/не объединялись; дубли invocations сокращались runtime selection, общим Gradle graph и одним final production handoff. Тестовая JFR обработка исправлена на streaming после MemoryError, actual native матрицы не повторялись ради анализа; исходные baseline event counts совпали. Измеренного общего процента экономии testing wall time/tool calls нет; build cache/task reuse подтверждены логами, не выдаются за speed benchmark. [Профиль до/после и ограничения](testing/stage14-profile-2026-10-20.md).
