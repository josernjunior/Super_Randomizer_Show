package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import net.bytebuddy.asm.Advice;
import srs.random.ShopRandomizer;

/** Feature 4 (npcs mode): gives the NPC its new shop before the shop is set up. */
@ModMethodPatch(target = HumanShop.class, name = "init", arguments = {})
public class HumanShopInitPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.This HumanShop mob) {
        ShopRandomizer.ensureShop(mob);
    }

}
