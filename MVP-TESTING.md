# Проверка Tactical NPC AI 0.2.0

Новое обновление и актуальные ручные действия: [team/короткие команды](docs/TEAM-COMMANDS.md). Для боя двух групп достаточно `/spawn npc team red count 4` и, в20–30 блоках, `/spawn npc team blue count 4`. Старые цифры/JAR ниже относятся к пакету08–14 до этого обновления; не использовать прежний SHA как проверку нового файла.

Текущий пакет 08–14 и отдельные ручные сценарии: [BATCH-08-14](docs/BATCH-08-14.md).
07 явно принят владельцем; 08–14 нельзя считать ACCEPTED по автоматическим проверкам.
Ниже исторические таблицы артефактов/метрик помечены датой; production handoff пакета08–14
и его результаты FULL/profile зафиксированы в [отчёте 14](docs/STAGE-14-ACCEPTANCE.md).

Handoff пакета08–14 до team-обновления, 2026-10-20: core792, FULL standalone98/98 и combined110/110, affected14/14+25/25 PASS; plain production build/verifier и ordinary dedicated reload/save/restart/stop PASS. [Профиль84+84/сопоставимые данные того среза](docs/testing/stage14-profile-2026-10-20.md): общего ускорения не доказано, AI p95≤2ms на30/50 в бою не достигнут; сниженное число rounds крупных групп не засчитано как улучшение. Human1v1/1v4 и client distribution playtest — NOT RUN, WAITING_PLAYTEST.

| Production JAR пакета08–14 до team-обновления (история) | Bytes/classes | SHA-256 |
|---|---:|---|
| `forge-addon/build/libs/tacticalnpcai-0.2.0.jar` | 191013/92 | `70ACF74FBA0C0E1792B94A82936AAD8ED982F89C86AABBA87B8302EEF3A533A0` |
| `forge-addon/build/libs/tacticalnpcai-0.2.0-npcs-compat.jar` | 7680/4 | `5DEEBFC0E9FAF3347EDBE42CDA4CA2C72D775CD63DFB7579E16DD7EE4F138DE1` |

[Исторические проверки/команды/SHA](docs/testing/stage14-validation-2026-10-20.json). Актуальный handoff team-обновления указан в отдельном отчёте выше; optional compat используется вместе с NPCs2.1.0. Ниже старая таблица сохранена как история.

## Версии и файлы

Закреплены Minecraft Java **1.20.1**, Forge **47.4.20**, Java **17** (проверено Microsoft OpenJDK 17.0.19), TaCZ **1.1.8-hotfix2**. Gradle 8.8 / ForgeGradle 6.0.54. Версии не обновлялись попутно с ИИ. SHA-256 оригинальных зависимостей и ссылки — [INTEGRATION-AUDIT](docs/INTEGRATION-AUDIT.md).

Самостоятельная установка, на **клиент и сервер** в их папку `mods/`:

- `tacz-1.20.1-1.1.8-hotfix2.jar` из `forge-addon/libs/`.
- `tacticalnpcai-0.2.0.jar` из `forge-addon/build/libs/` — production build выполнен.

Для сравнения с оригинальными NPC дополнительно установите на обе стороны `tacznpcs-2.1.0-1.20.1.jar` и `tacticalnpcai-0.2.0-npcs-compat.jar`. **Собственный NPC не требует NPCs**. Старый аддон 0.1.0 одновременно не устанавливать. Не используйте mapped_official JAR из Gradle cache в игровом профиле. Вложенные библиотеки официального TaCZ отдельно не дублировать. Зависимости и чужие ресурсы в наш JAR не включены.

Это экспериментальный MVP. Факт существования JAR проверяется отдельно от боя; текущие фактические результаты и логи приведены в [IMPLEMENTATION-STATUS](docs/IMPLEMENTATION-STATUS.md). Успешная сборка сама по себе не означает завершённый PvP-релиз.

Исторические артефакты сборки 2026-10-11 (не текущий handoff пакета 08–14):

| JAR | Размер | SHA-256 |
|---|---|---|
| `tacticalnpcai-0.2.0.jar` | 131175 bytes | `F8A088041825B39BA4F17E39F7CE1B6F4B34A69267674A4BF1920405AD3E8E1E` |
| `tacticalnpcai-0.2.0-npcs-compat.jar` | 7680 bytes | `91622634C43F488462B65F9381CD94FCD7080F1F32B929266B23571C5F1FDD64` |

