# Compatibility audit: independent NPC / optional NPCs 2.1.0

## SavedData lifecycle / names audit, 2026-10-24

Independent Forge layer, same pinned dependencies and hashes below. `javap -c -p`
against Forge47.4.20 mapped official1.20.1 reconfirmed:

- `Entity.RemovalReason.shouldDestroy()` is true for KILLED/DISCARDED and false for
  UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER and CHANGED_DIMENSION. Existing removal,
  tracking and dimension hooks retain instance identity checks; no new Mixin/event scan.
- `DimensionDataStorage.readSavedData` catches `Exception`, logs and returns null;
  `computeIfAbsent` then calls the supplier. The guarded supplier refuses an empty
  replacement when the overworld `data/<registry>.dat` exists/unreadable. Version/type
  errors come from the strict schema loader before this fallback.
- `PlayerTeam.setNameTagVisibility(Team.Visibility)` and `Team.Visibility.NEVER`
  use native scoreboard sync/persistence. Only newly reserved API teams are changed.
- `EntityRenderer.render` posts RenderNameTagEvent; ALLOW bypasses shouldShowName,
  then calls virtual `renderNameTag`. Both overrides in the existing Dist.CLIENT
  TacticalNpcEntity renderer prevent names, including crosshair selection. No
  client symbol was added to server entity/registry classes.

GO for these pinned APIs; TaCZ operation, core contracts and optional NPCs signatures
are unchanged. Schema migration/one-use filter limits: [SavedData migration](SAVED-DATA-MIGRATION.md).
Executed validation: [2026-10-24 report](SAVED-DATA-VALIDATION-2026-10-24.md).

## Match API audit, 2026-10-23

Independent Forge production layer only; no TacticalTablet classes, new library or Mixin.
Pinned TaCZ/NPCs SHA256 rechecked unchanged against the values below; JDK17.0.19,
Minecraft1.20.1 / Forge47.4.20. `javap` on the pinned TaCZ JAR reconfirmed
`GunFireEvent(LivingEntity,ItemStack,LogicalSide)` with `getShooter()` and
`EntityHurtByGunEvent.Pre(Entity,Entity,LivingEntity,ResourceLocation,ResourceLocation,float,Pair,boolean,float,LogicalSide)`
with inherited `getBullet/getHurtEntity/getAttacker/getLogicalSide`.
The existing audited native per-cartridge event and pre-damage cancellation path is reused.

`javap` on the cached Forge47.4.20 mapped JAR reconfirmed
`ServerPlayer.hasDisconnected()`, Forge `Entity.isAddedToWorld()/getPersistentData()`,
`ServerChunkCache.getChunkNow(int,int)`,
`Scoreboard.removePlayerFromTeam(String)/getPlayersTeam(String)`.
Lifecycle uses post-admission `onAddedToWorld`, existing removal hooks and overworld
`SavedData` via the same `computeIfAbsent` pattern as `NpcNames`.
No deserializer-thread registry/team mutation; old/corrupt ownership is checked on admission.

GO for these independent public/event APIs. Optional NPCs signatures were not changed;
its existing source set remains separate. Native geometry, reload/bolt, rounds,
ordinary disk restart and external published-JAR consumer evidence are recorded in
[MATCH-NPC-VALIDATION](MATCH-NPC-VALIDATION-2026-10-23.md).

## Combat upgrade audit, 2026-10-22

Pinned artifacts rehashed unchanged: TaCZ 1.1.8-hotfix2 SHA256
`2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2`;
NPCs 2.1.0 SHA256 `2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE`.
Minecraft 1.20.1 / Forge 47.4.20 / Java 17 are unchanged. No dependency or Mixin added.

Additional `javap -c -p` evidence from these binaries is retained locally in
`forge-addon/build/upgrade-{bullet,script,gun}-bytecode.txt`:

- `BulletData.getLifeSecond/getGravity/getFriction/getBulletAmount` exist. Bullet construction
  converts life seconds to integer ticks with `*20`. `EntityKineticBullet.tick` moves by the old
  velocity, multiplies velocity by `(1-friction)`, then subtracts gravity. Water changes drag/gravity;
  the new dry-air predictor is an estimate, not a replacement for native projectile physics.
- `ModernKineticGunScriptAPI.shootOnce` reads live cached `GunProperties.AMMO_SPEED`, applies
  `AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER`, divides by 20, then passes speed to native shooting.
  The adapter uses those units, live attachment speed, indexed gravity/friction/life. Supported
  maintenance-only scripts cannot supply an unaudited custom ballistic modifier.
