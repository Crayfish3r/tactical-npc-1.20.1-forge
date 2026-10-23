# Match NPC API — проверка 2026-10-23

Локальные изменения ветки `feature`, исходный HEAD
`b617e87ab9da81d5eed9ee9c6675ad86c0e6126f`. Commit/push/merge не выполнялись.
Minecraft1.20.1 / Forge47.4.20 / Microsoft OpenJDK17.0.19+10-LTS /
TaCZ1.1.8-hotfix2. Optional NPCs2.1.0 доступен для compileCompatJava, не подключён
в standalone runtime. Хеши pinned dependencies совпали с INTEGRATION-AUDIT.

## Реализация

[API и примеры подключения](MATCH-NPC-API.md). Production main JAR содержит
`TacticalNpcMatchApi`, `NpcSpawnService`, `SpawnGroupRequest`, `SpawnMemberSpec`,
`SpawnResult`, `MatchNpcPolicy`, `MatchNpcRegistry`. Внешний Forge-мод вызывает Java
API на server thread. Оружие/finite reserve/приказ индивидуальны; native scoreboard
объединяет группу. Match spawn транзакционный, administrator spawn частичный.

`CombatTargets` проводит match policy через существующие acquisition, память,
hearing/squad и native fire validity. `ShotSafetyEvents` проверяет native rounds,
сохраняет ownership в projectile и запрещает недопустимый native/Forge damage.
Отсутствие/ошибка/неактивность политики — fail closed. Core AI не менялся.

Schema7 хранит ownerMatchId/groupId; старые1–6 не получают match binding.
SavedData хранит ownership и tombstones, без сериализации callback. После рестарта
старые match IDs закрыты; загруженный orphan удаляется при admission, отложенный —
при естественной загрузке чанка. Весь мир/region files не сканируются. Native team
membership не дублируется в entity NBT. Persistent marker защищает чужую одноимённую
team; посторонние участники/ручные NPC сохраняются.

## Итоговая STAGE оценка

**PASS применимого выборочного покрытия: 53 уникальных GameTests, включая17 API.**
Покрытие собрано из успешных сценариев STAGE и последующего FAST10/10.
Первоначальный STAGE остаётся **FAIL48/51**, новый единый зелёный STAGE/FULL не заявлен.
Повторялись изменённые/неуспешные scopes; успешные неизменённые scopes не повторялись.
Условия применимости: final production code, выбранные tests/helpers, fixtures,
config/dependencies/runtime учтены в
[manifest/точных командах](testing/match-api-validation-2026-10-23.json).

| Проверка | Фактически выполнено | Итог |
|---|---|---|
| Git Bash `bash ai-core/run-tests.sh` |72+94+38+235+353+26+118 =936 checks|PASS|
| Первый API FAST |13 completed:11 passed,2 fixture failures|FAIL, [receipt](testing/match-api-initial-api-2026-10-23.json)|
| Первый expanded compile |0 GameTests, ошибка типа в новом fixture|FAIL compilation, [receipt](testing/match-api-compile-failure-2026-10-23.json)|
| Expanded standalone STAGE |51 completed:48 passed,3 fixture failures|FAIL, [receipt](testing/match-api-stage-2026-10-23.json)|
| Scoped FAST после исправлений |10 selected=completed=required passed, exit0|PASS, [receipt](testing/match-api-recovery-2026-10-23.json)|
| Plain production build + Maven publication |22s,15 tasks:10 executed/5 up-to-date|PASS; Gradle test=NO-SOURCE не засчитан как тест|
| Production verifier |137 main/4 compat classes, Java61, без bundled dependencies/tests/arenas/NPCs linkage в main|PASS, [identity](testing/match-api-artifacts-2026-10-23.json)|
| Внешний ForgeGradle consumer |Отдельный проект, `fg.deobf` опубликованного main JAR; реальные group4+remote1, chunk unload, disk save/restart, cleanup/team/manual checks;94.312s|PASS, [receipt](testing/match-api-consumer-2026-10-23.json)|
| Существующий ordinary dedicated harness |Реальные commands/reload/config migration, chunk unload/load, disk save/restart UUID/name/service/ammo/stack/order/team;114.031s|PASS, [receipt](testing/match-api-dedicated-2026-10-23.json), [properties/EULA preservation](testing/match-api-preservation-2026-10-23.json)|
| Static diff/links/Python syntax |Changed docs/source/test scripts; no game JVM required|PASS|

