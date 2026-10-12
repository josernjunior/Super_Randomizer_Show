package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4 (npcs mode): gives the NPC its new shop before the saved stock is loaded into it. */
@ModMethodPatch(target = HumanShop.class, name = "applyLoadData", arguments = {LoadData.class})
public class HumanShopLoadPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This HumanShop mob) {
        ShopRandomizer.ensureShop(mob);
    }

}
