# Унификация команд NPC — 2026-10-22

Локальный патч в ветке `feature` репозитория tactical-npc-1.20.1-forge. Commit/push/merge не выполнялись. Уже существовавшие изменения маршрутов, их тестов/скриптов и отчётов сохранены. Исторические отчёты помечены ссылкой на текущий интерфейс; их receipts не переписываются.

Полный [справочник, таблица старое → новое и семантика двух радиусов](COMMANDS.md).

## Реализация

- `NpcCommands` регистрирует единственный `/npc` с OP2, справкой и Tab. Отдельных tacticalnpc/spawn npc/order/faction/squad/hard/unfair веток нет.
- `NpcTargetArgument` разбирает имя/число/vanilla selector; целиком проверяет список до операций. Игроки допускаются только явным selector для team. Именной выбор не зависит от LOS, дальности и измерения; числовой требует игрока и использует 3D distance<=radius,1–128.
- `NpcOperations` содержит одну реализацию mode/team/equip/ammo/info/list. `NpcSpawning` сохраняет прежние49 loaded-safe placement candidates и фактический partial spawn count. Нет новых обходов native TaCZ.
- `NpcNames` — глобальный overworld SavedData:50 базовых имён и суффиксы, регистронезависимое владение UUID, tombstones без переиспользования, schema6 NBT. Entity lifecycle индекс слабый и учитывает экземпляр/измерение. Native admission отделён от off-thread NBT decoding; canceled join не расходует имя.
- Hard defaults принадлежат ServerConfig; Loading/Reloading мигрирует прежние профиль/боевые overrides, сохраняя enabled/diagnostics/ammo policy. Retired hints не создаются. FINITE всегда проверяет native reserve; INFINITE сохраняется. NPC management не нормализует config. Test-only fixture overrides исключены из live Reloading после начальной миграции.

## Проверки

Среда: Minecraft1.20.1, Forge47.4.20, Microsoft Java17.0.19, Gradle8.8, Windows11 amd64; TaCZ1.1.8-hotfix2, optional NPCs2.1.0.

- `bash ai-core/run-tests.sh`: **936 PASS** (72+94+38+235+353+26+118). [Receipt](testing/commands-core-2026-10-22.txt).
- `gradlew.bat compileJava compileCompatJava --offline --console=plain`: выполнено успешно до GameTests; production build проверяется отдельно ниже.
- Первый STAGE: **29/30**, FAIL переноса имени между измерениями. Отдельный diagnostic FAST: **0/1**, подтвердил name/team intact, native lookup=null у уже admitted entity. Оба raw receipt сохранены, не переименованы в PASS. Исправление: weak per-instance handle для промежутка до native tracking visibility.
- Предварительный FULL standalone: **136/136 PASS**,1145.593s. После него добавлена защита краткой потери tracking и отдельный137-й тест; финальная матрица ниже проверяет этот срез. [Receipt](testing/commands-preliminary-full-standalone-2026-10-22.json).
- FULL standalone с исправлением повторного tracking: **137/137 PASS**, [receipt](testing/commands-full-standalone-2026-10-22.json). После его компиляции добавлены client ask_server provider и startup prewarm; эти локальные изменения проверены финальным standalone command/name набором **14/14 PASS**, включая реальный packet round-trip и вызов client customSuggestion: [receipt](testing/commands-final-command-standalone-2026-10-22.json). Предыдущий полный source manifest: [behavior sources](testing/commands-behavior-sources-2026-10-22.json).
- FULL combined: повторно выполняется на финальных исходниках,149 сценариев. Предыдущий прерванный запуск24/149 не является PASS: [receipt](testing/commands-interrupted-combined-2026-10-22.json). Причина — исправление добавленного теста provider-wrapper identity на проверку фактического запроса; production код от этой правки не изменён.
- Ordinary dedicated save/restart: ожидает GameTests; скрипт использует отдельный мир, legacy UNFAIR config, именованные команды, native reload, remote chunk unload/load и сохранение name/UUID/serviceId/order/team/native stack.
- Production build/artifact verification: ожидает runtime matrix.

[Production source identity](testing/commands-sources-2026-10-22.json), [фактический поиск старых команд](testing/commands-reference-audit-2026-10-22.json), [проверенные API](INTEGRATION-AUDIT.md).

## Границы

Имена старых, ещё ни разу не загруженных после обновления NPC присваиваются при их первой серверной активации; ради миграции не загружаются чанки. Записи умерших/удалённых NPC не переиспользуются; `unloaded/absent` включает обе категории. Страницы list по10; info выводит весь явно выбранный допустимый набор. Raw UUID больше не является пользовательским аргументом; для административной фильтрации используются selectors.

Автоматические dedicated проверки не заменяют human1v1/1v4, графическую приёмку nametag/Tab и игру с production JAR через внешний launcher. Сопоставимое combat/load/JFR измерение производительности в этот командный патч не включено; ускорение/20TPS/баланс не заявляются.
