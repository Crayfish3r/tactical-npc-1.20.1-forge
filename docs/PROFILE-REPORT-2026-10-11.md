# Dedicated профиль 2026-10-11

Закреплены Minecraft 1.20.1 / Forge 47.4.20 / Java Microsoft 17.0.19 / TaCZ 1.1.8-hotfix2 / Tactical NPC AI 0.2.0. Для сравнения исходного контроллера отдельно добавляются NPCs 2.1.0 и compatibility artifact. Версии не менялись.

Оборудование: Intel Core i5-10300H 2.50 GHz, 4 ядра / 8 logical processors, RAM 17 041 244 160 bytes, Windows NT 10.0.26300.0. [Снимок](testing/profile-hardware-2026-10-10.json). JVM -Xms1G -Xmx4G; Java Flight Recorder settings=profile, maxsize=64m. Это обычный runServer, не ускоренный GameTestServer; графический клиент во время измерения не работал.

## Метод

Мир profile-map, seed 731101, плоская тестовая площадка 44×520 из камня на y100, постоянные forced chunks x62..65/z62..95. В каждой полосе NPC с AK-47 и synthetic invulnerable FakePlayer в 22 блоках. Не человеческая дуэль и не 1v50: число synthetic targets равно числу NPC. Независимые NPC имеют DEFEND_AREA radius 6 и локальные группы по четыре. Baseline — те же сущности/оружие/targets без tactical_ai; combat — native стрельба с реальными projectiles. Для исходных NPC используется штатный controller без tactical_ai, с явно установленной видимой целью в открытой полосе. Не сравниваем win rate или интеллект по этому стенду.

На фазу: warmup 80 ticks, затем обычно 240 samples (около 12 s при 20 TPS). Dynamic: стены меняются раз в 40 ticks, 320 samples. Injected-low-tps: test-only Thread.sleep(100) на tick, 160 samples; это искусственная нагрузка. Unload/reload: targets удалены, forced chunks сняты; реально проверено отсутствие всех 30 сущностей в level и runtime, затем восстановлены UUID/serviceId/order/squad/AK-47 из чанков. Short soak: 2400 ticks, реально 120.004 s; это короткий прогон, не многочасовая проверка утечек.

Tick time — wall duration между Forge ServerTick START/END, без ожидания до следующего тика. AI time — сумма TacticalCombatGoal и native fire safety gate, nested вызовы не считаются дважды. Native TaCZ tick/projectile physics, оригинальный AI и весь server cost видны в общем tick/JFR, но не целиком в AI counter. У original-npcs AI=0 означает отсутствие нашего instrumented controller, а не бесплатный исходный ИИ. CPU core percent — process CPU time / phase wall time ×100, не процент от всех восьми логических ядер. Heap без принудительного GC, поэтому его p95 не является retained-memory/leak оценкой.

TPS вычислен как samples/wall. Значения выше 20 в unload/catch-up фазах после искусственного lag отражают догоняющий scheduler; это не обещание TPS сервера выше 20. Baseline/combat короткие, JIT/GC/нагрев ноутбука и прогретость мира могут влиять на различия. Одного прогона недостаточно для статистического утверждения об ускорении относительно NPCs.

## Реальные данные

Полный standalone прогон: [summary CSV](testing/stage7-profile-2026-10-11.csv), [raw samples](testing/stage7-profile-samples-2026-10-11.csv), [лог](testing/stage7-profile-2026-10-11.log). JFR: `forge-addon/build/stage7-profile.jfr`. Модлист в PROFILE_START: minecraft, forge, tacz, tacticalnpcai, mixinextras 0.3.6, simplebedrockmodel 2.2.2-forge+mc1.20.1; tacznpcs отсутствует.

