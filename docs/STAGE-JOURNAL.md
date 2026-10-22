# Журнал этапов

> Команды в этом датированном отчёте относятся к прежнему интерфейсу. Актуальные команды, постоянный Hard и таблица миграции: [COMMANDS](COMMANDS.md).

## 00 — baseline, 2026-10-16

Статус: **ACCEPTED** — владелец явно принял 00 в запросе этапа 01.
При завершении 00 статус был WAITING_PLAYTEST; проверки ниже относятся именно к baseline.

Checkpoint: `main`, HEAD `d3d7de3de41a27c52db51e14df2473206bd3d11c`.
Первый `git status --short --branch`: `## main...origin/main`, без изменений.
HEAD проверен локально, совпадение с исходным ревью установлено заново; reset/checkout/pull не выполнялись.
Remote: `https://github.com/Crayfish3r/tactical-npc-1.20.1-forge.git`.

Прочитаны `AGENTS.md`, локальные skills `tacz-npc-audit`/`forge-ai-profiling`,
`README.md`, `forge-addon/README.md`, `MVP-TESTING.md`, `docs/IMPLEMENTATION-STATUS.md`,
`docs/INTEGRATION-AUDIT.md`, `docs/TEST_PLAN.md`, `docs/testing/README.md`,
alpha-1 release notes; регистрация команд и основные runtime-контракты сверены с исходниками.
Исторические PASS не используются как результаты этого запуска.

### Среда и зависимости

- Windows 11 amd64; Microsoft OpenJDK/Javac **17.0.19+10-LTS**.
- Minecraft **1.20.1**, Forge **47.4.20**, Gradle wrapper **8.8**, ForgeGradle **6.0.54**.
- Оригиналы в `forge-addon/libs/`; имена/manifest проверены из JAR, хеши совпадают с аудитом:

| Зависимость | SHA-256 |
|---|---|
| `tacz-1.20.1-1.1.8-hotfix2.jar` | `2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2` |
| `tacznpcs-2.1.0-1.20.1.jar` | `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE` |

Локальный Gradle cache доступен; initial offline build действительно разрешил зависимости.
Обновления/загрузки зависимостей не выполнялись. Первые sandbox-попытки Git Bash и Gradle
остановились до тестов (MSYS `NtCreateDirectoryObject` access denied / wrapper `.zip.lck` access denied).
Повтор с разрешённым доступом к среде прошёл; это ограничения запуска, не FAIL проекта.
Одна первоначальная команда проверки хешей использовала лишний префикс `forge-addon/` из этой же
директории; правильные пути `libs/` проверены повторно.

### Инвентаризация baseline

Ниже — существующий контракт, без изменения поведения. Команды зарегистрированы в
`forge-addon/src/main/java/dev/zuma/tacticalnpc/debug/DebugCommands.java`, требуют permission 2.
В обычном dedicated `help tacticalnpc` подтвердил регистрацию всех девяти команд,
`diagnostics` реально выполнен. Это не отдельный ручной прогон каждого Brigadier handler.

| Команда / механизм | Фактическое назначение и границы |
|---|---|
| `tacticalnpc spawn` | Independent `tacticalnpcai:npc`, +3 блока по X, пустой AK-47, `tactical_ai`, HOLD_POSITION, faction `hostile`, infinite reserve. Агрессивный baseline; NEUTRAL отсутствует. |
| `spawnlegacy` | Только при загруженном optional runtime; создаёт исходного NPCs NPC с тем же tactical controller. |
| `equip <npc> <gun>` | Supported independent/legacy runtime, loaded TaCZ ID; пустой магазин, native draw/reload. Не всякий существующий gun ID технически поддержан. |
| `ammo <npc> finite <0..1000000>` / `infinite` | Только independent; FINITE через native DummyAmmo, INFINITE сохраняет расход магазина и native reload. Смена policy отменяет текущую reload и старые команды; finite loadout switch задаёт initial reserve нового оружия. |
| `order <npc> <kind> <4..64>` | Только independent; HOLD_POSITION, DEFEND_AREA, ASSAULT, отдельный PATROL. Якорь — текущая позиция. PATROL ходит локально без цели; DEFEND возвращается к якорю. |
| `faction <npc> <name>` | Только independent, 1..32 символа `[a-zA-Z0-9_-]`; одинаковая faction и scoreboard allies учитываются. Полноценной NEUTRAL/personal hostility модели пока нет. |
| `squad <npcs> <UUID>` / `clear` | Только independent; союзные factions, reports с задержкой 10 ticks, radius 48, TTL до 80; anchor/flanker/suppressors и cover claims, lifecycle cleanup. |
| `hard` | Устанавливает perception/reaction/reacquire/acquisition/turn = **1/3/1/3/17**; persistence требует проверки world config. В baseline-smoke не вызывается, чтобы сохранить настройки. |
| `diagnostics` | Агрегированные counters/histograms, gates/routes; пустая projectile histogram не доказывает latency. |
| Оружие | Штатная matrix: `tacz:ak47`, `glock_17`, `aa12`, `ai_awp`, `uzi`, `m249`; native shoot/reload/bolt/modes и каждый native round сохраняют gates. M870/неизвестные scripts, charge/inventory-fed/heat/explosive/incendiary блокируются. DMR default sample не подтверждён. |
| Suppression | Отдельный permit известной замороженной точки: age ≤40 ticks, confidence ≥0.35, uncertainty ≤12, до 6 native rounds/контакт, паузы ≥10 ticks. Стена запрещает огонь, скрытые координаты не обновляются. |
| Cover/recovery | Локальные checked cover/peek, HOLD/PEEK/REPOSITION/RETREAT, path cooldown/backoff, bounded route recovery; emergency останавливает старый peek даже при DEFERRED нового пути. Полной гарантии сложного terrain нет. |
| Optional adapter | Отдельный `-npcs-compat.jar`, NPCs **2.1.0**; захват/восстановление goals/look/body, сохранение equipment/rendering/native maintenance; legacy infinite reserve. Main JAR не должен ссылаться на NPCs. |
| Persistence | NBT schema 2: serviceId/loadout/ammo/order/anchor/faction/squad; schema 1 migration, future schema отключает tactical control. Цели/память/пути не сохраняются. |

Ограничение текущего кода: `VisibilitySensor.acquire/valid` и `TacticalCombatGoal` работают с
`Player`; selectable targets — eligible survival players. NPC против NPC пока не реализован.
Обычные mobs/animals/armor stands не выбираются как цели, но living entities могут блокировать
огневой сектор. Это инвентаризация, не исправление baseline.

Документация `MVP-TESTING.md` содержит старые defaults perception/reaction/turn **4/6/10**;
в исходниках актуальные **1/3/17**. Существующий smoke world также хранит **4/6/10**,
поэтому старые world overrides важны. Исторические размеры/хеши JAR не заменяют новые ниже.

### Запуски этого этапа

Все Gradle-команды выполняются из `forge-addon`; core и verifier — из корня.
Raw logs локальные/ignored в `docs/testing/baseline-00-*-2026-10-16.log`.

| Проверка | Точная команда | Результат / лог |
|---|---|---|
| Core | `bash ai-core/run-tests.sh` через `C:\Program Files\Git\bin\bash.exe` | PASS: 72 policy + 94 temporal checks; `baseline-00-core-2026-10-16.log` |
| Initial compile/build | `.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain` | PASS: BUILD SUCCESSFUL, 12 executed, 36s; `baseline-00-initial-build-2026-10-16.log` |
| Standalone | `.\gradlew.bat runGameTestServer --offline -PcombatTests --console=plain` | PASS: 29/29 required, BUILD SUCCESSFUL, 5m04s; `baseline-00-standalone-2026-10-16.log` |
| Combined/compatibility | `.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility --console=plain` | PASS: 38/38 required, BUILD SUCCESSFUL, 6m02s; `baseline-00-compat-2026-10-16.log` |
| Final production rebuild | `.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain` | PASS: BUILD SUCCESSFUL, 12 executed, 34s; `baseline-00-production-build-2026-10-16.log` |
| Production boundary | `python -B tools/verify-production-artifacts.py` | PASS; `baseline-00-production-artifacts-2026-10-16.log` |
| Ordinary dedicated | `.\gradlew.bat runServer --offline --console=plain`, затем `help tacticalnpc`, `tacticalnpc diagnostics`, `stop` | PASS: Done (6.676s), сохранение всех dimensions, exit 0 / BUILD SUCCESSFUL (1m26s); `baseline-00-dedicated-2026-10-16.log` — server log, Gradle итог проверен в terminal output |
| Human playtest / distribution launcher / disk restart / perf | Команды и сценарий ниже | NOT RUN: приёмка владельца |

Gradle `test = NO-SOURCE` отдельно от core/Forge и не засчитывается как тест.
Deprecated API/Gradle warnings не исправляются в этом этапе.
Оба GameTest-прогона содержат `Can't keep up`; функциональный PASS не подтверждает 20 TPS,
массовый perf или человеческий баланс. GameTest NBT round-trip не заменяет новый disk restart.

Dedicated использовал существующий `forge-addon/run/standalone/independent-smoke`,
`127.0.0.1:25585`, прежний `online-mode=false`; ни один test/profile/clientSmoke flag не задан,
`Enabled Gametest Namespaces: []`, native xmag script verified=true.
Новые NPC в этом startup-smoke не создавались; `diagnostics` projectile n=0 — не проверка боя.
Forge штатно добавил отсутствовавшие `reacquireTicks=1`/`activeAcquisitionTicks=3` в старый world config.
После clean stop исходные world config и server.properties восстановлены byte-for-byte из
локальных копий `forge-addon/build/baseline-00/`, SHA-256 совпали с состоянием до запуска:

| Сохранённый файл | SHA-256 до = после |
|---|---|
| `run/standalone/eula.txt` | `E4DB182AA77B5E31657F4B83F8973C79A8C65217A4383200F9F59F7C94752FAD` |
| `run/standalone/server.properties` | `E0F9353DD613EF3E426B1B1C4CA4097881568C6F6C9BBB78FDE89563C9375658` |
| `run/standalone/independent-smoke/serverconfig/tacticalnpcai-server.toml` | `46B775E8556A2966C1876613955FCC3BA29FC8D49533CF89188977166081B53A` |

EULA не записывалась/не принималась агентом. Мир штатно сохранён сервером; его runtime/logs/build
выходы ignored. Отсутствующих обязательных проверок/BLOCKED нет; FAIL проекта в этом этапе нет.

### Проверенные production artifacts

Оба JAR собраны/reobfuscated из указанного HEAD после GameTests без
`combatTests`, `legacyCompatibility`, `profile`, `clientSmoke`.
Verifier запущен bundled Python по пути
`C:\Users\makar\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`.

| Файл в `forge-addon/build/libs/` | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 149042 / 77 | `98B274837CDBC43FBE43575C540A2FA7EF3E69F52047ABC29BFBE28BD3283358` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `08A9E631241BDC0ABAD2232800C00D05A37B3CE6CC351FDBF71F8BB97834A053` |

Java major 61; нет test/profile/client smoke classes, тестовых арен или вложенных dependency JAR;
NPCs-ссылки отсутствуют в main, игровых ссылок нет в core. Серверные прогоны используют
ForgeGradle userdev source sets, а не distribution JAR; эти виды доказательств различаются.

### Ручная приёмка владельцем

На клиенте и сервере Minecraft 1.20.1 / Forge 47.4.20: основной production JAR + оригинальный
TaCZ 1.1.8-hotfix2. Для legacy дополнительно NPCs 2.1.0 + optional compat JAR.
Обычный startup без test/profile/clientSmoke flags; использовать отдельный тестовый мир.
Сохранить его world config, чтобы сравнение настроек после restart было воспроизводимым.

В игре с cheats/OP, на ровной свободной площадке:

```mcfunction
/gamemode creative
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add baseline00
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] tacz:ak47
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] finite 7
/tacticalnpc faction @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] red
/tacticalnpc squad @e[type=tacticalnpcai:npc,tag=baseline00] 00000000-0000-0000-0000-000000000041
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] ASSAULT 24
/tacticalnpc diagnostics
/gamemode survival
```

Отойти на 15–25 блоков в поле зрения: проверить один бой, расход именно finite reserve,
исчерпание, стены/союзный сектор и native reload/bolt. Вернуться в creative, переключить:

```mcfunction
/gamemode creative
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] infinite
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] tacz:ai_awp
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] PATROL 16
/data get entity @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] tacticalnpc
/data get entity @e[type=tacticalnpcai:npc,tag=baseline00,limit=1] HandItems
/save-all flush
```

Проверить PATROL без цели. Через серверную консоль `stop`, снова запустить тот же мир,
повторить оба `data get`: сравнить serviceId/faction/squad/PATROL/anchor/ammo/loadout;
native ammo может изменяться при reload/бое, live target/path не должны сохраняться.
При желании проверить finite persistence отдельным restart с `finite 17` до сохранения.
`/tacticalnpc squad @e[type=tacticalnpcai:npc,tag=baseline00] clear` проверяет снятие squad.
`/tacticalnpc hard` проверять отдельно с фиксацией world config; затем `/tacticalnpc diagnostics`.
При optional runtime `/tacticalnpc spawnlegacy`, equip через селектор `type=tacznpcs:npc`,
снять/вернуть `tactical_ai` и проверить отсутствие двойного огня/возврат исходных goals.

Изменение этого этапа: только новый `docs/STAGE-JOURNAL.md`; production/core/tests/build scripts
не редактировались. Финальный HEAD прежний, tracked diff пустой, untracked — только этот журнал.
Хеши обоих JAR после ordinary server повторно совпали с verifier. Commit/PR не создавались.
Минимальная починка baseline не требуется: исходный проект собрался и прошёл обязательные suites.
При завершении 00 — остановка на WAITING_PLAYTEST; human playtest/distribution launch/save-restart
агентом не выполнялись. Последующее принятие 00 не является новым техническим прогоном этих проверок.

