# Согласование инструкций — 2026-10-22

Этап меняет только AGENTS.md, семь проектных SKILL.md и связанную документацию.
Локальные исходники/тесты/инструменты навигации сохранены; их приёмка относится
к следующему этапу. Инструкции задают требования, а не объявляют черновики готовыми.

## Что опубликовано и что находится только локально

GitHub API проверен в этой сессии: [ветки репозитория](https://api.github.com/repos/Crayfish3r/tactical-npc-1.20.1-forge/branches?per_page=100).
Опубликованная `feature` указывает на [f990246](https://github.com/Crayfish3r/tactical-npc-1.20.1-forge/commit/f990246dea8e876cbacb3be2fd36540c7c0a20b7),
совпадающий с локальным HEAD в начале этапа. `main` и локальный `origin/main` —
`3077098f1338c3c8fdb1f564257fd71f8ea98701`. Наличие commit в GitHub/ветке не означает
релиз JAR или человеческую приёмку.

В опубликованном `feature` уже есть ServerConfig Hard/Unfair, UnfairHints,
Ballistics, SuppressionCycle/SuppressionPermit, SuppressionSectors и обновлённая
координация отряда. Проверены исходники этого commit и соответствие текущим
неизменённым профильным классам; [боевой отчёт](COMBAT-UPGRADE-2026-10-22.md)
использован как указатель на доказательства, а не как замена чтению кода.

Новые ActionRoute/TransitionReservations, Forge ActionRoutePlanner/Executor,
ActionGeometry/RouteInteractions/TransitionResources и ActionRouteTests находятся
в незакоммиченном рабочем дереве вместе с изменениями MovementExecutor и других
адаптеров. В дереве опубликованного HEAD этих новых классов нет. Последняя правка
безопасной отмены начатого прохода двери и её тест ещё требуют итоговой проверки;
актуального полного green для всей этой локальной реализации не заявлено.
Локальный `docs/ACTION-ROUTES-2026-10-22.md` остаётся рабочим документом
следующего этапа, его цифры относятся к указанным там попыткам/срезам.

## Противоречия и подтверждение по коду

| Найдено | Проверенное поведение / источник | Исправление |
|---|---|---|
| Weapon skill задавал устаревший фиксированный короткий permit, шесть патронов и фиксированную паузу | [ServerConfig](../forge-addon/src/main/java/dev/zuma/tacticalnpc/config/ServerConfig.java), TacticalCombatGoal.suppressionPermit и [SuppressionCycle](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/SuppressionCycle.java) используют действующие duration/round/burst/pause; [SuppressionPermit](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/SuppressionPermit.java) проверяет возраст, confidence, uncertainty и сектор | Убраны дубли чисел; настройки и неизменяемые алгоритмические ограничения различаются и ссылаются на владельца |
| Brain/squad skills запрещали скрытое знание без оговорки Unfair либо предполагали несуществующие источники reports | [UnfairHints](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/UnfairHints.java) хранит отдельные quantized nonvisual подсказки без скорости; TacticalCombatGoal выбирает канал; [SquadBlackboard.report](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/SquadBlackboard.java) принимает только VISION | Явные реализованные исключения Unfair; hints не становятся VISION/SQUAD; hearing/damage не объявлены реализованной squad-рассылкой |
| Упоминание бесконечного резерва могло распространяться на любой runtime или подразумевать обязательный finite в Hard | [TacticalNpcEntity.needCheckAmmo](../forge-addon/src/main/java/dev/zuma/tacticalnpc/entity/TacticalNpcEntity.java) применяет Unfair override к независимой сущности и возвращает сохранённую ammo policy в Hard | Область исключения названа точно; аналогичное поведение optional adapter не обещано |
| Audit skill безусловно останавливал любую реализацию без NPCs 2.1.0 | [NpcRuntimeAdapter](../forge-addon/src/main/java/dev/zuma/tacticalnpc/runtime/NpcRuntimeAdapter.java), NpcEntities, main/compat source sets в [build.gradle](../forge-addon/build.gradle) разделяют независимую сущность и optional adapter | Блокируется только работа/проверка, которой нужны соответствующие классы/сигнатуры |
| Compat skill требовал создать новый ForgeGradle-проект | Уже существуют main/compat/compatTest, pinned mappings, runs и native lifecycle | Требуется развивать существующую структуру и проверять затронутый API/владение |
| Profiling skill подменял before/after текущего AI сравнением с оригинальным модом и требовал одну матрицу для любой правки | Текущие профильные launchers/receipts различают frozen source baseline и original NPCs experiment; отчёты содержат реальные rounds/shooters и неполные/неуспешные прогоны | Разные эксперименты, risk-scoped проверки, запрет performance credit за снижение фактической работы |
| Navigation skill смешивал новый path search и ежетиковое движение; двери упоминались как обычное препятствие без action-контракта | Опубликованный MovementExecutor отделён от локальных action-route черновиков; native управление нуждается в актуальных inputs | Отдельны planning/execution; следующий этап задаёт WALK/OPEN_DOOR/CLIMB_UP/CLIMB_DOWN, предусловия, физическое завершение, ожидания/тайм-ауты, leases и безопасную отмену |
| Architecture сохраняла старые числовые расписания и зависимый от NPCs runtime-контракт | Текущий независимый main runtime и эффективные настройки уже иные | Уточнён статус design reference, удалён второй числовой профиль, исправлены runtime и роли |

Проверены также [INTEGRATION-AUDIT](INTEGRATION-AUDIT.md),
[IMPLEMENTATION-STATUS](IMPLEMENTATION-STATUS.md), последние записи STAGE-JOURNAL,
текущие ForgeConfigSpec, native weapon gates, код squad reports, наблюдений и hint reset.
Новые исключения или игровые функции на этом этапе не добавлялись.

## Проверка изменения

- Все семь frontmatter имеют корректные name/description; name совпадает с каталогом.
- Локальные Markdown-ссылки инструкций и связанных документов разрешаются в файлы;
  ссылки на config/core/adapter указывают на существующие исходники.
- Отдельно проверены отсутствие старых fixed suppression limits, общего NPCs-blocker,
  генерации нового проекта и несуществующего RUSHER в актуальных skills.
- `git diff --check` для файлов этого этапа; SHA сравнение всех сохранённых исходников,
  тестов и инструментов с началом этапа. Изменения ограничены инструкциями/документацией.
- [Машинный результат проверки](testing/instruction-alignment-2026-10-22.json).

Игровые сборки/нагрузочные матрицы не требуются для проверки этого diff и не
засчитываются ему. Ранее начатый дочерний GameTest завершился отдельно, его
FAIL остаётся свидетельством незавершённой игровой приёмки. Отсутствие optional
JAR физически не имитировалось: граница подтверждена кодом/source sets, а не
заявлением, что каждый агрегатный Gradle launcher работает без compile-зависимости.

Состав отдельного diff: AGENTS.md; семь `.agents/skills/*/SKILL.md`;
ARCHITECTURE.md; TESTING-PROFILES.md; этот отчёт и его validation receipt.
История Git содержит отдельные scoped commits; этот согласованный пакет
подготавливается отдельным локальным commit без исходников навигации.
