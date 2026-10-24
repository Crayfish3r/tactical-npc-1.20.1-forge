# SavedData lifecycle и скрытие имён — 2026-10-24

Локальная ветка `feature`, исходный HEAD `ab6ad7a993210e4a86588a5eb0cbb64ba1e11e18`, без commit/push/merge. Minecraft 1.20.1 / Forge 47.4.20 /
Microsoft OpenJDK 17.0.19+10-LTS / TaCZ 1.1.8-hotfix2. Optional NPCs 2.1.0 доступен
для compilation, standalone runtime его не содержит. Pinned dependency SHA совпали
с [аудитом](INTEGRATION-AUDIT.md). [Контракт миграции](SAVED-DATA-MIGRATION.md).

## Изменение поведения

Временное владение именем записывается явно; окончательный removal, rollback и
подтверждённый cleanup освобождают все индексы. Unload/перенос измерения сохраняют
идентичность, административные записи сохраняют прежнюю семантику.
Удалённый матч больше не сохраняет группы/UUID/policy/weak handles. Одноразовые ID
хранятся в фиксированном fail-closed фильтре без TTL. Неизвестная binding не допускает
NPC к бою. Оба реестра читают schema1 и пишут schema2; повреждённый файл не заменяется
пустым. Имена скрыты только у TacticalNpcEntity, собственные API teams имеют NEVER.
Командная диагностика показывает размеры текущих коллекций по запросу.

## Стресс: размеры до и после циклов

Это сравнение начального и конечного состояния свежих изолированных реестров
на исправленной реализации, не измеренный before/after MSPT baseline прежнего коммита.
В каждом цикле проверяются конечные коллекции, повторное удаление и имена.

| Проверка | До | После |
|---|---:|---:|
| 1000 временных NPC: entries/owners/loaded | 0/0/0 | 0/0/0 |
| NpcNames несжатый NBT | 32 bytes | 32 bytes |
| 1000 матчей: matches/members/policies/loaded/pendingCleanup | 0/0/0/0/0 | 0/0/0/0/0 |
| MatchNpcRegistry несжатый NBT | 131170 bytes | 131170 bytes |
| 1000 матчей: временные имена/созданные teams | 0/0 | 0/0 |

`owners` проверяется повторным получением одного освобождённого имени и отсутствием
поискового результата для удалённого UUID; loaded/members/policy — счётчиками.
Каждый из 1000 использованных ID отклоняется и в текущей сессии, и после NBT roundtrip.
Фиксированный NBT включает 128 KiB фильтра; gzip-файл может расти до ограниченного
размера по мере заполнения битов. Боевой throughput/MSPT не измерялся.

## Проверки

- FAST 14/14 PASS: новые lifecycle7, NpcNameTests6, существующий native rollback1.
  [Receipt](testing/saved-data-fast-2026-10-24.json). Выполнен до дополнительной
  проверки corrupt disk IO и усиления admission; окончательное покрытие уточняется ниже.
- Все ai-core suites: 936 PASS. [Receipt](testing/saved-data-core-2026-10-24.json).
- Первый запуск Gradle в sandbox: 0 выполнено, access denied к wrapper lock.
  [Сохранённый receipt](testing/saved-data-sandbox-2026-10-24.json). Это не test PASS.
  Первый Git Bash также остановлен MSYS access denied до suites; разрешённый запуск прошёл.

- FULL standalone: 161 completed, 160 PASS / 1 FAIL, 1179.610 s. [Исходный receipt](testing/saved-data-full-2026-10-24.json).
  `DefendPatrolTests#persistedDefendRebuildsPatrolAfterEntityLoad` требовал writer schema6,
  хотя writer уже до этой задачи использовал schema7. Fixture теперь отдельно проверяет
  запись schema7 и явно преобразует snapshot к schema6 для legacy reader/patrol rebuild.
- Recovery FAST: 25 selected = completed = required passed, exit0, 197.109 s.
  [Receipt](testing/saved-data-recovery-2026-10-24.json). Повторены MatchNpcApiTests17,
  SavedDataLifecycleTests7 и исправленный patrol1. Повтор потребовался также после
  устранения двойного чтения weak reference: GC мог обнулить её между чтениями.
  Ссылку теперь читают один раз в локальную переменную. Нового зелёного FULL нет.

