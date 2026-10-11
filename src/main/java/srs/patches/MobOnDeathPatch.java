package srs.patches;

import java.util.HashSet;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.DropRandomizer;

/** Feature 2: marks which mob is dying so its loot table can be swapped. */
@ModMethodPatch(target = Mob.class, name = "onDeath", arguments = {Attacker.class, HashSet.class})
public class MobOnDeathPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This Mob mob) {
        DropRandomizer.enterDeath(mob);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit(@Advice.This Mob mob) {
        DropRandomizer.exitDeath(mob);
    }

}
