package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.ShopManager;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/**
 * Feature 4: changes which item a shop slot sells. Some shops add an item only if it isn't there yet, checked by its
 * original ID (the exotic merchant's vinyls); that item is already there under its new ID, so the second add is
 * skipped instead of registering a duplicate, which threw and made the NPC impossible to create.
 */
@ModMethodPatch(target = ShopManager.class, name = "addSellingItem", arguments = {String.class, SellingShopItem.class})
public class ShopAddSellingItemPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static SellingShopItem onEnter(@Advice.This ShopManager shop, @Advice.Argument(value = 0, readOnly = false) String stringID,
                                   @Advice.Argument(1) SellingShopItem item) {
        stringID = ShopRandomizer.remapSelling(stringID);
        return ShopRandomizer.isSelling(shop, stringID) ? item : null;
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.Enter SellingShopItem skipped, @Advice.Return(readOnly = false) SellingShopItem result) {
        if (skipped != null) result = skipped;
    }

}