[Проверка содержимого](docs/testing/stage7-production-artifacts-2026-10-11.json): Java 17, 67 основных/4 compatibility classes; в основном JAR нет NPCs bytecode references, GameTests, client/profile fixtures, тестовых арен или вложенных JAR зависимостей. В чистом core нет игровых ссылок. Оба JAR reobfuscated. Запуски сервера/клиента ниже выполнялись через ForgeGradle userdev; установка этих distribution JAR в отдельный пользовательский launcher/modpack остаётся ручной проверкой.

## Создание и экипировка

Нужны cheats/OP level 2, свободная ровная площадка. Создание в creative:

```mcfunction
/gamemode creative
/tacticalnpc spawn
```

Безопасно рядом с источником появляется собственный активный `tacticalnpcai:npc` в ATTACK: vanilla renderer, пустой AK-47, тег `tactical_ai`; `/spawn npc` использует тот же путь. Без team он враг остальным NPC и survival/adventure игрокам. Для готовых групп используйте `/spawn npc team red count 4`; team автоматически определяет союзников и squad-координацию. Native reload работает без цели. Без тега NPC не получает тактического контроллера. Низкоуровневый summon также доступен; затем выдайте оружие:

```mcfunction
/summon tacticalnpcai:npc ~3 ~ ~ {PersistenceRequired:1b}
/tacticalnpc equip @e[type=tacticalnpcai:npc,sort=nearest,limit=1] tacz:glock_17
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add tactical_ai
```

Проверяемые обычные образцы: `tacz:ak47`, `tacz:glock_17`, `tacz:aa12`, `tacz:ai_awp`, `tacz:uzi`, `tacz:m249`. Конкретные результаты native firing matrix смотрите в статусе, а не считайте перечень обещанием поддержки любого gunpack. M870 с неизвестным shooting script блокируется; для первого теста дробовика используйте AA-12.

```mcfunction
/tacticalnpc ammo @e[type=tacticalnpcai:npc,sort=nearest,limit=1] finite 90
/tacticalnpc ammo @e[type=tacticalnpcai:npc,sort=nearest,limit=1] infinite
```

По умолчанию резерв бесконечный, магазин расходуется и перезаряжается штатно. FINITE хранит native DummyAmmo: расходуется реальное конечное число патронов, после исчерпания повторной перезарядки нет. Смена ammo policy явно отменяет текущую reload и инвалидирует старые команды. Выдача оружия командой задаёт пустой магазин; смена loadout в FINITE задаёт настроенный начальный резерв для нового оружия. Подбор патронов с земли и полноценный инвентарь не реализованы.

## Приказы и team

```mcfunction
/tacticalnpc order @e[type=tacticalnpcai:npc,sort=nearest,limit=1] HOLD_POSITION 24
/tacticalnpc order @e[type=tacticalnpcai:npc,sort=nearest,limit=1] DEFEND_AREA 16
/tacticalnpc order @e[type=tacticalnpcai:npc,sort=nearest,limit=1] ASSAULT 24
/tacticalnpc order @e[type=tacticalnpcai:npc,sort=nearest,limit=1] PATROL 16
/tacticalnpc faction @e[type=tacticalnpcai:npc,sort=nearest,limit=1] red
/npc team red
/npc team red @s
/npc team clear
/npc info
```

Якорь задаётся текущей позицией NPC при выдаче приказа. STAND/HOLD_POSITION фиксирует WAIT без движения → COMBAT XZ6 после реального native shot/прошедшего допустимого damage → обычный DEFEND32 после окончания контакта с исходным anchor. Старый HOLD radius не заменяет фиксированные6/32. DEFEND_AREA/`npc mode defend` принимает4–128, ограничивает все добровольные сегменты/тело по XZ и возвращает к якорю; PATROL — отдельный прежний маршрут. ASSAULT/attack свободно ищет легитимно известную цель. Остальные старые radius аргументы остаются4–64. Одна native team — союзники; без team NPC не союзны друг другу. Сохранённый legacy NEUTRAL отвечает только личному допустимому attacker. Radius территории не увеличивает detection/weapon range и не загружает дальние chunks.

Приказы, якорь, standPhase, loadout, ammo policy и serviceId сохраняются в NBT schema5; native team — в штатном scoreboard.dat. Schema1–4 явно мигрируют, архивируя прежние faction/squad UUID; существующая vanilla team имеет приоритет. Живые цели, память и пути не сохраняются. Неизвестная будущая schema отключает тактическое управление с диагностикой.