Окончательное Forge-покрытие: 136 применимых успешных сценариев FULL + 25 recovery =
161 уникальный сценарий. Затем дополнительно исполнен FAST2/2 для marker presence:
[receipt](testing/saved-data-binding-2026-10-24.json). Проверены true/false/неверный тип
marker при отсутствии обоих UUID после compaction. Изменение weak-reference pruning затрагивает API/lifecycle,
полностью включённые в recovery; остальные core/combat/navigation inputs не менялись.
Изменение одного legacy assertion не затрагивает остальные методы патруля/fixtures.
Изменения client run directories/options касаются только clientSmoke run.
Добавленный совместимый четырёхаргументный Entry constructor проверен внешним consumer;
внутренние join/load/serialization продолжают использовать тот же пятиаргументный путь.

- Graphical client: два входа в одну ordinary server session, 10/10 стадий, 252.000 s.
  [Receipt](testing/saved-data-client-2026-10-24.json). На обоих входах aimedClose=true,
  native loaded=true, noNpcs=true, hidden=true; forced ALLOW events 3636/3791.
  Проверены собственная API team, команды1/2/clear/foreign, NBT readmission и
  пересоздание группы при повторном входе. Сохранены10 PNG; визуально просмотрены
  кадры повторного входа и readmission: у обоих NPC нет имён, VisibleSheep виден.
  Readmission здесь использует native UNLOADED_TO_CHUNK + повторное добавление;
  естественная выгрузка entity/chunk и disk admission отдельно проверены consumer.
- Plain production build + локальная Maven publication: PASS, 26 s, 15 tasks
  (9 executed / 6 up-to-date, первоначальный build). Gradle test=NO-SOURCE не засчитан как тест.
  Production verifier: PASS, main141 / compat4 classes, Java61, без test/profile/
  clientSmoke/арен/вложенных dependency JAR, без NPCs linkage в main.
  [Artifact identity и команда](testing/saved-data-artifacts-2026-10-24.json).
- Внешний fg.deobf consumer против опубликованного distribution JAR: PASS,
  99.828 s (final artifact), два ordinary dedicated запуска. [Receipt](testing/saved-data-consumer-2026-10-24.json).
  Group4+remote1 действительно выгружается; после сохранения fixture преобразует оба
  настоящих .dat в schema1, следующий startup читает их и сохраняет schema2.
  Все5 временных имён удалены, manual1 сохранён, matches/members/policies/loaded=0;
  старые local/remote actors не оживают. Полный native weapon NBT, UUID/serviceId,
  manual name/team/order/ammo policy совпали. Наследуемый Entry constructor4 также исполнен.
- Ordinary command harness: PASS, 134.156 s, два запуска нового изолированного мира.
  [Receipt](testing/saved-data-dedicated-2026-10-24.json), [properties/EULA preservation](testing/saved-data-preservation-2026-10-24.json).
  Проверены реальные commands/reload/config migration, естественный chunk unload/load,
  name/UUID/serviceId/finite policy/native stack/order/team save/restart и штатный stop.
- После сверки первого consumer обнаружен stale weak handle в диагностике NpcNames:
  native unload завершался после Forge tracking-end callback. Active API уже исключал
  удалённую сущность, но loaded-size показывал6 вместо4 local+1 manual.
  `counts/loaded` теперь удаляют null/removed handles по запросу; nonremoved tracking
  loss сохраняет addressability. Это не tick scan и не изменение SavedData/NBT.
  Именной FAST7/7 PASS, 93.469 s: [receipt](testing/saved-data-names-final-2026-10-24.json).
  Serialization, active results и renderer не менялись: applicable ordinary/client
  проверки переиспользованы. Финальный consumer отдельно повторяет настоящий unload
  с точной assertion loaded=5; первоначальный consumer был PASS до усиления assertion:
  [первоначальный receipt](testing/saved-data-consumer-initial-2026-10-24.json).
  Последний plain production rebuild26s: 15 tasks (10 executed/5 up-to-date), verifier PASS.
  Предыдущая идентичность сохранена: [initial artifacts](testing/saved-data-artifacts-initial-2026-10-24.json).