- The native `lambda$shootOnce$2` posts one cancellable server `GunFireEvent` per cartridge before
  creating its `bullet_amount` pellet loop. A monotonically increasing authorization token, confirmed
  only by an actual uncanceled projectile join, counts one cartridge for all its pellets. A later
  canceled event consumes no tactical pressure quota. Distinct BURST cartridges keep separate tokens,
  including when delivered in the same game tick. Every native event retains fresh safety checks.
- `GunProperties.EFFECTIVE_RANGE` still means damage falloff onset. It no longer clamps firing to
  the 14–64 block behavioural preference. The immutable weapon snapshot now exposes preference,
  falloff, and a bounded kinetic reach separately. Actual damage, spread, ammo and reload stay native.
- Acquisition still pages the existing level player list / lifecycle NPC registry. LOS now preflights
  all crossed loaded chunks with a bounded chunk-grid DDA, including thin corner crossings, before
  `Level.clip`; cover rays use the same guard. It creates no chunk ticket and never calls `getChunk`
  to search. Native path creation remains budgeted and uses loaded navigation regions.
- Suppression exempts its intended eligible hostile UUID from the conservative living-entity sector,
  just as DIRECT exempts its target. This lookup does not read hidden coordinates/velocity. All other
  living entities, fresh block rays, actual barrel direction and final allied pre-damage guard remain.

User explicitly authorized sustained configurable pressure in place of the historical six-round /
40-tick skill guidance, and Unfair exceptions only to AGENTS constraints 5/9. `UNFAIR_HINT` is a
separate transient memory, quantized region with zero velocity, never VISION or a visual squad report.
Hard restores legitimate knowledge and the saved ammo policy. Profile changes invalidate the old
controller frame and cancel an in-progress native reload before applying the changed reserve policy.
Pinned Forge47.4.20 `ConfigFileTypeHandler.reader` builds a synchronous autosaving FileConfig.
Config changes are visible on disk (standalone fixture memoryTicks160 instead of default320);
Hard/Unfair commands therefore persist to the world serverconfig.
Current measurements and limitations: [combat upgrade report](COMBAT-UPGRADE-2026-10-22.md).

Audited 2026-10-10 from official binaries, not legacy tacz_npc sources.

Текущий срез 0.2.0: собственный `tacticalnpcai:npc` работает через TaCZ без NPCs. NPCs 2.1.0 вынесен в отдельный `npcs-compat` JAR (`dev.zuma.tacticalnpc.npcs`); в общем коде нет импортов NPCs. Разделы MVP ниже сохраняют исторический аудит 0.1.0. Фактические срезы и логи: [IMPLEMENTATION-STATUS](IMPLEMENTATION-STATUS.md).

Первый независимый срез проверен standalone GameTests 7/7 и клиентом rendered=51. Текущая регрессия: Java 72 PASS, combined Forge 34/34 и standalone 25/25, новый графический client smoke rendered=691/armed/loaded/noNpcs PASS, реальные chunk unload/load 30 NPC и schema 2 disk save/restart. Оба production JAR собраны/reobfuscated и проверены по содержимому. Запуски Minecraft — ForgeGradle userdev; установка distribution JAR в отдельный launcher и human PvP ещё ручные. Это не релизная проверка баланса.

## Verified boundaries for 0.2.0