## 01 — продолжение поиска целей (R2), 2026-10-16

Статус: **WAITING_PLAYTEST**. Зависимость 00 принята владельцем. Этапы 02+ не запускались.
База: HEAD `d3d7de3de41a27c52db51e14df2473206bd3d11c`, `main...origin/main`;
при старте только ранее созданный untracked `docs/STAGE-JOURNAL.md`. Reset/checkout/pull не было.
Сборка 01 относится к этой базе **плюс working-tree diff**, а не к неизменённому HEAD.
Pinned Minecraft 1.20.1 / Forge 47.4.20 / Java 17.0.19+10 / TaCZ 1.1.8-hotfix2 /
optional NPCs 2.1.0, Gradle 8.8 / ForgeGradle 6.0.54; dependency SHA-256 повторно совпали с таблицей 00.
Применены локальные `hardcore-pvp-brain` и `forge-ai-profiling`; перечитаны AGENTS и integration audit.

### Что изменено

- `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/VisibilitySensor.java`: постоянный
  курсор по штатному `ServerLevel.players()`, новая порция до 16 записей, сортировка только этой
  порции, не более 4 завершённых LOS за scan. Cheap valid/range/FOV до expensive budget/LOS;
  retained page содержит только UUID, каждый UUID разрешается через native entity lookup и
  проверяется заново. DEFERRED сохраняет индекс ещё не проверенной цели. Нет новых координатных
  наблюдений без CLEAR, полного world entity scan/сбора всех игроков/сортировки всего уровня.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java`: отличает
  acquisition DEFERRED от пустого результата; сохраняет candidate/contact/memory, запрещает
  огонь по отложенному LOS. `stop()` очищает cursor/page. UUID staggering, cadence, текущий
  per-tick LOS, perception/reaction, ContactReadiness, budgets и native gates сохранены.
  Свежий CLEAR кандидат, ещё ожидающий perception, не заменяется следующей страницей;
  nextScan при этом продолжает обновляться в исходной UUID phase.
- `forge-addon/src/gametest/java/dev/zuma/tacticalnpc/PerceptionTests.java`: шесть реальных Forge
  регрессий — четыре закрытых/пятый открытый; первые четыре вне FOV; 34 игрока с открытым вне
  исходного capped query; настоящий исчерпанный ray budget и повтор того же кандидата;
  вооружённый controller через cadence/perception, затем стена+budget и frozen memory/no fire;
  отдельный >16 runtime fixture perception=8/cadence=2, без сокращения задержки восприятия.
- Этот журнал: принятие 00 и результаты 01. Команды, приказы/PATROL, оружие, faction/squad,
  радиусы, movement, core, сборочные скрипты и зависимости не изменялись. Поиск остаётся Player-only.

Переход от capped spatial query к native player list прямо необходим для R2: курсор только
внутри первой выборки 16 оставил бы всех остальных в вечном starvation. Новый код индексирует
существующий список, не копирует/не обходит его целиком за tick. На scan — максимум 16 новых
cheap записей и максимум 16 cheap повторных проверок retained page; expensive candidate/ray
лимиты AiWorkBudget остаются прежними. Дальние/неподходящие игроки занимают только cheap slots.

### Граница ожидания

Для конечного стабильного списка из **N игроков измерения** (включая дальних/неподходящих),
фиксированных допустимости/FOV/позиций и доступных четырёх candidate+LOS разрешений на scan:
из начала цикла доступная цель найдётся за **≤4×ceil(N/16)** вызовов acquire, либо раньше будет
найдена другая открытая цель. Для произвольного прогресса после изменения списка консервативно
**≤4×(ceil(N/16)+2)**. Пустая/отфильтрованная страница занимает один scan.
При одной гарантированной завершённой LOS-проверке на scan вместо четырёх множитель — 16.
При бесконечном budget starvation конечного tick-предела нет; DEFERRED не теряет прогресс.

Cadence **I=activeAcquisitionTicks** и UUID phase сохранены: верхняя граница поиска — I × число
scan-возможностей, плюс до 20 ticks существующего activation cache; perception/reaction и
turn/readiness добавляются отдельно. Это условная граница поиска, не SLA динамического боя:
движение, вращение, join/leave, потеря LOS и недоступный бюджет нарушают условия стабильности.
Ближайшая цель выбирается внутри страницы, глобальный nearest-first больше не обещается.

### Проверки 01

Core/verifier — из корня, Gradle — из `forge-addon`; raw logs ignored в `docs/testing/`.
Временный `forge-addon/build/stage01-perception.init.gradle` исключает только прочие `*Tests.java`
для red/targeted-прогонов; production build script не меняется. Полные suites идут без фильтра.

| Проверка | Команда | Результат / лог |
|---|---|---|
| Core | `bash ai-core/run-tests.sh` через Git Bash | PASS 72 policy + 94 temporal; `stage01-core-2026-10-16.log` |
| Первый red | `.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage01-perception.init.gradle --console=plain` | Ожидаемый FAIL 5/7, старые 2 PASS; `stage01-red-2026-10-16.log`. Первый сценарий раньше R2 упал на незаданном body yaw стенда; исправлен только fixture. |
| Подтверждённый red, production ещё старый | та же команда | FAIL 5/7 именно по R2, старые 2 PASS; `stage01-red-confirmed-2026-10-16.log`. Открытый fifth имеет CLEAR/FOV; >16 fixture подтверждает исключение открытого из старой выборки. |
| Targeted green | та же команда после исправления | PASS 7/7 required, BUILD SUCCESSFUL (1m08s); `stage01-green-targeted-2026-10-16.log` |
| Первый full standalone | `.\gradlew.bat runGameTestServer --offline -PcombatTests --console=plain` | PASS 34/34 required, BUILD SUCCESSFUL (5m39s); `stage01-standalone-2026-10-16.log`. До добавления pending-perception regression/guard, не финальная версия. |
| Pending-perception red | targeted-команда выше | FAIL 1/8, остальные 7 PASS (1m15s); `stage01-pending-red-2026-10-16.log`. Подтверждено прерывание видимого кандидата при perception=8/cadence=2; добавлен только guard, не меняющий phase/delays. |
| Full standalone после guard, persistent world | `.\gradlew.bat runGameTestServer --offline -PcombatTests --console=plain` | FAIL 1/35, остальные 34 PASS (5m44s); `stage01-standalone-final-2026-10-16.log`. Все R2 PASS, существующий moveReloadAndPeek не увидел projectile в PEEK, хотя последующий ENGAGE стрелял. Причина не установлена; cover/movement не менялись. |
| Full standalone, fresh world, тот же код | та же команда | PASS 35/35 required, BUILD SUCCESSFUL (6m01s); `stage01-standalone-fresh-2026-10-16.log`. Старый generated world сохранён в `forge-addon/build/stage01-gametest-standalone-world-before-fresh`, абсолютные пути проверены до Move-Item; EULA/configs launcher не менялись. |
| Full compatibility | `.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility --console=plain` | PASS 44/44 required, BUILD SUCCESSFUL (7m04s); `stage01-compat-2026-10-16.log`. Свежий world; прежний сохранён в `forge-addon/build/stage01-gametest-compat-world-before-fresh` после проверки абсолютных путей. |
| Final production | `.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain` | PASS BUILD SUCCESSFUL, 12 executed (26s); `stage01-production-build-2026-10-16.log` |
| Package verifier | `python -B tools/verify-production-artifacts.py` | PASS; `stage01-production-artifacts-2026-10-16.log`, bundled Python как в 00 |
| Ordinary startup/stop | `.\gradlew.bat runServer --offline --console=plain`, `tacticalnpc diagnostics`, `stop` | PASS Done (4.892s), все dimensions saved, exit 0 / BUILD SUCCESSFUL (54s); `stage01-dedicated-2026-10-16.log` — server log, Gradle итог в terminal output |
| Human multiplayer / distribution launcher / performance | сценарий ниже | NOT RUN |

После targeted green UUID resolution заменён на native `getEntity(UUID)`, чтобы не делать
линейный поиск player list при каждом retained UUID; runtime regression дополнен wall+memory
проверками. После первого full добавлен pending-perception red/guard. Финальные полные suites
проверяют именно итоговую версию. Core не менялся.

Обычный dedicated использовал прежний `run/standalone/independent-smoke`, localhost:25585;
test namespaces пусты, native xmag verified=true. Новых NPC/боевого испытания в этом smoke не было.
Forge автоматически добавил два отсутствовавших config-ключа, как в 00; после stop исходные
server.properties/world server config восстановлены из `build/stage01-preserve/`.
Их SHA-256 и EULA до/после совпали с таблицей 00. EULA не записывалась/не принималась агентом.
Оба JAR после smoke повторно совпали с хешами verifier. `git diff --check` PASS.

### Production artifacts 01

Собраны/reobfuscated после тестов без combatTests/profile/clientSmoke/legacyCompatibility flags.
Verifier PASS: Java major 61, main не содержит NPCs references, core не содержит игровых references;
нет test/profile/client smoke классов, тестовых арен и вложенных dependency JAR.

| Файл в `forge-addon/build/libs/` | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 149971 / 77 | `4485C41F8B8426721B08F3AB4CE01C5B23D54D2C6D96DFA21F4B0DECE805D27D` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `927589F846CC0724492981853B87471D9869338892ACCAE27E7DF8FCF93D15D6` |

GameTests/dedicated — ForgeGradle userdev, не distribution JAR launcher. Человеческий бой и
distribution launch остаются NOT RUN. Один промежуточный CoverTests FAIL на persistent мире
не скрывается: fresh standalone и combined PASS, но причина нестабильности не установлена.
Изменений cover/movement ради получения PASS не было. `Can't keep up` в тестовых логах не
позволяет заявлять perf/20 TPS. Отдельный perf/длительный soak не выполнялся.
Сохранён production/test diff (без журнала) в ignored `forge-addon/build/stage01-source.patch`,
SHA-256 `66DCD2FD8678D8EAF2FDD6BFE082CE120E9324BB9DED65D6430C80508F16A612`.
Сохранён через native `git diff --binary --output`; `git apply --reverse --check --ignore-space-change`
подтвердил соответствие diff текущим файлам (без применения/изменения исходников).
Tracked diff: **3 файла, +191/-15**; остальные изменения — этот ранее untracked журнал.

| Source file (сокращённый путь) | SHA-256 итогового working-tree файла |
|---|---|
| `combat/VisibilitySensor.java` | `22504AC6091CD88FAA61C06081E4BEAE6BCF30DCD36C986C3811A490E5399EC3` |
| `combat/TacticalCombatGoal.java` | `C04DC14DA1A2CA9172C231E4722B00B116498B03FE8EA799E8273F2EC3355191` |
| `PerceptionTests.java` | `A358DFEC855AEF6807AB24E95F1C62AF1826936E9A11F5C0AA349FF86310E560` |

### Ручная проверка 01

Собранный основной JAR + TaCZ 1.1.8-hotfix2 на клиенте и сервере; для optional проверки также
NPCs 2.1.0 + compat JAR. Использовать свободную ровную тестовую арену с несколькими игроками.
Из creative/OP на фиксированной площадке:

```mcfunction
/gamemode creative
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage01
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage01,limit=1] tacz:ak47
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage01,limit=1] infinite
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage01,limit=1] HOLD_POSITION 24
/tacticalnpc diagnostics
```

Четыре ближних survival игрока стоят за непрозрачной стеной перед NPC, пятый дальше — в его FOV
с открытой линией зрения через отдельный проход. На каждом тестовом клиенте `/gamemode survival`;
наблюдатель остаётся creative. Проверить обнаружение/реакцию/огонь по доступному пятому, без
наведения на скрытые позиции. Повторить с ближними игроками сзади (вне FOV), затем с >16
кандидатами, если есть столько клиентов. Диагностика: `/tacticalnpc diagnostics`.

Затем убрать лишних survival игроков из сцены (перевести в creative), оставить один обычный
видимый контакт: проверить прежнюю реакцию. Закрыть этот контакт сплошной стеной — новые
native выстрелы должны прекратиться; открыть — обычный reacquire. Старые ограниченные
suppression permits по известной открытой точке сохраняются; сквозь сплошную стену огня нет.
Новый NEUTRAL, NPC targets и изменение баланса в этот этап не входят.

Итог: обязательные финальные автоматические проверки выполнены; BLOCKED нет.
Промежуточные red failures и отдельный cover FAIL сохранены выше, не объявляются PASS.
Неповторившийся cover сбой остаётся известным ограничением с неустановленной причиной.
HEAD не менялся, commit/PR не создавались; production/test diff только в трёх перечисленных файлах.
Остановка на **WAITING_PLAYTEST**: ручная multiplayer-приёмка ещё NOT RUN, этап 02 не запускать
до отдельного сообщения владельца о принятии и разрешении следующего этапа.

## 03 — единый интерфейс mode/order, 2026-10-16

Статус: **ACCEPTED** — владелец явно принял 03 в запросе этапа 04.
При завершении 03 статус был WAITING_PLAYTEST. Зависимость 02 принята владельцем по его сообщению. Работа только над 03;
04–08 не реализуются. Фактическая база: HEAD `d3d7de3de41a27c52db51e14df2473206bd3d11c`, main,
плюс принятый working-tree diff 01 (PerceptionTests/TacticalCombatGoal/VisibilitySensor) и этот журнал.
Отдельных локальных файлов/записи 02 при старте не было; отката/восстановления по старому SHA нет.
Ранее принятые Java-файлы 01 не редактируются. Новая сборка относится к базе плюс diff 01 и 03.
Pinned Minecraft 1.20.1 / Forge 47.4.20 / Java 17.0.19+10 / TaCZ 1.1.8-hotfix2 / optional NPCs 2.1.0;
Gradle 8.8 / ForgeGradle 6.0.54. Dependency hashes снова совпали с таблицей 00.
Прочитаны AGENTS, локальные hardcore-pvp-brain/forge-ai-profiling/forge-compat-hook,
актуальные runtime/order/commands, Forge README и integration audit; для проверок применяется
forge-ai-profiling. Core и адаптер NPCs не меняются.