Прогоны GameTests и ordinary серверов последовательные; не выполнялись измерения
производительности. Ordinary consumer работает через ForgeGradle userdev с
опубликованным distribution JAR, без исходников TacticalNPC на classpath consumer.
Его Java/resources находятся в отдельном тестовом моде, основной JAR их не содержит.
Оба ordinary harness используют явно указанные test-only chunk tickets; production
spawn/API их не создают.

### Отказы и исправления

1. Sandbox запретил Gradle wrapper lock и системный объект MSYS: тестов0.
   Разрешённые запуски с доступом к существующему окружению прошли. Исходный
   [zero-execution receipt](testing/match-api-initial-sandbox-2026-10-23.json) сохранён.
2. FAST13: `m870` оказался штатно неподдержанным Lua shooting script, а fixture
   ожидал успешный group spawn. Заменён на поддержанный `b93r`; проверка unsupported
   `rpg7` оставлена отдельной. Тест памяти вызывал `hear` при уже выбранной визуальной
   цели; перед слуховым контактом теперь явно очищается прежний контакт.
3. Expanded compile: `Entity.getTeam()` возвращает `Team`, а удаление принимает
   `PlayerTeam`; fixture получает его через scoreboard. Никаких GameTests тогда
   не исполнилось.
4. STAGE51: ягода без опоры разрушалась и переставала быть head hazard — заменена
   устойчивым powder snow. Activation sensor конкурировал с собственным AI за
   optional budget в том же тике — cadence измеряется до45, AI останавливается
   только на отдельный explicit sensor probe. Lifecycle helper требовал shots к
   фиксированному тику55 — теперь ждёт реальный native projectile, затем проверяет
   немедленную инвалидизацию и отсутствие stale fire ещё55 тиков. Все три lifecycle
   сценария этого helper повторены; timeout остаётся150.
5. Последний scoped набор также проверяет malformed owner/group UUID, реальное
   выполнение обоих spawn-алиасов, native hit по участнику/постороннему, orphan bullet
   и деактивацию уже запущенного настоящего native round. Проверяется cancellation
   native Pre event на captured actual projectile; это не отдельный физический
   collision-test случайно пересёкшего траекторию постороннего игрока.

Исходные FAIL/logs не удалялись; raw logs и миры лежат в ignored `forge-addon/build/`.
Маленькие JSON receipts сохранены в `docs/testing/`. JFR/load/soak, FULL, combined
runtime, human PvP и фактический TT-2.0 playtest в этот scope не входили и не объявлены
PASS. Optional adapter компилировался в launcher/build; это отдельно от его runtime.

## Покрытие сценариев запроса

Методы находятся в [MatchNpcApiTests](../forge-addon/src/gametest/java/dev/zuma/tacticalnpc/MatchNpcApiTests.java).

| Требование | Проверка |
|---|---|
|1. Одиночный NPC|`singleNpcAndServerThread`|
|2. Группа4|`groupFourIndividualLoadoutsAndOrders`|
|3. Индивидуальное оружие/ammo/orders|Тот же group4, все четыре order kinds; native finite/infinite regression suites|
|4. Неверное/unsupported оружие|`invalidAndUnsupportedWeaponsLeaveNoGroup`|
|5. Мало места/rollback|`insufficientSpaceIsAtomicAndNoChunkLoads`, `nativeAdmissionFailureRollsBackEntitiesAndTeam`|
|6. Match/group NBT reload|`bindingNbtReloadPreservesNativeState`|
|7. Старые NBT|`schemasOneThroughSixRemainManual` и существующие TeamTests/ModeCommandTests|
|8. Защита постороннего игрока|`playerMembershipEliminationSpectatorAndMemory` и native hit assertions в flight test|
|9. Нет policy/ошибка callback|`missingAndFailingPolicyCannotFireOrDamage`; acquisition, generic damage, GunFireEvent|
|10. Деактивация после выстрела|`deactivationRevokesAnActuallyLaunchedNativeBullet`|
|11. Loaded/unloaded cleanup|`cleanupLoadedAndPreviouslyUnloadedIsIdempotent`, внешний ordinary consumer с реальным unload и disk restart|
|12. Повторная очистка|Тот же cleanup test и ordinary consumer|
|13. Ручные NPC|`manualNpcAndForeignTeamsArePreserved`, spawn aliases, старые suites и оба ordinary harness|
|14. Teams не утекают|Cleanup/rollback tests, `recreatedForeignTeamIsNeverRemovedAfterRestart`, consumer startup cleanup|

