# Tactical NPC AI — экспериментальный MVP 0.2.0

Собственный серверный NPC для **Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2**. `[TACZ] NPCs 2.1.0` используется отдельным optional compatibility artifact для сравнения; самостоятельному NPC этот мод не нужен. Зависимости закреплены, сторонние ресурсы в аддон не включаются.

Реализованы native TaCZ shoot/reload/bolt и finite/infinite reserve, ограниченные восприятие/поворот/реакция, память и приблизительный слух, локальные укрытия и выглядывание, ограниченное подавление известной области, приказы/оружейные профили и небольшие squads. Выполнены автоматизированные 1v4 и ordinary dedicated профиль до 50 NPC; это экспериментальная проверка, не человеческий PvP-релиз. Точное состояние срезов и результаты: [IMPLEMENTATION-STATUS](docs/IMPLEMENTATION-STATUS.md). Историческое архитектурное ревью описывает 0.1.0, а не текущую реализацию.

Установка и ручной сценарий: [MVP-TESTING.md](MVP-TESTING.md). Собран основной `forge-addon/build/libs/tacticalnpcai-0.2.0.jar`; дополнительный `tacticalnpcai-0.2.0-npcs-compat.jar` нужен только вместе с NPCs 2.1.0. Production bytecode/package verification PASS; тестовые классы и арены не включены. Клиенту и серверу нужны одинаковые основные моды. Renderer использует vanilla модель и текстуру; новые модели/анимации не создавались.

```mcfunction
/gamemode creative
/npc spawn team red count 4
# Перейти на 20–30 блоков, повернуться к первой группе и создать противников:
/npc spawn team blue count 4
```

`/npc spawn` создаёт активного независимого NPC с native AK-47 и постоянным именем (Steve, Kevin, John и далее с суффиксами). `/npc team Steve red` назначает команду, `/npc team @s red` присоединяет игрока, `/npc team Steve clear` убирает принадлежность. `/npc mode 32 assault` отдаёт приказ всем живым загруженным независимым NPC в32 блоках от игрока; `/npc mode Kevin defend 16` задаёт оборону конкретного NPC без прицела и LOS. `/npc list [page]`, `/npc info Kevin` и `/npc help` показывают состояние и справку. [Полный синтаксис и миграция](docs/COMMANDS.md).

Creative/spectator не атакуются. Стены, защищённые тела и незавершённая safety-проверка запрещают новый native round; союзный pre-damage guard защищает и от уже летящих bullets. Неизвестные shooting scripts, charge, inventory-fed, heat, explosive/incendiary свойства заблокированы.

Имена TacticalNPC скрыты над головами, но доступны через команды, `npcName()` и NBT.
Временные match-owned имена и завершённые матчи очищаются; одноразовые ID защищены
ограниченным постоянным фильтром. [Совместимость старых миров и ограничения](docs/SAVED-DATA-MIGRATION.md),
[проверки lifecycle и размеров SavedData](docs/SAVED-DATA-VALIDATION-2026-10-24.md).

Пакет 08–14: [постоянный план и отдельные отчёты](docs/BATCH-08-14.md). Владелец принял 07 и разрешил последовательную работу, но автоматический PASS не является приёмкой владельца. STAND/HOLD_POSITION теперь WAIT на anchor → COMBAT XZ6 после реального native shot/damage → обычный DEFEND32 после окончания контакта. DEFEND принимает радиусы 4–128; PATROL остаётся отдельным. Приказы имеют синтаксис `/npc mode <имя|радиус|selector> assault|defend <4..128>|patrol <4..64>|stand`; permission2.

Handoff пакета08–14 до team-обновления, 2026-10-20: core792, FULL98/98+110/110 и affected14/14+25/25 PASS; production main/compat и ordinary restart. [Исторические SHA/ручные действия](docs/STAGE-14-ACCEPTANCE.md), [ordinary профиль того среза](docs/testing/stage14-profile-2026-10-20.md). Общего ускорения сервера не доказано; для30/50 combat AI p95>2ms, часть больших сцен стреляет реже baseline и не получает performance credit. Human1v1/1v4 и приёмка08–14 — WAITING_PLAYTEST. Актуальные проверки/JAR унификации команд — в [отчёте](docs/COMMAND-UNIFICATION-2026-10-22.md).

```bash
bash ai-core/run-tests.sh
```

```powershell
cd forge-addon
.\gradlew.bat build --offline
.\gradlew.bat runGameTestServer --offline -PcombatTests
.\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility
.\gradlew.bat runServer --offline
```

