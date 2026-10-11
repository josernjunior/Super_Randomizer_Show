package srs.patches;

import java.util.ArrayList;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameRandom;
import necesse.inventory.InventoryItem;
import necesse.inventory.lootTable.LootTable;
import net.bytebuddy.asm.Advice;
import srs.random.DropRandomizer;

/** Feature 2: replaces a dying mob's loot with the loot of the mob it was swapped with. */
@ModMethodPatch(target = LootTable.class, name = "getNewList", arguments = {GameRandom.class, float.class, Object[].class})
public class LootTableGetNewListPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static ArrayList<InventoryItem> onEnter(@Advice.This LootTable table, @Advice.Argument(0) GameRandom random,
                                            @Advice.Argument(1) float lootMultiplier, @Advice.Argument(2) Object[] extra) {
        return DropRandomizer.replaceMobLoot(table, random, lootMultiplier, extra);
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.Enter ArrayList<InventoryItem> replaced, @Advice.Return(readOnly = false) ArrayList<InventoryItem> result) {
        if (replaced != null) result = replaced;
    }

}
