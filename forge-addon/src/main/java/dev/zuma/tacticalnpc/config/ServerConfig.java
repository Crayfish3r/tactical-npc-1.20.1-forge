package dev.zuma.tacticalnpc.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, DEBUG, SUPPRESSION, TELEMETRY;
    public static final ForgeConfigSpec.DoubleValue RANGE, TURN, ERROR, STRAFE, FOV, MEMORY_RANGE, HEARING_RANGE, ACTIVATION_RANGE;
    public static final ForgeConfigSpec.IntValue REACTION, PERCEPTION, REACQUIRE, ACQUISITION, MEMORY;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Only NPCs with entity tag tactical_ai are affected.").define("enabled", true);
        DEBUG = b.comment("Log target, state, successful shots and reload requests.").define("debug", false);
        TELEMETRY=b.comment("Aggregate in-memory AI samples; log every 200 ticks, not every NPC tick.").define("telemetry",false);
        SUPPRESSION=b.comment("Limited fire at a fresh known open point. No deliberate wall penetration.").define("suppression",true);
        RANGE = b.defineInRange("detectionRange", 40.0, 4.0, 64.0);
        ACTIVATION_RANGE=b.comment("Dormant AI updates every ten ticks outside this radius; never smaller than sight/hearing. Does not grant a target observation.").defineInRange("activationRange",64.0,16.0,128.0);
        FOV = b.defineInRange("horizontalFov", 140.0, 30.0, 180.0);
        PERCEPTION = b.comment("Hard starting profile: perception 1-2, reaction 2-3, reacquire 1-2, acquisition 2-4, turn 16-18. Existing world overrides remain authoritative; latency requires measurement.").defineInRange("perceptionTicks", 1, 1, 40);
        REACQUIRE = b.defineInRange("reacquireTicks", 1, 1, 20);
        ACQUISITION = b.comment("Active scan interval with UUID phase staggering.").defineInRange("activeAcquisitionTicks", 3, 2, 10);
        MEMORY = b.comment("Search memory; longer than the separate 40-tick suppression permit.").defineInRange("memoryTicks", 160, 20, 600);
        MEMORY_RANGE = b.defineInRange("memoryRange", 64.0, 8.0, 128.0);
        HEARING_RANGE = b.defineInRange("hearingRange", 40.0, 4.0, 64.0);
        TURN = b.comment("Maximum aim rotation in degrees per tick.").defineInRange("turnRate", 17.0, 1.0, 18.0);
        ERROR = b.comment("Correlated aim error amplitude in degrees, plus native TaCZ spread.").defineInRange("aimError", 1.0, 0.1, 5.0);
        REACTION = b.comment("First-contact reaction; same visual UUID within 60 ticks uses reacquireTicks. Geometry is always fresh.").defineInRange("reactionTicks", 3, 2, 20);
        STRAFE = b.defineInRange("strafeSpeed", 0.55, 0.0, 1.0);
        SPEC = b.build();
    }
    private ServerConfig() {}
}
