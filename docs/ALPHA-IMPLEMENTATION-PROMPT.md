# Промпт для следующего этапа разработки

Скопируй текст ниже в новую рабочую сессию. Это задание на первый вертикальный срез alpha; последующие механики описаны как контекст, чтобы исполнитель не попытался переписать весь мод за один раз.

```text
Ты работаешь в C:\MCDev\[TACZ] Military NPC над Tactical NPC AI 0.2.0.
Цель — первый играемый alpha-срез: быстро реагирующий, подвижный NPC без пассивных зависаний.

Сначала прочитай AGENTS.md, релевантные .agents/skills/*/SKILL.md,
docs/INTEGRATION-AUDIT.md, docs/ALPHA-CODE-REVIEW-2026-10-11.md,
docs/ALPHA-DEVELOPMENT-PLAN.md и текущий код. Не принимай design intentions за реализацию.
Проверь состояние рабочей директории и сохрани пользовательские изменения.

Стек не менять: Minecraft 1.20.1, Forge 47.4.20, Java 17,
TaCZ 1.1.8-hotfix2, optional NPCs 2.1.0. Не использовать старый tacz_npc.
Сохрани pure ai-core, immutable snapshots, Frame/generation/TTL,
server-thread mutation, единое владение navigation/look/weapon,
native TaCZ shoot/reload/bolt/cooldown и friendly-fire gates.
Не переписывай рабочую интеграцию и не создавай пули вручную.

В этой задаче реализуй:
1. Emergency priority: критическое HP/неотложное действие прерывает старый
   PEEK/HOLD/cover commitment. MovementPlanner не отменяет RETREAT из CombatBrain.
2. REPOSITION с реальным fallback при отсутствии cover; bounded альтернативы,
   backoff на конкретный отказавший destination, явный timeout.
   Не оставляй NPC ждать 160 ticks после единственного плохого пути.
3. Выход из HOLD, если у cover нет usable peek: bounded timeout и переоценка позиции.
4. Contact readiness отдельно от visible: короткий reacquire того же недавнего UUID;
   исчезновение за углом немедленно запрещает DIRECT, но не сбрасывает весь контакт.
5. Fresh weapon readiness каждый tick: cached cooldown=false не блокирует уже
   готовое оружие до следующего planning interval.
6. Aim tracking обновляет точку каждый tick по свежему визуальному Observation,
   независимо от частоты смены тактики. Не подключай AimLead без проверки ballistics.
7. Различай LOS BLOCKED и DEFERRED из-за бюджета: DEFERRED сохраняет контакт,
   но не разрешает выстрел без свежей геометрии. Приоритет active LOS выше cover scans.
8. Добавь понятный Hard профиль: perception 1–2 ticks, reaction 2–3,
   reacquire 1–2, active acquisition 2–4 со staggering, finite turn 16–18°/tick
   как начальные значения для замера. Не объявляй target latency достигнутой без замера.
9. Добавь locomotion policy: pursuit/relocation run, patrol walk, combat strafe,
   peek/retreat. Убери безусловный setSprinting(false).
   Калибруй фактическую pursuit скорость относительно обычного player sprint на
   одной полосе, с учётом native TaCZ ADS/sprint/fire ограничений.
10. Добавь компактную агрегированную диагностику first-contact/reacquire latency,
    причин запрета огня и route recovery, без I/O каждого NPC tick.

Сначала воспроизведи R1/R2/R4/R5 с tools/review/AlphaReviewProbe.java.
Его текущий PASS означает воспроизведение плохого MVP-поведения.
После исправления замени эти ожидания настоящими regression tests нужного поведения;
не оставляй зелёный диагностический тест, который требует наличия дефектов.
Тестируй временные последовательности, не только отдельные snapshots.

Контекст следующих срезов, НЕ реализуй всё сразу:
- Patrol -> Engage/Pursue -> elimination/search timeout -> Return -> Patrol,
  с сохранением route/index и отдельными patrol/combat bounds.
- Hold и Assault как разные миссии, приказ на позицию/группу через runtime capabilities.
- AssaultAssist: пользователь сознательно разрешает скрытую информацию после
  подтверждённого visual contact. Начальная гипотеза p=0.8 один раз на эпизод,
  TTL 8–12 секунд, updates 8–12 ticks; immutable PursuitHint только для navigation.
  Нельзя подмешивать hint в VISION Observation, DIRECT aim или SUPPRESS provenance.
  Перед реализацией assist документируй узкое исключение к AGENTS.md и связанным
  fairness контрактам на основании этого пользовательского задания. Остальные
  ограничения server/native weapons/geometry сохраняются. В этом первом срезе
  скрытые координаты не добавляй.
- SuppressionRegion для ранее известного проёма/области, quotas/native-round checks;
  синхронно обновить permit/aim/native gate вместо замены одной точки в planner.
- Reachable flank и различимые ANCHOR/SUPPRESSOR/FLANKER.
- SmartBrainLib 1.15 для Forge 1.20.1 — отдельный spike, не обязательная зависимость
  этого среза. Нельзя одновременно запускать её executors и TacticalCombatGoal.
- Переиспользовать NPCs 2.1.0 shell через существующий adapter, без нового fork,
  пока нет конкретного доказанного ограничения. Core/weapon/geometry общие.

Делай небольшие вертикальные изменения и проверяй каждое.
Для core запускай bash ai-core/run-tests.sh.
В Windows Git Bash при необходимости:
  "C:\Program Files\Git\bin\bash.exe" -c
  'export PATH="/usr/bin:/mingw64/bin:$PATH"; bash ai-core/run-tests.sh'
Из forge-addon проверь:
  .\gradlew.bat compileJava compileCompatJava --offline
  .\gradlew.bat runGameTestServer --offline -PcombatTests
  .\gradlew.bat runGameTestServer --offline -PcombatTests -PlegacyCompatibility
Для обычного dedicated запуска и perf используй изолированный тестовый мир,
сохрани предыдущие логи/JFR; не принимай EULA за владельца автоматически.
Не выдавай FakePlayer 1v4 за человеческий баланс, GameTestServer за обычный runServer,
старый профиль за новый, UP-TO-DATE за clean rebuild.

Критерии завершения:
- Emergency прерывает peek; REPOSITION выбирает доступную альтернативу либо честно
  завершает попытку; unusable cover не удерживает NPC бесконечно.
- Reacquire быстрее первого контакта; fresh cooldown/aim не ждут tactical replanning.
- Нет огня через стену/дублирования выстрелов/сброса native cooldown и allied damage.
- Движение заметно быстрее в погоне; на плоском стенде записана реальная скорость
  либо явно указано, что ручная калибровка ещё не выполнена.
- Тесты и диагностические сценарии подтверждают новое поведение.
- Обновлены IMPLEMENTATION-STATUS и отдельные alpha release notes с точными
  версиями, файлами, командами, результатами и оставшимися рисками.

В конце сообщи, что реально улучшено, что измерено, что осталось ручным.
Не объявляй всю alpha готовой по завершении только первого среза.
```
