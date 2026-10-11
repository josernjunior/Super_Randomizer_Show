package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.ShopManager;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4: changes which item a shop slot sells. */
@ModMethodPatch(target = ShopManager.class, name = "addSellingItem", arguments = {String.class, SellingShopItem.class})
public class ShopAddSellingItemPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) String stringID) {
        stringID = ShopRandomizer.remapSelling(stringID);
    }

}
