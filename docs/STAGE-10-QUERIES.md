# Этап 10 — entity query budgets

Checkpoint: `3f9e810` + пакет 08–10. Новое разрешение precedes acquire list page, activation world query и occupied world query. Active/native safety имеет отдельную полосу от optional acquisition/idle. При отказе acquire не очищает страницу/cursor, activation возвращает DEFERRED, safety запрещает round. Отказ query не создаёт path FAILED.

Файлы: `combat/AiWorkBudget`, `combat/VisibilitySensor`, `combat/AiTelemetry`, `runtime/RuntimeRegistry`; `QueryBudgetTests`; этот отчёт. Геометрия сектора меняется отдельно на11, hearing — на12. Core не менялся.

Ограниченный FIFO pending набор резервирует query quanta для ранее отложенных владельцев на следующем tick; поздний tick order не позволяет ранним постоянно забирать их ёмкость. Pending cap 1024 identities/lane, TTL 200 ticks, очистка entity leave и level unload. Счётчики queries/denials и максимального deferred age находятся в памяти; per-tick I/O нет. Queries/denials относятся к выданным разрешениям, включая ограниченные list pages. Цифры не следует выдавать за все внутренние запросы Minecraft/TaCZ.

STAGE: `QueryBudgetTests,ActivationTests,PerceptionTests,NeutralTests#returningNeutralCancelsUncreatedNativeBurstRounds` — 12/12 PASS, 87.610 s; compileJava и compileCompatJava в том же графе. Receipt `forge-addon/build/test-runs/20261019T195355.647732Z-STAGE.json`. Проверены denial до запросов, no-fire, продолжение acquisition cursor/readiness/memory, 100 identities с фиксированным порядком, unload cleanup. На 11 один occupied query разделён на 4 участка: лимит native queries масштабируется ровно в 4 раза (256→1024, owner 8→32, quantum 4→16), ёмкость эквивалентных полных проверок не увеличивается. Отдельные 30 NPC overload/performance сцены — на 14, не заменены этим тестом 100 identities.

Ручные действия владельца:
1. В копии мира разместить 30 активных NPC и допустимые цели; включить diagnostics, сравнить queries/denials/deferred age.
2. Добавить плотные препятствия/объекты и пересекающего сектор союзника: при DEFERRED выстрел запрещён.
3. Сохранять контакт за стеной кратко: legitimate memory не исчезает из-за отказа бюджета, скрытые координаты не обновляются.
4. Проверить самых поздних созданных NPC: они продолжают получать обслуживание; нет бесконечного path spam. Unload/load группы очищает старые очереди.

Automated PASS; WAITING_PLAYTEST. Полный профиль и production handoff на 14.

На 14 отдельно воспроизведён фиксированный порядок native rays: при 50 владельцах ×8 rays прежний лимит384 обслуживал только48/50 (`20261019T205020.241136Z-FAST-compat.json`, 2/3 PASS). Добавлена bounded FIFO-защита ранее отложенных ray credits при тех же384/128/16 лимитах, отдельная от optional работы; stale ray requests истекают через8ticks, leave/unload очищает их. Регрессии native/optional fairness и DEFERRED: combined23/23 PASS (`20261019T205229.444706Z-STAGE-compat.json`), отдельный cover/optional cadence8/8 PASS (`20261019T205633.413723Z-FAST.json`). Счётчик worldQueries отдельно считает фактические вызовы адаптера, не все TaCZ/vanilla запросы. Это минимальная найденная на итоговой проверке правка; лимиты rays не повышались.