## Автоматическая координация team

```mcfunction
/spawn npc team red count 4
/npc team red @e[type=tacticalnpcai:npc,distance=..30]
```

Одинаковая native team автоматически образует группу; отдельный squad UUID назначать не нужно. Старые faction/squad команды сохранены как алиасы назначения team: squad UUID становится её именем, clear удаляет принадлежность. В пределах одного измерения передаются только реальные зрительные наблюдения с задержкой 10 тиков и радиусом связи 48 блоков. Исходный tick и известная позиция сохраняются, report TTL ≤80, confidence уменьшается, uncertainty растёт. Нет доступа к скрытой сущности через squad. Локальные роли и ограниченный огневой контракт сохранены. Cover claims имеют TTL80 и освобождаются при stop/death/unload/смене цели или team.

## Включение и сравнение исходного ИИ

```mcfunction
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] remove tactical_ai
/tag @e[type=tacticalnpcai:npc,sort=nearest,limit=1] add tactical_ai
```

Снятие тега с собственного NPC освобождает тактическое движение/aim. Для исходного NPC при установленном compatibility artifact:

```mcfunction
/tacticalnpc spawnlegacy
/tag @e[type=tacznpcs:npc,sort=nearest,limit=1] remove tactical_ai
/tag @e[type=tacznpcs:npc,sort=nearest,limit=1] add tactical_ai
```

В legacy runtime сохраняются внешний вид, экипировка и native обслуживание оружия. Оба исходных goal selectors захватываются на время нового ИИ; vanilla target остаётся null, чтобы не конкурировать с дополнительной логикой AutoGunOperator. При снятии тега/ошибке/выгрузке восстанавливаются прежние goals и LookControl. Исходный NPC без тега служит сравнением. Его бесконечный резерв сохранён; команды finite/orders рассчитаны на собственный runtime.

## Настройки и честность

Файл `<мир>/serverconfig/tacticalnpcai-server.toml`; в одиночной игре внутри saves. После изменения перезапустите тестовый мир. Старые world config сохраняют старые значения: memoryTicks нового default 160 не заменяет автоматически ранее записанные 120.

| Настройка | Default |
|---|---|
| enabled / debug | true / false |
| detectionRange / memoryRange / hearingRange | 40 / 64 / 40 блоков |
| activationRange | 64; вне него без памяти AI обновляется раз в 10 тиков |
| horizontalFov | 140° |
| perceptionTicks / reactionTicks | 4 / 6 тиков; scan phase добавляет ожидание |
| memoryTicks | 160 |
| turnRate / aimError | 10° за тик / 1° плюс native spread |
| strafeSpeed | 0.55 |
| suppression | true |
| telemetry | false; агрегат раз в 200 тиков, без per-tick файлового I/O |

Позиция и скорость скрытого игрока не доступны планировщику. Зрение даёт свежие samples, слух — квантованную область с uncertainty ≥8 блоков. Урон сам по себе не раскрывает стрелка. После LoS loss память замораживает точку, увеличивает uncertainty и истекает. DIRECT требует свежую видимость; SUPPRESS_AREA требует отдельный permit: возраст ≤40 тиков, confidence ≥0.35, uncertainty ≤12, максимум 6 native rounds на непрерывный скрытый контакт и паузы ≥10 тиков. Направление не следует за новым скрытым положением. Стена до выбранной известной точки запрещает выстрел.

Activation query возвращает только boolean присутствия игрока, без Observation/координат для планировщика. Она кешируется до 20 тиков и не уменьшает sight/hearing range; при появлении игрока может добавлять честную задержку пробуждения. Native TaCZ tick продолжает работать каждый тик, в том числе во время редких AI updates. Тяжёлое оружие сохраняет штатные штрафы веса/reload; при перемещении к укрытию/отходе/сближении ADS выключается, без увеличения vanilla attributes.

Перед каждым native round проверяются геометрия, свойства оружия и занятый сектор с учётом разброса/дроби/продолжения за целью до 128 блоков. Это консервативное ограничение, а не математическая гарантия любой случайной траектории; союзный kinetic damage дополнительно отменяется TaCZ pre-damage event для tagged projectile. Насыщенный запрос сущностей запрещает огонь. Charge, inventory-fed, heat, RPG, explosive/incendiary свойства, неизвестные shooting/property scripts блокируются; audited xmag reload проверяется по SHA-256 при загрузке ресурсов. Намеренный wallbang отсутствует.