## Окончательная STAGE assessment

**PASS применимого итогового покрытия; исходный FULL остаётся FAIL160/161.**
Final consumer и production verifier прошли. Forge-покрытие собрано из
FULL + scoped25 + binding2 + names7, без повторного полного запуска. После финального
on-demand pruning используется130 неизменённых PASS из FULL,23 из scoped25,1 из
binding2 и7 из names7: всего161 уникальный сценарий. NULL/removed pruning не меняет
сериализацию или результат активного выбора: уже успешные core/combat/navigation,
ordinary persistence и графический renderer сохраняют применимость. Stress endpoints
переиспользованы: в них loaded map уже пуст после каждого удаления, новый pruning
не меняет эти входы/выходы. Настоящий delayed unload дополнительно прошёл final consumer.
Source/resource/dependency SHA и команды/receipts: [manifest](testing/saved-data-manifest-2026-10-24.json).
Static document links/Python AST/JSON/diff проверены после всех правок. Эта оценка
собрана из нескольких применимых запусков, не новый единый зелёный FULL.
FULL combined, JFR/load/soak, human1v1/1v4 и TT-specific playtest не выполнялись.

### Сохранённые клиентские FAIL

Первый QuickPlay run: connection1 прошёл5/5, connection2 не завершил Forge login:
server отправил payloads, client не прислал acknowledgements и не вошёл в мир.
[Исходный receipt](testing/saved-data-client-initial-2026-10-24.json). Причина transport/
QuickPlay сбоя не доказана; production network-код не менялся. Harness теперь использует
проверенные pinned APIs status ping -> ConnectScreen, как обычный список серверов.

Первый status run подключился, но исходная общая assertion готовности через40 ticks
завершилась FAIL до снимков. [Receipt](testing/saved-data-client-warmup-2026-10-24.json).
Сообщение не разделяло readiness predicates, поэтому точное неготовое условие не доказано.
Fixture теперь немедленно проверяет hidden-name contract, а фактические ammo/aim readiness
ждёт в пределах прежнего overall timeout; условия PASS не ослаблены. Исправлены также
onboardAccessibility=false и допустимый client simulationDistance=5. Успешный следующий
run содержит ровно10 стадий и оба PASS. Старые FAIL/logs не удалены. Попытка закрыть
onboarding через Computer Use не выполнила UI input: runtime не стартовал из-за glob
workspace path с квадратными скобками; итоговый прогон полностью автоматический.


## Изменённые файлы

- `forge-addon/src/main/java/dev/zuma/tacticalnpc/command/{NpcNames,StrictSavedData,NpcCommands}.java`:
  schema2, cleanup/counters/cache, защита от пустой замены, диагностика.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/api/{MatchNpcRegistry,UsedMatchIds,TacticalNpcMatchApi}.java`:
  lifecycle, ограниченное хранение использованных ID, API contract.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/entity/TacticalNpcEntity.java`:
  removal reasons, скрытый CustomName, fail-closed matchOwned marker в schema7.
- `forge-addon/src/main/java/dev/zuma/tacticalnpc/client/NpcClient.java`: renderer boundary.
- `forge-addon/src/gametest/java/dev/zuma/tacticalnpc/api/SavedDataLifecycleTests.java`,
  `MatchNpcApiTests.java`, `NpcNameTests.java`, `DefendPatrolTests.java`: регрессии, конечные размеры/NBT и явный legacy reader6.
- `forge-addon/src/clientSmoke/java/dev/zuma/tacticalnpc/{ClientSmokeFixture,ClientSmokeProbe}.java`,
  `ClientSmokeConnect.java`, `forge-addon/build.gradle`, `tools/testing/run-name-client-check.py`: изолированный клиент и обычный ping/connect.
- `tools/testing/fixtures/match-api-consumer/src/main/java/probe/MatchApiConsumer.java`,
  `tools/testing/run-match-api-consumer.py`: внешний JAR consumer и реальная disk migration.
- README, COMMANDS, MATCH-NPC-API, INTEGRATION-AUDIT, миграция, этот отчёт и receipts.

## Ограничения

