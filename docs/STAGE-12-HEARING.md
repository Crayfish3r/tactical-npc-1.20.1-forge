# Этап 12 — слух и локальная доставка

Файлы: новый `combat/GunHearing`, `combat/TacticalCombatGoal`, `compat/ShotSafetyEvents`, `runtime/RuntimeRegistry`, `combat/AiTelemetry`; `HearingTests`, sound fixture `PerceptionTests`; этот отчёт. Core не менялся.

Checkpoint `3f9e810` + 08–12. Source eligibility использует supported type/lifecycle/alliance/range независимо от visibility/FOV. Invisible Player или supported NPC за стеной даёт только quantized region, uncertainty 8, confidence .55, TTL 60; не DIRECT и не разрешение suppression. NEUTRAL игнорирует звук, включая уже reactive NPC: sound не заменяет authenticated attacker.

Доставка перенесена из общего обхода activeIn в LOWEST EntityJoinLevelEvent native kinetic round с достоверным LivingEntity owner; cancelled join игнорируется. Локальный spatial query разрешается optional budget до world query. Максимум 16 получателей и 112 predicate calls/event; дедупликация shooter/tick, recent source/listener 10 ticks помогает доставить следующие реальные события поздним получателям. Нет отложенного повторного создания звуков: при budget/saturation событие пропускается без продления памяти. State ограничен 1024 shooters/4096 recent pairs/level, очищается при leave/unload. Плотная сцена сверх этих границ может терять optional sound; fire safety и vision не подменяются звуком.

STAGE `HearingTests,SuppressionTests,NeutralTests#proximitySoundAndSquadCannotStartCombat,NeutralTests#hiddenAttackerGrantsNoAimAndExpiresWithoutThirdParty` — 6/6 PASS, 84.547 s. Receipt `forge-addon/build/test-runs/20261019T200130.842518Z-STAGE.json`; compileJava/compileCompatJava в том же графе. Реальные NPC native rounds проверены отдельно от прямого вызова delivery fixture.

Измерение local fixture: 50 слушателей, 5 доставляемых событий + 5 same-tick coalesced, 5 query, 198 рассмотренных candidates, 50 доставок, суммарно 14.8931 ms. Старый activeIn вызвал бы hear 250 раз для этих 5 событий. Это счётчики/один замер, не сопоставимое время старого delivery: общего ускорения сервера этим не доказано.

Ручные действия владельца:
1. За стеной выстрелить invisible Player: NPC примерно ищет область, не наводится на точный скрытый hitbox и не простреливает стену.
2. Повторить с враждебным NPC shooter; дальний/союзный источник не даёт hostile observation.
3. Подождать TTL без новых shots: звук не обновляется сам. При automatic fire проверить bounded receivers и diagnostics query denial.
4. NEUTRAL рядом с выстрелами остаётся нейтрален; после реального damage отвечает исходному attacker, звук третьего лица его не подменяет.
5. Unload/load группы: старые receiver/coalescing данные не продолжают контакт.

Automated PASS; WAITING_PLAYTEST. Production rebuild/verification на 14.
