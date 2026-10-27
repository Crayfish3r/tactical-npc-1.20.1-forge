# Исправления пассивности при стрельбе — 2026-10-27

Локальный патч поверх `d188efe04dbc47b3e045c63c9958d3df60db043b`, не опубликованный коммит и не релиз.
Minecraft 1.20.1 / Forge 47.4.20 / Microsoft Java 17.0.19 / TaCZ 1.1.8-hotfix2;
optional NPCs 2.1.0. Точные команды и dependency/source hashes: [manifest](testing/firing-validation-2026-10-27.json).

## Проверка ревью

1. **Подтверждено: WAIT + пустое оружие + резерв + укрытие.**
   `MovementPlanner` откладывал RELOAD до прибытия, а финальный STAND overlay заменял путь на HOLD.
   `GunSupport.create()` действительно создаёт пустые магазин и патронник. Новая runtime-регрессия
   начинает без резерва, получает настоящий визуальный контакт, проверяет выбранное укрытие по реальному миру
   и затем выдаёт конечный резерв. До исправления: `reloads=0`, `rounds=0`.
   После: ровно одна принятая native reload, настоящий projectile, WAIT остаётся неподвижным до первого round,
   затем переходит в COMBAT. Отсутствие резерва по-прежнему не разрешает невозможную перезарядку.

2. **Подтверждено: pathBlocked запрещал даже безопасный огонь; HOLD удерживал recovery.**
   Воспроизведён близкий видимый противник у края DEFEND4 при EXHAUSTED.
   Исчерпание конечного recovery задаётся через существующий core RouteRecovery в fixture;
   реальный OrderPlanner отменяет lateral destination, реальный MovementExecutor получает HOLD.
   До: REPOSITION / NONE / HOLD, `firePermitted=false`.
   После: безопасный fire permit сохраняется, episode становится IDLE, backoff точки сохраняется;
   после инъекции состояния зарегистрированы новые native projectiles.
   Core дополнительно проверяет DEFEND и PATROL, а также ранее начатое сближение ASSAULT.
   Это тест обработки уже возникшего EXHAUSTED, не утверждение о частоте его возникновения на карте.

3. **Подтверждено как намеренное правило, изменённое этим патчем.**
   При healthRatio <= .25 запрет присутствовал и в CombatBrain, и в nativeFireSafe.
   RETREAT сохраняется, но может сопровождаться DIRECT огнём по видимому противнику в пределах дальности
   при свободных LOS/секторе и готовом оружии. Native sprint/reload/bolt/cooldown, reaction, aim,
   generation/TTL и нейтральная политика остаются обязательными. Тест подтверждает настоящий round на границе .25.

4. **Подтверждён механизм, частота на пользовательской карте не измерена.**
   Прежняя длина сектора была `max(128,min(512,distance+8))`; мешали все прочие living entities,
   включая допустимых противников. OCCUPIED ошибочно сводился к telemetry gate ALLY.
   Теперь сектор охватывает `min(512,distance+8)` и блокируется существами, которым нельзя нанести урон
   по текущему CombatTargets.eligible: союзниками и прочими защищёнными/нейтральными телами.
   Другой допустимый противник не является союзным препятствием. Продолжение за целью, консервативная
   оболочка разброса, bounds запросов, fail-closed SATURATED/DEFERRED/UNSUPPORTED и свежая проверка каждого
   native round сохранены. За локальным сектором неизменённый pre-damage guard защищает неразрешённые цели,
   в том числе при потерянном владельце снаряда. Это осознанное изменение консервативной политики, а не
   доказательство того, что исходная проверка всех тел была случайной ошибкой.

## Изменения

- [CombatBrain](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/CombatBrain.java) отделяет emergency movement
  от безопасного ответного огня; [AttackRoute](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/AttackRoute.java)
  не подменяет его старым сближением.
- [MovementPlanner](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/MovementPlanner.java) получает явную
  возможность движения. [TacticalCombatGoal](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/TacticalCombatGoal.java)
  передаёт stationary WAIT до выбора cover cycle и не запускает недоступный flank.
