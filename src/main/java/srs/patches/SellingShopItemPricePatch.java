package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameRandom;
import necesse.engine.util.IntRange;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4: multiplies the price a shop sells an item for. */
@ModMethodPatch(target = SellingShopItem.class, name = "generatePrice",
        arguments = {GameRandom.class, ServerClient.class, HumanShop.class, GameBlackboard.class, InventoryItem.class})
public class SellingShopItemPricePatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This SellingShopItem item, @Advice.Return(readOnly = false) IntRange price) {
        price = ShopRandomizer.modifySellingPrice(item, price);
    }

}
