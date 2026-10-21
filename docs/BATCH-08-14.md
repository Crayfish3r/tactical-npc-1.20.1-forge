# Пакет 08–14, 2026-10-19

Checkpoint: `3f9e810d0ce7684d78f438c91819c71e4db47539`, исходное дерево чистое.
Владелец явно принял 07 и снял запрет последовательного выполнения 08–14 в одном запуске.
Приёмка владельцем не выводится из автоматического PASS. Публикация/push/merge не разрешены.

| Этап | Состояние | Отдельные результаты и ручные шаги |
|---|---|---|
| 08 STAND | automated 21/21 PASS; WAITING_PLAYTEST | STAGE-08-STAND.md |
| 09 snapshot | automated 13/13 PASS; WAITING_PLAYTEST | STAGE-09-SNAPSHOT.md |
| 10 budgets | automated 12/12 PASS; WAITING_PLAYTEST | STAGE-10-QUERIES.md |
| 11 sector | automated 8/8 PASS; WAITING_PLAYTEST | STAGE-11-SECTOR.md |
| 12 hearing | automated 6/6 PASS; WAITING_PLAYTEST | STAGE-12-HEARING.md |
| 13 cached peek | automated repeat 15/15 PASS; initial failure retained; WAITING_PLAYTEST | STAGE-13-PEEK.md |
| 14 acceptance/profile | core792, FULL98/98+110/110, affected14/14+25/25 PASS; ordinary profile84+84/JFR/build/verifier/restart PASS; performance target unmet; WAITING_PLAYTEST | STAGE-14-ACCEPTANCE.md |

Каждый срез: узкий diff → compileJava/compileCompatJava и относящиеся GameTests;
core целиком при изменении core. Для оптимизационных этапов — измерения до/после.
Нет промежуточной передачи JAR владельцу; production rebuild/verifier выполняются перед
финальной передачей. FULL/JFR/ordinary dedicated и согласованная матрица относятся к 14.
Human 1v1/1v4 и приёмка владельца остаются отдельными обязательными действиями.
