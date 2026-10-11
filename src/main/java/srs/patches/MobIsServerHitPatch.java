package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.MobDamageRandomizer;

/** Features 5.2 and 5.3: scales the damage dealt by enemies and bosses. Every server-side hit goes through here. */
@ModMethodPatch(target = Mob.class, name = "isServerHit", arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class})
public class MobIsServerHitPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This Mob target, @Advice.Argument(value = 0, readOnly = false) GameDamage damage, @Advice.Argument(4) Attacker attacker) {
        damage = MobDamageRandomizer.enterHit(damage, attacker, target);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit() {
        MobDamageRandomizer.exitHit();
    }

}
