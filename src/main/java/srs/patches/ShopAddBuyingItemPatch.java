package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.humanShop.BuyingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.ShopManager;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4: changes which item a shop slot buys. A swapped item the shop already buys is skipped, like in ShopAddSellingItemPatch. */
@ModMethodPatch(target = ShopManager.class, name = "addBuyingItem", arguments = {String.class, BuyingShopItem.class})
public class ShopAddBuyingItemPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static BuyingShopItem onEnter(@Advice.This ShopManager shop, @Advice.Argument(value = 0, readOnly = false) String stringID,
                                  @Advice.Argument(1) BuyingShopItem item) {
        stringID = ShopRandomizer.remapBuying(stringID);
        return ShopRandomizer.isBuying(shop, stringID) ? item : null;
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.Enter BuyingShopItem skipped, @Advice.Return(readOnly = false) BuyingShopItem result) {
        if (skipped != null) result = skipped;
    }

}