### Контракт промежуточного интерфейса

| Новый синтаксис | Существующий приказ / радиус |
|---|---|
| `/npc mode attack [targets]` | ASSAULT; сохраняет текущий радиус каждого NPC |
| `/npc mode defend <radius> [targets]` | DEFEND_AREA; radius 4..64 |
| `/npc mode stand [targets]` | HOLD_POSITION; сохраняет текущий радиус каждого NPC |

Это **совместимые алиасы прежнего поведения**, не новые полноценные режимы 04–08.
Например, stand пока допускает прежние локальные перемещения HOLD_POSITION; нового STAND→DEFEND нет.
`/tacticalnpc order <npc> <kind> <radius>` остаётся, PATROL — отдельный приказ без нового alias.
При любом переключении якорь — текущая позиция NPC, как раньше.

Permission 2 на root и mode, suggestions attack/defend/stand. Без targets нужен Player:
серверный eye/view ray до 32 блоков, COLLIDER block clip и ближайшее пересечение hitbox.
NPC вне луча не подставляется как ближайший. Попадание в неподдерживаемую сущность отклоняется,
NPC за ней не выбирается; стена блокирует выбор. Дальность — до пересечения hitbox, как у ray pick.
До 64 pickable entity candidates на одно выполнение; насыщение даёт ошибку и просит targets.
Explicit targets — vanilla EntityArgument.entities (selector/UUID/имя), в конце команды;
не ограничены 32 блоками. Для консоли/non-player без targets ясная ошибка.
Вся выбранная группа сначала проверяется: только живые independent tacticalnpcai:npc, server thread.
Mixed batch с игроком/мобом/armor stand/legacy не меняет ни одного адресата.

### Изменения 03