- [MovementExecutor](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/MovementExecutor.java) завершает
  отменённый HOLD эпизод, сохраняя failed-destination backoff и бюджеты. Уже начатые специальные переходы
  по-прежнему завершаются через прежний action-specific guard.
- [VisibilitySensor](../forge-addon/src/main/java/dev/zuma/tacticalnpc/combat/VisibilitySensor.java) сохраняет UUID
  блокирующего тела и его отношение. `/npc debug info <target>` показывает последний tick, действие,
  policy/gate, LOS, сектор, blocker UUID, allied, готовность оружия и состояние маршрута.
  Телеметрия различает ALLY, NON_HOSTILE, SECTOR_SATURATED, SECTOR_DEFERRED, SECTOR_UNSUPPORTED.

Новых TaCZ/Forge API, Mixins, зависимостей, схемы NBT, конфигурационных параметров, загрузки чанков
или отдельного владельца оружия нет. [Аудит](INTEGRATION-AUDIT.md) применим: SHA256 обоих pinned JAR перепроверены.
Числа длины сектора и порога emergency health — константы алгоритма в этих классах, не настройки ServerConfig.

## Выполненные проверки и итоговый STAGE

- Исходный production `d188efe`, новые regressions: **4/4 выполнены, 4 FAIL**, все по ожидаемым причинам.
  [Receipt](testing/firing-baseline-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T031450.413428Z-FAST.log).
  Core отдельно воспроизвёл запрет ответного огня: [исходный FAIL](testing/firing-core-baseline-2026-10-27.txt).
- Исправленный код: **903 core checks PASS** в policy/temporal/territory/patrol/attack/combat.
  [Вывод](testing/firing-core-after-2026-10-27.txt). Actions suite не запускался: core action-route контракт не менялся.
- Новые runtime-регрессии: **4/4 PASS**.
  [Receipt](testing/firing-fast-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T031910.780748Z-FAST.log).
  Fixture дополнена проверками сохранённого backoff и точной классификации blocker, production inputs этого PASS окончательные.
- Связанный standalone STAGE: **20/20 PASS**, [receipt с точной selection/командой](testing/firing-stage-standalone-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T032148.689533Z-STAGE.log).
  STAND, сектор/отказ бюджета, конечные reload/bolt, pre-damage ally guard, neutral BURST cancellation,
  легитимная suppression memory, восстановление маршрута и emergency stop, границы DEFEND,
  фактические дробины native shotgun и безопасная отмена лестничного перехода.
- Optional NPCs STAGE: **3/3 PASS**, [receipt с точной selection/командой](testing/firing-stage-optional-2026-10-27.json),
  [raw log](../forge-addon/build/test-runs/20261027T032544.170676Z-STAGE-compat.log).
  Общий controller/native gate, native BURST с перекрытием LOS, защищённое тело перед/за целью.

Первый запуск остановлен sandbox до тестов (0 execution):
[Forge receipt](../forge-addon/build/test-runs/20261027T031428.298515Z-FAST.json),
[Bash IPC error](testing/firing-core-before-2026-10-27.txt). После разрешённого запуска с доступом к локальному cache
проверки выполнились; исходные FAIL и логи сохранены.

**Итоговый STAGE: PASS в выбранной области — 903 core checks, 24 standalone GameTests и 3 optional GameTests.**
Покрытие собрано из FAST4 + standalone STAGE20 + optional STAGE3 на окончательных соответствующих входах;
успешный FAST повторно не запускался. Production sources, тесты/общие helpers, fixtures/resources,
build/launcher и зависимости зафиксированы manifest; после успешного FAST менялись только отчёт и receipts.
Компиляция main/compat выполнена launcher; в optional запуске также выполнена compileCompatTestJava.
FULL, JFR/load/soak, ordinary save/restart, human 1v1/1v4 и production-JAR playtest не запускались:
это не релиз, persistence/config/packaging не менялись, performance/balance не заявляются.
Новый production JAR не передаётся. Автоматические проверки не заменяют приёмку владельца на своей карте.