- `IGunOperator.fromLivingEntity(LivingEntity)` работает на собственной PathfinderMob благодаря native TaCZ LivingEntityMixin. Native `initialData`, `draw`, cache, reload/bolt/cooldown и synchronization проверены; tick обслуживания не копируется из AutoGunOperator.
- `AbstractGunItem.canReload(LivingEntity,ItemStack)` проверяет резерв обычного магазина; `IGun.hasInventoryAmmo` относится к inventory-fed и возвращает false для обычного магазина. Finite reserve использует native DummyAmmo, тест подтверждает расход ровно 7 патронов без пополнения из воздуха.
- `GunData.getFireModeSet()` определяет режим, AUTO → SEMI → BURST из реально доступных. `GunFireEvent` публикуется для каждого native BURST round: каждый раунд проходит повторную геометрию и safety gate.
- Lua maintenance разрешён только для проверенного xmag reload: ресурс `tacz:scripts/xmag_reload_logic.lua`, SHA-256 `5F25D34F7866FA928D2C937D52A2A5E14C47ADCE1CBF727D5B58114F33755F46`. Hash читается при server start/datapack sync, вне боевого тика. Неизвестные hooks (`shoot`, spread/property changes и прочие) запрещены.
- Обычный `ModernKineticGunItem` с magazine/chamber/bolt поддержан. Charge, inventory-fed, heat, RPG, explosive/incendiary projectile/attachments и неизвестный gun item implementation заблокированы с диагностикой.
- Native `EntityHurtByGunEvent.Pre` cancellable и предшествует damage/ignite: allied living hit для `tactical_projectile` отменяется даже после снятия AI-тега. Предварительный сектор учитывает максимальную cached INACCURACY, дробь и продолжение за целью до 128 блоков, максимум 32 living candidates с fail-closed saturation. Вероятностный разброс не имеет абсолютной конусной границы; финальный damage guard дополняет сектор. Намеренный wallbang не поддержан. Неизвестные скриптовые боеприпасы не объявлены безопасными.
- Vanilla `PathNavigationRegion` bytecode использует `ChunkSource.getChunkNow`; candidate/executor проверяют загруженные чанки, collision/fluid/hazards и `Path.canReach()`. Укрытие включает torso/eye и запас на ширину тела/допуск прибытия. Полная матрица сложных карт остаётся отдельным испытанием.
- `Entity.setRemoved` final; собственный runtime очищается через `remove`, `removeAfterChangingDimensions` и Forge leave-event. NBT хранит конфигурацию, loadout и service identity, не живую цель или navigation path.
- Core получает только зарегистрированные Observation. Скрытая позиция/скорость не обновляется; проверка player alive/removed/dimension перед исполнением не передаёт координаты планировщику. Публичные API и vanilla Access Transformer достаточны; собственных Mixins нет.
- Этап 6 проверяет `CommonGunIndex.getType()` по default gunpack: pistol/SMG/rifle/sniper/shotgun/mg → соответствующие profiles. DMR доступен как отдельный профиль для метаданных dmr, но default DMR-образец пока не проверен. Неизвестный type использует консервативные 6..16 блоков после всех технических блокировок. Тип поведения не означает автоматическую поддержку неизвестного скрипта.
- `GunProperties.EFFECTIVE_RANGE` имеет тип Float. `EffectiveRangeModifier.initCache` берёт первую distance из ExtraDamage.damageAdjust: это начало падения damage, **не максимальная баллистическая дальность**. Рабочая дальность ограничивается меньшим из этого значения и поведенческого профиля; projectile speed/lifetime не меняются. Паузы очередей добавляют ожидание поверх native cooldown, не отменяют его. FINITE блокирует native reload data с isInfinite=true.
- Приказы используют immutable OrderState, отдельный pure OrderPlanner и NBT schema 2 (anchor/order/radius). Schema 1 читается с локальным якорем; будущие неизвестные schema отключают tactical tag. Cover/peek учитывает границы приказа. Движение к известной точке нормализуется на проверенную поверхность; первое падение после spawn не считается недостижимым путём.
- Squad reports содержат только первоначальное зрительное Observation и UUID, доставляются через 10 тиков в пределах 48 блоков, не обновляют скрытую координату. Pure board отделён по ServerLevel с weak key, не держит world/entity references. Claims/roles ограничены TTL и очищаются при lifecycle; optional NPCs adapter по умолчанию не подключается к squads.
- Ordinary dedicated profile выполнен отдельно от GameTest pacing. JFR показал затраты сектора; запросы до готовности оружия/поворота убраны, per-native-round safety сохранён. Dormant activation — только proximity boolean, не наблюдение скрытого Player; native gun tick не пропускается. Точные результаты и ограничения: [IMPLEMENTATION-STATUS](IMPLEMENTATION-STATUS.md).

## Supported target boundary, stage 02, 2026-10-19

The shared `CombatTargets` filter accepts Player or an entity whose registered
`NpcRuntimeAdapter.entity()` is that exact entity. The independent TacticalNpcEntity
provider and the existing optional provider (`getClass()==AutoGunOperator.class`)
are the only providers; arbitrary LivingEntity/gun holders/subclass-name guesses are excluded.
Pinned binary signatures were rechecked with `javap -p` against NPCs 2.1.0 (SHA below):
AutoGunOperator extends PathfinderMob, implements IGunOperator, and exposes
`isAlliedTo(LivingEntity)`, `isOpposedTo(LivingEntity)`, `getTemplate()`.
No new NPCs imports or hooks were added to main; existing adapter ownership is retained.

RuntimeRegistry keeps only UUIDs of loaded supported NPCs, per weak ServerLevel key,
from join/leave/unload events. Vision pages at most 16 combined Player/NPC identities and
four LOS candidates, with retained DEFERRED cursor; activation pages at most 16 NPCs per
20-tick check, grants only a proximity boolean and retains its previous state on deferral.
For a finite stable combined list of N entries with four available rays, the existing
`4*ceil(N/16)` fresh-cycle bound remains; no full-world entity scan/sort is introduced.
UUID resolution/lifecycle/alliance validation reads no hidden position or velocity.
Dead/removed/other-dimension contacts invalidate generation, memory and native fire gates;
hearing remains Player-only. Our tagged native projectile pre-damage guard also rejects
unsupported victims, while native faction/team alliances remain authoritative.

