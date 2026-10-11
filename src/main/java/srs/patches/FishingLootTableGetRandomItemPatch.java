package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameRandom;
import necesse.inventory.InventoryItem;
import necesse.level.maps.biomes.FishingLootTable;
import necesse.level.maps.biomes.FishingSpot;
import net.bytebuddy.asm.Advice;
import srs.random.FishingRandomizer;

/** Feature 18: swaps what is caught (players and settlers). The static overloads all end up here. */
@ModMethodPatch(target = FishingLootTable.class, name = "getRandomItem", arguments = {FishingSpot.class, GameRandom.class})
public class FishingLootTableGetRandomItemPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.Return(readOnly = false) InventoryItem caught) {
        caught = FishingRandomizer.mapCatch(caught);
    }

}