Если TaCZ не может разрешить владельца нашей уже летящей пули после unload, весь её kinetic damage блокируется: определить союзника безопасно уже нельзя. LookControl ограничивает итоговый угол головы/прицела; стандартный body controller сохраняет движение тела, но не получает второго права скачком менять серверный прицел. В legacy runtime оба исходных контроллера восстанавливаются при освобождении управления.

## Сценарий ручной перестрелки

1. Арена ≥40×40, ровный пол, сплошные стенки высотой ≥3, несколько широких укрытий. Создайте одного NPC в creative, задайте ASSAULT или DEFEND_AREA и выбранное оружие.
2. Отойдите на 15–25 блоков, включите survival. Проверьте задержку обнаружения/реакции, ограниченный поворот, реальные звуки/пули/урон и боковое движение. Cooldown и разброс TaCZ сохраняются.
3. При исчерпании магазина NPC ищет локально проверенное укрытие; уже начатая native reload не отменяется каждый тик. В укрытии он ждёт, выходит на checked peek и меняет позицию. Если подходящего места нет, полноценная cover-тактика не гарантируется.
4. Спрячьтесь за стеной и переместитесь вдоль неё. NPC помнит прежнюю область; допускается краткое ограниченное подавление открытой прежней точки/проёма. Через сплошную стену новые выстрелы запрещены. Подождите истечения памяти: огонь/поиск старой области прекращается.
5. Проверьте FINITE 7, затем исчерпание; AWP bolt; AA-12 pellets; UZI/shotgun на 40 блоках должны сокращать дистанцию вместо одинакового дальнего огня. Sniper предпочитает дальнее положение, но его detectionRange остаётся отдельным ограничением.
6. Поставьте союзника на линии, за целью и поперёк сектора. Проверьте запрет native огня и отсутствие союзного урона. Различайте уже летящие пули и новые выстрелы.
7. Смерть/creative/spectator/смена измерения игрока должны прекращать атаку. Переключите тег несколько раз; сравните legacy NPC без тега. Двойных владельцев оружия быть не должно.
8. Сохраните/перезапустите мир, выгрузите/загрузите чанк, перенесите NPC в другое измерение, убейте его. Проверьте loadout/reserve/order/serviceId и отсутствие старого runtime/цели.
9. Отдельно проверьте двери, ступени, полублоки, ямы, воду/лаву и границы чанков. По умолчанию NPC не ломает блоки и не загружает чанки ради пути.
10. После 1v1 повторите 1v4 с человеком. Автоматический FakePlayer smoke не доказывает баланс, контригру, человеческий TTK или полноценную синхронизацию всех анимаций.

## Фактические проверки и остающиеся риски

Полная таблица запущенных команд, логи и состояние этапов — [IMPLEMENTATION-STATUS](docs/IMPLEMENTATION-STATUS.md), контракты — [TEST_PLAN](docs/TEST_PLAN.md). Там отдельно фиксируются Java PASS, Forge GameTest, обычный dedicated startup, клиент, persistence и профиль; Gradle test имеет NO-SOURCE.

Последний реальный клиентский smoke без NPCs: **rendered=691 armed=true loaded=true noNpcs=true**, [лог](docs/testing/stage7-client-smoke-2026-10-11.log). Новый NPC отрисован, оружие и загруженный после server reload магазин получены клиентом. Первая попытка соединения не завершилась; повтор localhost прошёл без изменения игрового кода. Причина первого прерывания не установлена, [неуспешный лог](docs/testing/stage7-first-client-connect-2026-10-11.log) сохранён.

Историческая проверка 2026-10-11: ordinary server startup и настоящий disk save/restart сохранили entity UUID, serviceId, schema2, DEFEND_AREA/anchor/radius 16, faction blue, squad UUID, FINITE 17, magazine 29 и chamber=true. До/после native data compounds совпали: [проверка](docs/testing/stage7-restart-verification-2026-10-11.json), [save log](docs/testing/stage7-dedicated-client-save-2026-10-11.log), [restart log](docs/testing/stage7-dedicated-restart-2026-10-11.log). Ordinary dedicated профиль отдельно выгрузил и загрузил 30 NPC из чанков. Исторические проверки этапа 2 сохранены, но не подменяют эти новые результаты.

