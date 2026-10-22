# Двери и вертикальные лестницы — 2026-10-22

> Команды в этом датированном отчёте относятся к прежнему интерфейсу. Актуальные команды, постоянный Hard и таблица миграции: [COMMANDS](COMMANDS.md).

Стек: Minecraft 1.20.1 / Forge 47.4.20 / Microsoft Java 17.0.19+10 /
TaCZ 1.1.8-hotfix2; optional NPCs 2.1.0. Исходный чистый checkpoint:
`f990246dea8e876cbacb3be2fd36540c7c0a20b7`.
Снимок **до первой правки**: `forge-addon/build/action-route-baseline.tar`.
Автоматические проверки завершены: core936, FULL standalone132/132; combined —
144/144 сценария покрыты с одиночными повторами после прерывания. Единого нового
зелёного FULL combined нет. Dedicated/load/build/JAR checks PASS. **WAITING_PLAYTEST**.

Перед продолжением навигации инструкции согласованы отдельным локальным commit
`b57bd49d70cd07bbe9cbce2a23faccc1b37ac398`:
[противоречия, подтверждение по коду и проверка](INSTRUCTION-ALIGNMENT-2026-10-22.md).
В этом commit нет исходников навигации. Gameplay baseline остаётся прежним `f990246`.
При финальном продолжении локальный HEAD уже `716200d` (`new mechanics: open doors,
up stairs e.t.c`): основная реализация сохранена в этом commit. Текущие правки —
завершающий test harness, combatSmoke fixture и итоговые evidence/docs.
Опубликованность этого нового commit заново через GitHub API не проверялась.

## Контракт и управление

Чистое ядро `ActionRoute` описывает WALK, OPEN_DOOR, CLIMB_UP, CLIMB_DOWN:
approach/entry/exit/resource, предусловие, grounded completion и timeout.
Статусы: RUNNING, WAITING_RESOURCE, WAITING_BUDGET, SUCCESS, FAILURE.
Ожидание имеет отдельный лимит400тиков, не увеличивает physical-stuck timer.
FIFO `TransitionReservations` хранит только UUID/ключи, lease40тиков,
heartbeat очереди60тиков, максимум256 ресурсов /64 ожидающих на ресурс.

Единственный владелец движения — прежний `MovementExecutor`; внутри него
`ActionRouteExecutor` исполняет выбранный маршрут. Боевой контроллер продолжает
perception/weapon upkeep. Изменение обычного решения не сбрасывает переход.
Новая identity цели или существенное изменение NAVIGATE destination (>6 блоков)
запрашивает отмену; незавершённая лестница сначала доводится до устойчивого выхода.
Смена приказа использует такой же безопасный выход. Начатый проход двери также
доводится до выхода всем bounding box; onGround внутри проёма не считается
завершением отмены. Смерть/выгрузка/снятие контроллера
немедленно освобождают ресурсы и ввод; оставшаяся гравитация и коллизия — штатные.
Уничтоженная лестница не превращается в разрешение летать или телепортироваться.

Сначала используется прежний native наземный путь. Для недостижимого/специального
перехода выполняется incremental local A*:64 expansions/NPC/tick,512/level/tick,
768 expansions/search, максимум2048 записей,128 переходов,100тиков поиска,
горизонтальная область ±16 и вертикальная ±24 блока. Бюджет path requests остаётся
8/level/tick и1/owner; новые native отрезки используют cooldown не меньше4тиков.
Обычный запрещённый DEFEND detour отвергается немедленно прежним recovery.
После диагностируемого отказа применяется прежний ограниченный RouteRecovery.
Диагностика сохраняет последнюю причину отказа даже после успешного восстановления.
Обычное прибытие теперь явно снимает старый native move/strafe input; внешняя
скорость knockback не стирается. Это исправляет возможность продолжить движение
мимо достигнутой точки между пересчётами пути.

