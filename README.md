# Tactical NPC AI — экспериментальный MVP 0.2.0

Собственный серверный NPC для **Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2**. `[TACZ] NPCs 2.1.0` используется отдельным optional compatibility artifact для сравнения; самостоятельному NPC этот мод не нужен. Зависимости закреплены, сторонние ресурсы в аддон не включаются.

Реализованы native TaCZ shoot/reload/bolt и finite/infinite reserve, ограниченные восприятие/поворот/реакция, память и приблизительный слух, локальные укрытия и выглядывание, ограниченное подавление известной области, приказы/оружейные профили и небольшие squads. Выполнены автоматизированные 1v4 и ordinary dedicated профиль до 50 NPC; это экспериментальная проверка, не человеческий PvP-релиз. Точное состояние срезов и результаты: [IMPLEMENTATION-STATUS](docs/IMPLEMENTATION-STATUS.md). Историческое архитектурное ревью описывает 0.1.0, а не текущую реализацию.

Установка и ручной сценарий: [MVP-TESTING.md](MVP-TESTING.md). Собран основной `forge-addon/build/libs/tacticalnpcai-0.2.0.jar`; дополнительный `tacticalnpcai-0.2.0-npcs-compat.jar` нужен только вместе с NPCs 2.1.0. Production bytecode/package verification PASS; тестовые классы и арены не включены. Клиенту и серверу нужны одинаковые основные моды. Renderer использует vanilla модель и текстуру; новые модели/анимации не создавались.

```mcfunction
/gamemode creative
/tacticalnpc spawn
/tacticalnpc order @e[type=tacticalnpcai:npc,sort=nearest,limit=1] ASSAULT 24
/gamemode survival
```

NPC появляется в трёх блоках к востоку, с пустым AK-47 и тегом `tactical_ai`; штатная перезарядка работает даже без цели. По умолчанию приказ HOLD_POSITION. Creative/spectator не атакуются. Консервативная безопасность блокирует стены и занятый сектор; случайный native разброс дополнительно ограничен союзным pre-damage guard. Неизвестные shooting scripts, charge, inventory-fed, explosive/incendiary свойства блокируются с диагностикой.

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
.\gradlew.bat compileJava compileCompatJava build
```

`fetch-dependencies.ps1` загружает ровно TaCZ 1.1.8-hotfix2 и NPCs 2.1.0 и проверяет SHA-256. NPCs нужен для сборки optional adapter, самостоятельный runtime от него не зависит. Сторонние mod JAR, декомпилированные исходники, сборки, тестовые миры, резервные копии, raw logs и JFR в Git не включаются. Собственные NBT GameTest fixtures и Gradle wrapper входят в репозиторий. Сводные результаты сохранены в `docs/testing/`; ссылки исторических отчётов на raw logs относятся к локальным материалам испытаний.

Архитектура: perception → immutable snapshot → decision → action arbitration → Forge/TaCZ adapter. Ядро `ai-core/` не импортирует игровые API; сущностями управляет server thread. `src/compat/` изолирует оригинальные goals, фракции и AutoGunOperator. Подробности подтверждённых API: [аудит](docs/INTEGRATION-AUDIT.md), контракты испытаний: [TEST_PLAN](docs/TEST_PLAN.md).

Новый код — MIT. Применимые лицензии зависимостей и распространение модпака рассматриваются отдельно; чужие JAR не вложены в артефакты.
