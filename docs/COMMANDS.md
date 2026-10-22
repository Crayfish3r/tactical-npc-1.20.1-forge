# Команды NPC — унификация 2026-10-22

Ветка `feature`, Minecraft1.20.1 / Forge47.4.20 / Java17 / TaCZ1.1.8-hotfix2. Все пользовательские команды имеют корень `/npc` и требуют permission level2. NPCs2.1.0 нужен только для optional adapter и его проверок.

## Справочник

| Команда | Действие |
|---|---|
| `/npc help` (или `/npc`) | Краткая справка |
| `/npc spawn` | Один независимый NPC, ASSAULT, native AK-47 |
| `/npc spawn count <1..16>` | Группа без team |
| `/npc spawn team <team> [count <1..16>]` | Группа с native scoreboard team |
| `/npc list [page]` | Страницы по10 записей: имя, team, mode, доступность, измерение |
| `/npc info <target>` | Оружие, патроны, режим, радиус поведения, профиль и доступность |
| `/npc mode <target> assault` | Свободное наступление ASSAULT; радиуса поведения нет |
| `/npc mode <target> defend <4..128>` | Прежний DEFEND_AREA |
| `/npc mode <target> patrol <4..64>` | Прежний PATROL |
| `/npc mode <target> stand` | HOLD_POSITION, прежние STAND-переходы |
| `/npc team <target> <team>` | Native team и автоматическая squad-координация |
| `/npc team <target> clear` | Удалить membership; имя team `clear` задаётся в кавычках |
| `/npc equip <target> <gun-id>` | Пустой магазин, native reload, прежняя ammo policy |
| `/npc ammo <target> finite <0..1000000>` | Конечный начальный резерв для каждого выбранного NPC |
| `/npc ammo <target> infinite` | Сохранённая политика бесконечного резерва; обычная перезарядка |
| `/npc debug info <target>` | Дополнительно UUID, serviceId, STAND phase и источник наблюдения |
| `/npc debug diagnostics` | Текущая агрегированная диагностика измерения источника |
| `/npc debug legacy spawn` | Optional NPCs2.1.0 spawn |
| `/npc debug legacy equip <selector> <gun-id>` | Тот же equip для поддерживаемого optional runtime |

Для team с пробелами/кириллицей используйте кавычки. Tab дополняет разделы, режимы, имена (включая выгруженные), существующие team, selector options и загруженные TaCZ gun IDs. Permission1 не получает административные ветки.

## Цели и два разных радиуса

`<target>` в mode/info/team/equip/ammo — общий аргумент:

- Имя: один независимый NPC во всех измерениях сервера, без LOS, прицела или ограничения расстояния. Регистр не важен. Работает из консоли. Известное, но выгруженное/умершее/удалённое имя выдаёт явную ошибку; чанк не загружается, приказ не ставится в очередь.
- Положительное число1–128 (дроби разрешены): все живые загруженные независимые NPC в измерении игрока. Центр — фактическая позиция игрока, даже у `execute positioned`; расстояние — евклидово 3D до позиции NPC, граница включена. Попадания AABB в сферу недостаточно. Выбираются все team, игроки и optional NPC не включаются. Источник без игрока получает ошибку.
- Vanilla selector: административный выбор, например `@e[type=tacticalnpcai:npc,tag=alpha]`. Правила vanilla dimension/filter/permission сохраняются. Только team разрешает явно выбранных игроков, например `/npc team @s red`. Любой неподдерживаемый/мёртвый получатель отменяет весь набор до изменений. Пустой набор — ошибка. Аргументы, оружие и все цели проверяются до мутаций.

**Радиус выбора** — первое число после названия операции, например32 в `/npc mode 32 defend 16`. **Радиус поведения** —16 в этом примере, индивидуальная территория после выдачи приказа. DEFEND использует прежние XZ-проверки тела/маршрута и ограниченную высоту, PATROL — прежний маршрут. Число выбора не меняет восприятие, дальность оружия или территорию. Каждый NPC получает anchor в собственной текущей позиции. ASSAULT свободен; лишнее число после него даёт поясняющую ошибку. STAND сохраняет WAIT на anchor → COMBAT XZ6 после native shot/допустимого damage → DEFEND32 на исходном anchor. Допустимые радиусы принадлежат [OrderState](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/OrderState.java).

Примеры:

```mcfunction
/npc spawn team red count 4
/npc list
/npc mode Steve assault
/npc mode 32 defend 16
/npc mode Kevin patrol 24
/npc team 64 blue
/npc team @s blue
/npc equip Steve tacz:glock_17
/npc ammo 32 finite 90
/npc info Steve
```

## Постоянные имена и миграция

