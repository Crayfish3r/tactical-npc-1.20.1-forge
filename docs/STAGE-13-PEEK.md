# Этап 13 — переоценка cached peek

Файлы: `combat/CoverSensor`, `combat/TacticalCombatGoal`; новые `PeekRevalidationTests`, deterministic/diagnostic `CoverTests`; этот отчёт. Scores/roles/reservations и core не переписывались.

Checkpoint `3f9e810` + 08–13. Cover protection TTL 20 ticks и peek validity TTL 4 ticks разделены; standing/body и order boundaries проверяются при использовании. Смещение легитимной угрозы инвалидирует обе проверки. При блокировании старого выхода ищутся следующие ограниченные альтернативы с сохранённым peekCursor. DEFERRED не экспортирует старый peek как CLEAR, не blacklist-ит его как path failure: безопасный HOLD до проверки, native maintenance сохраняется, emergency RETREAT имеет приоритет. Reservations/blacklist/recovery и existing defend/stand path constraints сохранены. Revision заставляет перепланировать изменившийся peek.

Первый STAGE: 15 выполнено, 14 PASS/1 FAIL (`moveReloadAndPeek`, не доказан projectile в PEEK). Failure receipt `20261019T200450.924063Z-STAGE.json` сохранён, не переименован в PASS. Узкий повтор с компактной диагностикой: 1/1 PASS (`20261019T200817.805246Z-FAST.json`). Затем UUID/seed cover fixture зафиксированы без удаления assertions. Повтор полного среза `PeekRevalidationTests,CoverTests,StandTests,DefendTerritoryTests`: **15/15 PASS**, 150.797 s, `20261019T200954.268361Z-STAGE.json`, compileJava/compileCompatJava в графе. Диагностика реального cover сценария: coveredReload=true, peek=true, peekFire=true, 32 PEEK ticks, 9 native projectiles, emergency interrupt подтверждён. Исходный случай не объявлен доказанно исправленным production-дефектом; нестабильность первоначальной случайной fixture остаётся явным ограничением, FULL проверяет детерминированную fixture повторно.

Ручные действия владельца:
1. Во время cover/reload дождаться выбранного peek; поставить стену непосредственно в его выходе. NPC меняет выход либо безопасно ждёт, не идёт циклически в стену.
2. Оставить второй свободный выход: проверить физическое перемещение и реальные native rounds с нового выхода.
3. Создать бюджетную перегрузку: незавершённый peek не считается CLEAR и не создаёт бесконечный blacklist/path spam.
4. Разрушить cover и сместить видимую угрозу: cover и peek перепроверяются отдельно в ограниченные сроки.
5. Повторить внутри DEFEND 4/32 и STAND COMBAT6: добровольные сегменты пути/тело остаются внутри XZ-границ; стена закрывает native fire сразу независимо от TTL сенсора.

Automated повтор PASS; WAITING_PLAYTEST. Production rebuild/verification на 14.

Дополнение14: budget denial candidate/ray/path сохраняет cursor и повторяет доступную работу со следующего tick (при path — с учётом существующего native cooldown), чтобы предыдущая отложенная заявка могла использовать защищённый fair credit. DEFERRED по-прежнему не FAILED. Финальные FULL standalone98/98 и combined110/110 включают эту правку и фактические PEEK projectiles; она также покрыта узким cover/optional8/8.