| Combat NPC | TPS | Server tick p50 / p95 ms | AI p50 / p95 ms | Native projectiles за фазу |
|---|---|---|---|---|
| 1 | 20.00 | 4.0752 / 10.6480 | 0.2215 / 0.9700 | 27 |
| 4 | 20.00 | 4.9971 / 11.5763 | 0.5910 / 2.2156 | 88 |
| 16 | 20.01 | 6.3173 / 15.9790 | 1.0685 / 3.4663 | 407 |
| 30 | 20.00 | 7.0323 / 17.9753 | 1.2967 / 3.8749 | 727 |
| 50 | 20.00 | 9.5180 / 21.3999 | 1.9978 / 5.3254 | 1164 |

Dormant 50: AI p95 0.3519 ms, rays=0, candidates mean 2.5/tick, no projectiles. Activation only grants a proximity boolean and does not create an Observation. Dynamic 30: tick p95 17.3725 ms, AI p95 3.7154 ms, 214 projectiles. Injected lag 30: 8.96 observed TPS, tick p95 119.8653 ms, AI p95 5.6502 ms; cooldowns remain real/native. Short soak 50: 20.00 TPS, tick p95 21.9074 ms, AI p95 4.4925 ms, 9923 projectiles; heap p50/p95 1557.0/1925.9 MiB. Полные CPU/heap/rays/paths — в CSV.

Первый прогон до оптимизации: [CSV](testing/stage7-initial-profile-2026-10-11.csv), [лог](testing/stage7-initial-profile-2026-10-11.log), [sampled JFR stacks](testing/stage7-initial-jfr-summary-2026-10-11.txt), JFR `forge-addon/build/stage7-initial-profile.jfr`. Он использовал FakePlayerFactory, чей strong cache мог удерживать targets между фазами; повтор использует прямой FakePlayer. Сравнение heap между этими прогонами не интерпретируется как эффект кода ИИ.

JFR показал AABB sector checks; убраны повторные лучи/sector queries при cooldown/reload/незавершённом повороте. Final arbitration и каждый native round сохраняют проверки. На 50 NPC средние rays снизились с 166.43 до 74.82, AI p95 с 5.7060 до 5.3254 ms в этих двух прогонах. Это наблюдение по коротким срезам, не стабильная гарантированная экономия CPU. Общие лимиты остаются 384 rays /128 candidates /8 paths per level tick и 16/6/1 на NPC; path interval ≥20 ticks. Асинхронность не добавлялась.

## Статус выпуска

В этом стенде normal combat и короткий soak сохраняли около 20 TPS. **Ориентир AI p95 ≤2 ms для 30/50 не достигнут.** Нужны человеческие 1v1/1v4, reconnect, сложные карты/рельеф, полный weapon/attachment matrix и длительный прогон. Не объявляем отсутствие утечек по двум минутам. Vanilla renderer smoke и lifecycle проверяются отдельно от профиля.

После полного профиля дополнительно исправлен vanilla navigation/body head clamp. Combined GameTests **34/34** и standalone **25/25** прошли; [логи и точный порядок](IMPLEMENTATION-STATUS.md). Контрольный профиль 30/50 фиксируется отдельно, чтобы не выдавать прежнее измерение за проверку изменённого LookControl/BodyRotationControl.

## Исходный NPCs 2.1.0

Выполнен обычный dedicated runServer, 10 фаз baseline/combat 1/4/16/30/50, BUILD SUCCESSFUL; сервер штатно остановлен. [CSV](testing/stage7-original-profile-2026-10-11.csv), [raw samples](testing/stage7-original-profile-samples-2026-10-11.csv), [лог](testing/stage7-original-profile-2026-10-11.log), JFR `forge-addon/build/stage7-original-profile.jfr`.

| Original combat NPC | TPS | Tick p50 / p95 ms | Native projectiles |
|---|---|---|---|
| 1 | 20.01 | 4.0388 / 9.3337 | 14 |
| 4 | 20.01 | 3.6765 / 7.8963 | 50 |
| 16 | 20.00 | 5.8505 / 13.6039 | 199 |
| 30 | 19.99 | 7.1060 / 14.3829 | 356 |
| 50 | 19.99 | 10.3259 / 22.2853 | 626 |