Реестр [NpcNames](../forge-addon/src/main/java/dev/zuma/tacticalnpc/command/NpcNames.java) сохраняется в overworld `data/tacticalnpc_names.dat`, общий для всего сервера. Пул из50 простых латинских имён после исчерпания продолжается Steve2, Kevin2, … Steve3. Имя отображается над NPC, при спавне, в list/info. UUID/serviceId остаются внутренними идентификаторами. NBT schema6 хранит отдельное поле `tacticalnpc.name`.

Реестр авторитетен для уже известного UUID. Старые NPC получают имя при первом успешном добавлении в загруженный мир; ради миграции выгруженные чанки не обходятся. Копия NBT с другим UUID и занятым именем получает новое; копия с тем же UUID подчиняется native запрету двойного UUID. Числовые имена и специальные NaN/Infinity запрещены. Native scoreboard membership продолжает использовать UUID, появление имени не меняет team. Имена удалённых NPC остаются закреплёнными навсегда, поэтому list включает записи `unloaded/absent`; отличить естественно выгруженного от внешне удалённого NPC по сохранённому реестру нельзя.

Временный индекс хранит UUID, измерение, ID экземпляра и WeakReference, не удерживающий Entity. Присвоение выполняется в `onAddedToWorld` после native admission, отменённый spawn не резервирует имя. Выгрузка/удаление убирают только текущий экземпляр из индекса; краткая потеря native tracking без удаления не уничтожает слабый handle; restart и перенос измерения восстанавливают связь. NBT-декодирование не трогает мир или реестр с deserializer thread. Реестр прогревается на ServerStarted до обычных тиков, включая миры без NPC.

## Постоянный Hard

[ServerConfig](../forge-addon/src/main/java/dev/zuma/tacticalnpc/config/ServerConfig.java) владеет единственным набором Hard defaults. Load/reload явно нормализует прежний difficultyProfile=UNFAIR и сохранённые боевые overrides; сообщение `Migrated legacy NPC difficulty/combat overrides to HARD` объясняет миграцию. Команды переключения удалены. `enabled`, `debug`, `telemetry` и NBT ammo policy независимы; команды NPC не вызывают нормализацию конфига. UNFAIR hints не предоставляются, сохранённая FINITE-политика снова проверяет native reserve; INFINITE остаётся INFINITE, если такова записанная политика. Нативные magazine/chamber/bolt/cooldown не обходятся. Активные контроллеры при миграции отменяют reload и очищают hints/решения.

Числовые эксперименты выполняются в test source sets. Выбранный GameTest launcher по-прежнему проходит начальный Loading, но его последующие fixture autosave/Reloading не сбрасывают эксперимент; production JAR не включает fixtures. Старый ключ difficulty остаётся только для чтения/миграции, `unfair()` всегда false.

## Таблица перехода

| Старая команда | Новая |
|---|---|
| `/spawn npc …`, `/tacticalnpc spawn …` | `/npc spawn …` |
| `/npc mode attack [targets]` | `/npc mode <target> assault` |
| `/npc mode defend <radius> [targets]` | `/npc mode <target> defend <radius>` |
| `/npc mode stand [targets]` | `/npc mode <target> stand` |
| `/tacticalnpc order <npc> ASSAULT <radius>` | `/npc mode <target> assault` (старое лишнее radius удалено) |
| `/tacticalnpc order <npc> DEFEND_AREA <radius>` | `/npc mode <target> defend <radius>` |
| `/tacticalnpc order <npc> PATROL <radius>` | `/npc mode <target> patrol <radius>` |
| `/tacticalnpc order <npc> HOLD_POSITION <radius>` | `/npc mode <target> stand` |
| `/npc team <team|clear> [targets]` | `/npc team <target> <team|clear>` |
| `/tacticalnpc faction <npc> <team>` | `/npc team <target> <team>` |
| `/tacticalnpc squad <targets> <UUID|clear>` | `/npc team <target> <UUID|clear>`; squad автоматический |
| `/npc info [targets]` | `/npc info <target>` |
| `/tacticalnpc equip <npc> <gun>` | `/npc equip <target> <gun>`; optional runtime — `/npc debug legacy equip …` |
| `/tacticalnpc ammo <npc> …` | `/npc ammo <target> …` |
| `/tacticalnpc spawnlegacy` | `/npc debug legacy spawn` |
| `/tacticalnpc diagnostics` | `/npc debug diagnostics` |
| `/tacticalnpc hard`, `/tacticalnpc unfair` | Удалены; Hard применяется при load/reload |

Старые root/alias/order/faction/squad регистрации отсутствуют. UUID больше не нужен для повседневного управления; для административной фильтрации доступны vanilla selectors. Датированные отчёты и receipts сохраняют исторические строки команд и не являются текущим справочником.

Результаты выполненных проверок и ограничения: [отчёт](COMMAND-UNIFICATION-2026-10-22.md).
