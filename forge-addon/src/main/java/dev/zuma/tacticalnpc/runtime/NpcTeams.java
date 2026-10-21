package dev.zuma.tacticalnpc.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Team;

/** Vanilla scoreboard owns membership and persistence; squad state is transient and per level. */
public final class NpcTeams {
    private static final Map<Team,UUID> GROUPS=new WeakHashMap<>();
    public static String name(LivingEntity entity){return entity.getTeam()==null?null:entity.getTeam().getName();}
    public static UUID group(LivingEntity entity){
        var team=entity.getTeam();return team==null?null:GROUPS.computeIfAbsent(team,key->UUID.randomUUID());
    }
    public static boolean allied(LivingEntity owner,LivingEntity other){
        var team=owner.getTeam();return owner==other || team!=null && team.isAlliedTo(other.getTeam());
    }
    public static void validate(String name){
        if(name!=null && (name.isBlank() || name.length()>64 || !name.equals(name.strip()) || name.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Team name must contain 1..64 characters without control characters or surrounding spaces.");
    }
    public static void assign(LivingEntity entity,String name){
        validate(name);
        if(!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread())throw new IllegalStateException("Team changes require server thread");
        if(entity instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc)npc.syncTeam();
        if(java.util.Objects.equals(name(entity),name))return;
        var active=RuntimeRegistry.active(entity);if(active!=null)active.stop();
        var scoreboard=level.getScoreboard();scoreboard.removePlayerFromTeam(entity.getScoreboardName());
        if(name!=null){var team=scoreboard.getPlayerTeam(name);if(team==null)team=scoreboard.addPlayerTeam(name);scoreboard.addPlayerToTeam(entity.getScoreboardName(),team);}
        if(entity instanceof dev.zuma.tacticalnpc.entity.TacticalNpcEntity npc)npc.syncTeam();
    }
    private NpcTeams(){}
}