## Server head rotation ownership, 2026-10-11

Confirmed in the pinned Forge/Mojang binary: `Mob.createBodyControl(): BodyRotationControl`, `Mob.tickHeadTurn(float,float): float`, `BodyRotationControl.clientTick(): void`, and final field `Mob.bodyRotationControl` / SRG `f_21361_`. `LookControl.tick()` applies a navigation clamp after `rotateTowards`; later `Mob.tickHeadTurn()` invokes a second head clamp through BodyRotationControl. A new GameTest reproduced a 105-degree head jump despite a 10-degree turn limit ([failed run](testing/stage7-first-look-regression-2026-10-11.log)).

`TacticalLookControl` caps its final head/pitch change. `TacticalBodyControl` delegates native body motion but preserves the server head angle, leaving head ownership to LookControl. The independent entity uses the public/protected `createBodyControl()` extension. The optional NPCs adapter captures/restores the exact original body controller together with LookControl; one vanilla Access Transformer (`public-f Mob f_21361_`) enables this swap. No third-party method patch or Mixin is introduced. Client body behavior remains vanilla. Dedicated regressions check both runtimes and restoration.

Pinned `EntityKineticBullet.onHitEntity` resolves `Projectile.getOwner()` and passes a nullable LivingEntity attacker to `EntityHurtByGunEvent.Pre`. An orphan tagged `tactical_projectile` now fails closed: if its owner/runtime cannot resolve, all of its kinetic damage is cancelled because alliance cannot be established. The event regression posts the real native cancellable event with null attacker; it is not represented as an actual simulated collision after unload.

## Pinned versions

Alpha-1 sprint boundary verified again against the pinned TaCZ JAR on 2026-10-11: `IGunOperator.getProcessedSprintStatus(boolean): boolean`, `getSynSprintTime(): float`. `LivingEntitySprint.getProcessedSprintStatus` rejects sprint while aiming and during reload except finishing; `LivingEntityShoot` returns `IS_SPRINTING` while native `ShooterDataHolder.sprintTimeS > 0`. [Current javap evidence](testing/alpha1-native-sprint-audit-2026-10-11.txt). MovementExecutor uses the processed status; GunController readiness and every native round retain the sprint gate. No native timers/cache fields are reset or copied. Hard responsiveness and locomotion changes are documented separately in [alpha-1 release notes](ALPHA-1-RELEASE-NOTES-2026-10-11.md); older measured results below are historical, not measurements of this profile.

| Component | Exact version / artifact | SHA-256 |
|---|---|---|
| Minecraft | 1.20.1 | official Mojang mappings |
| Forge | 47.4.20 | net.minecraftforge:forge:1.20.1-47.4.20 |
| Java | 17, tested Microsoft OpenJDK 17.0.19 | Java 17 bytecode |
| NPCs | tacznpcs-2.1.0-1.20.1.jar | 2593AA66CD56C08538A91ED3D274C054B043F0C6A4C0FF51A37DE310D60734EE |
| TaCZ | tacz-1.20.1-1.1.8-hotfix2.jar | 2CBB89F44333D2EB4E825D86D789FF119B272FF75184390B55BAFD4F0E7F10C2 |

Official version IDs: NPCs Nvq2rJvB, TaCZ AzCBJlex. Downloads:

- https://modrinth.com/mod/tacz-npcs/version/Nvq2rJvB
- https://cdn.modrinth.com/data/A3oZ8vhW/versions/Nvq2rJvB/tacznpcs-2.1.0-1.20.1.jar
- https://cdn.modrinth.com/data/SzzJttH8/versions/AzCBJlex/tacz-1.20.1-1.1.8-hotfix2.jar

Local originals: forge-addon/libs/. Not embedded in the add-on. ForgeGradle 6.0.54, Gradle wrapper 8.8.

## Binary evidence

Commands: jar tf, javap -p -c, CFR 0.152. Local generated inspection files: tools/audit/ (ignored development artifacts).