- `forge-addon/src/main/java/dev/zuma/tacticalnpc/debug/DebugCommands.java`: новый `/npc mode`,
  optional targets, eye ray, общая prevalidation/applyOrder для нового и старого синтаксиса.
  Оба вызывают существующий `TacticalNpcEntity.setOrder`; второго контроллера/ModeState нет.
  Все девять старых command branches и PATROL suggestions сохранены.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/entity/TacticalNpcEntity.java`: одна строка
  `getNavigation().stop()` в существующем setOrder. Прямо необходима по критерию 03: тест выявил,
  что MovementExecutor.stop в уже HOLD не очищал отдельно активный path. Теперь оба синтаксиса
  гарантированно инвалидируют path; generation/decision сбрасывает прежний controller.stop.
  Native cooldown/ammo/reload не сбрасываются новым обработчиком.
- `forge-addon/src/gametest/java/dev/zuma/tacticalnpc/ModeCommandTests.java`: 5 реальных Forge
  сценариев через текущий Brigadier dispatcher — одинаковые order/anchor/radius, NBT round-trip
  schema 2/serviceId, path/generation/native ammo/cooldown, whole-batch prevalidation,
  console/error/radius/permission/suggestions/старые branches/PATROL, прицел/стена/пустой прицел,
  дальность 31/35 блоков, границы radius 4/64 и неподдерживаемая front entity. В compatibility
  процессе дополнительно создаётся реальный legacy NPC и проверяется отказ до batch mutation.
- Этот журнал: результаты и ручная приёмка. Состояние по-прежнему только order/anchor/leash,
  NBT schema **2**, без mode-поля. GUI, новые spawn/neutral/targets и новое поведение не добавлялись.

### Проверки 03

Core/verifier — из корня; Gradle — из forge-addon, cache готов. Raw logs ignored в docs/testing.
Временные init scripts находятся только в build, production build.gradle не меняется:
`stage03-command.init.gradle` выбирает ModeCommandTests; `stage03-regression.init.gradle` —
ModeCommandTests + IndependentNpcTests + WeaponProfileTests. При legacyCompatibility также
компилируются и запускаются существующие compatTest classes. Это относящиеся к 03 наборы,
**не полные 40/49 combat suites**. Zero-test запуск PASS не засчитывается.

| Проверка | Команда | Реальный результат / лог |
|---|---|---|
| Initial compile | `.\gradlew.bat compileJava compileCompatJava --offline --console=plain` | PASS (17s); `stage03-compile-2026-10-16.log` |
| Core | `bash ai-core/run-tests.sh` через Git Bash | PASS 72 policy + 94 temporal; `stage03-core-2026-10-16.log` |
| Initial commands | `.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage03-command.init.gradle --console=plain` | FAIL 3/5 (48s); `stage03-commands-2026-10-16.log`. Два aim FAIL — FakePlayer yHeadRot не задан; pinned LivingEntity.getViewYRot(1) подтвердил чтение head yaw. Третий — реальный stale path при HOLD; исправлен общий setOrder. |
| Commands после правок | та же команда | PASS 5/5 required (45s); `stage03-commands-final-2026-10-16.log` |
| Relevant standalone | `.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage03-regression.init.gradle --console=plain` | FAIL 1/21 (3m15s), остальные 20 PASS, новые commands 5/5; `stage03-standalone-2026-10-16.log`. Старый independentFourAgainstOne потерял health: no allied damage during converging fire. Он не вызывает команды/setOrder; combat-код 03 не менялся. Источник damage пока не установлен. |
| Damage diagnostic repeat | предыдущая команда + `-I build/stage03-damage.init.gradle` | PASS 21/21 required (2m55s); `stage03-standalone-diagnostic-2026-10-16.log`. Временный read-only LivingDamageEvent listener в build/stage03-diagnostics, без combat-правок. Потеря health не повторилась, причина первого FAIL не установлена. |
| Expanded compatibility | `.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage03-regression.init.gradle -I build/stage03-damage.init.gradle --console=plain` | FAIL 1/30 (4m37s), остальные 29 PASS, новые commands 5/5; `stage03-compat-2026-10-16.log`. Старый weaponDistanceReallyMoves: sniper не достиг >18 blocks за время стенда. Причина не установлена; movement/weapon code не исправлялся вне scope 03. Legacy отдельно по UUID и mixed selector без armor stand корректно отклонён. |
| Commands + legacy contracts | `.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage03-command.init.gradle --console=plain` | PASS 14/14 required (1m37s); `stage03-compat-commands-2026-10-16.log` |
| Final production | `.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain` | PASS 12 executed / BUILD SUCCESSFUL (26s); `stage03-production-build-2026-10-16.log` |
| Production verifier | `python -B tools/verify-production-artifacts.py` | PASS; `stage03-production-artifacts-2026-10-16.log`, bundled Python как в 00 |
| Ordinary dedicated startup/stop | `.\gradlew.bat runServer --offline --console=plain`, `help npc`, `npc mode stand`, `tacticalnpc diagnostics`, `stop` | PASS Done (4.887s), help mode и ожидаемая ошибка console targets, все dimensions saved, exit 0 / BUILD SUCCESSFUL (1m11s); `stage03-dedicated-2026-10-16.log` — server log, Gradle итог в terminal output |
| Full 40/49 combat suites, graphical client, real disk restart, human playtest, perf | — | NOT RUN в 03 |

Relevant standalone запускался на новом generated world: прежний сохранён в
`forge-addon/build/stage03-standalone-world-before-regressions`; абсолютные source/destination
проверены до Move-Item. GameTest configs/рабочие миры/build/logs ignored. EULA не меняется.
Compatibility world аналогично сохранён в `build/stage03-compat-world-before-regressions`.
Обычный dedicated — прежний independent-smoke, localhost:25585, test namespaces [], xmag verified=true.
После stop server.properties/world config восстановлены из build/stage03-preserve; их SHA-256
и EULA совпали с таблицей 00. EULA агентом не записывалась/не принималась. JAR hashes после smoke
совпали с verifier. `git diff --check` PASS. Gradle test=NO-SOURCE отдельно, не засчитывается как тест.

### Ручная приёмка 03

На клиенте и сервере основной JAR + pinned TaCZ; optional adapter нужен только вместе с NPCs 2.1.0.
В creative/OP создать NPC старой командой и отметить его для явных проверок:

```mcfunction
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage03
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage03,limit=1] tacz:ak47
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage03,limit=1] infinite
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage03,limit=1] ASSAULT 24
```

Навести прицел именно на него (не дальше 32 блоков, без стены) и по очереди выполнить:

```mcfunction
/npc mode attack
/npc mode defend 16
/npc mode stand
/data get entity @e[type=tacticalnpcai:npc,tag=stage03,limit=1] tacticalnpc
```

Поставить второго NPC ближе, но вне прицела: меняться должен только пересечённый лучом.
Со стеной/пустым прицелом команда должна отказать, не выбрав ближайшего.
Проверить новый/старый интерфейсы и отдельный PATROL:

```mcfunction
/npc mode attack @e[type=tacticalnpcai:npc,tag=stage03,limit=1]
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage03,limit=1] DEFEND_AREA 16
/npc mode defend 16 @e[type=tacticalnpcai:npc,tag=stage03,limit=1]
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage03,limit=1] HOLD_POSITION 24
/npc mode stand @e[type=tacticalnpcai:npc,tag=stage03,limit=1]
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage03,limit=1] PATROL 16
/tacticalnpc diagnostics
/npc mode defend 16 @e[type=tacticalnpcai:npc,tag=stage03,limit=1]
/data get entity @e[type=tacticalnpcai:npc,tag=stage03,limit=1] tacticalnpc
/save-all flush
```

В dedicated console команды без `/`; `npc mode stand` без targets — ошибка,
`npc mode stand @e[type=tacticalnpcai:npc,tag=stage03,limit=1]` — успех.
Проверить invalid radius 3/65, unsupported mixed targets и permission <2: без изменений NPC.
После сохранения штатный stop и запуск того же мира; повторить data get, сравнить order/anchor/leash,
faction/squad/ammo/serviceId. NBT GameTest не заменяет эту ручную disk-restart проверку.

### Production artifacts 03

Собраны/reobfuscated после тестов без combatTests/profile/clientSmoke/legacyCompatibility и без
init scripts. Verifier PASS: Java major 61; без test classes (включая Stage03DamageTests), арен,
вложенных dependencies, NPCs references в main, игровых references в core.

| Файл в forge-addon/build/libs | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 153062 / 77 | `4FC6A9FC14995880A2E106FD742D777118FDB544108B10BC57C33B447073CC11` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `522167FF60118DF6CBD3AA3C90E82E87F39B118A8D41EB07205871F1430095D2` |

Diff именно 03: DebugCommands +64/-6, TacticalNpcEntity +1/-0,
новый ModeCommandTests 159 строк; всего **3 Java-файла, +224/-6**, плюс запись в этом журнале.
Production patch (два существующих файла): `forge-addon/build/stage03-production.patch`,
SHA-256 `19E4FEE7047B0AE4F6052F5B292C002571F2C4C80633F5350D10337A2DE8FB17`.

| Source file 03 | SHA-256 итогового working tree |
|---|---|
| `debug/DebugCommands.java` | `575508E9CDFAAA0ED947A15853D094972DCDCD618244C6C599A98B21D730134C` |
| `entity/TacticalNpcEntity.java` | `96F4219EA8F750BB51B105C843AAC352F6FA26229CE510D4DE564BE5D5A2F5C2` |
| `ModeCommandTests.java` | `00ACF3236DC458CA4D183D6EF86085FB9EE886B4CF7547D8FAA2B87E9F6342C0` |

SHA-256 всех трёх принятых Java-файлов 01 совпали с manifest 01: они не менялись.
Broader combat regression **не полностью зелёный**: FAIL 1v4 не повторился на диагностическом
21/21, FAIL sniper-distance в expanded compatibility не исправлялся. Причины не установлены;
это известные ограничения для владельца, не PASS и не повод менять AI вне 03.
Полные 40/49 suites, distribution launcher, graphical/human playtest и настоящий disk restart
не запускались в 03. GameTests/dedicated запускаются в ForgeGradle userdev.
Следующий этап не запускать до приёмки владельца.

Итог 03: интерфейс и обязательные command-проверки выполнены; доступных, но не выполненных
обязательных проверок интерфейса нет. Расширенный combat-набор имеет явно указанные FAIL,
поэтому общий combat PASS не заявлен. Чинить unrelated AI в 03 не разрешено.
HEAD прежний, commit/PR не создавались. Остановка на **WAITING_PLAYTEST**;
после ручной приёмки только владелец разрешает следующий этап.

## 04 — строгая территория defend, 2026-10-18

Статус: **ACCEPTED** — владелец явно принял 04 в запросе этапа 05.
При завершении 04 статус был WAITING_PLAYTEST. Зависимость 03 принята владельцем в запросе 04.
Checkpoint: `main`, HEAD `d3d7de3de41a27c52db51e14df2473206bd3d11c`, проверен заново;
исходное ревью не использовалось вместо проверки checkout. Принятые незакоммиченные 01/03
сохранены. Reset/checkout/pull, commit/PR и соседние этапы не выполнялись.
При старте изменены PerceptionTests, TacticalCombatGoal, VisibilitySensor, DebugCommands,
TacticalNpcEntity; untracked STAGE-JOURNAL и ModeCommandTests. Снимки изменяемых файлов 03
до работы: ignored `forge-addon/build/stage04-before/`.
Прочитаны AGENTS.md, tactical-navigation/forge-ai-profiling SKILL.md, актуальные README,
IMPLEMENTATION-STATUS, INTEGRATION-AUDIT и код команд/планирования/движения.

### Контракт и diff 04

- `DefendTerritory`: единый чистый контракт круга по XZ. Высота не расходует радиус;
  поверхность, collision, опасности и loaded chunks по-прежнему проверяет adapter.
  Рабочая граница центра консервативно уменьшена на `width / sqrt(2) + 0.75`:
  circumscribed body footprint плюс запас vanilla arrival/coasting. Для width=0.6 это ~1.174 блока.
- DEFEND_AREA принимает конечный radius 4..128; 3/129/NaN/Infinity отвергаются.
  Новый `/npc mode defend`, старый `/tacticalnpc order ... DEFEND_AREA`, setter, runtime record
  и NBT согласованы. Schema 2/order/leash сохранены: radius 128 реально проходит save/load.
  Для остальных приказов command/setter/NBT максимум остаётся 64. При attack/stand без radius
  после defend 128 сохранённый radius нормализуется до 64; интерфейсы используют тот же state.
- Planner проверяет XZ и body/arrival margin; внешняя видимая цель не запрещает огонь,
  но внешний pursuit endpoint отклоняется. Radius не меняет vision/shot range или FOLLOW_RANGE.
- Cover/peek endpoints и реальные `Path.getEntityPosAtNode` проходят тот же контракт;
  внутренний endpoint не оправдывает внешний обход. Executor дополнительно проверяет recovery
  alternatives, strafe/backward retreat с прогнозом движения и старые активные пути.
  Guard вызывается каждый controller tick до activation/cadence return; максимум 128 узлов,
  без дополнительных raycasts/path requests. Текущие LOS/reaction/native-round gates не менялись.
- Снаружи разрешены безопасные направленные внутрь сегменты, включая начальные внешние узлы.
  После входа повторный выход запрещён; пересечение круга с выходом на противоположной стороне
  также отвергается. Knockback velocity не стирается, production teleport не добавлен.
  Дальние defend destinations разбиты на локальные legs <= min(16, FOLLOW_RANGE/2) с конечным
  сокращением до 1 блока при поиске безопасного standing point. Чанки production не форсирует.
- Compatibility дополнительно выявил остановку у radius128: следующий внешний waypoint уже
  считался достигнутым vanilla, но guard проверял его как новое движение наружу. В pinned mapped
  `PathNavigation.followThePath` проверены offset `(width+1)/2`, tolerance `0.75-width/2`
  для width<=0.75, иначе width/2, и |dy|<1. Guard пропускает такой уже достигнутый текущий
  waypoint и проверяет следующие реальные сегменты; новый synthetic partial-path assertion
  и фактический возврат это проверяют. Initial-node exception не распространяется на весь путь.
- PATROL без цели, STAND, оружие/баллистика, радиусы обнаружения, фракции/союзы и набор команд
  не изменялись. Native cooldown/ammo не сбрасываются. Pure core не импортирует игровые API.

Именно 04: **12 code-файлов, +336/-20**, плюс запись в этом журнале:

| Файл (от корня репозитория) | Diff 04 |
|---|---|
| ai-core/run-tests.sh | +1/-0: runner нового набора |
| ai-core/src/main/java/dev/zuma/tacticalnpc/core/DefendTerritory.java | новый, 22 строки |
| ai-core/src/main/java/dev/zuma/tacticalnpc/core/OrderPlanner.java | +6/-2 |
| ai-core/src/main/java/dev/zuma/tacticalnpc/core/OrderState.java | +3/-1 |
| ai-core/src/test/java/dev/zuma/tacticalnpc/core/DefendTerritoryTest.java | новый, 46 строк |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/CoverSensor.java | +26/-1 |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/MovementExecutor.java | +44/-4 |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java | +2/-2 относительно принятого 01 |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/debug/DebugCommands.java | +6/-6 относительно 03 |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/entity/TacticalNpcEntity.java | +2/-2 относительно 03 |
| forge-addon/src/gametest/java/dev/zuma/tacticalnpc/ModeCommandTests.java | +2/-2 относительно 03 |
| forge-addon/src/gametest/java/dev/zuma/tacticalnpc/DefendTerritoryTests.java | новый, 176 строк |

Patch именно 04: `forge-addon/build/stage04-code.patch`, SHA-256
`7CB9CDA970FBC42C03A80D01AE2E207B8FB17427F8C693C992FD6932BD5E337E`.
SHA-256 каждого code-файла: `forge-addon/build/stage04-manifest.json`.
`git apply --check --reverse --ignore-space-change` для этого patch и `git diff --check` PASS;
patch не применялся к рабочему дереву. VisibilitySensor/PerceptionTests SHA совпали с принятым 01.

### Фактические проверки 04

Среда повторно проверена: Microsoft Java **17.0.19+10-LTS**, Minecraft **1.20.1**, Forge **47.4.20**,
Gradle **8.8**, ForgeGradle **6.0.54**, TaCZ **1.1.8-hotfix2**, optional NPCs **2.1.0**.
Оба binary SHA совпали с аудитом: TaCZ `2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2`,
NPCs `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE`.
Cache заполнен; все Gradle-команды из forge-addon. Raw logs ниже — в ignored docs/testing/.

| Запуск | Реальный результат / лог |
|---|---|
| `bash ai-core/run-tests.sh` до production-правок, с новым desired-behavior тестом | **FAIL** на высоте: горизонтально внутренний NPC терял fire/уходил RETREAT. Старые 72+94 PASS; `stage04-core-red-2026-10-18.log` |
| `bash ai-core/run-tests.sh` финальный | **PASS 72 policy + 94 temporal + 38 territory**; `stage04-core-final-2026-10-18.log` |
| `gradlew.bat compileJava compileCompatJava --offline --console=plain` | **PASS**, 20s; `stage04-compile-2026-10-18.log` |
| Ошибочный первый init-script path | **FAIL до тестов**, лишний forge-addon в пути; исправлено. Ноль GameTests не считался PASS |
| Первый selected standalone | **FAIL 4/15**, остальные 11 PASS; `stage04-standalone-2026-10-18.log`. Ошибка нового стенда: spawn внутри пола/неверные feet Y; исправлено |
| Standalone после высоты, fresh generated world | **FAIL 2/16**, 14 PASS; `stage04-standalone-fixed-fixture-2026-10-18.log`. Дальний actor за ticking area; callback регистрировал onEachTick во время исполнения |
| Focused 7 defend tests | **FAIL 1/7**, 6 PASS; `stage04-focused-2026-10-18.log`. С corridor chunk tickets дальний возврат PASS; оставшийся callback вынесен до тиков |
| Selected standalone до arrival edge case | **PASS 18/18 required**, BUILD SUCCESSFUL 3m08s; `stage04-standalone-final-2026-10-18.log` |
| Selected compatibility до arrival fix | **FAIL 2/27**, 25 PASS, 4m36s; `stage04-compat-final-2026-10-18.log`. Дальний возврат у границы и отдельный legacy shotgun corridor |
| Standalone с arrival fix, до явной опоры direct native probe | **FAIL 1/18**, 17 PASS, 3m; `stage04-standalone-arrival-2026-10-18.log`. Дальний возврат PASS; direct createPath вернул null. Для probe теперь отдельно проверены support/feet Y и onGround, fixture не зависит от первого physics tick |
| Финальный selected standalone с arrival fix | **PASS 18/18 required**, BUILD SUCCESSFUL 3m01s; `stage04-standalone-acceptance-2026-10-18.log` |
| Финальный selected compatibility с arrival fix | **PASS 27/27 required**, BUILD SUCCESSFUL 3m29s; `stage04-compat-acceptance-2026-10-18.log` |
| Plain production build | **PASS**, 12/12 tasks executed, 32s; `stage04-production-build-2026-10-18.log` |
| tools/verify-production-artifacts.py | **PASS**; ignored `forge-addon/build/stage04-production-artifacts-2026-10-18.json` |
| Ordinary dedicated startup/stop | **PASS** Done → help/console validation/diagnostics → stop/save → BUILD SUCCESSFUL 1m10s; `stage04-dedicated-smoke-2026-10-18.log` |

Новые семь Forge-сценариев: обе команды и setter 4/64/128, rejection 3/129/NaN и старые maxima;
реальное NBT128; внутренний endpoint с реальным внешним wall-detour/recovery; strafe и backward
retreat у края; внешнее смещение и сохранённый knockback с фактическим возвратом; 140-block
смещение и реальные короткие пути обратно внутрь radius128; подъём по склону; native TaCZ
projectiles по внешнему игроку при body внутри defend4.
Финальные regressions дополнительно: ModeCommandTests 5, AlphaResponsivenessTests 3,
AlphaLocomotionTests 1, CoverTests 2. Compatibility добавляет 9 настоящих legacy GameTests.
Новый тестовый corridor форсирует только конечный набор чанков и снимает добавленные tickets
перед финальной проверкой; production таких tickets не создаёт.

Точные финальные Forge-команды:

```powershell
cd forge-addon
.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage04-regression.init.gradle --console=plain
.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage04-regression.init.gradle --console=plain
```

Для воспроизведения ignored init script содержит:

```groovy
gradle.projectsEvaluated {
    allprojects {
        if (plugins.hasPlugin('java')) {
            sourceSets.main.java.exclude { element ->
                !element.isDirectory() && element.file.path.replace('\\','/').contains('/src/gametest/') &&
                    element.file.name.endsWith('Tests.java') && !(element.file.name in ['ModeCommandTests.java','DefendTerritoryTests.java','AlphaResponsivenessTests.java','AlphaLocomotionTests.java','CoverTests.java'])
            }
        }
    }
}
```

Прежние generated test worlds перемещены после проверки абсолютных путей внутрь workspace:
`build/stage04-world-before-fresh`, `build/stage04-compat-world-before-fresh`; не удалены.
Ordinary smoke world остаётся independent-smoke; EULA владельца не меняется.

### Ручная приёмка владельца

На ровной безопасной площадке создать/отметить одного NPC; tag stage04 должен быть уникальным.

```mcfunction
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage04
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage04,limit=1] tacz:ak47
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage04,limit=1] infinite
/npc mode defend 4 @e[type=tacticalnpcai:npc,tag=stage04,limit=1]
/data get entity @e[type=tacticalnpcai:npc,tag=stage04,limit=1] tacticalnpc
```

В Survival держать видимого противника снаружи круга в обычной оружейной дальности; NPC стреляет,
но strafe/retreat/cover/peek и обход стены не выводят тело наружу. Проверить узкий внутренний
endpoint, доступный только обходом снаружи. После этого принудительно вынести NPC на 7 блоков
по чистому загруженному коридору (это ручной teleport, не production-механика):

```mcfunction
/execute as @e[type=tacticalnpcai:npc,tag=stage04,limit=1] at @s run tp @s ~7 ~ ~
```

Дождаться фактического возвращения, затем проверить обе команды и сохранение 128:

```mcfunction
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage04,limit=1] DEFEND_AREA 128
/npc mode defend 128 @e[type=tacticalnpcai:npc,tag=stage04,limit=1]
/data get entity @e[type=tacticalnpcai:npc,tag=stage04,limit=1] tacticalnpc
/save-all flush
```

Штатный `stop` из dedicated console и запуск того же мира. Повторить data get: DEFEND_AREA,
leash=128 и anchor/serviceId/faction/squad/ammo сохранены. Каждая выдача команды обновляет anchor
на текущую позицию NPC, это ожидаемо. Затем defend4 у края и на склоне. Проверить отказ 3/129/NaN,
старый PATROL и обычный одиночный контакт/стену. Команды console — без `/`.

### Ограничения 04

- Возврат требует загруженного безопасного пути. Снаружи маршрут, который сначала ещё увеличивает
  удаление от круга, отклоняется; используется существующий конечный recovery, гарантии выхода
  из любого лабиринта нет. Budget DEFERRED не становится path failure.
- Запас body/arrival делает рабочую границу центра строже номинального круга; внешние воздействия
  могут вынести NPC, добровольные команды этого не разрешают.
- Полные 47 standalone / 56 combined, новый 30+ profiling, graphical/human playtest,
  distribution launcher и disk-restart вручную в 04 **NOT RUN**. Исторические 1v4/sniper-distance
  FAIL из 03 не объявлены исправленными или перепроверенными этим selected-набором.
- Legacy shotgun corridor FAIL в первом 04 combined не повторился на окончательном 27/27;
  причина нестабильности не установлена, ASSAULT/оружейный код ради него не менялся.
- Новые этапы, нейтральность, NPC targets, STAND/PATROL и дальность обнаружения не реализовывались.

### Production artifacts 04

После тестов без combatTests/profile/clientSmoke/legacyCompatibility и init scripts выполнены:

```powershell
# Из forge-addon
.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain
# Из корня репозитория, Python 3
python -B tools/verify-production-artifacts.py
```

Verifier PASS: Java major 61, без test/profile/clientSmoke классов, арен, вложенных зависимостей,
NPCs references в main и игровых imports в core. Main содержит 78 own classes, compat — 4.
Это сборка текущего working tree поверх указанного HEAD, а не чистого HEAD без принятых 01/03/04.
Stage04 patch и source manifest выше связывают код с результатом; новый commit не создавался.

| Файл в forge-addon/build/libs | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 157580 / 78 | `FE516B83FBBD859B79B0A0D787E922B83C57AB45CE6DCD5A033BDD1C3F3E73A1` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `63FAE0DDF3EB45522B2E0E806094F9630BF5AFA2523F8C6FB610A068E8E2E6F2` |

Обычный `gradlew.bat runServer --offline --console=plain` без flags/init scripts:
Done, `help npc`, ожидаемый отказ `npc mode defend 128` без console targets,
`tacticalnpc diagnostics`, штатный `stop`, сохранены все dimensions, process exit 0 / BUILD SUCCESSFUL.
Combat в этом smoke не проверялся; он проверен отдельно указанными GameTests.
После stop server.properties и world tacticalnpc config восстановлены byte-for-byte из
`build/stage04-preserve/`; EULA не изменялась. SHA-256 после восстановления:

| Файл ordinary smoke | SHA-256 |
|---|---|
| eula.txt | `E4DB182AA77B5E31657F4B83F8973C79A8C65217A4383200F9F59F7C94752FAD` |
| server.properties | `E0F9353DD613EF3E426B1B1C4CA4097881568C6F6C9BBB78FDE89563C9375658` |
| independent-smoke/serverconfig/tacticalnpcai-server.toml | `46B775E8556A2966C1876613955FCC3BA29FC8D49533CF89188977166081B53A` |

Оба JAR SHA после dedicated совпали с manifest выше; source manifest и patch проверены ещё раз.
Обязательные проверки 04 выполнены, недоступных обязательных проверок/BLOCKED нет.
Исторические FAIL не скрыты: ошибки нового fixture устранены, найденный arrival edge case
исправлен в пределах 04, отдельный legacy corridor FAIL не повторился и остаётся известной
нестабильностью. Итоговые selected suites 18/18 standalone и 27/27 combined PASS.
HEAD неизменён, новый commit/PR не создавался. **WAITING_PLAYTEST — ОСТАНОВКА**.
Следующий этап разрешает только владелец после своей игровой приёмки.

## 05 — внутренний patrol в defend, 2026-10-18

Статус: **ACCEPTED** владельцем в запросе 06. Зависимость 04 принята владельцем в запросе 05.
Начато с git status: `main...origin/main`, HEAD повторно подтверждён как
`d3d7de3de41a27c52db51e14df2473206bd3d11c`. Принятые working-tree изменения 01/03/04 сохранены;
reset/checkout/pull/commit/PR не выполнялись. Перед изменениями четыре затрагиваемых файла
сохранены в ignored `forge-addon/build/stage05-before/`. Прочитаны AGENTS.md,
локальные tactical-navigation/forge-ai-profiling skills, README, INTEGRATION-AUDIT,
IMPLEMENTATION-STATUS и принятые planner/executor/recovery/territory/command-контракты.

### Поведение и файлы 05

- Чистый `DefendPatrol` принадлежит существующему `OrderPlanner`, хранит один waypoint,
  направление и паузу. Отдельный Goal/controller не добавлен. Это ephemeral route state;
  команды/order/anchor/radius и schema 2 остаются единственным persisted источником состояния.
- Seed зависит от UUID, случайность меняется только при выборе следующего leg/паузы.
  На новый leg проверяется максимум восемь геометрических кандидатов; waypoint округлён к
  native node center, проверяется принятый DefendTerritory с body/arrival clearance.
  Локальные legs <=12 блоков, без глобального cap 10; большие зоны исследуются следующими legs.
- Без контакта выбирается внутренний waypoint; native executor до движения проверяет loaded
  terrain/collision/hazards, canReach и реальные узлы по контракту 04. После прибытия пауза
  20..40 тиков плюс текущая cadence. На паузах используется существующий idle sweep; для defend
  pause он выполняется и при dormant UUID phase, которая иначе могла пропускать tick%4.
- Подтверждённый отказ проходит прежний bounded RouteRecovery. После EXHAUSTED или прибытия
  на recovery alternative выбирается другой waypoint после паузы/backoff; последняя закрытая
  точка отвергается на 160 тиков. Path budget/cooldown/backoff executor сохраняются.
  Budget DEFERRED без failure evidence не меняет waypoint и не запускает timeout «недостижим».
- Любой действующий контакт/легитимная память прекращает idle patrol. Смена patrol leg/contact
  останавливает старый путь тем же executor и отменяет только recovery episode, сохраняя
  failed-point backoff и бюджет. По окончании памяти снова строится внутренний patrol.
  Вынос снаружи по-прежнему сначала включает принятый возврат 04.
- Lifecycle stop/mode change/remove освобождает patrol state; после entity save/load маршрут
  восстанавливается из прежних order/anchor/radius, новая NBT schema не нужна.
- Самостоятельный PATROL и его старые cardinal points/cap 10 не изменены. Attack, STAND,
  detection/weapon profiles, ammo/native cooldowns, фракции/squads, команды и зависимости
  не менялись. Новых world scans, Mixins, worker/world mutation или production telemetry нет.

Именно 05: **7 code-файлов, +302/-2**, плюс запись в этом журнале:

| Файл от корня репозитория | Diff 05 |
|---|---|
| ai-core/src/main/java/dev/zuma/tacticalnpc/core/DefendPatrol.java | новый, 56 строк |
| ai-core/src/main/java/dev/zuma/tacticalnpc/core/OrderPlanner.java | +11/-0 относительно 04 |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/MovementExecutor.java | +2/-0 относительно 04 |
| forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java | +5/-2 относительно 04 |
| ai-core/src/test/java/dev/zuma/tacticalnpc/core/DefendPatrolTest.java | новый, 65 строк |
| forge-addon/src/gametest/java/dev/zuma/tacticalnpc/DefendPatrolTests.java | новый, 162 строки |
| ai-core/run-tests.sh | +1/-0 относительно 04 |

Patch только 05: ignored `forge-addon/build/stage05-code.patch`, SHA-256
`9C03D4E0962DB73B697C0132A2E2EF6E6F11A0347F60BA4C60EF4E5944F15616`.
Source SHA-256 каждого из семи файлов — `forge-addon/build/stage05-manifest.json`.
`git apply --check --reverse --ignore-space-change` PASS, patch не применялся.
Manifest 04 проверен: восемь незатрагиваемых файлов 04 не изменились.

### Реальные проверки 05

Java Microsoft **17.0.19+10-LTS**, Minecraft **1.20.1**, Forge **47.4.20**, Gradle **8.8**,
ForgeGradle **6.0.54**, TaCZ **1.1.8-hotfix2**, optional NPCs **2.1.0**.
Зависимости повторно сверены: TaCZ SHA `2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2`,
NPCs SHA `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE`.
Готовый cache подтверждён compilation. Все Gradle-команды из forge-addon;
raw logs в ignored docs/testing, scripts/manifest/patch — в ignored build.

| Проверка | Реальный результат |
|---|---|
| `bash ai-core/run-tests.sh` до production changes с новым desired-behavior тестом | **FAIL**: idle defend держит anchor; прежние 72+94+38 PASS. `stage05-core-red-2026-10-18.log` |
| Первый core vertical slice | **PASS 72+94+38+1**, `stage05-core-initial-2026-10-18.log`; затем расширены регрессии |
| `bash ai-core/run-tests.sh` после реализации | **PASS 72 policy +94 temporal +38 territory +235 patrol**, `stage05-core-2026-10-18.log` |
| `gradlew.bat compileJava compileCompatJava --offline --console=plain` | **PASS**, 16s, `stage05-compile-2026-10-18.log` |
| Selected standalone | **PASS 25/25 required**, BUILD SUCCESSFUL 5m56s; `stage05-standalone-2026-10-18.log` |
| Selected compatibility | **PASS 34/34 required**, BUILD SUCCESSFUL 6m40s; `stage05-compat-2026-10-18.log` |
| Plain production build / artifact verifier | **PASS**, 12/12 tasks executed, 30s; `stage05-production-build-2026-10-18.log`; verifier PASS, ignored `forge-addon/build/stage05-production-artifacts-2026-10-18.json` |
| Ordinary dedicated startup/stop | **PASS** Done → help/console-target validation/diagnostics → stop/save → BUILD SUCCESSFUL 1m14s, process exit 0; `stage05-dedicated-smoke-2026-10-18.log` |

Семь новых GameTests проверяют реальные movement/разные точки/паузы/осмотр 4/32/128,
закрытый waypoint и замену после recovery/backoff, native TaCZ projectiles + скрытую живую
память + движение после контакта, реальный entity save/load с повторным движением,
отдельный PATROL. Read-only test-only LevelTick END observer считает реально взятые path slots
после entity AI (при одном actor); проверяются maxPerTick<=1, minGap>=4, конечное число запросов.
Это не 30+ benchmark и не утверждение о TPS. Core дополнительно проверяет неизменность waypoint
при 100 budget/no-progress samples, pauses/backoff, deterministic route, suspend/resume/reset,
20 legs для каждого радиуса и прежнюю формулу PATROL.

Фактические path slots в standalone (один actor; horizon — GameTest ticks):

| Сценарий | Horizon | Requests | Min gap | Max per tick |
|---|---:|---:|---:|---:|
| idle defend 4 | 500 | 11 | 40 | 1 |
| idle defend 32 | 600 | 6 | 100 | 1 |
| idle defend 128 | 600 | 6 | 80 | 1 |
| blocked waypoint/recovery | 500 | 6 | 20 | 1 |
| combat/memory/resume | 500 | 8 | 29 | 1 |
| entity save/load | 500 | 6 | 40 | 1 |
| standalone PATROL | 500 | 4 | 130 | 1 |

Счётчик отражает path requests через принятый AI budget, не внутренние ванильные пересчёты
PathNavigation. Seed/UUID каждого actor и raw результаты записаны в логе; это функциональная
проверка отсутствия AI path spam, а не измерение стоимости pathfinding.

Финальные selected команды:

```powershell
cd forge-addon
.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage05-regression.init.gradle --console=plain
.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage05-regression.init.gradle --console=plain
```

Ignored init script для воспроизведения:

```groovy
gradle.projectsEvaluated {
    allprojects {
        if (plugins.hasPlugin('java')) {
            sourceSets.main.java.exclude { element ->
                !element.isDirectory() && element.file.path.replace('\\','/').contains('/src/gametest/') &&
                    element.file.name.endsWith('Tests.java') && !(element.file.name in ['DefendPatrolTests.java','DefendTerritoryTests.java','ModeCommandTests.java','AlphaResponsivenessTests.java','AlphaLocomotionTests.java','CoverTests.java'])
            }
        }
    }
}
```

Состав: 7 patrol +7 territory +5 commands +3 alpha responsiveness +1 locomotion +2 cover =25
standalone; compatibility добавляет прежние 9 native legacy tests, всего 34. Нулевой список
не считается PASS. Production rebuild выполняется отдельно без любых test flags/init scripts.

### Ручная приёмка 05

На безопасной загруженной площадке выбрать одного NPC, убедиться, что tag stage05 уникален:

```mcfunction
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage05
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage05,limit=1] tacz:ak47
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage05,limit=1] infinite
/npc mode defend 32 @e[type=tacticalnpcai:npc,tag=stage05,limit=1]
```

В Creative без врагов наблюдать несколько различных точек, короткие остановки и осмотр;
поставить препятствие на следующую точку — NPC должен после bounded recovery/backoff выбрать
другую. В Survival начать бой видимым игроком снаружи круга в обычной оружейной дальности:
patrol прекращается, NPC не выходит наружу. Закрыться/отойти: пока жива память, остаётся боевой
поиск; после окончания контакта/памяти вновь видны внутренние точки и паузы.
Проверить малую/большую зону и старый отдельный приказ:

```mcfunction
/npc mode defend 4 @e[type=tacticalnpcai:npc,tag=stage05,limit=1]
/npc mode defend 128 @e[type=tacticalnpcai:npc,tag=stage05,limit=1]
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage05,limit=1] PATROL 32
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage05,limit=1] DEFEND_AREA 32
/data get entity @e[type=tacticalnpcai:npc,tag=stage05,limit=1] tacticalnpc
/save-all flush
/tacticalnpc diagnostics
```

Штатный stop/restart того же мира, повторить data get и наблюдение: DEFEND_AREA/anchor/leash,
faction/squad/ammo/serviceId сохранены, patrol снова ходит. Каждая выдача команды меняет anchor
на текущую позицию NPC; это прежний контракт. В console команды без `/` и с explicit targets.

### Ограничения 05

- Нужен безопасный достижимый маршрут в загруженных чанках. Неизвестные/опасные/закрытые точки
  проверяет executor; waypoint candidate сам по себе не означает достижимость. При полностью
  закрытой сцене остаются конечный recovery и backoff/паузы. Production не форсирует чанки.
- Маршрут не является обещанием обойти весь круг за фиксированное время. Он может возвращаться
  к прежним участкам; глобального ограничения 10 блоков в defend нет.
- Эфемерная точка/пауза не сохраняются в NBT: после загрузки строится новый безопасный маршрут
  внутри сохранённой зоны. Состояние order/PATROL этим не подменяется.
- Full 54/63 suites, новый 30+ profiling, graphical/human playtest, distribution launcher и
  ручной disk-restart **NOT RUN**. Исторические flaky combat FAIL из 03/04 не объявлены исправленными.

### Production artifacts 05

После тестов, без combatTests/profile/clientSmoke/legacyCompatibility и init scripts:

```powershell
# Из forge-addon
.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain
# Из корня, Python 3
python -B tools/verify-production-artifacts.py
```

Verifier PASS: Java major 61; тестовых классов (включая probe), арен, profile/clientSmoke,
вложенных зависимостей нет; core не содержит игровых references; main не зависит от NPCs.
Сборка из текущего working tree поверх указанного HEAD с принятыми 01/03/04 и этим 05,
а не из чистого исходного review SHA. Новый commit не создавался.

| Файл в forge-addon/build/libs | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 160824 / 79 | `6AA688E1EE0009E5DB72F718E44DE45C6E80E9E85A90E64914DCE0BFEC95990B` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `887E7557403E910BC0FDB42F5F82320436E1B44D0B9E0CF10CB1D332AD2B41A7` |

Обычный `gradlew.bat runServer --offline --console=plain` без flags/init scripts:
Done, `help npc`, ожидаемый отказ `npc mode defend 32` без console targets,
`tacticalnpc diagnostics`, `stop`, сохранение всех dimensions, exit 0 / BUILD SUCCESSFUL.
Combat в этом smoke не проверялся. После stop server.properties и world tacticalnpc config
восстановлены byte-for-byte из `build/stage05-preserve/`; EULA владельца не менялась.
SHA после восстановления совпали с 04:

| Ordinary smoke file | SHA-256 |
|---|---|
| eula.txt | `E4DB182AA77B5E31657F4B83F8973C79A8C65217A4383200F9F59F7C94752FAD` |
| server.properties | `E0F9353DD613EF3E426B1B1C4CA4097881568C6F6C9BBB78FDE89563C9375658` |
| independent-smoke/serverconfig/tacticalnpcai-server.toml | `46B775E8556A2966C1876613955FCC3BA29FC8D49533CF89188977166081B53A` |

После dedicated оба JAR SHA совпали с manifest выше; семь source SHA и patch SHA повторно
проверены без перезаписи исходников. `git diff --check` и reverse patch check PASS.
Обязательные проверки 05 выполнены; BLOCKED нет. Единственный FAIL 05 — ожидаемый red
до реализации; все последующие core, compilation, selected Forge и artifact/dedicated проверки PASS.
HEAD прежний, commit/PR не создавались. **WAITING_PLAYTEST — ОСТАНОВКА**.
Этап 06 и следующие не запускались; их разрешает только владелец после игровой приёмки 05.

## 06 — свободный attack / ASSAULT, 2026-10-18–19

Статус: **ACCEPTED** — владелец явно написал «Этап 06 принят, продолжай!» 2026-10-19 в текущем чате.
При завершении 06 статус был WAITING_PLAYTEST. Этап 05 принят владельцем в запросе 06.
HEAD/main: `d3d7de3de41a27c52db51e14df2473206bd3d11c`; работаем поверх принятого
dirty checkpoint 01/03/04/05, без reset/checkout/pull. Новый commit/PR не создаётся.
Исходный review SHA сам по себе не подтверждает эти изменения.
При старте: 10 modified tracked файлов и 8 untracked файлов/журнал прежних этапов;
их исходное содержимое сохранено/сверено, посторонних правок нет.
Java Microsoft 17.0.19+10-LTS, Minecraft 1.20.1, Forge 47.4.20, Gradle 8.8,
ForgeGradle 6.0.54, TaCZ 1.1.8-hotfix2, optional NPCs 2.1.0. SHA зависимостей совпали с 00.
AGENTS и tactical-navigation / hardcore-pvp-brain / forge-ai-profiling прочитаны.

**Уточнение зависимости 02:** фактический sensor/controller всё ещё Player-only.
В этом запуске владелец подтвердил, что пропустил 02, и разрешил продолжить 06.
Поэтому 06 проверяет движение/контакт с игроками; обнаружение враждебных поддерживаемых NPC
остаётся невыполненным 02. Обычные мобы/животные/стойки целями не добавлялись.
Историческая формулировка «02 принят» в запросе 03 не подтверждает реализацию 02.

### Изменения 06

- `AttackRoute` — чистое эфемерное состояние внутри прежнего OrderPlanner, без второго
  controller. Idle legs около 12 блоков от текущей позиции, owner-seeded heading на leg,
  паузы 20..40 ticks, bounded recovery и backoff; anchor/radius не ограничивают ASSAULT.
- Видимое ADVANCE удерживает оружейный standoff с боковым отклонением до 2 блоков.
  Обновление из-за смещения видимой цели >=4 блоков возможно не чаще 20 ticks.
  Сохраняются weapon profiles, cover/HOLD/PEEK/emergency, reload/bolt и native gates.
- SEARCH использует только frozen legitimate observation. По достижении/исчерпании пути
  ждёт окончания этой памяти; TTL не продлевается. Затем снова свободный поиск.
  DEFERRED не считается failure и не сбрасывает выбранную точку.
- MovementExecutor использует существующее безопасное разбиение на короткие loaded legs
  также для ASSAULT (до min(16, FOLLOW_RANGE/2)); общий defend contract сохранён.
  Это необходимо для свободного сближения/памяти без одного чрезмерно длинного native path.
- Старый ASSAULT и новый attack читают тот же order; отдельный PATROL, команды/NBT schema,
  оружейные классы, faction/squad, vision/shot range и TaCZ не изменялись.
- Новые core AttackRouteTest и Forge AttackTests: движение без цели, unreachable waypoint,
  удержание пути, frozen memory/TTL, простой обход, RUN→ADS→native projectile, path slots.
  Добавлен core regression сохранения cover/HOLD/PEEK при скрытом контакте.

### Ручная приёмка 06

В загруженной безопасной области создать одного NPC и уникально пометить его:

```mcfunction
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage06
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage06,limit=1] tacz:uzi
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage06,limit=1] infinite
/npc mode attack @e[type=tacticalnpcai:npc,tag=stage06,limit=1]
```

В Creative без врагов наблюдать разные точки/паузы. В Survival показаться примерно в 30..38
блоках: NPC сближается до рабочей дистанции UZI, отпускает sprint/переходит в native ADS
и стреляет. Отойти дальше прежнего anchor, затем закрыться за стеной и сменить скрытую
позицию: NPC использует последнюю виденную точку, после TTL вновь обследует местность.
Повторить с простой короткой преградой и недостижимой точкой; нет нового createPath каждый tick.
Проверить старый синтаксис и регрессию территории:

```mcfunction
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage06,limit=1] ASSAULT 24
/npc mode defend 4 @e[type=tacticalnpcai:npc,tag=stage06,limit=1]
/tacticalnpc order @e[type=tacticalnpcai:npc,tag=stage06,limit=1] PATROL 32
/npc mode attack @e[type=tacticalnpcai:npc,tag=stage06,limit=1]
/data get entity @e[type=tacticalnpcai:npc,tag=stage06,limit=1] tacticalnpc
/save-all flush
/tacticalnpc diagnostics
```

После штатного stop/restart ASSAULT сохраняется, эфемерный маршрут строится заново.
Консоль использует explicit targets и команды без `/`; прицелом можно использовать `/npc mode attack`.
Человеческий playtest и disk restart владельца ещё NOT RUN.

### История проверок 06

| Запуск | Реальный результат |
|---|---|
| Новый AttackRouteTest на прежнем коде | ожидаемый **FAIL**: `attack without contact must move, not ASSAULT HOLD`; `testing/stage06-core-red-2026-10-18.log` |
| `bash ai-core/run-tests.sh` после реализации | **PASS 785** (72+94+38+235+346); `testing/stage06-core-2026-10-18.log` |
| После исправления приоритета cover/peek и потери LOS во время recovery | **PASS 792** (72+94+38+235+353); `testing/stage06-core-final-2026-10-18.log` |
| `compileJava compileCompatJava --offline --console=plain` | **PASS**, 20 s; `testing/stage06-compile-2026-10-18.log` |
| Первый selected standalone, 38 tests | **FAIL 35/38**, 8m30s; `testing/stage06-standalone-2026-10-18.log` |
| Повтор selected standalone, 38 tests | **FAIL 36/38**, 8m25s; `testing/stage06-standalone-retry-2026-10-18.log` |
| Focused standalone до уточнения ticking/contact fixture, 14 tests | **PASS 14/14**, 6m25s; `testing/stage06-standalone-final-2026-10-18.log` |
| Selected combined с NPCs 2.1.0, 47 tests | **FAIL 46/47**, 8m47s; `testing/stage06-compat-2026-10-18.log` |
| Окончательный focused standalone 19 октября, 14 tests | **PASS 14/14**, 6m18s; `testing/stage06-standalone-final-2026-10-19.log` |
| Окончательный focused combined 19 октября, 23 tests | **PASS 23/23**, 7m02s; `testing/stage06-compat-final-2026-10-19.log` |

Первый Forge FAIL выявил production-регрессию добавленного overlay: hidden SEARCH подменял
штатные HOLD/PEEK. Исправлен приоритет только в AttackRoute, добавлены core assertions;
cover/reload/peek и emergency реально прошли повторный Forge запуск.
Два других FAIL первого прогона были в новых fixtures: проверка финального положения уже
после расхода finite ammo/штатного отхода вместо достигнутого удаления за весь сценарий;
и проверка hidden LOS в том же tick, где стенд ещё только поставил стену/переместил игрока.
Они исправлены в AttackTests без изменения баланса/оружия.

Во втором прогоне движение вокруг преграды и path-slot assertions выполнились, но observer
после `finish/discard` прочёл уже отсутствующий controller и вызвал NPE. Защищены оба
memory observers; после cleanup они сразу завершаются. Ещё один FAIL —
прежний `persistedDefendRebuildsPatrolAfterEntityLoad` (движение после load), без установленной
причины; его исходник/production defend не редактировались, он включён в повторные проверки.
Не объявляется исправленным только на основании предыдущего 05 PASS.

Окончательные fixtures дополнительно используют вооружённого NPC в обходе преграды,
INFINITE policy в memory-сценарии (чтобы исчерпание finite магазина не было причиной паузы),
и **дельту** настоящих ProjectileProbe counters именно этого запуска. Finite расход отдельно
проверяется в RUN→ADS→projectile. Эти изменения не затрагивают production.

В focused standalone 18 октября реально прошли все 5 AttackTests, 2 CoverTests и 7 DefendPatrolTests,
включая повтор прежнего save/load. Это **14**, не полный standalone suite. Полные 59/68 suites
и новый 30+ profiling в этом этапе не запускались; данные path slots — не benchmark/TPS claim.

В combined 47 все прежние 42 теста и 4 новых attack прошли, включая все 9 optional regressions
и повтор save/load defend. Единственный FAIL нового memory fixture: в фиксированный tick 100
не было текущего видимого контакта. Причина этого конкретного состояния не установлена:
старый assert не печатал entityTicks/decision. Это не засчитано как PASS frozen memory.
Стенд уточнён без production-правок: границы его 64x64 арены явно entity-ticking через
test-only chunk tickets (предыдущие forced tickets сохраняются; свои снимаются при finish,
удалении actor, следующем begin и server stop). Стена теперь закрывается по настоящему
fresh visual contact в интервале 40..100 ticks; отсутствие контакта к 100 остаётся FAIL
с entityTicks/position/decision. Наблюдения, LOS и память не подставляются вручную.
После прерывания работа продолжена 19 октября; HEAD/dirty state и три production SHA сверены,
EULA и ordinary server/config hashes прежние. Запущены повторные проверки этого fixture.

Воспроизведение автоматических запусков (Git Bash для core; Gradle из `forge-addon`;
offline cache проверен готовым; init scripts только выбирают классы тестов):

```bash
bash ai-core/run-tests.sh
```

```powershell
.\gradlew.bat compileJava compileCompatJava --offline --console=plain
# Широкий набор: 38 main + при втором флаге 9 compatibility
.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage06-regression.init.gradle --console=plain
.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage06-regression.init.gradle --console=plain
# Повтор изменённых fixtures и затронутого combat/patrol цикла: 14 main + optional 9
.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage06-focused.init.gradle --console=plain
.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage06-focused.init.gradle --console=plain
```

`stage06-regression.init.gradle` оставляет AttackTests, PerceptionTests, DefendPatrolTests,
DefendTerritoryTests, ModeCommandTests, AlphaResponsivenessTests, AlphaLocomotionTests, CoverTests.
`stage06-focused.init.gradle` оставляет AttackTests, CoverTests, DefendPatrolTests.
Оба сохраняют helpers; compatTest не фильтруют. Полный исходный suite не подменяется этими цифрами.

### Итоговый diff и artifacts 06

Только этот этап относительно принятого working tree 05: **6 code/test files, +275/-7**,
плюс продолжение этого журнала и отметка принятия 05. Прежние изменения 01/03/04/05 сохранены.

| Файл от корня | + / - |
|---|---|
| `ai-core/run-tests.sh` | 1 / 0 |
| `ai-core/src/main/java/dev/zuma/tacticalnpc/core/AttackRoute.java` | 66 / 0 |
| `ai-core/src/main/java/dev/zuma/tacticalnpc/core/OrderPlanner.java` | 6 / 4 |
| `ai-core/src/test/java/dev/zuma/tacticalnpc/core/AttackRouteTest.java` | 59 / 0 |
| `forge-addon/src/gametest/java/dev/zuma/tacticalnpc/AttackTests.java` | 139 / 0 |
| `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/MovementExecutor.java` | 4 / 3 |

Локальный patch `forge-addon/build/stage06-code.patch`, SHA-256
`EAE31C57F268F3BCC80B9F69D70E5792B9E1FF156D56344370D3ECDF4A04400C`.
Receipt каждого source SHA — `forge-addon/build/stage06-manifest.json`; pre-06 copies —
`forge-addon/build/stage06-before/`. Reverse patch check и `git diff --check` PASS.
VisibilitySensor `22504AC6091CD88FAA61C06081E4BEAE6BCF30DCD36C986C3811A490E5399EC3`,
PerceptionTests `A358DFEC855AEF6807AB24E95F1C62AF1826936E9A11F5C0AA349FF86310E560`,
TacticalCombatGoal `3F44263ED483ED1C82F420D020BDE71478E0A1AC679D223F51801693A2938EAE`
не менялись в 06; остальные принятые источники проверены по последним receipts 04/05.

После всех GameTests, без combatTests/profile/clientSmoke/legacyCompatibility/init scripts:

```powershell
# Из forge-addon
.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain
# Из корня
python -B tools/verify-production-artifacts.py
```

Build **PASS**, 28 s, 12 tasks выполнены; `testing/stage06-production-build-2026-10-19.log`.
Gradle `test=NO-SOURCE` не засчитывается как unit/GameTest PASS: настоящие результаты core и
Forge перечислены отдельно выше. Verifier **PASS**, receipt
`forge-addon/build/stage06-production-artifacts-2026-10-19.json`: Java major 61, без тестовых
классов/арен/profile/clientSmoke/вложенных зависимостей; pure core не ссылается на игру,
main JAR не ссылается на NPCs. Все pinned metadata checks выполнены.

| Файл в forge-addon/build/libs | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 164690 / 80 | `BA1882E263505BA44B0FFF871791D6F23F00813F991886AF760B88C0C9E80F13` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `286018F2B486D66C987347DBD1F5654AC0C7108EE6BBE61EE38EAFEC87F1FEAF` |

Архивы построены из текущего working tree поверх HEAD
`d3d7de3de41a27c52db51e14df2473206bd3d11c`, а не из чистого исходного review commit.
Новых commit/PR нет.

### Dedicated smoke и остановка 06

Обычный `gradlew.bat runServer --offline --console=plain` из `forge-addon`, без любых
test/profile/clientSmoke/legacyCompatibility flags и init scripts: **PASS**, 56 s,
Done, namespaces `[]`, `help npc`, ожидаемый ясный отказ `npc mode attack` без console targets,
`tacticalnpc diagnostics`, `stop`, сохранение всех dimensions, exit 0 / BUILD SUCCESSFUL.
Raw log: `testing/stage06-dedicated-smoke-2026-10-19.log`. Это startup/stop/registration smoke,
а не новый controlled combat или доказательство disk-restart сущности.
Ordinary ForgeGradle userdev запуск не подменяет standalone launcher/modpack installation.

Непосредственно перед запуском сохранены актуальные server.properties и world tactical config;
после stop они восстановлены **byte-for-byte**, EULA не записывалась. Итоговые hashes:

| Файл ordinary smoke | SHA-256 |
|---|---|
| eula.txt | `E4DB182AA77B5E31657F4B83F8973C79A8C65217A4383200F9F59F7C94752FAD` |
| server.properties | `E0F9353DD613EF3E426B1B1C4CA4097881568C6F6C9BBB78FDE89563C9375658` |
| independent-smoke/serverconfig/tacticalnpcai-server.toml | `46B775E8556A2966C1876613955FCC3BA29FC8D49533CF89188977166081B53A` |

После dedicated SHA обоих JAR совпали с artifact receipt выше. `build/stage06-final-check.py`
PASS: HEAD, все шесть source SHA, stage-only patch SHA, оба JAR, EULA, byte-exact configuration.

### Практические ограничения 06

- **Player-only** до пропущенного 02 — владелец явно разрешил продолжить этот этап.
  Враждебные поддерживаемые NPC пока не обнаруживаются как цели; обычные мобы исключены.
- Поиск локальный в доступной загруженной местности. Нет обещания обойти весь мир/каждый
  участок за фиксированное время, нет глобального chase, новых радиусов зрения/стрельбы,
  AimLead, повышения урона, новой squad роли или нейтрального spawn.
- Закрытая/опасная местность может дать остановку с recovery/backoff. DEFERRED сохраняет
  точку; finite no-ammo RETREAT и native reload/bolt остаются прежними боевыми решениями.
- Один ранний save/load defend FAIL имел неустановленную причину; в последующих standalone
  и combined он не повторился. Его исходники/production defend не исправлялись в этом этапе.
  Ручная save/restart проверка владельца обязательна для игровой приёмки.
- Full 59/68 suites, новый 1v4/30+ profiling, graphical client/human PvP,
  distribution launcher и ручной disk restart **NOT RUN**. Исторические flaky FAIL 03/04
  не объявлены исправленными. Path-slot данные не являются latency/TPS benchmark.

Итог для согласованного Player-only scope 06: core **792 PASS**; final standalone
**14/14 PASS**; final combined **23/23 PASS**; compilation/build, production verifier,
ordinary dedicated startup/stop **PASS**. История FAIL сохранена выше, нулевой test count
не засчитывался. Недоступных обязательных проверок нет; **BLOCKED нет**.
HEAD прежний, stage-only diff и verified JAR перечислены выше.
**WAITING_PLAYTEST — ОСТАНОВКА.** Приёмка 06 только сообщением владельца;
07 или любой другой следующий этап автоматически не запускается.

## 02 — поддерживаемые NPC как цели, возврат к пропущенному этапу, 2026-10-19

Статус: **WAITING_PLAYTEST**. Зависимость 01 явно принята владельцем в запросе 02.
Работа поверх фактического checkpoint после 03–06; их изменения сохраняются.
HEAD/main `d3d7de3de41a27c52db51e14df2473206bd3d11c`, при старте 10 modified и 11
untracked code/test/journal files предыдущих этапов. Reset/checkout/pull не выполнялись.
Этот SHA сам по себе не подтверждает рабочие изменения; pre-02 copies сохранены в
`forge-addon/build/stage02-before/`. Следующие этапы не запускаются.

Прочитаны AGENTS, hardcore-pvp-brain / forge-compat-hook / tacz-npc-audit /
forge-ai-profiling, architecture, Forge README и integration audit. Minecraft 1.20.1,
Forge 47.4.20, Java Microsoft 17.0.19+10-LTS, TaCZ 1.1.8-hotfix2, optional NPCs 2.1.0,
Gradle 8.8 / ForgeGradle 6.0.54. Оба dependency SHA совпали с таблицей 00.
`javap -p` pinned NPCs повторно подтвердил AutoGunOperator/PathfinderMob/IGunOperator,
isAlliedTo/isOpposedTo/getTemplate; evidence `build/stage02-npcs-signatures.txt`,
граница поддержки отражена в существующем `docs/INTEGRATION-AUDIT.md`.

### Изменения и контракт 02

- Общий CombatTargets: Player либо NPC с зарегистрированным подтверждённым runtime,
  причём runtime.entity() — именно эта сущность. Свой TacticalNpcEntity обязателен;
  exact AutoGunOperator допускается прежним optional provider. Main не импортирует NPCs.
  Self/dead/removed/creative/spectator/другое измерение и союзники отвергаются.
  Обычные мобы/животные/armor stands и просто носители TaCZ-оружия не поддерживаются.
- Vision/aim point/UUID resolver/validity/native direct gates обобщены на LivingEntity,
  но каждый кандидат проходит закрытый type filter. Hidden resolver/validity не читают
  координат/скорости; память обновляется только после настоящего VISION/старого слуха.
- RuntimeRegistry хранит per-level UUID index только загруженных поддерживаемых NPC,
  с O(1) join/remove и без сильных ссылок на сущности/уровни. Это прямо необходимо для
  bounded продолжения после cap 16 без full-world scans или вечной первой выборки.
  Страница 16 Player/NPC IDs, четыре LOS, UUID staggering и fresh current-target checks сохранены.
- Activation теперь учитывает NPC без игрока: <=16 NPC checks на двадцатитиковый запрос,
  прежние LOD и budgets. Это только proximity boolean. Partial page/budget DEFERRED не
  превращается в доказанное отсутствие и сохраняет предыдущую activation state.
- Death/leave/unload/dimension инвалидируют memory/contact/generation/decision/path;
  каждый native round повторно проверяет generic target. Native ammo/cooldown не сбрасываются.
- Тот же pre-damage guard защищает неподдерживаемые типы от случайной нашей пули.
  Существующие faction/team alliances сохранены; default equal faction может быть союзной.
  Никакой автоматической смены faction/персональной neutral retaliation не добавлено.
  Hearing от NPC не реализуется здесь (этап 12); прежний Player hearing сохраняется.

### Проверки 02 — история

| Запуск | Реальный результат |
|---|---|
| Initial compileJava/compileCompatJava | **FAIL**: оставшийся Player loop variable при LivingEntity page; исправлен в 02. `testing/stage02-compile-2026-10-19.log` |
| Повтор compileJava/compileCompatJava | **PASS**, 30s; `testing/stage02-compile-final-2026-10-19.log` |
| `bash ai-core/run-tests.sh` | **PASS 792**; pure core не менялся. `testing/stage02-core-2026-10-19.log` |
| Первый selected standalone | **FAIL 29/30**, 2m59s; `testing/stage02-standalone-2026-10-19.log` |
| Final selected standalone | **PASS 30/30**, 3m10s; `testing/stage02-standalone-final-2026-10-19.log` |
| Selected combined с NPCs 2.1.0 | **PASS 41/41**, 3m46s; `testing/stage02-compat-2026-10-19.log` |

Единственный Forge FAIL первого прогона — новый page fixture не подтвердил исходный open
candidate (один составной assert). Его неподконтрольному shooter не был установлен body yaw
вместе с head yaw. Test helper согласован; index/eligibility/FOV/LOS теперь проверяются раздельно.
Production не менялся по этому FAIL; повтор тех же 30 tests завершился PASS. Новые assertions также
явно проверяют creative/spectator. Первый прогон действительно прошёл противоположные
NPC factions с настоящими native projectiles без игроков, allies, excluded types, frozen NPC
memory, activation, смерть/unload/dimension и прежние player/ammo/command сценарии.

### Ручная приёмка 02

В Creative, в загруженной области с прямой видимостью создать двух NPC примерно в 10..20
блоках друг от друга. Метки stage02a/b должны быть уникальными; после первого spawn/tag
перейти на место второго NPC перед следующим spawn. Используются существующие команды:

```mcfunction
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add stage02a
/tacticalnpc spawn
/tag @e[type=tacticalnpcai:npc,tag=!stage02a,sort=nearest,limit=1] add stage02b
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage02a,limit=1] tacz:ak47
/tacticalnpc equip @e[type=tacticalnpcai:npc,tag=stage02b,limit=1] tacz:ak47
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage02a,limit=1] infinite
/tacticalnpc ammo @e[type=tacticalnpcai:npc,tag=stage02b,limit=1] infinite
/npc mode stand @e[type=tacticalnpcai:npc,tag=stage02a]
/npc mode stand @e[type=tacticalnpcai:npc,tag=stage02b]
/tacticalnpc faction @e[type=tacticalnpcai:npc,tag=stage02a,limit=1] red
/tacticalnpc faction @e[type=tacticalnpcai:npc,tag=stage02b,limit=1] red
```

Дождаться native reload: при одинаковой red фракции боя между ними нет. Создать рядом
зомби; NPC не выбирают его. Затем дать второму другую faction:

```mcfunction
/summon minecraft:zombie ~ ~ ~ {NoAI:1b,PersistenceRequired:1b}
/tacticalnpc faction @e[type=tacticalnpcai:npc,tag=stage02b,limit=1] blue
/tacticalnpc diagnostics
```

Должны появиться настоящие выстрелы между NPC; red у первого не меняется. Игрок остаётся
Creative, чтобы самому не стать третьей допустимой целью. При необходимости разместить NPC
лицом друг к другу; FOV/реакция не обходятся. Для проверки cleanup:

```mcfunction
/kill @e[type=tacticalnpcai:npc,tag=stage02b,limit=1]
/tacticalnpc diagnostics
/data get entity @e[type=tacticalnpcai:npc,tag=stage02a,limit=1] tacticalnpc
/save-all flush
```

После смерти второго первый не создаёт новые выстрелы по старому target и не переключается
на зомби. Уже летящая пуля не является новым запросом стрельбы. Старые faction/squad/team
правила сохраняются. Optional NPC можно создать прежним `/tacticalnpc spawnlegacy` при
установленных NPCs 2.1.0 + compatibility JAR; его faction остаётся native template-механикой,
основная faction-команда по-прежнему относится к самостоятельному NPC.

### Diff, команды проверок и production artifacts 02

Относительно pre-02 working tree: **8 code/test/audit files, +321/-43**, плюс этот журнал.

| Файл | + / - |
|---|---|
| `docs/INTEGRATION-AUDIT.md` | 22 / 0 |
| `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/CombatTargets.java` | 28 / 0 |
| `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/VisibilitySensor.java` | 42 / 25 |
| `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java` | 17 / 14 |
| `forge-addon/src/main/java/dev/zuma/tacticalnpc/runtime/RuntimeRegistry.java` | 30 / 0 |
| `forge-addon/src/main/java/dev/zuma/tacticalnpc/compat/ShotSafetyEvents.java` | 4 / 4 |
| `forge-addon/src/gametest/java/dev/zuma/tacticalnpc/TargetTypeTests.java` | 136 / 0 |
| `forge-addon/src/compatTest/java/dev/zuma/tacticalnpc/OptionalTargetTests.java` | 42 / 0 |

Stage-only patch: `forge-addon/build/stage02.patch`, SHA-256
`1C011C9B2705E93748E78F469D6962629079DA134588D3ABBEB47B17785107A3`.
Все восемь SHA — `forge-addon/build/stage02-manifest.json`; неизменённые принятые source
receipts 04/05/06 сверены без перезаписи исходников. Reverse patch check и diff whitespace PASS.

```bash
# Из корня, Git Bash
bash ai-core/run-tests.sh
```

```powershell
# Из forge-addon, ready cache
.\gradlew.bat compileJava compileCompatJava --offline --console=plain
.\gradlew.bat runGameTestServer --offline -PcombatTests -I build/stage02-regression.init.gradle --console=plain
.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility -I build/stage02-regression.init.gradle --console=plain
# После всех GameTests: без test/profile/clientSmoke/legacy flags/init scripts
.\gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain
.\gradlew.bat runServer --offline --console=plain
# Из корня
python -B tools/verify-production-artifacts.py
```

Init script сохраняет TargetTypeTests (9), PerceptionTests (8), IndependentNpcTests (7),
ActivationTests (1), ModeCommandTests (5) и helpers. Compat source set не фильтруется:
9 прежних + 2 новых OptionalTargetTests. Это selected **30/41**, не full-suite count.
Нулевой список не засчитывался. Real native duel без игроков и cross-runtime бой прошли;
reverse legacy alliance действительно `false`, то есть обратный optional тест проверил
native projectiles, а не только allied fallback. Ordinary mobs/animals/armor stands,
self/creative/spectator и native pre-damage exclusions проверены.

Production build **PASS**, 26s, обе compilation и все 12 tasks выполнены;
`testing/stage02-production-build-2026-10-19.log`. Gradle test=NO-SOURCE не подменяет
отдельные core/GameTest результаты выше. Verifier **PASS**:
`forge-addon/build/stage02-production-artifacts-2026-10-19.json`, Java major 61,
без Test/probe/arena/profile/clientSmoke/embedded dependencies; core без game references,
main без NPCs references; metadata pinned.

| Файл в forge-addon/build/libs | Bytes / classes | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 169443 / 83 | `58C7B6D0DBD391BD8C5CA927F568641A98FEF5D0EB22A3730D2A26D319DD64B4` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 / 4 | `2EE1B7A7E8504E05CED89687829F0118B8EE726CD95C58F349D8321C89FDCFB9` |

Сборка относится к HEAD `d3d7de3de41a27c52db51e14df2473206bd3d11c` плюс сохранённые
working-tree изменения 01/03/04/05/06 и этот 02. Новый commit/PR не создавался.

### Завершение 02

Обычный dedicated `runServer --offline --console=plain` без test/profile/clientSmoke flags —
**PASS**, Gradle BUILD SUCCESSFUL за 1m30s, exit 0. Сервер достиг `Done (6.032s)` в 06:00:23,
принял `help tacticalnpc`, `help npc`, `tacticalnpc diagnostics`, `stop` и сохранил все три
измерения в 06:01:09. Raw server log: `testing/stage02-dedicated-smoke-2026-10-19.log`.
Это существующий independent-smoke world: в нём оставались сущности предыдущих smoke runs;
зафиксирована гибель сохранённого NPC `tactical_client_smoke` от другого NPC. Этот запуск
подтверждает startup/commands/stop, но не подменяет изолированные GameTests или приёмку владельца.

После stop `server.properties` и `independent-smoke/serverconfig/tacticalnpcai-server.toml`
восстановлены побайтово из непосредственного pre-run snapshot; EULA не записывалась.
Их SHA-256 соответственно:
`E0F9353DD613EF3E426B1B1C4CA4097881568C6F6C9BBB78FDE89563C9375658`,
`46B775E8556A2966C1876613955FCC3BA29FC8D49533CF89188977166081B53A`;
EULA `E4DB182AA77B5E31657F4B83F8973C79A8C65217A4383200F9F59F7C94752FAD`.
Финальный `python -B forge-addon/build/stage02-final-check.py` — **PASS**:
HEAD, все восемь source/audit receipts, stage-only patch, оба production JAR, EULA и конфиги.
`git diff --check` и `git apply --reverse --check forge-addon/build/stage02.patch` — **PASS**.

Ограничения / **NOT RUN**: полный набор всех GameTests вне выбранных 30/41, отдельный 1v4,
профилирование >=30 NPC и ручная игровая приёмка владельца на этих JAR не запускались.
Слух от NPC остаётся вне этапа (12); неизвестные сторонние NPC без подтверждённого runtime
не становятся целями. FOV, vision/shot range, faction/team и загруженные чанки продолжают
ограничивать контакт. Базовые одинаковые faction могут оставаться союзными.
Начальные два FAIL сохранены выше; после исправлений итоговые обязательные проверки PASS,
неподтверждённых обязательных проверок этапа 02 нет.

**WAITING_PLAYTEST — ОСТАНОВКА.** Этап 02 ещё не принят; принятие 06 также не предполагается
автоматически. Следующий этап не запускался и требует отдельного сообщения владельца.

## 07 — нейтральный spawn / реальный damage, 2026-10-19

Статус: **WAITING_PLAYTEST**. Владелец явно принял 06 сообщением
«Этап 06 принят, продолжай!»; запись 06 выше дополнена этим подтверждением.
База `main`, HEAD `eb1f04eefb2141adaefd91c90fb82b56a0d9c030`, плюс сохранённый dirty diff
аудита тестирования. Принятие аудита/02/07 не предполагается. Commit/reset/push/merge отсутствуют.

Выполнен только 07. Полный stage-only отчёт, actual commands/receipts, production SHA,
ошибки запусков и ручные шаги: [STAGE-07-NEUTRAL](STAGE-07-NEUTRAL.md).
Изменены 4 production файла (entity/targets/goal/commands), 2 новых test classes,
2 прежние NBT schema assertions/диагностика одного patrol fixture, audit и этот журнал.
Сохранения schema 3; schema 1/2 остаются active и сохраняют прежние order/radius128/identity/ammo.

FAST 3/3; первый STAGE standalone 22/24, reload fixture исправлен; точечный повтор 2/2;
save/load с UUID упавшего NPC 1/1; combined selected 7/7. Всего 37 executions / 32 уникальных
сценария, каждый имеет итоговый PASS receipt. Новый core run не нужен: core не менялся.
Старый необъяснённый ранний save/load patrol FAIL из принятого 06 не объявлен исправленным;
повторная диагностика: 318 entity ticks, chunkTicking=true, несколько leg points, 14.528 blocks.

Один plain production build PASS (18s), artifact verifier PASS; main 171937 bytes / 83 classes,
compat 7680 / 4, Java major 61. Точные SHA в отчёте. Финальный ordinary runServer без flags/init:
новый stage07-neutral-smoke, `/spawn npc`, schema3/neutral1b NBT, save-all и clean stop PASS.
Неудачные helper-запуски также задокументированы: прежний ignored independent-smoke загружался
и сохранялся, его runtime state не объявляется неизменным. Properties/TOML восстановлены
побайтово, EULA не менялась. Ordinary smoke не доказывает бой/native reload: magazine=0,
projectile n=0; реальные native reload/BURST/projectile проверены GameTests.

Human playtest/save-restart/FULL/perf/JFR/soak NOT RUN. 08–14 не начинались.
**WAITING_PLAYTEST — ОСТАНОВКА.** Следующий этап только после «Этап 07 принят, продолжай».

## 08–14 — разрешённый владельцем пакет, 2026-10-19/20

Владелец: «Этап 07 принят! Запрет снимаю - можно делать подряд. Но инструкции и ручные тесты раздели для каждого этапа по отдельности». Этим подтверждено принятие07 и разрешено последовательное выполнение08–14 без промежуточного barrier. Новые этапы по автоматическим PASS не принимаются. Старт: чистое дерево, HEAD `3f9e810d0ce7684d78f438c91819c71e4db47539`; reset/commit/push/merge не выполнялись.

Отдельные отчёты с diff scope, actual commands/counts/receipts, рисками и3–6 ручными шагами:
[08](STAGE-08-STAND.md), [09](STAGE-09-SNAPSHOT.md), [10](STAGE-10-QUERIES.md),
[11](STAGE-11-SECTOR.md), [12](STAGE-12-HEARING.md), [13](STAGE-13-PEEK.md),
[14](STAGE-14-ACCEPTANCE.md). Сводный статус: [BATCH-08-14](BATCH-08-14.md).

STAGE08–13:21/21,13/13,12/12,8/8,6/6,повтор15/15 PASS. Core792 PASS; FULL standalone98/98, optional combined110/110 PASS; последние affected combined14/14 (STAND/squad) и25/25 (equip/weapon/optional) PASS. Это число выполнений в конкретных receipts, не сумма уникальных тестов. Начальные96/90/6 и108/107/1 FAIL, proof48/50 ray starvation, STAND report FAIL и исправления test-only isolation/teardown сохранены в отчётах, не переписаны в PASS. Production-правки14 минимальны и покрыты затронутыми повторными проверками.

Release: один plain production build25s/5 executed/7 UP-TO-DATE; verifier PASS, main191013bytes/92classes, compat7680/4, Java61. Ordinary standalone startup→reload→save/stop→restart/load/stop PASS128.719s; исходные properties/EULA неизменны. Both ordinary before/after84/84 и JFR analysis PASS,2×120s soak и2 unload/reload30 в каждой матрице. Только5 боевых пар допускают timing comparison после rounds/reaction/owner checks; общего server speedup нет, AI p95≤2ms на30/50 combat не достигнут. Снижение rounds крупных групп указано как существенное ограничение, не ускорение. [Таблица/CSV/JFR](testing/stage14-profile-2026-10-20.md), [полный proof/команды/SHA](testing/stage14-validation-2026-10-20.json).

Итог08–14: **WAITING_PLAYTEST**. Human1v1/1v4, client distribution-JAR playtest и приёмка владельца NOT RUN/PENDING. Исторические метрики Oct11 не являются новым baseline. Commit/push/merge/reset не выполнялись, пакет не ACCEPTED.


## Согласованное обновление point1 — team/короткие команды,2026-10-20

HEAD `3f9e810d0ce7684d78f438c91819c71e4db47539`, исходный рабочий пакет08–14 сохранён. Новые NPC активны; native team — единственное membership и источник автоматической squad-координации. Без team — враг остальным NPC и survival/adventure игрокам кроме союзников. Safe count1–16, group spacing4, implicit aimed NPC only; players/console explicit targets. Schema5 сохраняет архив legacy faction/squad, native membership в scoreboard.dat; mutation только server activation.

Core792 PASS; актуальные scoped12/12,placement5/5,финальные targeting5/5 PASS. FULL history109/105/4,121/118/3 и свежий121/120/1 остаётся FAIL с минимальными fixture corrections/affected repeats. Production weapon/AI gates не ослаблены. Native4×4 после размещения:63 rounds,3red/1blue owners;30 lanes:119 rounds,1red/1blue,AI p95=3.859ms,не30 стреляющих/не speedup. Initial crowded0 and one-sided40 FAIL сохранены.

Plain production build18s/verifier PASS (main198277bytes/94classes,compat7680/4); normal dedicated reload/save/stop/restart102.171s PASS, точное native policy/UUID/stack/team и неизменные source properties/EULA. [Полный отчёт/команды/SHA/6 ручных шагов](TEAM-COMMANDS.md),[machine evidence](testing/team-validation-2026-10-20.json). **WAITING_PLAYTEST**, не ACCEPTED; пункт2 интеллекта не начат, commit/push/merge/reset не выполнялись.


## Усиление combat AI — 2026-10-22

Явный запрос владельца: detection128, дальний native бой/lead, продолжительное давление, настоящий обход/роли и Hard/опциональный Unfair. Исходное дерево чистое, HEAD `3077098f1338c3c8fdb1f564257fd71f8ea98701`; commit/push/merge/reset не выполнялись. Стек Minecraft1.20.1/Forge47.4.20/Java17.0.19/TaCZ1.1.8-hotfix2/optional NPCs2.1.0 закреплён, SHA зависимостей совпали. Новых библиотек и Mixins нет; AGENTS5/9 исключения только в явно разрешённом Unfair.

Реализация и этапы: [боевой отчёт](COMBAT-UPGRADE-2026-10-22.md), [machine evidence](testing/combat-upgrade-validation-2026-10-22.json). Core818 PASS; combined36/36, standalone24/24, финальный DEFEND7/7 PASS. История FULL115/9fail→129/3fail→130/3fail→131/2fail не переписана. В последнем остались displaced-return и lifecycle с посторонним gravel в воздушном коридоре; test-only драйвер затем синхронизирован с entity physics/готовностью чанка и весь DEFEND7/7 повторно прошёл. Lifecycle corridor затем явно подготовлен как воздух; строгий raw ray сохранён, финальный lifecycle/native9/9 PASS. Последний весь каталог остаётся FAIL; новый полный green не заявлен.

Оба ordinary matched профиля9/9 PASS с идентичным fixture и неизменными production SHA. 64/96/128: реальные выстрелы31/31/31; running64:21 hits/47 rounds. Pressure:21 actual area rounds после стены;1v4:36 area rounds и3 фактических flank arrivals,25,87 блока. При30/50 все30/50 стреляли,1357/2205 native rounds против1274/1943; p95 MSPT15,40/18,37ms. Работа не уменьшалась для performance credit, общего speedup нет, AI2ms budget не достигнут. Это одна синтетическая пара, не human TTK/win rate.

Plain production build25s/12 executed, verifier Java61/101+4 classes PASS. Ordinary dedicated startup/reload/save/stop/restart102,938s PASS; исходные properties/EULA неизменны, policy/UUID/native stack/team сохранились. Все JVM userdev; новый client/distribution/human playtest не выполнялся. Итог **WAITING_PLAYTEST**, не ACCEPTED. Конкретные ручные проверки перечислены в боевом отчёте.


## Маршруты с действиями — 2026-10-22

Сначала инструкции/7skills согласованы отдельным локальным commit `b57bd49d70cd07bbe9cbce2a23faccc1b37ac398`;
gameplay baseline — опубликованный feature `f990246dea8e876cbacb3be2fd36540c7c0a20b7`.
Основная навигация уже в локальном commit `716200d`; заключительные test-only fixes/evidence
остаются рабочим diff. Закреплённый стек прежний, новых библиотек/Mixins нет.
Pure-core transitions/UUID leases; single movement owner; loaded bounded planning;
native wooden doors/LadderBlock physics; actual grounded completion, safe cancellation,
FIFO/TTL cleanup и limited recovery реализованы. TaCZ/friendly gates и UNFAIR_HINT сохранены.

Core936, affected STAGE31/31 и DefendPatrol7/7; FULL standalone132/132 PASS.
После прерывания combined оставались80 PASS,2 FAIL,62 не завершены. По последнему запросу
владельца повторены только два проблемных теста и выполнены оставшиеся62; все144 ID имеют
PASS evidence. Это aggregate coverage, не новый единый FULL green. Предыдущие FAIL/0-executed,
Windows errno1455 и missing test class receipts сохранены. Runner изолирует classes/resources
от обычных сборок; combatSmoke retreat origin перенесён внутрь пола без ослабления assertions.

Оба matched ordinary dedicated load profiles5/5 PASS; все заявленные NPC alive/entity-ticking.
При50 все50 прибыли,25 стреляли,421 native rounds/421 impacts с фиксированных после прибытия
позиций. Вклад veto/ролей не изолирован. MSPT p95 8,386→12,612ms; AI p95 4,485ms, target2ms
не достигнут. Baseline не проходил маршрут: это новая работа, не оптимизация. CSV/JFR/source SHA
сохранены; dedicated reload/save/restart и plain production build/verifier PASS, properties/EULA
не менялись. Инструкции, команды, длительности, причины, SHA и ручной сценарий:
[отчёт](ACTION-ROUTES-2026-10-22.md), [evidence](testing/action-routes-validation-2026-10-22.json).
Human1v1/1v4, distribution launcher, встречные потоки/claim mods, natural chunk edges и soak
NOT RUN. **WAITING_PLAYTEST**, не ACCEPTED. Push/PR/merge не выполнялись.
