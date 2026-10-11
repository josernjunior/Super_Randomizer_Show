package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.MobHealthRandomizer;

/** Feature 6: max health of enemies, villagers, critters and animals. */
@ModMethodPatch(target = Mob.class, name = "getMaxHealth", arguments = {})
public class MobGetMaxHealthPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) int maxHealth) {
        maxHealth = MobHealthRandomizer.modify(mob, maxHealth);
    }

}
