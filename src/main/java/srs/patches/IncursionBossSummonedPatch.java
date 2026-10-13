package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.IncursionLevelEvent;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.BossStandIn;

/** Feature 1: an incursion tracks the replacement of a summoned boss that was swapped when added, not the original. */
@ModMethodPatch(target = IncursionLevelEvent.class, name = "onBossSummoned", arguments = {Mob.class})
public class IncursionBossSummonedPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) Mob mob) {
        mob = BossStandIn.current(mob);
    }

}
