package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.humanShop.BuyingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.ShopManager;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4: changes which item a shop slot buys. */
@ModMethodPatch(target = ShopManager.class, name = "addBuyingItem", arguments = {String.class, BuyingShopItem.class})
public class ShopAddBuyingItemPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) String stringID) {
        stringID = ShopRandomizer.remapBuying(stringID);
    }

}
