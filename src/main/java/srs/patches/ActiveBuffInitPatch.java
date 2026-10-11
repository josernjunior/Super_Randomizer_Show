package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import net.bytebuddy.asm.Advice;
import srs.random.EquipmentRandomizer;

/** Features 6 and 9: randomizes crit/damage values of trinket and set bonus buffs. */
@ModMethodPatch(target = ActiveBuff.class, name = "init", arguments = {BuffEventSubscriber.class})
public class ActiveBuffInitPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ActiveBuff buff) {
        EquipmentRandomizer.onBuffInit(buff);
    }

}
