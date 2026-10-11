package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.GameDamage;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolItem;
import net.bytebuddy.asm.Advice;
import srs.random.WeaponRandomizer;

/** Features 5.1 and 7: weapon damage and weapon crit chance. */
@ModMethodPatch(target = ToolItem.class, name = "getFlatAttackDamage", arguments = {InventoryItem.class})
public class ToolItemFlatDamagePatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ToolItem item, @Advice.Return(readOnly = false) GameDamage damage) {
        damage = WeaponRandomizer.modify(item, damage);
    }

}