WALK требует пол/clearance, валидирует реальные native nodes и swept body при
коротком доведении. Лестничные промежуточные точки требуют LADDER и clearance,
но не пола. Границы приказа применяются и к action exits. Завершение требует
onGround, проверенного пола, высоты±.12 и горизонтальной ошибки<.32 блока.
Истечение времени или приблизительная высота не считаются прибытием.

## Двери и лестницы

Vanilla wooden DoorBlock: распознаются lower/upper, FACING/HINGE и соответствие
половин. Подход с обеих сторон вдоль оси проёма, range≤2.5, открытие штатным
`DoorBlock.setOpen(entity,level,state,pos,true)` на сервере, подтверждение OPEN обеих
половин и реальной коллизии проёма. При закрытии перед бойцом OPEN проверяется
повторно. Автоматического закрытия нет. Закрытые iron/модовые двери не получают
action edge; поиск может найти обычный обход.

`RouteInteractions.DoorAdapter` — точка расширения; текущая реализация намеренно
поддерживает только namespace minecraft и wooden door. Проверяется Forge
`getMobGriefingEvent`; отменяемый `RouteInteractions.OpenDoorEvent` позволяет
серверному protection adapter запретить действие. Player-only RightClickBlock
не подделывается fake player: NPC не является игроком. Автоматическое соблюдение
произвольного claim-мода, который слушает только player events, требует его адаптера.

Только непрерывный vanilla LadderBlock, одинаковый FACING, высота≤24. Подход с
лицевой стороны нижнего блока; выход — проверенная соседняя площадка наверху.
Обратный переход использует ту же колонну. Положение выравнивается внутри клетки
с учётом формы лестницы. Подъём запрашивает native JumpControl на onClimbable,
спуск использует native gravity/ladder clamp. Ни setPos, ни injected Y velocity,
ни noClip в production исполнителе нет. Ступень выхода завершается только на полу.
Проверяются непрерывность/необходимая высота/clearance/выход каждый execution tick.

Резервируется дверь или **вся колонна** по нижнему BlockPos до подхода к горловине;
ожидающий останавливается вне неё. Разные отряды также не занимают один ресурс.
Лизинг продлевается только активным владельцем, освобождается после фактического
выхода, отмены, ошибки, смерти, EntityLeaveLevel и LevelUnload.

Native TaCZ shot/reload/bolt/cooldown и fire safety не изменены. Route planner
получает только MovementCommand из разрешённых наблюдений; hidden entity position
не читается. UNFAIR_HINT остаётся отдельным источником боевого AI.

## Проверенные API и исследования

Аудит `javap -c -p` выполнен по локальному
`forge-1.20.1-47.4.20_mapped_official_1.20.1.jar`:
DoorBlock.setOpen(Entity,Level,BlockState,BlockPos,boolean), DoorInteractGoal,
GroundPathNavigation, MoveControl, LivingEntity.onClimbable/handleOnClimbable/travel,
PathNavigationRegion. Forge region берёт **ChunkSource.getChunkNow**; новый planner
проверяет hasChunkAt до чтения геометрии. Нулевые/missing chunks не загружаются.
Новых chunk tickets в production нет; fixture tickets отмечены в тестах/профиле.

