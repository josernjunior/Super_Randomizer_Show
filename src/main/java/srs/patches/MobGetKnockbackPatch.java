package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.MobStatRandomizer;

/** Feature 8: how much enemies, villagers, critters, animals and bosses get pushed when hit. */
@ModMethodPatch(target = Mob.class, name = "getKnockbackModifier", arguments = {})
public class MobGetKnockbackPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) float knockback) {
        knockback = MobStatRandomizer.modifyKnockback(mob, knockback);
    }

}