Старые имена без доказанного временного происхождения сохраняются. Удаление entity
из выгруженного региона откладывается до естественной загрузки; сканирования/forced
loads production-код не добавляет. Новые UUID могут консервативно отклоняться Bloom
filter; пределы насыщения и отсутствие TTL описаны в миграции. Контракт не защищает
от удаления всех доказательств владения или несогласованного отката файлов оператором.
Human PvP, TT-specific модпак и отдельный combined runtime не являются PASS этого отчёта.

## Фактически проверенные версии NBT

| Данные | Выполненное чтение / запись |
|---|---|
| tacticalnpc_names | schema1 synthetic migration + реальный .dat restart; schema2 roundtrip/save |
| tacticalnpc_matches | schema1 synthetic migration + реальный .dat restart; schema2 roundtrip/save и used-filter checksum |
| TacticalNpcEntity | reader1–6 synthetic legacy fixtures; reader6 с действительным восстановлением патруля; reader/writer7 с match/group, stack, ammo и service identity |
| Повреждённые данные | registry schema0/99, неверные list types, испорченный filter checksum, повреждённый disk file; malformed/missing UUID и true/false/wrong-type matchOwned после compaction |

Архивный пользовательский мир не изменялся; legacy fixtures моделируют поддержанные
схемы, не каждую возможную историческую комбинацию ключей. Ordinary/world fixtures
используют test-only chunk tickets; production не добавляет forced loads.

## Артефакты

Финальный main: `forge-addon/build/libs/tacticalnpcai-0.2.0.jar`,310770 bytes,
SHA256 `29CA531216D36ECCA85C540853B989E3E6274A5B5AE93FD37C13EF690E9BCFFC`.
Optional compat:7680 bytes,
SHA256 `D9C3C37DE785BACD28BDB69A57209E9CA22A5A00324875C50774562EC2890C90`.
Maven publication содержит тот же main JAR; внешний consumer не использует исходники
TacticalNPC. Нового релиза/commit/push/merge не создавалось.

## Сценарии запроса

| Сценарий | Фактическое покрытие |
|---|---|
| 1000 временных NPC | thousandTemporaryNamesReuseFreedSlots; entries/lookup/loaded и размер NBT |
| 1000 матчей | thousandMatchesStayBoundedAndIdsNeverReopen; counts, NBT, повторный ID до/после reload |
| Повторный cleanup | Каждый stress cycle, cleanupLoadedAndPreviouslyUnloadedIsIdempotent, ordinary consumer |
| Смерть | naturalDeathReleasesTemporaryButKeepsAdministratorIdentity; естественный KILLED после death ticks |
| Spawn rollback | nativeAdmissionFailureRollsBackEntitiesAndTeam; отменён реальный второй EntityJoin, names/NBT/teams неизменны |
| Chunk unload/load | NpcNameTests, API binding roundtrip, естественный remote unload/load ordinary harness и consumer |
| Disk save/restart | Оба ordinary harness, current2 и legacy1 registries, entity7 |
| Старые registry1 | schemaOneUsesProvenMembersAndPreservesUnknownNames и настоящее чтение обоих .dat при restart |
| Старые entity NBT | schemasOneThroughSixRemainManual, Team/Neutral/Mode suites, явный reader6 patrol rebuild |
| Постоянные admin NPC | NpcNameTests65, manual/team preservation, ordinary commands/restart |
| Независимость от TT | manualNpcAndForeignTeamsArePreserved, aliases, core/native independent suites; TT не на classpath |
| Нет роста записей | Проверка конечного состояния после каждого из1000 match cycles + финальный NBT; снятие всех1000 teams |
| Имена на клиенте | Два actual graphical connections,10 кадров, crosshair <6 blocks, native NBT readmission, ALLOW event и visible sheep |
| Союзность/friendly fire | hiddenGroupNamesPreserveAllianceAndForeignTeams, existing allied/native damage suites, foreign player/team preservation |

Ручная установка финального reobfuscated JAR в отдельный Minecraft launcher/модпак
не выполнялась: внешний ordinary consumer использует именно опубликованный distribution
JAR через fg.deobf в ForgeGradle userdev. Графический прогон использует source run.
