package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.hostile.bosses.ChieftainMob;
import net.bytebuddy.asm.Advice;
import srs.random.BossStandIn;

/** Feature 1: the Runebound Chieftain of an arena becomes its mapped boss when it lands after its gauntlet and turns hostile. */
@ModMethodPatch(target = ChieftainMob.class, name = "serverTick", arguments = {})
public class ChieftainMobServerTickPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ChieftainMob mob) {
        if (mob.spawnPos == null) return;
        if (mob.isHostile) BossStandIn.awaken(mob);
        else BossStandIn.backToWaiting(mob);
    }

}
