# Этап 08 — STAND

Основа: `3f9e810d0ce7684d78f438c91819c71e4db47539`; владелец явно принял 07 и разрешил пакет 08–14.

Файлы: `entity/TacticalNpcEntity`, `combat/TacticalCombatGoal`, `debug/DebugCommands`; GameTests `StandTests`, `ModeCommandTests`, schema assertions `NeutralTests`; этот отчёт. Core не менялся.

STAND/HOLD_POSITION фиксирует anchor и WAIT без добровольного движения. Первый действительно созданный native projectile либо прошедший damage допустимого противника включает COMBAT. Единственный существующий контроллер использует DEFEND с XZ radius 6, включая проверку тела и всех узлов пути. Видимый контакт, легитимная память и ограниченная identity-only память реального damage удерживают фазу. После окончания контакта включается обычный DEFEND 32 с тем же anchor; DEFERRED не завершает контакт. Новые приказы сбрасывают фазу. Schema 4 сохраняет фазу, schema 1/2/3 HOLD мигрирует в WAIT; live target/damage source не сохраняется. NEUTRAL остаётся отдельной политикой.

Проверки: `bash ai-core/run-tests.sh` — 792 PASS. `python tools/testing/run-gametests.py STAGE --tests StandTests,ModeCommandTests,NeutralTests --offline` — 21/21 PASS, 146.500 s, compileJava и compileCompatJava выполнены в этом графе. Receipt: `forge-addon/build/test-runs/20261019T194346.633748Z-STAGE.json`. FakePlayer подтверждает автоматические сценарии, не человеческий playtest. Production JAR будет собран и проверен один раз после пакета перед передачей владельцу.

Ручная проверка владельца:
1. На копии мира создать NPC; назначить `/npc mode stand <uuid>`. Без противника подождать: исходный anchor, движения нет; reload/aim разрешены.
2. Открыть допустимого противника: до первого реального выстрела WAIT, затем локальный бой; во время долгого контакта NPC и путь не выходят за XZ 6.
3. Закрыть стену: скрытое перемещение цели не должно обновлять точное наведение; дождаться окончания легитимной памяти. Проверить обычный патруль DEFEND 32 с исходным anchor. Отдельно убить последнюю цель: переход возможен сразу.
4. Повторить stand в другой точке и вручную переключить attack/defend/PATROL: старая фаза отменяется. HOLD_POSITION использует фиксированные 6/32 независимо от старого radius.
5. Сохранить/перезапустить отдельно WAIT, COMBAT и DEFEND32: фаза/anchor/inventory сохраняются, live contact заново не создаётся из NBT.
6. В бою со squad убить последнюю видимую цель одновременно с доставкой допустимого delayed report о другой цели: контакт удерживает COMBAT6, зона не расширяется до завершения новой легитимной памяти.

Итоговая проверка на 14 нашла преждевременное завершение STAND перед обработкой доставленного squad report. Регрессия сначала воспроизведена как FAIL (`20261019T212803.114046Z-FAST.json`), затем guard перенесён после receive/memory без расширения контроллера. Затронутый combined STAGE: StandTests (5), SquadTests (2), ModeCommandTests (5), два neutral sound/BURST — **14/14 PASS**, `20261019T213011.529964Z-STAGE-compat.json`. Завершившийся guard не применяет старый immutable frame; обычный DEFEND планирует следующий tick.

Статус автоматических проверок: PASS. Статус владельца: WAITING_PLAYTEST, не ACCEPTED.
