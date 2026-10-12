package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.armorItem.ArmorItem;
import net.bytebuddy.asm.Advice;
import srs.random.EquipmentRandomizer;

/** Feature 10 (double mode): lists the extra set bonus in the helmet's tooltip. */
@ModMethodPatch(target = ArmorItem.class, name = "getPostEnchantmentTooltips", arguments = {InventoryItem.class, PlayerMob.class, GameBlackboard.class})
public class ArmorSetBonusTooltipPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ArmorItem self, @Advice.Argument(0) InventoryItem item, @Advice.Argument(1) PlayerMob perspective,
                       @Advice.Argument(2) GameBlackboard blackboard, @Advice.Return ListGameTooltips tooltips) {
        EquipmentRandomizer.addExtraSetBonusTooltip(self, tooltips, item, perspective, blackboard);
    }

}
