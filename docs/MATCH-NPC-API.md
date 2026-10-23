# TacticalNPC match API v1

Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / TaCZ 1.1.8-hotfix2.
Публичный пакет `dev.zuma.tacticalnpc.api` входит в основной production JAR.
TacticalTablet не является зависимостью TacticalNPC; optional NPCs adapter не нужен.

## Подключение из другого Forge-проекта

В `forge-addon` выполнить plain build и публикацию, без test/profile flags:

```powershell
.\gradlew.bat build publishTacticalNpcPublicationToIntegrationRepository --offline --console=plain
```

Maven repository создаётся в `forge-addon/build/repository`; его можно разместить
на Maven-хостинге организации. Публикуется reobfuscated основной JAR и sources,
сторонние моды не вкладываются. Публичный удалённый Maven endpoint этим патчем
не развёртывается. Для локальной интеграции TT:

```groovy
repositories {
    maven { url = uri(providers.gradleProperty('tacticalNpcRepository').get()) }
}
dependencies {
    implementation fg.deobf('dev.zuma.tacticalnpc:tacticalnpcai:0.2.0')
}
```

Путь/URL передать через `-PtacticalNpcRepository=...`, не добавляя бинарники в Git.
TaCZ подключается отдельно существующим способом TT; POM не пытается разрешать
локальные `local:tacz` координаты на машине потребителя. В `mods.toml` TT объявить
обязательные зависимости от `tacticalnpcai` версии `[0.2.0]` и `tacz` версии
`[1.1.8-hotfix2]`, `side="BOTH"`, `ordering="AFTER"`. Установить основной TacticalNPC
и TaCZ на сервер и клиенты. Не включать их классы в shadow/JarJar.

## Фактические сигнатуры

```java
public interface TacticalNpcMatchApi {
    static TacticalNpcMatchApi get(MinecraftServer server);
    void registerPolicy(UUID matchId, MatchNpcPolicy policy);
    SpawnResult spawn(SpawnGroupRequest request);
    void deactivate(UUID matchId);
    int remove(UUID matchId);
    List<UUID> activeNpcIds(UUID matchId);
    int activeNpcCount(UUID matchId);
}
public interface MatchNpcPolicy {
    boolean isActive();
    boolean canAttack(ServerPlayer player);
}
public record SpawnGroupRequest(ServerLevel level, UUID matchId, UUID groupId,
    Vec3 origin, float yaw, float pitch, List<SpawnMemberSpec> members) {}
public record SpawnMemberSpec(ResourceLocation weapon, TacticalNpcEntity.AmmoPolicy ammoPolicy,
    int reserve, OrderState.Kind order, double radius) {}
public record SpawnResult(SpawnResult.Status status, List<UUID> entityIds, String diagnostic) {
    boolean success();
}
```

Все вызовы API, включая getters и смену политики, выполняются на server thread.
Сетевой обработчик TT должен передать работу через `NetworkEvent.Context.enqueueWork`
или `MinecraftServer.execute`. Callback политики также вызывается на этом потоке;
он должен читать готовое состояние матча, без I/O, загрузки чанков и изменения мира.
Ошибки контракта запроса (null, неверный размер/радиус/поток/чужой сервер) вызывают
исключение до спавна. Ожидаемые отказы возвращаются через `SpawnResult.Status`:
`SUCCESS`, `INACTIVE_MATCH`, `DUPLICATE_GROUP`, `INVALID_WEAPON`, `NO_SPACE`,
`ENTITY_REJECTED`. Список UUID неизменяемый и при неудаче match-спавна пуст.

## Порядок работы TT

```java
// Server thread; match is TT's own live session object, not a TacticalNPC type.
var api = TacticalNpcMatchApi.get(server);
UUID matchId = UUID.randomUUID();
api.registerPolicy(matchId, new MatchNpcPolicy() {
    public boolean isActive() { return match.isRunning(); }
    public boolean canAttack(ServerPlayer player) {
        return match.contains(player.getUUID()) && !match.isEliminated(player.getUUID());
    }
});

var members = List.of(
    new SpawnMemberSpec(new ResourceLocation("tacz", "ak47"),
        TacticalNpcEntity.AmmoPolicy.FINITE, 120, OrderState.Kind.ASSAULT, 24),
    new SpawnMemberSpec(new ResourceLocation("tacz", "ai_awp"),
        TacticalNpcEntity.AmmoPolicy.FINITE, 30, OrderState.Kind.DEFEND_AREA, 16)
);
var result = api.spawn(new SpawnGroupRequest(level, matchId, UUID.randomUUID(),
    arenaSpawn, 90, 0, members));
if (!result.success()) {
    // Report result.status()/diagnostic() to TT's match setup; no half-squad exists.
    return;
}
List<UUID> participants = result.entityIds();
int loadedAlive = api.activeNpcCount(matchId);

// At match end, before assigning players to the next session:
api.deactivate(matchId); // Immediate revocation, including bullets already in flight.
api.remove(matchId);     // Can be repeated safely; returns loaded actors discarded now.
```

