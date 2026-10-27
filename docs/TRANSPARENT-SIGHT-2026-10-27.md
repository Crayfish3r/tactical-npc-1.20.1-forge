# Разрешённые преграды восприятия и огня NPC — 2026-10-27

Локальная реализация в ветке `feature` поверх `ca8665f` (исправления пассивности).
Minecraft 1.20.1 / Forge 47.4.20 / Microsoft Java 17.0.19 / TaCZ 1.1.8-hotfix2;
optional NPCs 2.1.0. Это AI-разрешение обнаружения/огня, не расчёт пробития снаряда.
Версии, SHA256 sources/fixtures/dependencies и точные команды: [manifest](testing/transparent-sight-validation-2026-10-27.json).

## Проверенная цепочка и изменённые файлы

| Этап | Текущий маршрут проверки |
|---|---|
| Первичное обнаружение | `VisibilitySensor.acquire -> los -> NpcSightRay.trace` |
| Удержание цели, текущие position/velocity | `TacticalCombatGoal.activeLos -> sensor.los`; VISION и velocity обновляются только при CLEAR |
| Планирование DIRECT/подавления | `TacticalCombatGoal` использует текущий LOS/сохранённую точку, отдельно проверяет фактическое направление ствола |
| Финальный native round | `ShotSafetyEvents -> allowNativeFire -> nativeFireSafe`: свежие лучи к точке и по направлению оружия, затем прежний сектор защищённых тел |
| Подавление | `SuppressionSectors.sample -> sensor.los`; выбранный сектор повторно проверяется при планировании и каждом native round |
| Укрытия и выход из них | `CoverSensor.clear -> NpcSightRay.trace`, с прежними числом лучей, TTL и бюджетом |

Изменены [VisibilitySensor](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/VisibilitySensor.java),
[CoverSensor](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/CoverSensor.java) и только существующая диагностика
[TacticalCombatGoal](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java).
Добавлены [NpcSightBlocks](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/NpcSightBlocks.java),
[NpcSightRay](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/NpcSightRay.java), четыре JSON-тега и
[TransparentSightTests](../forge-addon/src/gametest/java/dev/zuma/tacticalnpc/TransparentSightTests.java).
В [FiringRegressionTests](../forge-addon/src/gametest/java/dev/zuma/tacticalnpc/FiringRegressionTests.java)
assertion фазы привязан к моменту первого настоящего projectile, а не к фиксированному позднему tick210.
`SuppressionSectors` и финальные native-разрешения получают новую политику через прежний общий вызов;
дополнительного сенсора, второго каскада лучей или нового оружейного контроллера нет.

Отдельным первым изменением уточнены [AGENTS](../AGENTS.md) и профильный
[weapon-control skill](../.agents/skills/tacz-weapon-control/SKILL.md): исключение ограничено указанными типами
и расширением собственных block tags, без самостоятельной физики пробития.
Проверенные pinned API и SHA256 зависимостей: [INTEGRATION-AUDIT](INTEGRATION-AUDIT.md).

## Начальные правила и datapack

Все теги находятся в `data/tacticalnpcai/tags/blocks/`:

| Тег мода | Значения по умолчанию |
|---|---|
| [npc_sight_transparent](../forge-addon/src/main/resources/data/tacticalnpcai/tags/blocks/npc_sight_transparent.json) | `#forge:glass`, `#forge:glass_panes`, явно `minecraft:tinted_glass`, `#minecraft:fences`, `minecraft:ladder`, `#minecraft:rails`, `#minecraft:all_signs` |
| [npc_sight_windowed_doors](../forge-addon/src/main/resources/data/tacticalnpcai/tags/blocks/npc_sight_windowed_doors.json) | Только `minecraft:oak_door` |
| [npc_sight_windowed_trapdoors](../forge-addon/src/main/resources/data/tacticalnpcai/tags/blocks/npc_sight_windowed_trapdoors.json) | Только `minecraft:oak_trapdoor` |
| [npc_sight_leaves](../forge-addon/src/main/resources/data/tacticalnpcai/tags/blocks/npc_sight_leaves.json) | `#minecraft:leaves` |