| Check | Verified class / method / result |
|---|---|
| Metadata | NPC META-INF/mods.toml: modId tacznpcs, version 2.1.0, Forge [47,), Minecraft [1.20.1,1.21), MIT, author Corrinedev. TaCZ modId tacz; manifest Implementation-Version 1.1.8-hotfix2. |
| Entity | mod.cdv.tnpc.entity.AutoGunOperator extends PathfinderMob implements IGunOperator. EntityRegistry.NPC_REGISTER registers tacznpcs:npc. |
| Construction | public AutoGunOperator(EntityType<? extends PathfinderMob>, Level); public static AutoGunOperator create(Level, String). NBT keys id and template. |
| Scheduler | protected registerGoals() / SRG m_8099_()V. Ordinary vanilla goalSelector and targetSelector; no SmartBrainLib. |
| Original gun goal | public FireAtNearestTargetGoal<AutoGunOperator> fireAtNearestTargetGoal, priority 3. FireAtNearestTargetGoal.tick() / m_8037_()V calls reload, shoot and bolt. |
| Competing actions | targetSelector also contains melee, avoidance, alerts, hurt/nearest targeting. AutoGunOperator.tick() / m_8119_()V separately has melee/look/move guarded by getTarget()!=null. |
| Maintenance | private AutoGunOperator.onTickServerSide()V updates draw/cache/bolt/reload/aim/sprint/cooldown/synchronization. Must remain active. Does not independently start ranged fire or reload. |
| Gun actions | IGunOperator.shoot(Supplier<Float> pitch, Supplier<Float> yaw): ShootResult; draw(Supplier<ItemStack>): void; reload(): void; bolt(): void; aim(boolean): void. |
| State | getSynShootCoolDown(): long; getSynDrawCoolDown(): long; getDataHolder(): ShooterDataHolder. ShooterDataHolder.reloadStateType and isBolting; ReloadState.StateType.isReloading(): boolean. |
| Ammo | IGun.getCurrentAmmoCount(ItemStack): int; hasBulletInBarrel(ItemStack): boolean; useInventoryAmmo(ItemStack): boolean; hasInventoryAmmo(LivingEntity,ItemStack,boolean): boolean; GunData.getBolt(): Bolt. |
| Outcomes | SUCCESS, COOL_DOWN, NO_AMMO, NOT_DRAW, NEED_BOLT, IS_RELOADING, IS_BOLTING, etc. Native LivingEntityShoot validates cooldown/draw/ammo/reload/bolt before actual gun implementation. |
| Reserve | NPC needCheckAmmo() always returns false. consumesAmmoOrNot() delegates to TaCZ and is true for NPCs. Magazines consumed; unlimited reload reserve is original NPC behavior. |
| Factions | public isAlliedTo(LivingEntity), isOpposedTo(LivingEntity), getTemplate(): NPCTemplate. Add-on respects aligned factions and scoreboard allies. Tagged NPCs target visible eligible survival players even when template opposed omits players. |
| Appearance/loadouts | Original entity, skin, equipment, animation and network packets retained. Actual config names are singular: config/tacznpc_loadouts.json, tacznpc_factions.json, tacznpc_structures.json. |
| Legal | NPC metadata MIT. TaCZ metadata GPL3 / CC BY-NC-ND 4.0. Add-on invokes APIs without bundling dependency code/resources. Modpack redistribution requires separate review. |

## Historical MVP 0.1.0 ownership without add-on Mixins

Server LivingTickEvent runs before vanilla Mob AI. For exact AutoGunOperator with tactical_ai tag, save original goals/priorities, stop/remove both selectors, install FloatGoal and one TacticalCombatGoal. Observed target UUID stays private; vanilla getTarget() remains null to suppress the extra hardcoded melee/turn path.

Forge Access Transformer changes only vanilla Mob.goalSelector (f_21345_), Mob.targetSelector (f_21346_) and Mob.lookControl (f_21365_) visibility. No NPC method patch. TacticalLookControl preserves pitch between ticks (vanilla resets it to zero); bounded body yaw prevents head clamping during turns behind a strafing NPC. The original LookControl is restored with the original goals. Original goals restored on tag removal, config disable, death, unload and controller failure. Weak keys/values avoid retaining unloaded entities; restoration state lives with the installed goal. Unsupported subclasses rejected. Exact dependency versions gated by mods.toml.

Everything executes on the Minecraft server thread, asserted at mutation boundaries. Pure core receives immutable snapshots. No workers. Active visibility checked each tick, idle acquisition every 10 ticks with at most 16 candidates / 4 rays. Fire-corridor queries cap at 32 entities and reject saturated queries. No pathfinding requests in MVP.

## Historical MVP 0.1.0 risks and scope

- Native spread, recoil, bolt, reload and cooldown retained; at most one shot request per tick, actual TaCZ API is final authority.
- No hidden-player tracking or prediction. Reaction resets on lost sight. Conservative corridor rejects all other living entities.
- Ordinary magazine-fed kinetic guns are the MVP contract. RPG, inventory-fed, charge and explosive weapons fail closed; custom gunpacks unvalidated.
- Another add-on changing goals or forcing targets may conflict. Start with the pinned minimal mod set.
- Native onTickServerSide cache work is upstream behavior. 30+ NPC profiling still required before production.
- Userdev runs require remapping TaCZ's shipped SRG refmap to Mojang names, configured in build.gradle. This is not a TaCZ patch.

