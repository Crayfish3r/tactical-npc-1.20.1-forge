# Forge addon 0.2.0

Minecraft 1.20.1, Forge 47.4.20, Java 17, Gradle 8.8 / ForgeGradle 6.0.54, TaCZ 1.1.8-hotfix2. Официальные закреплённые зависимости находятся в ignored libs/; fetch-dependencies.ps1 проверяет SHA-256. NPCs 2.1.0 нужен для компиляции compatibility artifact, но отсутствует в standalone runtime.

Из этой директории: `gradlew.bat build --offline`. Результаты: `build/libs/tacticalnpcai-0.2.0.jar` и optional `tacticalnpcai-0.2.0-npcs-compat.jar`. На клиент и сервер ставьте основной JAR с TaCZ; для сравнения оригинального NPC добавьте NPCs и compatibility JAR. Старый 0.1.0 одновременно не устанавливать.

`-PcombatTests runGameTestServer` запускает независимый набор. Дополнительный `-PlegacyCompatibility` включает исходный NPC и его тесты. `-PclientSmoke runClient` подключается к локальному серверу 127.0.0.1:25585 и проверяет отрисовку вооружённого NPC и синхронизацию заполненного магазина после native reload; требует запущенного соответствующего тестового runServer. Эти флаги добавляют тестовые исходники, обычный build их исключает.

Самостоятельные source sets: main, compat, compatTest. Main включает чистое ядро из ../ai-core/src/main/java; реальные NPCs imports находятся в compat/compatTest. TaCZ предоставляет LivingEntityMixin/IGunOperator и native tick, собственный runtime их не копирует. Собственных Mixins нет; Access Transformer нужен только optional legacy goal ownership.

Инструкции игроку, сценарии и фактические результаты: [MVP-TESTING](../MVP-TESTING.md). Срезы: [IMPLEMENTATION-STATUS](../docs/IMPLEMENTATION-STATUS.md).

`-Pprofile runServer` — отдельный синтетический benchmark world в run/profile; `-PprofileQuick` оставляет combat 30/50. Для исходного контроллера добавьте `-PprofileLegacy -PlegacyCompatibility`: test-only task устанавливает минимальные factions/loadouts в **run/profile/config**, чтобы исходные goals сами выбирали survival dummy. Эти fixtures не распространяются в production JAR и не изменяют configs игрового мира. Методика и ограничения: [PROFILE-REPORT](../docs/PROFILE-REPORT-2026-10-11.md). После обычной сборки `tools/verify-production-artifacts.py` проверяет Java 17, отсутствие тестов/вложенных зависимостей и NPCs-ссылок в основном JAR.