Использованы штатные goals и gun controller, **без tactical_ai и без принудительного target каждый тик**. Test-only fixtures в src/profile/fixtures задают default opposed=[players] только в run/profile/config. Dummy остаётся selectable survival Player и игнорирует damage в test-only override hurt; baseline dummy — creative. Сам NPC, его damage/cooldown/stats/goals не изменялись. Контрольный standalone 30/50 использует такой же dummy. Полный прежний standalone прогон использовал vanilla invulnerable dummy; поэтому это не строго парный benchmark всех фаз и не доказательство ускорения или win rate.

Первый original run имел нейтральный default template и invulnerable dummy, не создал projectiles и признан **невалидным для сравнения боя**: [лог](testing/stage7-original-profile-invalid-2026-10-11.log), [CSV](testing/stage7-original-profile-invalid-2026-10-11.csv). Он сохранён, а не заменён успешным результатом. Отдельная ошибка Gradle preparation-task до запуска сервера также сохранена в статусе.

В original CSV ai/rays/candidates/paths=0 означает отсутствие наших instrumentation scopes. Эти счётчики охватывают custom goal/native fire safety и собственный work budget, а не все ванильные/чужие rays и paths. Для общего сравнения смотрите server tick/JFR, различая число реальных projectiles и разные тактические задачи.

## Контроль финального кода, standalone 30/50

После исправления LookControl/BodyRotationControl и orphan bullet guard выполнен ещё один ordinary runServer **без NPCs**, BUILD SUCCESSFUL и штатное сохранение/остановка. [CSV](testing/stage7-final-quick-profile-2026-10-11.csv), [raw](testing/stage7-final-quick-profile-samples-2026-10-11.csv), [лог](testing/stage7-final-quick-profile-2026-10-11.log), JFR `forge-addon/build/stage7-final-quick-profile.jfr`. Использован новый selectable damage-ignoring dummy, как в успешном original-профиле.

| NPC | TPS | Tick p50 / p95 ms | AI p50 / p95 ms | Native projectiles |
|---|---|---|---|---|
| 30 | 20.03 | 9.3753 / 23.8438 | 1.9705 / 4.7520 | 707 |
| 50 | 20.00 | 9.6615 / 18.2387 | 2.0471 / 4.0663 | 1228 |

Ориентир AI p95 ≤2 ms вновь не достигнут. Это две короткие фазы после startup; CPU core percent для первой 103.8% включает несколько JVM threads/JIT и не означает использование одного ядра свыше физического предела. Нет достаточных оснований приписывать разницу p95 только исправлению поворота.

При обратном запуске standalone в том же **synthetic profile-map**, где только что работал NPCs, Forge сообщил missing registry entry `tacznpcs:npc` и автоматически продолжил с backup/removal mapping. Все benchmark actors удалялись до завершения предыдущего прогона; новый модлист и PROFILE_COMPLETE standalone=true подтверждены. Это известное следствие переключения модлиста тестового мира, не пропущенная dependency или падение контроллера. Для чистого сравнения будущих повторов лучше использовать раздельные новые worlds с одинаковым seed. Standalone GameTests и client smoke используют отдельные world directories.

## Воспроизведение

Из forge-addon, после EULA владельца локального сервера:

```powershell
.\gradlew.bat runServer --offline -Pprofile
.\gradlew.bat runServer --offline -Pprofile -PprofileLegacy -PlegacyCompatibility
.\gradlew.bat runServer --offline -Pprofile -PprofileQuick
```

Harness находится только в src/profile и включается флагом; автоматически создаёт synthetic сцену, пишет CSV один раз на фазу и штатно останавливает сервер. Не запускайте его в игровом мире: рабочая директория run/profile и отдельный profile-map предусмотрены специально для теста. Raw/JFR нужно сохранить перед следующим запуском, который перезапишет файлы. Production build выполняется **без** profile/combatTests/clientSmoke.