## Historical MVP 0.1.0 executed validation, 2026-10-10

- Java Core: `bash ai-core/run-tests.sh` via Git Bash, PASS 35 policy checks.
- Pinned Forge GameTestServer: one required lifecycle/combat test passed, BUILD SUCCESSFUL. Real NPC/TaCZ APIs produced successful shots and accepted reloads; bounded turning, movement, reaction, wall/corridor blocking, target death and scheduler restoration checked. Test-only pacing is excluded from the release JAR.
- Ordinary `runServer`: reached Done on localhost:25585 after explicit user EULA acceptance. Spawn/equip commands and tag off/on control restoration worked. Clean console stop saved all dimensions.
- Logs: [GameTest](testing/gametest-2026-10-10.log), [dedicated server](testing/dedicated-server-2026-10-10.log).
- No graphical-client/human duel, 1v4 or >=30 NPC profiling performed. Separate bolt-action/custom packs, complex terrain and reconnect/persistence checks remain manual.

Installation and full verification commands: [MVP-TESTING.md](../MVP-TESTING.md).

## Historical 0.1.0 independent NPC feasibility audit, 2026-10-10

At the time of the 0.1.0 architecture review both dependency SHA-256 checks matched. That add-on still required NPCs: `mods.toml` marked `tacznpcs` mandatory and shared production classes imported `AutoGunOperator`. Removing the JAR or changing metadata alone was insufficient. This limitation is resolved by the separate artifacts in 0.2.0.

Additional binary inspection against `forge-addon/libs/tacz-1.20.1-1.1.8-hotfix2.jar`:

```powershell
javap -classpath 'forge-addon/libs/tacz-1.20.1-1.1.8-hotfix2.jar' -p -c com.tacz.guns.api.entity.IGunOperator
javap -classpath 'forge-addon/libs/tacz-1.20.1-1.1.8-hotfix2.jar' -p -c com.tacz.guns.mixin.common.LivingEntityMixin
javap -classpath 'forge-addon/libs/tacz-1.20.1-1.1.8-hotfix2.jar' -p com.tacz.guns.resource.pojo.data.gun.GunData
```

- `IGunOperator.fromLivingEntity(LivingEntity)` bytecode performs a cast to `IGunOperator`; it is not an NPCs factory.
- TaCZ's `LivingEntityMixin extends Entity implements IGunOperator, KnockBackModifier`. Its inspected `onTickServerSide(CallbackInfo)` services reload, aiming, sprint, crawl, bolt, melee scheduling, speed, heat and synchronized cooldown/state data.
- `GunData.getFireModeSet(): List<FireMode>` is available for valid mode selection. Current debug equipment creation unconditionally selects SEMI.
- These are verified TaCZ internals/API signatures, not new add-on Mixins or proof that an independent entity already works. A custom `PathfinderMob` integration must validate initialization, property cache, draw, ammo, firing, bolt, reload, synchronization and lifecycle without NPCs installed. Avoid copying NPCs' weapon maintenance tick into it before checking native TaCZ maintenance ownership.

Conclusion: an independent NPC runtime using TaCZ has a binary-supported path worth testing. Keep the current NPCs adapter during contract extraction; prove a standalone vertical slice before porting larger tactical systems. See [architecture review](ARCHITECTURE-REVIEW-2026-10-10.md) and [development instructions](DEVELOPMENT-PLAN.md).

## Stage 07 actual-damage / ownership audit, 2026-10-19

- Pinned mapped Forge 47.4.20 binary confirms `LivingEntity.actuallyHurt(DamageSource,float): void`
  is protected/overridable. The independent entity calls super and compares health + absorption
  after Forge hurt/damage cancellation and reductions. A pre-event, `hurt()` boolean or
  `lastHurtByMob` alone never grants reactive hostility. No new Mixin/event-order dependency.
- `DamageSource.getEntity()` identifies the causing entity; for a direct `Projectile` the actual
  `Projectile.getOwner()` must be that same supported living attacker. Unknown/unresolved owner,
  self, ordinary mobs, cross-level/dead/creative/spectator sources cannot grant a target.
- Rechecked `javap -c -p EntityKineticBullet` from pinned `libs/tacz-1.20.1-1.1.8-hotfix2.jar`:
  `createDamageSources(MaybeMultipartEntity)` normally supplies bullet as direct entity and
  `getOwner()` as causing entity to `ModDamageTypes.Sources.bullet/bulletVoid`. Magic uses the
  same bullet/owner pair; PRETEND_MELEE_DAMAGE_ON uses owner directly. This is damage-source
  inspection only, not a new unsupported weapon/script allowance. Raw evidence local:
  `forge-addon/build/stage07-bullet-api.txt`.
