package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.hostile.bosses.FallenWizardMob;
import net.bytebuddy.asm.Advice;
import srs.random.BossStandIn;

/** Feature 1: the Fallen Wizard waiting in the temple arena is replaced by its mapped boss when hit, before it announces itself. */
@ModMethodPatch(target = FallenWizardMob.FallenWizardAI.class, name = "makeHostile", arguments = {FallenWizardMob.class})
public class FallenWizardMakeHostilePatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.Argument(0) FallenWizardMob mob) {
        return !mob.isHostile && BossStandIn.awaken(mob);
    }

}
