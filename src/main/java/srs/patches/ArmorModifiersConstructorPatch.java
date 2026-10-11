package srs.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.engine.modifiers.ModifierValue;
import necesse.inventory.item.armorItem.ArmorModifiers;
import net.bytebuddy.asm.Advice;
import srs.random.EquipmentRandomizer;

/** Features 7 and 10: randomizes crit/damage values of the armor piece being equipped or inspected. */
@ModConstructorPatch(target = ArmorModifiers.class, arguments = {ModifierValue[].class})
public class ArmorModifiersConstructorPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ArmorModifiers modifiers) {
        EquipmentRandomizer.onArmorModifiersCreated(modifiers);
    }

}
