# Проверки — Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2

Фактические прогоны, ошибки и логи: [IMPLEMENTATION-STATUS](IMPLEMENTATION-STATUS.md). NPCs 2.1.0 — отдельный compatibility runtime; версии зависимостей закреплены.

```bash
bash ai-core/run-tests.sh
```

Из `forge-addon`:

```powershell
.\gradlew.bat --offline build
.\gradlew.bat --offline -PcombatTests runGameTestServer
.\gradlew.bat --offline -PcombatTests -PlegacyCompatibility runGameTestServer
.\gradlew.bat --offline runServer
```

GameTests включаются только флагом `combatTests`, не входят в production build. Test-only clock выдерживает 50 ms с учётом ранних unpark: TaCZ использует реальные миллисекунды. Последовательные batches очищают оставшиеся сущности и сбрасывают изменяемые настройки сцены. Smoke измеряет native projectiles, reload, ограниченный поворот и горизонтальное движение, отдельно от успешного shoot request.

Покрытие: no-target maintenance; reaction; reload за стеной; bolt без цели; LoS loss; союзник в секторе дроби и за целью; native BURST/SEMI/AUTO; tag ownership/восстановление goals; смерть/unload/dimension; finite reserve; NBT; память/FOV/приблизительный слух; движение к проверенному cover, HOLD/reload/PEEK; hazards/work budgets. Клиентский smoke отдельно проверяет реальное соединение, renderer и main-hand synchronization без NPCs.

Этап 6 добавляет отдельные native firing сценарии AK-47/Glock/AA-12/AWP/UZI/M249, с конечным нулевым резервом для воспроизводимого magazine/bolt расхода. Проверяются реальные projectiles/pellets, profile metadata и ограничение рабочей дальности. Отдельный сценарий — фактический возврат DEFEND_AREA, schema 2 anchor/order и patrol navigation. Независимые 1v4 без squad должны пройти до подключения координации; FakePlayer остаётся автоматизированным стендом, не человеческой дуэлью.

Этап 7: задержанный squad report за стеной не следует за скрытым игроком; смерть лидера и снятие тега освобождают membership/claims; coordinated 1v4 создаёт native projectiles без союзного урона. Dormant NPC штатно обслуживает оружие и пробуждается только через допустимое восприятие. Отдельные regression tests создают активный vanilla path с противоположным body yaw и проверяют реальный head turn limit, включая legacy runtime и восстановление оригинальных look/body controllers.

Тестовый `-Pprofile` запускает обычный dedicated server, а не GameTestServer. Полный отчёт, seed, оборудование, JFR/CSV, метод измерения и ограничения: [PROFILE-REPORT](PROFILE-REPORT-2026-10-11.md). `-PprofileQuick` оставляет контрольные combat-фазы 30/50. Production build выполняется без этих флагов. Автоматический клиентский `-PclientSmoke` дополнительно требует, чтобы на клиенте появился загруженный магазин после серверной native reload.

## Отдельные основания огня

DIRECT: свежий видимый Observation, законченная реакция, ограниченный поворот/ошибка, native ammo/cooldown, clear target и фактическая muzzle-direction line, безопасный сектор. Потеря видимости запрещает старую DIRECT команду.

SUPPRESS_AREA: отдельный SuppressionPermit, легитимный VISION/HEARING/SQUAD track; возраст ≤40 тиков, confidence ≥0.35, uncertainty ≤12; максимум 6 native rounds за непрерывный скрытый контакт и пауза ≥10 тиков между принятыми requests. Координата берётся только из Observation. Новые звуки не пополняют квоту непрерывного контакта бесконечно. SEARCH memory может жить дольше права подавления (по умолчанию 160 тиков).

Оба режима проверяют каждый native round: alive/owner generation/TTL, свойства боеприпаса, reload/bolt, геометрию и living entities. Линия до известной точки закрыта — огня нет. Проверка существования/смерти player не передаёт его скрытые координаты планировщику. Wallbang, explosive/incendiary и неизвестные Lua shooting hooks запрещены. Native TaCZ создаёт projectile, расходует магазин и соблюдает свои задержки.

Suppression-сценарии: игрок уходит за другой угол, последний открытый point обстреливается ограниченно и не следует за скрытым игроком; старая точка закрыта стеной — выстрелов нет; ally crossing блокирует команду; истёк permit/квота/цель умерла/вышла из измерения — огонь прекращён. Общая защита DIRECT не ослабляется ради SUPPRESS_AREA.

## PvP и критерии выпуска

Проверить человеком 1v1/1v4, reconnect, save/restart, chunk unload/load, портал, динамические стены, ступени/полублоки/двери/воду/лаву/ямы. Сравнить попадания по дистанции, TTK, задержку первого выстрела, полезность укрытий и stuck retries. Новые gunpacks требуют отдельного технического аудита.

Профиль обычного dedicated server: 1/4/16/30/50 NPC, baseline/бой/динамические препятствия/низкий TPS/unload/длительный прогон. Указать CPU/RAM, JVM/модлист/seed/мир, tick и AI p50/p95, rays/candidates/paths, heap/CPU, ошибки и метод измерения. 20 TPS и AI p95 ≤2 ms — цели, пока конкретный прогон их не подтвердил. Без human PvP или длительного профиля выпуск остаётся экспериментальным.