- Damage grants only a transient UUID. Exact aim points enter memory only after budgeted CLEAR
  LOS; real-hit rearward response can sample LOS without FOV/acquisition delay, while turn limits,
  native weapon/ADS/reload/cooldown and final per-round safety remain. No attacker coordinates
  are serialized; schema 3 persists only neutral policy plus the existing saved state.
- Personal exception is evaluated by the same `CombatTargets.eligible` used by vision and the
  existing TaCZ pre-damage guard. It does not change faction/team alliances; any other ally is
  protected. Existing optional spawn/operator/equipment code is unchanged.

## Stages 08–14 native boundaries, 2026-10-19/20

Pinned `javap` inspection confirms the existing `IGunOperator.draw`, `getCacheProperty`,
`getDataHolder`, `shoot/reload/bolt/aim` interfaces; no invented API, new dependency or Mixin.
Native property maps remain mutable: metadata caching never freezes ammo, cooldown, sprint,
range/spread or unsupported script/charge/heat/explosion gates. The already registered resource
verification at startup/datapack reload increments an in-memory generation, not a guessed reload hook.

Hearing observes successfully joined `EntityKineticBullet` at LOWEST Forge join priority,
ignores canceled joins and resolves the genuine living owner. Pinned bullet type builder calls
`noSummon`, `noSave`, `fireImmune` (`m_20698_`, `m_20716_`, `m_20719_` in the pinned mappings):
loading a saved bullet cannot fabricate a new gunshot. Already flying projectiles remain distinct
from uncreated BURST rounds; the existing native event/safety ownership is retained.

Independent NBT schema4 adds STAND phase only; schema1/2/3 migration preserves configured
order/radius128 and does not silently neutralize old entities. No live target/path/LOS is serialized.
The optional adapter source/equipment/spawn integration is unchanged; common tactical controller
and tagged `/equip` use explicit tick draw maintenance. Untagged native optional equipment keeps
the prior direct draw path. Runtime failures stay fail closed.

## Native teams / command update, 2026-10-20

Pinned mapped Minecraft/Forge bytecode confirms `LivingEntity.getTeam`,
`Entity.getScoreboardName`, `Scoreboard.addPlayerTeam/addPlayerToTeam/removePlayerFromTeam`,
`Team.isAlliedTo`, and `ScoreboardSaveData.save/load`. NPC entries use entity UUIDs;
players use scoreboard names. Membership is persisted by the ordinary server scoreboard,
not duplicated in independent entity schema5. Native packet team names use `readUtf()`;
the new command bounds names to64 characters and rejects blank/control/surrounding whitespace.
Local inspection: `forge-addon/build/team-native-bytecode.txt`.

`EntityStorage.loadEntities` uses `thenApplyAsync` on `entityDeserializerQueue` in this pinned
version (`forge-addon/build/team-entity-storage-bytecode.txt`). Schema1–4 decoding therefore
only records a pending migration; native team mutation occurs at explicit server activation.
Pending migration survives resave. Native membership wins over archived legacy faction;
schema5 does not resurrect a removed scoreboard team. Original faction/squad UUID are archived.

Independent entities use native team membership for ally checks and transient per-team squad
boards. Team changes invalidate the controller before the next native gate; suppression also
revalidates subject eligibility without inspecting hidden coordinates. No TaCZ API, weapon
cooldown/damage rule, dependency, optional adapter class or Mixin was added. Native BURST
regression counts actual joined projectiles while the target joins/leaves an allied team.

## Action routes, 2026-10-22

Pinned mapped Forge47.4.20 bytecode was inspected with `javap -c -p` for
DoorBlock.setOpen(Entity,Level,BlockState,BlockPos,boolean), DoorInteractGoal,
GroundPathNavigation, MoveControl, LivingEntity.onClimbable/handleOnClimbable/travel,
and PathNavigationRegion. The latter uses ChunkSource.getChunkNow, including null
unloaded slots; native local walking therefore does not request missing chunks.
New geometry reads first require hasChunkAt, and no production path adds tickets.
Vertical movement uses native MoveControl/JumpControl/gravity/ladder collision;
no teleport, noClip, bullet spawning, new dependency or Mixin.

Wooden door interaction uses native setOpen and verifies both halves plus body
clearance. Forge getMobGriefingEvent and the cancellable RouteInteractions.OpenDoorEvent
gate NPC interaction. Player-only events are not impersonated; claim mods needing
their own permission API require a DoorAdapter. Exact scope, third-party reference
commits/licenses and execution receipts: [ACTION-ROUTES](ACTION-ROUTES-2026-10-22.md).


