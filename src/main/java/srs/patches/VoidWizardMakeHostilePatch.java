package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.hostile.bosses.VoidWizard;
import net.bytebuddy.asm.Advice;
import srs.random.BossStandIn;

/** Feature 1: the Void Wizard waiting in the dungeon arena is replaced by its mapped boss when hit, before it announces itself. */
@ModMethodPatch(target = VoidWizard.VoidWizardAI.class, name = "makeHostile", arguments = {VoidWizard.class})
public class VoidWizardMakeHostilePatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.Argument(0) VoidWizard mob) {
        return !mob.isHostile && BossStandIn.awaken(mob);
    }

}
