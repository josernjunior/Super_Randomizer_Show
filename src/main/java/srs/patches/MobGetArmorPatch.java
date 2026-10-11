package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.MobStatRandomizer;

/** Feature 7: defense (armor) of enemies, villagers, critters, animals and bosses. */
@ModMethodPatch(target = Mob.class, name = "getArmor", arguments = {})
public class MobGetArmorPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) float armor) {
        armor = MobStatRandomizer.modifyArmor(mob, armor);
    }

}
