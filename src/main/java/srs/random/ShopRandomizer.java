package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import necesse.engine.registries.ClassIDDataContainer;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.humanShop.BuyingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 4: every item sold (and bought) by a town NPC is swapped with an item from some other shop slot.
 * Prices, stock and unlock requirements belong to the slot, so they stay where they were.
 */
public final class ShopRandomizer {

    private static final Map<String, String> selling = new HashMap<>();
    private static final Map<String, String> buying = new HashMap<>();
    private static boolean collecting;

    private ShopRandomizer() {
    }

    public static void build() {
        selling.clear();
        buying.clear();
        if (!SRS.settings().shops) return;

        // Construct every shop NPC once to learn what the shops contain
        TreeMap<String, List<String>> shopSelling = new TreeMap<>();
        TreeSet<String> sellIDs = new TreeSet<>();
        TreeSet<String> buyIDs = new TreeSet<>();
        collecting = true;
        try {
            for (ClassIDDataContainer<Mob> container : MobRegistry.getMobs()) {
                Mob mob;
                try {
                    mob = container.newInstance();
                } catch (Throwable e) {
                    continue;
                }
                if (!(mob instanceof HumanShop)) continue;
                HumanShop shopMob = (HumanShop) mob;
                List<String> items = new ArrayList<>();
                for (SellingShopItem item : shopMob.shop.sellingShop.getItems()) {
                    String id = item.getStringID();
                    if (isValidItem(id)) {
                        sellIDs.add(id);
                        items.add(id);
                    }
                }
                for (BuyingShopItem item : shopMob.shop.buyingShop.getItems()) {
                    String id = item.getStringID();
                    if (isValidItem(id)) buyIDs.add(id);
                }
                shopSelling.put(container.getIDData().getStringID(), items);
            }
        } finally {
            collecting = false;
        }

        selling.putAll(SeedRandom.permutation("shops:selling", new ArrayList<>(sellIDs)));
        buying.putAll(SeedRandom.permutation("shops:buying", new ArrayList<>(buyIDs)));

        List<String> lines = Spoiler.section("4 - Lojas (NPC: item original -> item vendido)");
        for (Map.Entry<String, List<String>> e : shopSelling.entrySet()) {
            for (String id : e.getValue()) lines.add(e.getKey() + ": " + id + " -> " + selling.get(id));
        }
        List<String> buyLines = Spoiler.section("4 - Lojas: itens que os NPCs compram (original -> novo)");
        List<String> keys = new ArrayList<>(buying.keySet());
        Collections.sort(keys);
        for (String k : keys) buyLines.add(k + " -> " + buying.get(k));
    }

    private static boolean isValidItem(String id) {
        return id != null && ItemRegistry.itemExists(id) && !SRS.isItemExcluded(id);
    }

    /** Called from the ShopManager.addSellingItem patch. */
    public static String remapSelling(String stringID) {
        if (collecting || selling.isEmpty() || stringID == null) return stringID;
        String to = selling.get(stringID);
        return to == null ? stringID : to;
    }

    /** Called from the ShopManager.addBuyingItem patch. */
    public static String remapBuying(String stringID) {
        if (collecting || buying.isEmpty() || stringID == null) return stringID;
        String to = buying.get(stringID);
        return to == null ? stringID : to;
    }

}
