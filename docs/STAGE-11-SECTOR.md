# Этап 11 — огневой сектор

Файлы: `combat/VisibilitySensor`, согласованные query quanta `combat/AiWorkBudget`, `combat/AiTelemetry`; `SectorTests`; этот отчёт. Core/native damage/cooldown не менялись.

Checkpoint `3f9e810` + пакет 08–11. Старый одинаково расширенный AABB 128 заменён четырьмя 32-block участками с локальным расширением. Narrow phase решает slab inequalities для линейно расширяющегося L-infinity envelope; это консервативная оболочка разброса, не обещание абсолютной вероятностной границы native spread. За целью по-прежнему проверяется длина 128, native pre-damage guard сохранён. Каждый вызов/round читает текущие тела заново; при DEFERRED сохраняется только порядок участков, не CLEAR геометрия. Predicate ограничен (64 проверок + <=32 sentinels), saturation никогда не CLEAR.

На той же сцене 32 тела сбоку: старая исходная broad-phase query стабильно насыщается 32; новый сектор CLEAR, native finite NPC стреляет. Замер после warmup, 30 вызовов: старый query 1.1044 ms, новый полный occupied 1.3940 ms, 1→4 query/check. Ускорения occupied в этой микросцене нет: устранён ложный запрет; дополнительные проверки стоят CPU. Сравнение reference выполняется на текущем мире точной исходной query, а не выдаётся за общий server benchmark. Сопоставимые фактические projectile и ordinary dedicated before/after — на 14.

STAGE `SectorTests,QueryBudgetTests,IndependentNpcTests#alliedDamageEventGuard,WeaponProfileTests#shotgunNativePellets,NeutralTests#returningNeutralCancelsUncreatedNativeBurstRounds` — 8/8 PASS, 72.547 s. Receipt `forge-addon/build/test-runs/20261019T195809.114273Z-STAGE.json`; compileJava/compileCompatJava в графе. Причины доступны как Sector CLEAR/OCCUPIED/SATURATED/DEFERRED/UNSUPPORTED; накопленные counters добавлены в telemetry.

Ручные действия владельца:
1. Поставить 32 тела сбоку от направления стрельбы: NPC не должен постоянно молчать из-за прежнего большого AABB.
2. Провести союзника перед целью и за целью: новые rounds должны блокироваться; уже летящие bullets защищены native pre-damage guard.
3. Повторить с дробовиком и BURST; двигать тела между rounds, построить стену и переключить mode.
4. Перегрузить сцену до SATURATED/DEFERRED: запреты различимы, незавершённая проверка не разрешает огонь.

Automated PASS; WAITING_PLAYTEST. Production rebuild/verification на 14.