Обычная сборка исключает GameTests, клиентский smoke и тестовый таймер. Gradle `test = NO-SOURCE` не заменяет отдельный Java-скрипт и Forge GameTests. Уже выполнялись подключение графического клиента без NPCs, реальный save/restart и chunk unload/load; это автоматизированные проверки, не человеческая дуэль. Релизные 1v1/1v4, баланс и длительная нагрузка требуют отдельных доказательств.

Последняя регрессия alpha-1 (2026-10-12): Java **72 policy + 94 temporal checks PASS**, Forge **38/38 combined** и **29/29 standalone**, production rebuild и обычный dedicated startup/stop PASS. [Alpha-1 release notes](docs/ALPHA-1-RELEASE-NOTES-2026-10-11.md) описывают emergency priority, bounded route recovery, reacquire, свежие aim/readiness и Hard. Сравнение с player sprint, человеческий баланс и новый массовый perf-прогон остаются открытыми; вся alpha готовой не объявляется. [Dedicated профиль 2026-10-11](docs/PROFILE-REPORT-2026-10-11.md) относится к предыдущему срезу и не подтверждает новый Hard.

## Сборка из исходников

Нужны Git и JDK 17. Gradle 8.8 запускается через включённый wrapper. Первые загрузки требуют сети; `--offline` используйте после заполнения локального Gradle cache.

```powershell
git clone https://github.com/Crayfish3r/tactical-npc-1.20.1-forge.git
cd tactical-npc-1.20.1-forge/forge-addon
.\fetch-dependencies.ps1
.\gradlew.bat build
```

`fetch-dependencies.ps1` загружает ровно TaCZ 1.1.8-hotfix2 и NPCs 2.1.0 и проверяет SHA-256. NPCs нужен для сборки optional adapter, самостоятельный runtime от него не зависит. Сторонние mod JAR, декомпилированные исходники, сборки, тестовые миры, резервные копии, raw logs и JFR в Git не включаются. Собственные NBT GameTest fixtures и Gradle wrapper входят в репозиторий. Сводные результаты сохранены в `docs/testing/`; ссылки исторических отчётов на raw logs относятся к локальным материалам испытаний.

Архитектура: perception → immutable snapshot → decision → action arbitration → Forge/TaCZ adapter. Ядро `ai-core/` не импортирует игровые API; сущностями управляет server thread. `src/compat/` изолирует оригинальные goals, фракции и AutoGunOperator. Подробности подтверждённых API: [аудит](docs/INTEGRATION-AUDIT.md), контракты испытаний: [TEST_PLAN](docs/TEST_PLAN.md).

Серверная интеграция Battle Royale: [Match NPC API v1](docs/MATCH-NPC-API.md) —
типизированный транзакционный spawn, индивидуальные loadouts, внешняя fail-closed
политика целей, очистка матчей и orphan NPC после рестарта. Основной JAR публикуется
в Maven repository для `fg.deobf`; зависимости от TacticalTablet нет.

Новый код — MIT. Применимые лицензии зависимостей и распространение модпака рассматриваются отдельно; чужие JAR не вложены в артефакты.

## Постоянный Hard

Обычная игра всегда использует Hard. При load/reload старые UNFAIR и сохранённые боевые overrides мигрируют к единому набору defaults в [ServerConfig](forge-addon/src/main/java/dev/zuma/tacticalnpc/config/ServerConfig.java); в логе появляется сообщение миграции. Диагностика и сохранённая ammo policy каждого NPC остаются самостоятельными. Переключающих профиль команд нет. Скрытые hints и профильный infinite-reserve override отключены; native reload/cooldown и защита союзников сохранены. Изменение чисел для автоматических экспериментов выполняется внутри тестовых fixtures.

Давление: `suppressionDurationTicks=160`, `suppressionRoundBudget=48`,
`suppressionBurstRounds=8`, `suppressionBurstPauseTicks=12`. Проверяются доступные сектора
вокруг известного контакта; hidden report не пополняет бюджет. Native team объединяет
прикрывающего, подавляющих и обходящего. Упреждение использует подтверждённую визуальную
скорость и проверенную dry-air баллистику; damage falloff не запрещает дальний выстрел.

Поиск не загружает чанки принудительно. Сервер должен уже тикать чанк NPC: для встреч на128
проверьте simulation distance и границы чанков подключённым клиентом. Автотесты dedicated
используют явные fixture tickets, отсутствующие в production.

[Реализация, before/after, артефакты и отдельный человеческий плейтест](docs/COMBAT-UPGRADE-2026-10-22.md).
