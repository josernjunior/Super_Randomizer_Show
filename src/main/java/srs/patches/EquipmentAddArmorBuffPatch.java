package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.EquipmentActiveBuff;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import srs.random.EquipmentRandomizer;

/** Features 7, 9 and 10: armor pieces use the stat modifiers of the piece they were swapped with. */
@ModMethodPatch(target = EquipmentActiveBuff.class, name = "addArmorBuff", arguments = {Mob.class, InventoryItem.class, InventoryItem.class})
public class EquipmentAddArmorBuffPatch {

    @Advice.OnMethodEnter
    static InventoryItem onEnter(@Advice.Argument(value = 1, readOnly = false) InventoryItem armorItem) {
        InventoryItem original = armorItem;
        armorItem = EquipmentRandomizer.beforeAddArmorBuff(armorItem);
        return original;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit(@Advice.This EquipmentActiveBuff buff, @Advice.Argument(0) Mob mob, @Advice.Argument(1) InventoryItem used,
                       @Advice.Enter InventoryItem original) {
        EquipmentRandomizer.afterAddArmorBuff(buff, mob, original, used);
    }

}
