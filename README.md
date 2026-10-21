# Tactical NPC AI — экспериментальный MVP 0.2.0

Собственный серверный NPC для **Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2**. `[TACZ] NPCs 2.1.0` используется отдельным optional compatibility artifact для сравнения; самостоятельному NPC этот мод не нужен. Зависимости закреплены, сторонние ресурсы в аддон не включаются.

Реализованы native TaCZ shoot/reload/bolt и finite/infinite reserve, ограниченные восприятие/поворот/реакция, память и приблизительный слух, локальные укрытия и выглядывание, ограниченное подавление известной области, приказы/оружейные профили и небольшие squads. Выполнены автоматизированные 1v4 и ordinary dedicated профиль до 50 NPC; это экспериментальная проверка, не человеческий PvP-релиз. Точное состояние срезов и результаты: [IMPLEMENTATION-STATUS](docs/IMPLEMENTATION-STATUS.md). Историческое архитектурное ревью описывает 0.1.0, а не текущую реализацию.

Установка и ручной сценарий: [MVP-TESTING.md](MVP-TESTING.md). Собран основной `forge-addon/build/libs/tacticalnpcai-0.2.0.jar`; дополнительный `tacticalnpcai-0.2.0-npcs-compat.jar` нужен только вместе с NPCs 2.1.0. Production bytecode/package verification PASS; тестовые классы и арены не включены. Клиенту и серверу нужны одинаковые основные моды. Renderer использует vanilla модель и текстуру; новые модели/анимации не создавались.

```mcfunction
/gamemode creative
/spawn npc team red count 4
# Перейти на 20–30 блоков, повернуться к первой группе и создать противников:
/spawn npc team blue count 4
```

`/spawn npc` и `/tacticalnpc spawn` безопасно создают активного независимого NPC с native AK-47. Без team он враждебен другим NPC, включая таких же одиночек; survival/adventure игроки — противники кроме союзников. `/spawn npc team red count 4` сразу создаёт вооружённую группу; одна native Minecraft team определяет союз и автоматически включает прежнюю squad-координацию. `/npc team red` назначает NPC в прицеле, `/npc team red @s` присоединяет игрока, `/npc team clear` убирает принадлежность, `/npc info` показывает режим и патроны. Имена с пробелами/кириллицей заключайте в кавычки. Старые сохранённые режимы, включая NEUTRAL, сохраняются. [Правила, миграция и ручная приёмка обновления](docs/TEAM-COMMANDS.md).

Creative/spectator не атакуются. Стены, защищённые тела и незавершённая safety-проверка запрещают новый native round; союзный pre-damage guard защищает и от уже летящих bullets. Неизвестные shooting scripts, charge, inventory-fed, heat, explosive/incendiary свойства заблокированы.

Пакет 08–14: [постоянный план и отдельные отчёты](docs/BATCH-08-14.md). Владелец принял 07 и разрешил последовательную работу, но автоматический PASS не является приёмкой владельца. STAND/HOLD_POSITION теперь WAIT на anchor → COMBAT XZ6 после реального native shot/damage → обычный DEFEND32 после окончания контакта. DEFEND принимает радиусы 4–128; PATROL остаётся отдельным. У `/npc mode attack|defend <radius>|stand [targets]` без targets выбирается NPC в прицеле до 32 блоков при LOS; консоль требует targets; permission 2.

Handoff пакета08–14 до team-обновления, 2026-10-20: core792, FULL98/98+110/110 и affected14/14+25/25 PASS; production main/compat и ordinary restart. [Исторические SHA/ручные действия](docs/STAGE-14-ACCEPTANCE.md), [ordinary профиль того среза](docs/testing/stage14-profile-2026-10-20.md). Общего ускорения сервера не доказано; для30/50 combat AI p95>2ms, часть больших сцен стреляет реже baseline и не получает performance credit. Human1v1/1v4 и приёмка08–14 — WAITING_PLAYTEST. Актуальные проверки/JAR отдельного обновления команд — в [TEAM-COMMANDS](docs/TEAM-COMMANDS.md).

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

Новый код — MIT. Применимые лицензии зависимостей и распространение модпака рассматриваются отдельно; чужие JAR не вложены в артефакты.
