package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ability.BooleanMobAbility;
import net.bytebuddy.asm.Advice;
import srs.random.BossStandIn;

/**
 * Feature 1: the Cursed Crone waiting in its arena (when hit) and the pirate village's captain (when it starts a
 * fight) are replaced by their mapped boss instead of turning on. Arena bosses turning off are waiting again.
 */
@ModMethodPatch(target = BooleanMobAbility.class, name = "runAndSend", arguments = {boolean.class})
public class BooleanMobAbilityRunAndSendPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This BooleanMobAbility ability, @Advice.Argument(0) boolean value) {
        return BossStandIn.onBooleanAbility(ability, value);
    }

}