[MineColonies pathing](https://minecolonies.com/wiki/systems/pathing/) изучен как
reference для специальных переходов и waypoint-подхода. Его forced relocation
при stuck не используется. Из [CNPC-npcpathfinding](https://github.com/XiaoBiniaoX/CNPC-npcpathfinding)
изучены EnhancedPathNavigator.handleLadder и PathComputationBudget на commit
`ea00d537defa90be34e4a23f161bdbeec3fce647`, ветка1.20.1Forge, MIT
(copyright2026 XiaoBiniaoX彬彬). Использованы идеи разделения вертикального движения
и бюджета, исходный код не копировался; его direct motion ladder control заменён
собственной реализацией через native travel. Новых библиотек, Mixins и заимствованных
ресурсов нет. Лицензии существующих зависимостей сохранены.

## Проверки и результаты

`bash ai-core/run-tests.sh` — **936 PASS**
(818 прежних +118 action-route), повторён координатором после финальных production
правок. [Core log](testing/action-routes-core-2026-10-22.txt).
Первый Forge compileJava/compileCompatJava — PASS24s.
Промежуточные FAIL receipts сохранены, не считаются итоговой приёмкой:
compile-only factory name (0 executed); разрушение лестницы сначала обнаруживалось
только physical-stuck, затем добавлена проверка требуемой высоты; усиленная проверка
полного completion обнаружила timeout двери. Общий STAGE19 дал17 PASS/2 FAIL
(combat after route и unload). Изолированные combat/door2 прошли; после переноса
в конечный sky fixture **все12 action GameTests PASS195,907s**.
Новый стенд явно строит пол/воздух над обычной генерацией и не делит геометрию
с наземными тестами, которые пишут длинные коридоры за границами template.
Fixture tickets освобождаются после успеха, следующего ScenarioIsolation и server stop.
Все physical completion/проходы/стрельба остались обязательными.
[FAST receipt](testing/action-routes-fast-2026-10-22.json),
[actual results](testing/action-routes-fast-results-2026-10-22.txt),
[история попыток](testing/action-routes-history-2026-10-22.json).

В этом FAST: дверь с двух сторон112/114тиков; повторное открытие206тиков для
двух последовательных проходов; подъём174, спуск113. Четыре полных прибытия:
203/265/324/390, каждый с `climbed=1`, ни одного route failure. Разрушение секции:
`ladder extent changed` на87тике; закрытый выход: `ladder landing obstructed` на93.
Оба освобождают ресурс. Combat fixture подтверждает реальное завершение лестницы
на122тике и новый native projectile на126. Время включает ожидание готовности
fixture chunks и native budgets; это тики теста, не гарантия wall-clock latency.

Окончательные результаты и продолжение после прерывания:

| Проверка | Фактический результат | Доказательство |
|---|---|---|
| Pure Java |936 assertions PASS|[core](testing/action-routes-core-2026-10-22.txt)|
| Affected STAGE combined |31/31 PASS,350,062s|[receipt](testing/action-routes-stage-2026-10-22.json)|
| Affected DefendPatrol combined |7/7 PASS,308,203s|[receipt](testing/action-routes-defend-patrol-2026-10-22.json)|
| FULL standalone |132/132 PASS,1126.391s|[receipt](testing/action-routes-full-standalone-2026-10-22.json), [physical results](testing/action-routes-full-standalone-results-2026-10-22.txt)|
| Combined после прерывания |80 PASS сохранены;2 отказа повторены по одному;62 незавершённых выполнены. Все144 уникальных сценария имеют PASS evidence|[coverage ledger](testing/action-routes-combined-coverage-2026-10-22.json), [actual results](testing/action-routes-combined-results-2026-10-22.txt)|
| IndependentNpcTests#combatSmoke |1/1 PASS,102.094s|raw receipt `forge-addon\build\test-runs\20261022T124430.029997Z-FAST-compat.json`|
| DefendPatrolTests#idleRadius128ExploresBeyondLegacyCap |1/1 PASS,120.516s|raw receipt `forge-addon\build\test-runs\20261022T124612.279751Z-FAST-compat.json`|
| 62 оставшихся сценария |62/62 PASS,618.985s|raw receipt `forge-addon\build\test-runs\20261022T124812.931826Z-STAGE-compat.json`|
| Ordinary dedicated startup/reload/save/restart/stop |PASS,107.157s|[receipt](testing/action-routes-dedicated-2026-10-22.json), [properties/EULA unchanged](testing/action-routes-dedicated-preservation-2026-10-22.json)|
| Plain compileJava/compileCompatJava/build |PASS,24.609s|[log](testing/action-routes-production-build-2026-10-22.txt)|
| Distribution boundary |PASS,Java major61;без fixtures/вложенных JAR/NPCs imports в main|[verifier](testing/action-routes-production-artifacts-2026-10-22.json)|

Последний запрос владельца изменил порядок приёмки: после исправления повторять только
проблемный тест, без перезапуска всей цепочки. Поэтому FULL standalone132/132 —
реальный завершённый прогон; последний FULL combined остался INTERRUPTED с80 PASS,
2 FAIL и62 незавершёнными. Их точные ID восстановлены по batch order и progress bar;
все144 записи ledger имеют отдельное PASS evidence, но это не новый единый FULL green.
Standalone не повторялся после двух последних test-only исправлений согласно этому запросу.
Последний предыдущий завершённый FULL combined143/144 остаётся FAIL в истории.

`combatSmoke` получил достаточное место для четырёхблочного отхода внутри своего пола:
origin x6.5 вместо3.5, target x14.5 вместо11.5. Требования движения>.25, настоящих
projectiles, native reload, потери LOS и lifecycle сохранены; сравнение ведётся с новым
фактическим origin, а не со старой точкой. У `idleRadius128...` отсутствовал lazy-loaded
вложенный класс `DefendPatrolTests$Probe`; общий build/classes уже не содержал test classes.
Причина внешнего изменения каталога не установлена. Runner теперь использует отдельные
classes/resources на каждый запуск. Первый такой заход выявил ошибку compat classpath
до запуска тестов (0 executed); output metadata исправлена, receipt сохранён.

Обычная production-сборка выполнена последней без test/profile flags. Dedicated использует
userdev: настоящий reload, native stack/reserve/team/приказы/UUID и сохранение после restart;
это не distribution launcher. Все80 SHA входов измеренного after-профиля совпадают
с текущим деревом. [Сводное evidence](testing/action-routes-validation-2026-10-22.json).

Production artifacts:

| Файл | bytes / classes | SHA-256 |
|---|---|---|
|`forge-addon\build\libs\tacticalnpcai-0.2.0.jar`|251595 /120|`78D46FAE9DE23925EF300C129967A28730A7842B112358FCCAEBDBA1EA50BF10`|
|`forge-addon\build\libs\tacticalnpcai-0.2.0-npcs-compat.jar`|7680 /4|`63BE718EEEAA07A974326B25B5EFFFE24DF7DF9488B52EA7D6B2383C63402B5D`|

История полной регрессии также сохраняется. Первый FULL standalone131:129 PASS,
2 FAIL (external displacement return и combatSmoke). Для их flat fixtures явно
построены пол и воздух; прежние критерии возврата/движения/стрельбы не ослаблены.
Повтор STAGE combined14/14 PASS194,657s. Первый FULL combined прервался после43
пройденных проверок с NoClassDefFoundError `IndependentNpcTests$1`; он не имеет
завершённого verdict. Runner теперь явно пересобирает выбранные inputs с
`--rerun-tasks`, так как test/profile/production используют общий main output.

Следующий FULL standalone131:128 PASS,3 FAIL (native hearing128, timeout door-veto,
cover emergency). Первичные сообщения не разделяли все возможные причины.
Добавлены полные диагностические состояния. Hearing fixture теперь отсчитывает
прежние140 активных тиков после entity-ticking готовности обоих удалённых NPC,
с конечным пределом ожидания; точное расстояние/native round/HEARING обязательны.
Cover fixture явно хранит легитимный контакт600тиков для270-тикового цикла,
чтобы отдельная expiry policy не подменяла проверку немедленного RETREAT.
Запрет последующих выстрелов сохранён. Door-veto сохраняет180 активных тиков,
отдельный конечный предел на подготовку remote fixture, и сообщает положение,
entity ticks, ticking, ground, navigation и action status.
Это промежуточные FAIL, они не заменяются итоговыми PASS задним числом.

Дальнейшие scoped receipts также сохранены:19/18/1 (нет native round в PEEK),
затем отдельный cover1/1;19/19;31/30/1 (slope),31/28/3 (return и два hearing),
точечные3/3 и9/9. Cover получил явную геометрию и отдельное отключение pressure-sector
overlay только в этом тесте: direct PEEK/native round/emergency остаются обязательными.
У hearing обнаружены смещение источника и critical-health RETREAT при rounds=0;
статические измерительные NPC защищены от постороннего урона, объём очищен,
исходные native/distance/HEARING assertions сохранены. Идентичность конкретного
источника постороннего урона первичным логом не установлена.
NBT fixture действительно имеет пол local y=0, а slope начинался ногами на y=2:
под ним построен предусмотренный сценой пол y=1. Конец рампы сделан площадкой
без более высокой ступени сразу за goal, сохранён distance<1 и добавлены
поддержка исходной точки/цели и onGround после прибытия. Последний STAGE31/31 PASS;
новый13-й action test проверяет настоящую отмену внутри дверного проёма.

Последний STAGE31 выполнен за350,062s: обе стороны двери113/118тиков,
повторно закрытая дверь223, подъём164, спуск146. Четыре прибытия —
201/263/342/429тиков; каждый действительно прошёл дверь и лестницу.
У последнего бойца был один ограниченный recovery после `walk native path rejected`,
он затем прибыл; это не нулевой уровень отказов. Отмена внутри двери завершилась
на83тике после освобождения проёма всем bounding box. Разрушенная секция и
закрытый выход дали ожидаемые `ladder extent changed` / `ladder landing obstructed`
на93/104тиках. В боевом сценарии подтверждены прибытие124 и новый native round128.
[STAGE receipt](testing/action-routes-stage-2026-10-22.json),
[physical outcomes](testing/action-routes-stage-results-2026-10-22.txt).
В GameTest четырёх бойцов достигший общего конечного waypoint удаляется только
после grounded arrival за пределами двери/лестницы; это teardown, освобождающий
одну и ту же конечную клетку. Дополнительный нагрузочный стенд оставляет всех
бойцов живыми на разных конечных позициях площадки: `alive_min=ticking_min=4/30/50`
и все прибытия обязательны. Эти два протокола не смешиваются.

Далее FULL standalone132/132 PASS1121,563s; FULL combined144/143/1 FAIL
в прежнем `unsafeVisibleContactDoesNotMonopolizeFirstPage`. Все13 action tests
в нём прошли. Старый лог не устанавливает причину отказа смены цели однозначно.
Для этого теста явно построены пол/воздух и защищён статический измерительный NPC
от постороннего урона, добавлены HP/позиции/target/cursor-state diagnostics.
Исходные assertions дальнего первого контакта и выбора конкретного ближнего UUID
на190тике сохранены. Отдельный combined повтор1/1 PASS119,578s; после этой
test-only правки обе полные матрицы запущены заново. Их окончательные receipts
перечисляются отдельно, предыдущие FAIL остаются в истории.

Следующий standalone был прерван после53 пройденных тестов без test-failure:
JVM `os::commit_memory`, DOS errno1455, Windows commit exhaustion. В crash log
на момент отказа оставалось560MiB pagefile availability, JVM пыталась добавить
около858MiB heap. Завершённого verdict нет (`executed=0` в fail-closed runner).
Оставшихся наших JVM проверка не обнаружила. Для последующего FULL заданы отдельные
GameTest/Gradle heap caps2560/1024MiB; они передаются явно и сохраняются в receipt.
Это настройки test launcher, не изменение production или измеренного профиля.
Этот повтор также прервался на Windows errno1455 после37 пройденных проверок:
требовалось828MiB при783MiB доступных. Затем heap GameTest сделан заранее выделенным
`Xms=Xmx2560m`, build heap уменьшен до768MiB. Владелец также подтвердил, что
освободит память приложений; чужие процессы автоматически не завершались.

После освобождения памяти и fixed heap FULL standalone132/132 PASS1117,500s;
combined144/143/1 FAIL — прежний `combatAndLiveMemoryInterruptThenPatrolResumes`
не подтвердил native round на180тике. Предыдущий отказ unsafe-retarget не повторился.
Первичный patrol log не содержит состояния, достаточного для однозначного вывода
о причине. Код стенда использовал случайную identity/траекторию патруля, один
раз задавал yaw при появлении цели и не подтверждал, что стимул действительно
был увиден. Этот тест проверяет interruption/resumption, а не вероятность
случайного взгляда. Теперь для него явно построены пол/воздух, задан seed,
измерительный NPC защищён от постороннего урона; до настоящего VISION удерживается
исходная поза, затем сразу возвращается обычная скорость. Контакт/weapon command
в контроллер не инжектируются. Добавлены VISION deadline120 и подробные diagnostics.
Сохранены native projectile на180тике, live-memory non-PATROL, последующее реальное
движение>.5 и finite0 без пополнения. После affected DefendPatrol-набора обе полные
матрицы повторяются с окончательным стендом.
Весь affected DefendPatrol combined-набор **7/7 PASS308,203s**:
[receipt](testing/action-routes-defend-patrol-2026-10-22.json).

Команды:

```text
bash ai-core/run-tests.sh
python tools/testing/run-gametests.py FAST --offline --tests ActionRouteTests
python tools/testing/run-gametests.py FULL --offline --heap-mb 2560 --gradle-heap-mb 768
python tools/testing/run-gametests.py FULL --legacy --offline --heap-mb 2560 --gradle-heap-mb 768
python tools/testing/run-action-routes.py before
python tools/testing/run-action-routes.py after
python tools/testing/run-dedicated-check.py
cd forge-addon
gradlew.bat compileJava compileCompatJava build --offline --rerun-tasks --console=plain
cd ..
python tools/verify-production-artifacts.py
```

Load fixture: ordinary dedicated, seed8675309,1/4/16/30/50 NPC,
до13 групп по4, общая дверь/лестница на группу. После фактического прибытия
единоличное управление передаётся боевому контроллеру; настоящие native projectile
joins/hits считаются отдельно. JFR и CSV хранятся рядом с receipt. Различие объёма
огня или неприбытие baseline не выдаются за выигрыш оптимизации.

Оба окончательных профиля **PASS5/5**, fixture/dependency SHA одинаковы,
production sources не менялись во время замеров. Первоначальный baseline с
драйвером ServerTick.START исключён: path counters сбрасывались при смене gameTime.
Окончательный драйвер работает на LevelTick.END перед telemetry sampling.
Финальные raw runs: `forge-addon/build/action-profile/20261022T083754-before` и
`20261022T084318-after`. [Сравнение/SHA](testing/action-routes-comparison-2026-10-22.json),
[before receipt](testing/action-routes-before-2026-10-22.json),
[after receipt](testing/action-routes-after-2026-10-22.json),
[raw actor outcomes](testing/action-routes-after-actions-actors.csv).

| NPC / группы | Прибытия до → после | Медиана / последний, тики после приказа | Native rounds / impacts до → после | Стрелявшие после | Первый round / impact после, тики | MSPT p95 до → после | AI p95 после |
|---|---|---|---|---|---|---|---|
|1 /1|0 →1|159 /159|0 /0 →100 /99|1|169 /170|4,870 →6,418|1,560|
|4 /1|0 →4|211 /345|0 /0 →27 /27|2|156 /157|4,517 →4,381|0,772|
|16 /4|0 →16|212 /346|0 /0 →108 /108|8|156 /157|4,864 →6,758|1,982|
|30 /8|0 →30|214 /348|0 /0 →288 /288|15|156 /157|6,966 →10,415|3,502|
|50 /13|0 →50|216 /350|0 /0 →421 /421|25|156 /157|8,386 →12,612|4,485|

961 measured samples/phase. Во всех пяти фазах до и после `alive_min` и `ticking_min`
равны заявленному cohort: подтверждены реальные entity ticks каждой сущности,
а не только spawn/registration. Raw CSV отдельно хранит `route_drivers` и
`combat_owners`; means показывают передачу управления после прибытия.
Конечная readiness проверяется до начала измеряемого окна.
Snapshot counters include manual movement work via
AiTelemetry.begin/end and native combat after handoff. `registered_combat_goals` in raw CSV counts
registered combat goals; manual route drivers are counted separately.
После прибытия скорость NPC в этом fixture обнуляется: это фиксированные огневые
позиции, не свободный squad PvP. В50 сцене стреляли25 владельцев, не50.
Стенд сохраняет штатный allied corridor veto; отдельный вклад блокировки,
ролей и оружейного арбитража в это число не изолирован. Защита союзников
не обходилась ради числа выстрелов. Target — неподвижный survival FakePlayer;
impacts считаются по настоящему bullet damage callback, потеря HP отключена стендом.
Эта пара доказывает прохождение/возобновление native combat и измеряет их стоимость,
но не общий performance win: baseline не выполнял ту же полезную работу. MSPT вырос,
AI target≤2ms при30/50 не достигнут. Mean path requests/tick:30 —.495 →.109,
50 —.977 →.182; уменьшение path retries и увеличение огня не объявляются независимыми
оптимизациями. Полные CSV и JFR сохранены с границами измеряемых фаз.

JFR окончательной пары разобраны после завершения игровых JVM, только по измеряемым
окнам CSV, без startup/warmup/teardown. Серверные CPU samples:
956 →1020; sampling counts не являются миллисекундами.

| Категория | CPU samples до → после |
|---|---|
|action_navigation|2 →12|
|goal_or_native_safety|0 →27|
|native_maintenance|11 →21|
|native_shoot|1 →1|
|hearing_delivery|0 →0|
|projectile_physics|0 →2|
|profiling_fixture|21 →25|
|other_server|921 →932|

Allocation weights сохранены как sampling estimates; fixture/native scopes разделены.
[Before JFR summary](testing/action-routes-jfr-before-2026-10-22.json), [after JFR summary](testing/action-routes-jfr-after-2026-10-22.json).

## Изменённые файлы

- `ai-core/.../core/ActionRoute.java`, `TransitionReservations.java`: immutable transitions,
  раздельные статусы/часы, FIFO leases; `ActionRouteTest.java`, `ai-core/run-tests.sh`.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/ActionGeometry.java`,
  `ActionRoutePlanner.java`, `ActionRouteExecutor.java`, `RouteInteractions.java`,
  `TransitionResources.java`: loaded local planning, native interaction/physics, reservations.
- `MovementExecutor.java`, `TacticalCombatGoal.java`, `AiWorkBudget.java`, `AiTelemetry.java`:
  единственный владелец, commitment/cancellation/expiry, поиск и lifecycle budgets.
- `entity/TacticalNpcEntity.java`, `runtime/RuntimeRegistry.java`, `compat/ShotSafetyEvents.java`:
  смена приказа, выгрузка, смерть. Optional NPCs production classes не изменены.
- `forge-addon/src/gametest/.../ActionRouteTests.java`, `ScenarioIsolation.java`:
  реальные13 сценариев; `DefendTerritoryTests.java`, `IndependentNpcTests.java`:
  явная пустая геометрия старых flat fixtures, без ослабления assertions.
- `CombatUpgradeTests.java`, `CoverTests.java`: явные условия готовности/времени
  контакта для изолированного hearing/cover цикла, диагностические причины отказов.
- `DefendPatrolTests.java`: воспроизводимая сцена interruption/resumption с
  подтверждённым VISION перед свободным боевым циклом и подробной диагностикой.
- `forge-addon/src/profile/.../ActionRouteProfile.java`, `ServerProfile.java`,
  `forge-addon/build.gradle`: изолированный ordinary dedicated fixture.
- `tools/testing/run-gametests.py`: полная пересборка выбранного test source set
  при переключении test/profile/production режимов, включая внутренние классы;
  отдельные classes/resources на каждый run и optional heap caps для game/build
  JVM через `gradle/testing.gradle`.
- `tools/testing/run-action-routes.py`, `compare-action-routes.py`,
  `summarize-batch-jfr.py`, `tools/verify-production-artifacts.py`: receipts, сравнение,
  JFR boundaries, исключение нового profile из production JAR.
- `docs/INTEGRATION-AUDIT.md`, `IMPLEMENTATION-STATUS.md`, этот отчёт и
  `docs/testing/action-routes-*`: аудит и фактические доказательства.

Точный полный список: [изменённые файлы](testing/action-routes-changed-files-2026-10-22.json).
Инструкции — отдельный commit `b57bd49`; основная навигация — commit `716200d`.
Заключительные правки стенда/runner и evidence остаются рабочим diff.
Я push/PR/merge не выполнял. Список файлов включает Stage2 целиком от `b57bd49`.

## Ручная приёмка production JAR

В отдельном Forge47.4.20 launcher установить основной JAR + TaCZ1.1.8-hotfix2,
затем отдельно повторить с NPCs2.1.0 + optional adapter. Получить реальный визуальный
контакт/слышимый источник, скрыться за деревянной дверью, подняться по непрерывной
лестнице на площадку и продолжить бой; затем уйти вниз. Повторить с4 NPC, закрывая
дверь между бойцами. Проверить обе стороны/FACING/HINGE, различную высоту и выход,
перекрытие площадки/разрушение секции, смену приказа на лестнице, смерть/выгрузку,
занятие прохода игроком и разрешения используемого claim-мода.

Минимальный воспроизводимый маршрут для человека:

1. Построить единственный коридор шириной1 блок с oak door и непрерывной
   лестницей высотой6 блоков; наверху сделать устойчивый выход/площадку с местом
   для4 тел. Наземного обхода, ступеней и прыжковой альтернативы быть не должно.
2. Выполнить `/tacticalnpc hard`, затем `/spawn npc team route count 4` на свободной
   нижней площадке. Перейти в survival вне team `route`. Сначала показать NPC
   своё положение на верхней площадке через открытую геометрию; затем закрыть дверь.
   Скрытое перемещение без какого-либо наблюдения не является командой навигации.
3. Подтвердить4 фактических прохода двери и выхода на пол наверху, продолжение
   native огня/перезарядки. Закрывать дверь между бойцами; повторить подход с другой
   стороны. Зафиксировать first shot/попадание и время каждого прибытия по записи.
4. Показаться внизу, затем укрыться: проверить спуск с реальным выходом на нижний
   пол. Отдельно сменить приказ во время подъёма/прохода двери; NPC должен закончить
   безопасный выход и освободить горловину перед выполнением нового приказа.
5. В отдельных повторах разрушить среднюю секцию лестницы и закрыть верхний выход.
   Проверить диагностируемый отказ, ограниченное восстановление и освобождение
   ресурса при смерти/выгрузке; не требовать невозможного прибытия через препятствие.
6. Сохранить/restart, повторить с optional adapter, затем отдельно проверить встречное
   движение, присутствие игрока в проёме и используемый protection mod. Сохранить
   JAR SHA, конфигурацию, фактическое число NPC/стрелявших и MSPT вместе с результатом.

Автотесты не заменяют человеческую оценку визуальной анимации, сетевой плавности,
контрплея, очередей с встречным движением и конкретных protection mods. Trapdoors,
iron-door interactions, vines/scaffolding, модовые двери/лестницы и parkour здесь
не реализуются. Длинный/разветвлённый маршрут за пределами локального горизонта
может потребовать дополнительных тактических waypoint. Статус: WAITING_PLAYTEST.

В этой итерации **NOT RUN**: человек против1/4 NPC и оценка контрплея;
отдельный distribution launcher с production JAR на клиенте и сервере;
встречные потоки/игрок, перекрывающий горловину; конкретный claim/protection mod;
естественное прохождение границ entity-ticking без fixture tickets; длительный
multiplayer soak. Optional combined userdev подтверждает свой каталог регрессий,
но не заменяет ручной маршрут через дверь/лестницу для каждой сущности адаптера.