Ручные человеческие дуэли, баланс, полная матрица сложного рельефа/сторонних gunpacks и длительный массовый прогон пока не подтверждены. Ballistic lead, сложные обходы, новые модели/анимации, редактор/UI и inventory pickup не реализованы. Слух не моделирует точно затухание глушителей. Небольшие арены с большим числом союзников могут консервативно блокировать большую часть стрельбы. В коротких измеренных combat-фазах достигнуто около 20 TPS; финальный AI p95 30/50 NPC — 4.7520/4.0663 ms, **ориентир 2 ms не достигнут**. [Методика, оборудование и все данные](docs/PROFILE-REPORT-2026-10-11.md). Short soak длится 120 секунд; отсутствие утечек или релизная устойчивость этим не доказаны.

Для диагностики debug=true даёт target acquired/lost, state, shot SUCCESS, reload started и gates; в legacy — suspended/restored. Подробные per-shot логи выключайте перед профилем; телеметрия и исходные метрики описываются отдельно. Исключение контроллера освобождает управление, неподдержанное оружие блокируется с причиной. Совместимость с другими модами, меняющими goals/weapon hooks, требует отдельного теста.

## Запущенные команды и результаты, 2026-10-11

Gradle-команды запускались из forge-addon, без обновления зависимостей. Java-скрипт — из корня через Git Bash с `/usr/bin` в PATH.

| Команда | Фактический результат |
|---|---|
| `bash ai-core/run-tests.sh` | PASS 72 policy checks. |
| `gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility` | После найденного head snap: All 34 required tests passed, BUILD SUCCESSFUL. [Лог](docs/testing/stage7-final-combined-gametest-2026-10-11.log). |
| `gradlew.bat runGameTestServer --offline -PcombatTests` | All 25 required tests passed, BUILD SUCCESSFUL, без NPCs; также проверен добавленный после combined orphan-damage guard. [Лог](docs/testing/stage7-final-standalone-gametest-2026-10-11.log). |
| `gradlew.bat runServer --offline -Pprofile` | 15 обычных dedicated-фаз, 1/4/16/30/50, dynamic, искусственный low TPS, unload/load и short soak; BUILD SUCCESSFUL. Выполнено до последнего head/body исправления. |
| `gradlew.bat runServer --offline -Pprofile -PprofileLegacy -PlegacyCompatibility` | Корректный повтор: 10 baseline/combat фаз исходного NPCs, native projectiles; BUILD SUCCESSFUL. Первый no-fire стенд признан невалидным и сохранён. |
| `gradlew.bat runServer --offline -Pprofile -PprofileQuick` | Финальный standalone контроль 30/50, native projectiles, около 20 TPS; BUILD SUCCESSFUL. |
| `gradlew.bat runServer --offline -PclientSmoke` | Два настоящих старта без NPCs, подключение клиента, save-all flush/stop и проверка после process restart; оба сервера штатно остановлены, BUILD SUCCESSFUL. |
| `gradlew.bat runClient --offline -PclientSmoke` | Первая попытка соединения не прошла; повтор PASS renderer/armed/loaded/noNpcs, BUILD SUCCESSFUL, клиент сам завершён после 200 игровых ticks. |
| `gradlew.bat build --offline` | BUILD SUCCESSFUL, reobfJar и reobfCompatJar выполнены; Gradle test=NO-SOURCE, это не отдельный PASS боевых тестов. [Лог](docs/testing/stage7-production-build-2026-10-11.log). |
| `gradlew.bat runServer --offline` | Последний обычный запуск без любых тестовых флагов: Done, штатной командой создан independent NPC с пустым AK-47; native reload без цели дал magazine=29/chamber=true. Test NPC удалён, сервер штатно остановлен, BUILD SUCCESSFUL. [Лог](docs/testing/stage7-production-startup-2026-10-11.log). |
| `python tools/verify-production-artifacts.py` | PASS состава обоих production JAR и архитектурных bytecode boundaries. |
| `python tools/generate-test-arena.py` | Перегенерированы собственные test-only arena/wide_arena; сторонние ресурсы не копировались. |

Ручными остаются установка distribution JAR в отдельный launcher, бой с человеком 1v1/1v4, reconnect в игровом сценарии, полный terrain/weapon/attachment/gunpack matrix, порталы с клиентом, баланс и длительная нагрузка. Автоматические FakePlayer, lifecycle GameTests, клиентский renderer smoke и короткий профиль не заменяют эти проверки.