Стекло и панели всех ванильных цветов, включая тонированное стекло, покрыты начальными тегами.
Модовые варианты могут участвовать через соответствующие Forge tags или дополнение собственного тега.
Оконные двери/люки пропускаются в открытом и закрытом состоянии, для обеих половин/положений;
пиксели и отдельные отверстия не анализируются. Для остальных дверей/люков сохранена настоящая геометрия:
закрытая еловая дверь блокирует; открытый свободный проход допускается, пересечение её непрозрачной створки — нет.
Все деревянные двери не объединяются в прозрачные. Дополнительные оконные типы требуют явной записи.

Первый пересечённый блок листвы допускается, второй блокирует луч. Счётчик общий на весь запрос,
не сбрасывается воздухом и не сохраняется между запросами. Leaves tag имеет приоритет над безусловной
прозрачностью, поэтому случайное включение листвы в оба тега не отменяет этот предел.

Остальные блоки проверяются по фактической collision shape: полная каменная стена закрывает луч,
а реальный свободный промежуток над полублоком остаётся свободным. Отсутствие *полной* коллизии
не используется как признак прозрачности. Разрешённое стекло перед стеной не скрывает саму стену.
Та же классификация применяется к оценке укрытия: стекло не защищает, стена за ним может защищать.

Для расширения datapack добавляет файл с тем же путём тега, например
`data/tacticalnpcai/tags/blocks/npc_sight_transparent.json`:

```json
{"replace": false, "values": ["examplemod:window_panel"]}
```

Это пример ID, а не обязательная зависимость. Для оконной двери/люка используются отдельные теги;
для листвы — счётный leaves tag. Список пробития и теги других модов не изменяются.
Проверки луча не кэшируются между запросами: новые native rounds читают текущие блоки и текущие теги.
Существующая оценка укрытий сохраняет свои ограниченные TTL. Отдельный live datapack reload здесь не испытывался.

## Ограничение работы и диагностика

Сохранены дальность, частоты и глобальные/per-owner бюджеты. Один запрос занимает один прежний ray token.
Существующий ограниченный chunk-grid preflight проверяет доступность, включая угловые соседства;
после него выполняется один блоковый DDA с ранним выходом, без повторного полного world raycast после стекла.
Пределы посещений/чтений — алгоритмические константы в `NpcSightRay`, не параметры ServerConfig.
Даже соседние чтения из collision shape идут через bounded BlockGetter и `getChunkNow`;
неизвестный блок не превращается в разрешённый воздух — результат всего запроса становится DEFERRED.
Production-код не загружает чанки и не добавляет tickets.

В прежнем `/npc debug info <target>` к диагностике добавлен последний ray result:
`BLOCK` с координатой, `LEAF_LIMIT`, `UNLOADED`, `RANGE`, `VISIT_LIMIT`, `READ_LIMIT` или `BUDGET`.
Новых команд или постоянного подробного логирования нет.

Сохранены native cooldown/reload/bolt/ammo, реакция, FOV, turn, generation/TTL, сектор союзников/нейтралов,
pre-damage guard и границы DIRECT/suppression. Исправления WAIT, отмены recovery и критического ответного огня
не переписаны. `ai-core` и навигация не менялись. Пули, урон, разрушение и перенос через препятствия не добавлялись.

## Проверки и итоговый STAGE

- Новый FAST: **6/6 PASS**, [receipt](testing/transparent-sight-fast-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T041022.944377Z-FAST.log).
  Классификация всех 32 цветных стеклянных блоков/панелей и 20 последовательных геометрических случаев;
  прозрачное/тонированное стекло, дверь/люк, непрозрачный вариант, заборы, ladder, rails и signs;
  один/два листовых блока через воздух, стена за стеклом, прежняя геометрия полублока,
  укрытие, подавление без hidden tracking и защищённые тела.