## Unified commands / names / Hard, 2026-10-22

Pinned mapped Forge47.4.20 was inspected with `javap -c -p` for `ServerLevel`,
`PersistentEntitySectionManager`, `ServerLevel$EntityCallbacks`, and `Entity`.
`ServerLevel.addEntity` calls `Entity.onAddedToWorld()` only after successful native admission;
its manager posts the cancellable join event and rejects duplicate UUIDs first. Chunk entity
loads call the same admission hook after deserialization. The callback runs on server activation,
so NBT decoding never allocates names or mutates SavedData/scoreboard.

`DimensionDataStorage.computeIfAbsent(Function<CompoundTag,T>, Supplier<T>, String)` and
`SavedData.save(CompoundTag)` are the verified1.20.1 APIs. Names use overworld data for all dimensions.
Dimension transfer copies entity NBT before `addDuringTeleport`; an admitted destination entity
can precede native tracking visibility (`ServerLevel.getEntity(UUID)` can still return null).
The transient name index therefore uses weak per-instance handles, cleared on removal and guarded
against an older instance's late leave callback. Tracking end can precede actual removal; the
weak handle survives retracking of that same still-loaded instance. Actual remove/dimension
removal clears it. It adds no chunk loading or Entity ownership.
Raw local evidence: `forge-addon/build/command-{server,entity,admission,callback}-api.txt`.

Verified `ArgumentTypeInfos.registerByClass` plus `SingletonArgumentInfo.contextFree` supplies
client command-tree serialization for the shared name/radius/vanilla-selector argument.
TaCZ `TimelessAPI.getAllCommonGunIndex()` supplies actual server gun-ID suggestions; existing
`GunSupport.create`, native operator draw/cancelReload and runtime.onEquip are retained in a single
operation. No new native shooting path, dependency, NPCs signature or Mixin.

Ordinary config Loading/Reloading migrates legacy combat overrides to one Hard default set;
GameTest fixture tuning is isolated by the existing selected-launcher property after initial Loading.
[Current commands/migration](COMMANDS.md); [execution evidence](COMMAND-UNIFICATION-2026-10-22.md).

Client suggestion audit: target nodes have an explicit custom provider. Pinned `SuggestionProviders.getName` maps unregistered providers to `minecraft:ask_server`; the serialized client command tree therefore asks the server for persistent NPC names. A GameTest round-trips ClientboundCommandsPacket through FriendlyByteBuf and checks that provider, in addition to dispatcher completion results. Raw API evidence: `forge-addon/build/command-suggestions-api.txt`.

## Independent NPC skins, 2026-10-22

Pinned mapped Minecraft1.20.1 / Forge47.4.20 `javap -c` confirms that
`PlayerModel<T extends LivingEntity>` extends `HumanoidModel<T>` and calls its `setupAnim`
before copying the body/arm/leg poses into jacket/sleeves/trousers. `ModelLayers.PLAYER`
with `new PlayerModel<>(root,false)` is the standard four-pixel-arm skin layout;
all three supplied PNGs are64x64 and have opaque pixels at the full-width arm edge.
The original files are copied byte-for-byte, including indexed/alpha data in the blue PNG.

`HumanoidMobRenderer` installs `CustomHeadLayer`, `ElytraLayer` and `ItemInHandLayer`.
The renderer retains that superclass and adds the verified four-argument
`HumanoidArmorLayer(parent,inner,outer,context.getModelManager())` using player armor layers.
TaCZ1.1.8-hotfix2 still hooks `HumanoidModel` and `ItemInHandLayer`; the model change does not
replace its native weapon renderer or animations. No NPCs classes, new dependency or Mixin.

Pinned `Entity.getTeam()` reads `level().getScoreboard().getPlayersTeam(getScoreboardName())`.
Texture selection reads that API each render, mapping exact team names `1`/`2` to red/blue
and everything else to default. Existing scoreboard synchronization and persistence own
membership; no cached skin, custom packet or NBT schema change is introduced.
[Skin mapping and targeted client smoke](../forge-addon/README.md).

Validation: `compileJava processResources -PclientSmoke --offline` passed, including the renderer
and test-only fixture. The first PowerShell invocation failed before compilation because its
JVM argument was unquoted; that log is preserved. PNG/source/processed-resource identity and
64x64 layout checks passed. One graphical-client attempt did not complete Forge login to the
isolated server; no NPC skin stage ran (0/5), no screenshot or visual PASS is claimed. The owned
client was stopped after diagnosis and the server stopped cleanly. No FULL/STAGE/combined or
load matrix was run. [Exact scope, hashes and retained logs](testing/skins-validation-2026-10-22.json).