Для одиночного NPC передать `List.of(member)`, для группы из четырёх — четыре specs.
Список поддерживает 1–16 участников. `groupId` уникален внутри матча; повторный
успешный group ID отклоняется, при неуспешной транзакции его можно повторить.
Для ATTACK использовать существующий `ASSAULT`, DEFEND — `DEFEND_AREA`, STAND —
`HOLD_POSITION`, также доступен `PATROL`. Радиусы проверяет
[OrderState](../ai-core/src/main/java/dev/zuma/tacticalnpc/core/OrderState.java).
Оружие проверяет [GunSupport](../forge-addon/src/main/java/dev/zuma/tacticalnpc/compat/GunSupport.java).
Магазин изначально пуст; FINITE задаёт reserve, INFINITE оставляет штатную
бесконечную политику резерва. Reload, chamber, bolt и cooldown остаются native TaCZ.

## Создание и scoreboard

[NpcSpawnService](../forge-addon/src/main/java/dev/zuma/tacticalnpc/api/NpcSpawnService.java)
проверяет всю группу до добавления сущностей. На участника не более 49 локальных
кандидатов, без chunk tickets и синхронной загрузки: одиночный поиск использует
существующую сетку ±6, групповой ±12 с шагом4. Проверяются loaded chunks, высота мира,
border, физическое тело, fluid/hazards и занятые места. Перед каждым admission
геометрия проверяется повторно. Отказ native entity admission удаляет все сущности
транзакции и созданную ею team. Имя уже допущенного и затем откаченного NPC остаётся
архивной записью существующего `NpcNames`; живого NPC или team это не оставляет.

Каждая группа получает отдельную native team `tnpc_<random UUID hex>`.
Существующие одноимённые teams не присваиваются: генерируется новое имя.
API никогда не перемещает игроков между teams. Членство хранится только vanilla
scoreboard. Администратор может менять его обычным `/team` или `/npc team`.
Очистка удаляет только созданную API team без посторонних участников; явно
присоединённый администратором игрок/чужой NPC и его team сохраняются. Удаление и
повторное создание team вручную в текущей сессии передаёт владение администратору.
Сохранённый marker display name отличает исходную team от одноимённой замены и
после рестарта. Изменение marker администратором также сохраняет team при очистке;
marker подтверждает владение, а членство по-прежнему определяет только scoreboard.

`/npc spawn`, `/spawn npc`, `/tacticalnpc spawn` используют общий сервис с прежней
частичной административной семантикой. Их NPC не получают match binding.
Другие исторические `/tacticalnpc` команды этим API не восстанавливаются.

## Политика, урон и lifecycle

Match-owned NPC выбирает только допустимых игроков этого матча и NPC того же
match ID; native союзность, neutral, creative/spectator, alive, dimension и
остальные существующие проверки продолжают действовать. Отсутствующая, неактивная
или бросающая исключение политика закрывает acquisition, память, DIRECT и
known-area suppression. Каждый native round снова проходит текущую eligibility,
LOS и безопасность. Projectile сохраняет match ID; финальные native pre-damage и
Forge living damage guards запрещают урон после деактивации, смены eligibility или
потери владельца. TT должен обновлять набор участников/выбывших до следующего
допустимого действия. Проверка callback выполняется и для случайного попадания в
постороннего игрока, а не только для выбранной цели.

`deactivate` навсегда закрывает ID, удаляет callback, останавливает загруженные
контроллеры и движение. `remove` также удаляет загруженных NPC и свои teams;
повторный вызов возвращает0. `activeNpcIds/count` означает alive+loaded, даже если
бой уже деактивирован, но `remove` ещё не вызван. Выгруженные акторы не учитываются
как активные. Все измерения сервера покрываются одним registry, без удержания
сильных ссылок на сущности.

Schema7 добавляет nullable `tacticalnpc.ownerMatchId/groupId`; схемы1–6 читаются
с сохранением weapon stack, reserve, order и serviceId. Некорректная match binding
не превращает NPC в независимого. Overworld SavedData `tacticalnpc_matches.dat`
хранит ownership/teams и закрытые ID. Политики и loaded handles не сериализуются.
После рестарта старые IDs закрыты навсегда: TT создаёт новый UUID сессии.
Старые NPC удаляются при native admission, в том числе из ранее выгруженных чанков;
при startup очищаются известные scoreboard teams. Для неизвестного match ID
сохранившаяся привязка сама достаточна для безопасного удаления NPC.

Физическое удаление записи из ещё не загруженного entity-region файла откладывается
до естественной загрузки: API не сканирует файлы и не грузит мир ради очистки.
Небольшие tombstones UUID матчей остаются в SavedData, чтобы закрытые IDs нельзя
было случайно повторно использовать. Политика может заменяться только у ещё
открытого ID текущей серверной сессии. Возобновление старых матчей не поддерживается.

Автотесты и фактический прогон текущего изменения: [MATCH-NPC-VALIDATION](MATCH-NPC-VALIDATION-2026-10-23.md).
