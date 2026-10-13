# Compatibility audit: independent NPC / optional NPCs 2.1.0

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

