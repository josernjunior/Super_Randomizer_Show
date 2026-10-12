package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.EquipmentBuffManager;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import srs.random.EquipmentRandomizer;

/** Feature 10 (double mode): puts the extra set bonus on and off together with the set's own bonus. */
@ModMethodPatch(target = EquipmentBuffManager.class, name = "updateArmorBuffs", arguments = {boolean.class})
public class EquipmentUpdateArmorBuffsPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This EquipmentBuffManager manager, @Advice.FieldValue("owner") Mob owner) {
        EquipmentRandomizer.beforeUpdateArmorBuffs(manager, owner);
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.This EquipmentBuffManager manager, @Advice.FieldValue("owner") Mob owner,
                       @Advice.FieldValue("setBonusBuff") Object setBonusBuff) {
        EquipmentRandomizer.afterUpdateArmorBuffs(manager, owner, setBonusBuff);
    }

}