Дополнительно: fluids/hazards/border, loaded-chunk guard, off-thread rejection,
corrupt ownership, нельзя повторно открыть старый match ID, нельзя повторить успешный
group ID, сохранность вручную присоединённого игрока и ручной team.

## Изменённые файлы

- Новый production пакет `forge-addon/src/main/java/dev/zuma/tacticalnpc/api/`:
  `TacticalNpcMatchApi.java`, `NpcSpawnService.java`, `SpawnGroupRequest.java`,
  `SpawnMemberSpec.java`, `SpawnResult.java`, `MatchNpcPolicy.java`, `MatchNpcRegistry.java`.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/entity/TacticalNpcEntity.java`:
  ownership, schema7, admission/removal/disabled-match lifecycle.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/CombatTargets.java`:
  единый match target gate, используемый существующими sensors/goal/memory.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/compat/ShotSafetyEvents.java`:
  native round/projectile/damage и final living damage guards.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/TacticalNpcAiMod.java`: startup cleanup.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/command/NpcSpawning.java`,
  `NpcCommands.java`: общий service, частичный admin spawn, два spawn-алиаса.
- `forge-addon/build.gradle`: Maven publication в `build/repository`, sources JAR.
- `forge-addon/src/gametest/java/dev/zuma/tacticalnpc/MatchNpcApiTests.java`:
  новые17 сценариев. `ModeCommandTests.java`, `TeamTests.java`: актуальная schema/aliases.
  `TargetTypeTests.java`: изоляция budget probe и actual-projectile lifecycle precondition.
- `tools/testing/run-dedicated-check.py`: assertion schema7.
- `tools/testing/run-match-api-consumer.py` и `tools/testing/fixtures/match-api-consumer/`
  (`settings.gradle`, `build.gradle`, `src/main/resources/META-INF/mods.toml`,
  `src/main/java/probe/MatchApiConsumer.java`): отдельная compile/runtime проверка.
- `README.md`, `docs/COMMANDS.md`, `docs/INTEGRATION-AUDIT.md`,
  `docs/IMPLEMENTATION-STATUS.md`, `docs/MATCH-NPC-API.md`, этот отчёт и
  `docs/testing/match-api-*-2026-10-23.json`: контракт, аудит и receipts.

## Артефакты и интеграция TT

Основной JAR `forge-addon/build/libs/tacticalnpcai-0.2.0.jar`:301604 bytes,
SHA256 `6FAA7CB48C012B9959E9386094A560765707E89DFBFB6CC28837525C69C4C806`.
Публикация Maven содержит тот же основной JAR. Optional compat:7680 bytes,
SHA256 `D9C3C37DE785BACD28BDB69A57209E9CA22A5A00324875C50774562EC2890C90`.

TT должен подключить Maven repository + `fg.deobf` dependency, объявить mod dependency,
создать новый match UUID, зарегистрировать policy перед spawn, проверять SpawnResult,
обновлять membership/elimination до последующих атак и вызывать `deactivate/remove`
перед завершением/сменой матча. После рестарта старые UUID не возобновляются.
Policies/requests не вызываются с network/worker threads напрямую.

Удаление NPC из выгруженного entity-region файла откладывается до его загрузки;
принудительной загрузки/сканирования нет. Закрытые UUID остаются tombstones.
TT-2.0 в этой рабочей папке не интегрировался; внешний consumer доказывает Java/JAR
контракт, но не заменяет TT-specific lifecycle/playtest. Human PvP и отдельная
установка модпака с TT остаются интеграционными действиями владельца.
