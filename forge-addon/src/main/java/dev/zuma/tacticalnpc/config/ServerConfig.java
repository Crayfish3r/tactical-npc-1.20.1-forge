package dev.zuma.tacticalnpc.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ServerConfig {
    public enum Difficulty { HARD, UNFAIR }
    public static final ForgeConfigSpec.EnumValue<Difficulty> DIFFICULTY;
    public static final ForgeConfigSpec.IntValue SUPPRESSION_DURATION,SUPPRESSION_ROUNDS,SUPPRESSION_BURST,SUPPRESSION_PAUSE,HINT_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_ERROR_MULTIPLIER;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, DEBUG, SUPPRESSION, TELEMETRY;
    public static final ForgeConfigSpec.DoubleValue RANGE, TURN, ERROR, STRAFE, FOV, MEMORY_RANGE, HEARING_RANGE, ACTIVATION_RANGE;
    public static final ForgeConfigSpec.IntValue REACTION, PERCEPTION, REACQUIRE, ACQUISITION, MEMORY;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        DIFFICULTY=b.comment("Migration key only: ordinary gameplay always uses HARD. Legacy UNFAIR and combat overrides are normalized on load/reload.").defineEnum("difficultyProfile",Difficulty.HARD);
        SUPPRESSION_DURATION=b.defineInRange("suppressionDurationTicks",160,20,400);
        SUPPRESSION_ROUNDS=b.defineInRange("suppressionRoundBudget",48,1,240);
        SUPPRESSION_BURST=b.defineInRange("suppressionBurstRounds",8,1,30);
        SUPPRESSION_PAUSE=b.defineInRange("suppressionBurstPauseTicks",12,2,80);
        HINT_INTERVAL=b.defineInRange("unfairHintIntervalTicks",60,20,200);
        ENABLED = b.comment("Only NPCs with entity tag tactical_ai are affected.").define("enabled", true);
        DEBUG = b.comment("Log target, state, successful shots and reload requests.").define("debug", false);
        TELEMETRY=b.comment("Aggregate in-memory AI samples; log every 200 ticks, not every NPC tick.").define("telemetry",false);
        SUPPRESSION=b.comment("Sustained bounded native bursts at checked sectors around known contact. No deliberate wall penetration.").define("suppression",true);
        RANGE = b.defineInRange("detectionRange", 128.0, 4.0, 128.0);
        ACTIVATION_RANGE=b.comment("Dormant AI updates every ten ticks outside this radius; never smaller than sight/hearing. Does not grant a target observation.").defineInRange("activationRange",128.0,16.0,192.0);
        FOV = b.defineInRange("horizontalFov", 140.0, 30.0, 180.0);
        PERCEPTION = b.comment("Hard preset; legacy world combat overrides are normalized on load. Experiments tune values only inside their test harness.").defineInRange("perceptionTicks", 1, 1, 40);
        REACQUIRE = b.defineInRange("reacquireTicks", 1, 1, 20);
        ACQUISITION = b.comment("Active scan interval with UUID phase staggering.").defineInRange("activeAcquisitionTicks", 3, 2, 10);
        MEMORY = b.comment("Search memory; suppression additionally requires confidence, uncertainty, duration and ammo budget.").defineInRange("memoryTicks", 320, 20, 600);
        MEMORY_RANGE = b.defineInRange("memoryRange", 160.0, 8.0, 256.0);
        HEARING_RANGE = b.defineInRange("hearingRange", 128.0, 4.0, 192.0);
        TURN = b.comment("Maximum aim rotation in degrees per tick.").defineInRange("turnRate", 17.0, 1.0, 18.0);
        ERROR = b.comment("Correlated aim error amplitude in degrees, plus native TaCZ spread.").defineInRange("aimError", 0.35, 0.1, 5.0);
        REACTION = b.comment("First-contact reaction; same visual UUID within 60 ticks uses reacquireTicks. Geometry is always fresh.").defineInRange("reactionTicks", 3, 2, 20);
        STRAFE = b.defineInRange("strafeSpeed", 0.55, 0.0, 1.0);
        DEFAULT_ERROR_MULTIPLIER = b.comment("Accuracy only: DEFAULT amplitude relative to HARD; preserved by legacy migration.").defineInRange("defaultAimErrorMultiplier",3.0,1.0,10.0);
        SPEC = b.build();
    }
    public static boolean unfair(){return false;}
    public static double hearingRange(){return HEARING_RANGE.get();}
    public static double memoryRange(){return Math.max(Math.max(RANGE.get(),hearingRange())+16,MEMORY_RANGE.get());}
    public static int reaction(){return REACTION.get();}
    public static int perception(){return PERCEPTION.get();}
    public static int reacquire(){return REACQUIRE.get();}
    /** Defaults above are the single Hard preset. Never invoked by an NPC command. */
    public static boolean normalizeHard(){
        boolean changed=false;
        for(var value:java.util.List.of(DIFFICULTY,RANGE,ACTIVATION_RANGE,MEMORY_RANGE,HEARING_RANGE,PERCEPTION,REACTION,REACQUIRE,ACQUISITION,TURN,ERROR,MEMORY,FOV,STRAFE,SUPPRESSION,SUPPRESSION_DURATION,SUPPRESSION_ROUNDS,SUPPRESSION_BURST,SUPPRESSION_PAUSE))
            changed=reset(value) || changed;
        return changed;
    }
    private static <T> boolean reset(ForgeConfigSpec.ConfigValue<T> value){
        if(java.util.Objects.equals(value.get(),value.getDefault()))return false;
        value.set(value.getDefault());return true;
    }
    public static void onConfig(net.minecraftforge.fml.event.config.ModConfigEvent event){
        if(event.getConfig().getSpec()!=SPEC || event instanceof net.minecraftforge.fml.event.config.ModConfigEvent.Unloading)return;
        // The selected GameTest launcher explicitly owns in-process experiment tuning.
        // Loading still exercises ordinary migration; fixture autosaves must not reset a running experiment.
        if(event instanceof net.minecraftforge.fml.event.config.ModConfigEvent.Reloading && System.getProperty("tactical.tests.catalog")!=null)return;
        if(!normalizeHard())return;
        dev.zuma.tacticalnpc.TacticalNpcAiMod.LOGGER.warn("Migrated legacy NPC difficulty/combat overrides to HARD; diagnostics, enabled flag and each NPC's saved ammo policy are preserved. UNFAIR hints and reserve override are disabled.");
        event.getConfig().save();
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null)server.execute(()->{for(var level:server.getAllLevels())for(var goal:dev.zuma.tacticalnpc.runtime.RuntimeRegistry.activeIn(level)){goal.migrateHard();}});
    }
    private ServerConfig() {}
}
