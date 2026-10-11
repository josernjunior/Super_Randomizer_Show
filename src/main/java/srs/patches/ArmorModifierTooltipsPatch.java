package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.ItemStatTipList;
import necesse.inventory.item.armorItem.ArmorItem;
import net.bytebuddy.asm.Advice;
import srs.random.EquipmentRandomizer;

/** Features 9, 11 and 12: armor tooltips show the swapped and randomized modifiers. */
@ModMethodPatch(target = ArmorItem.class, name = "addModifierTooltips", arguments = {ItemStatTipList.class, InventoryItem.class, InventoryItem.class, Mob.class})
public class ArmorModifierTooltipsPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This ArmorItem self, @Advice.Argument(0) ItemStatTipList list, @Advice.Argument(1) InventoryItem current,
                           @Advice.Argument(2) InventoryItem last, @Advice.Argument(3) Mob perspective) {
        return EquipmentRandomizer.addModifierTooltips(self, list, current, last, perspective);
    }

}