- В native BURST тесте первое VISION и **настоящий joined projectile на tick9** получены через стекло;
  к моменту появления каменной стены создан **1 projectile**, дальнейшие rounds заблокированы при оставшихся патронах.
  Отдельно проверен реальный `allowNativeFire` при прозрачном направлении и при непрозрачной преграде на стволе.
  Test-only сброс ray budget перед изолированной проверкой направления описан в fixture; сам первый native projectile
  получен без этого сброса. Тестовые арены используют существующие конечные chunk tickets ScenarioIsolation,
  production search их не использует.
- Первый связанный standalone STAGE: **11 выполнено, 10 PASS, 1 FAIL**;
  [исходный FAIL receipt](testing/transparent-sight-stage-initial-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T041313.988409Z-STAGE.log).
  FAIL `emptyStandReloadsWithSelectedCoverBeforeFirstRound`: reload/actual round уже подтверждены предыдущим assertion,
  но прежняя проверка требовала COMBAT именно на tick210. Между первым projectile и tick210 тест не удерживает
  визуальный контакт; обычный STAND вправе закончить контакт по правилам памяти. Проверка неверно связывала
  переход WAIT с произвольным поздним моментом. Вместо ослабления немедленного перехода теперь assertion проверяет
  COMBAT на первом фактическом projectile; до него остаются строгие WAIT/no-movement assertions.
  Добавлена test-only запись смен фаз, без изменений production поведения и общих fixtures/helpers.
- Повтор проблемного сценария + полный цикл STAND: **2/2 PASS**,
  [receipt](testing/transparent-sight-stand-recovery-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T041731.870299Z-FAST.log).
  Немедленный WAIT -> COMBAT подтверждён на первом projectile на tick69; отдельно пройден обычный
  STAND COMBAT -> завершение контакта -> DEFEND32 patrol. Исходный FAIL не превращён в зелёный единый STAGE.
  Изменён только один метод теста и его локальная диагностика, не общий helper, production-код или ресурсы.
  Новые6 и остальные10 сценариев исходного STAGE сохраняют применимость и не повторялись.
- Optional NPCs: **2/2 PASS**, [receipt](testing/transparent-sight-optional-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T042042.777583Z-STAGE-compat.log).
  Общий контроллер/native gate и native BURST с появлением непрозрачной стены. Main/compat/compatTest compilation
  выполнена launcher; отсутствие compatTest resources не выдаётся за выполнение теста.

**Итоговая оценка STAGE: PASS в выбранной области — 18 уникальных standalone GameTests и 2 optional GameTests.**
Покрытие собрано из нового FAST6, применимых10 PASS исходного STAGE, исправленного STAND + связанного lifecycle FAST2,
и optional STAGE2. Это несколько запусков, не новый единый зелёный STAGE: исходный STAGE11 остаётся FAIL.
Успешные6/10 не повторялись после локальной правки отдельного тестового метода. Финальные источники/fixtures,
ресурсы и зависимости зафиксированы [manifest](testing/transparent-sight-validation-2026-10-27.json),
исходные inputs до правки тестового assertion — в [первом snapshot](testing/transparent-sight-inputs-initial-2026-10-27.json).
Core suites не перезапускались: ai-core и его контракты не менялись. Сохранение, lifecycle, загрузчик конфигурации
и build packaging не изменены; новые tag resources загружены реальным GameTestServer. Отдельный ordinary
save/restart и новый production build не выполнялись.

Фактические попадания/пробитие через стекло или другие блоки **не проверялись**: тестовый набор не содержит
дополнений пользовательской сборки на пробитие. Подтверждены AI-обнаружение, разрешение native-огня и создание
настоящих native снарядов. Пройдёт ли снаряд и нанесёт ли урон, определяют существующие моды; без нужного
дополнения попадание не гарантируется. Перед выстрелом AI не пытается определить такие дополнения.

FULL, нагрузочная матрица, JFR/soak и human playtest автоматически не запускаются.
Снижение нагрузки не измерялось и не заявляется. Production JAR не передаётся.
