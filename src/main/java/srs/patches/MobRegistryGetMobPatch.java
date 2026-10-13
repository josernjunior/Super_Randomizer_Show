package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.Mob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import srs.random.MobSpawnRandomizer;

/** Feature 1: swaps the requested mob ID at known spawn points, and marks bosses that are swapped later. */
@ModMethodPatch(target = MobRegistry.class, name = "getMob", arguments = {String.class, Level.class})
public class MobRegistryGetMobPatch {

    @Advice.OnMethodEnter
    static MobSpawnRandomizer.Decision onEnter(@Advice.Argument(value = 0, readOnly = false) String stringID, @Advice.Argument(1) Level level) {
        MobSpawnRandomizer.Decision decision = MobSpawnRandomizer.decide(stringID, level);
        if (decision != null) stringID = decision.spawnID;
        return decision;
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.Enter MobSpawnRandomizer.Decision decision, @Advice.Return Mob mob) {
        if (decision != null) MobSpawnRandomizer.created(decision, mob);
    }

}
