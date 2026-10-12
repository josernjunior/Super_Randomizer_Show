package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.friendly.human.humanShop.BuyingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4: multiplies the price a shop pays for an item. */
@ModMethodPatch(target = BuyingShopItem.class, name = "generatePrice",
        arguments = {GameRandom.class, ServerClient.class, HumanShop.class, GameBlackboard.class})
public class BuyingShopItemPricePatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This BuyingShopItem item, @Advice.Return(readOnly = false) int price) {
        price = ShopRandomizer.modifyBuyingPrice(item, price);
    }

}
