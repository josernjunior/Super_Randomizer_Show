package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import necesse.engine.registries.ClassIDDataContainer;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.util.IntRange;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.humanShop.BuyingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.ShopManager;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 4: town NPC shops.
 * <ul>
 * <li>items mode: every item sold (and bought) by a town NPC is swapped with an item from some other shop slot.
 * Prices, stock and unlock requirements belong to the slot, so they stay where they were.</li>
 * <li>npcs mode: items stay together, whole shops move between NPCs (the blacksmith sells what the farmer sold).
 * The shop's items only use the NPC they're asked from as a {@link HumanShop}, so any shop works on any NPC.</li>
 * <li>prices: every item's buy and sell price is multiplied by a fixed factor from the seed.</li>
 * </ul>
 */
public final class ShopRandomizer {

    private static final Map<String, String> selling = new HashMap<>();
    private static final Map<String, String> buying = new HashMap<>();
    /** NPC stringID -> NPC whose shop it gets (npcs mode). */
    private static final Map<String, String> npcShops = new HashMap<>();
    private static final Map<String, Float> priceFactors = new HashMap<>();
    /** Shops already handed to an NPC, so loading a save and then adding the NPC to a level swaps only once. */
    private static final Map<ShopManager, Boolean> installedShops = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean collecting;

    private ShopRandomizer() {
    }

    public static void build() {
        selling.clear();
        buying.clear();
        npcShops.clear();
        synchronized (priceFactors) {
            priceFactors.clear();
        }
        SRSSettings s = SRS.settings();
        if (s.shopPrices) {
            Spoiler.section("4 - Lojas: precos").add("Cada item tem os precos de compra e venda multiplicados por um fator fixo entre "
                    + s.shopPricesMin + " e " + s.shopPricesMax + " (calculado pela seed + ID do item).");
        }
        if (!s.shops) return;

        // Construct every shop NPC once to learn what the shops contain
        TreeMap<String, List<String>> shopSelling = new TreeMap<>();
        TreeSet<String> sellIDs = new TreeSet<>();
        TreeSet<String> buyIDs = new TreeSet<>();
        List<String> shopNPCs = new ArrayList<>();
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
                String npc = container.getIDData().getStringID();
                List<String> items = new ArrayList<>();
                for (SellingShopItem item : shopMob.shop.sellingShop.getItems()) {
                    String id = item.getStringID();
                    if (isValidItem(id)) {
                        sellIDs.add(id);
                        items.add(id);
                    }
                }
                int bought = 0;
                for (BuyingShopItem item : shopMob.shop.buyingShop.getItems()) {
                    String id = item.getStringID();
                    if (isValidItem(id)) {
                        buyIDs.add(id);
                        bought++;
                    }
                }
                shopSelling.put(npc, items);
                if ((!items.isEmpty() || bought > 0) && !SRS.isMobExcluded(npc)) shopNPCs.add(npc);
            }
        } finally {
            collecting = false;
        }

        if ("npcs".equalsIgnoreCase(s.shopsMode)) {
            npcShops.putAll(SeedRandom.permutation("shops:npcs", shopNPCs));
            List<String> lines = Spoiler.section("4 - Lojas (NPC: vende como)");
            List<String> keys = new ArrayList<>(npcShops.keySet());
            Collections.sort(keys);
            for (String npc : keys) {
                String to = npcShops.get(npc);
                lines.add(npc + " -> loja de " + to + ": " + String.join(", ", shopSelling.get(to)));
            }
            return;
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

    /** True when a swapped selling ID is already in this shop (the add patches skip it instead of registering a duplicate). */
    public static boolean isSelling(ShopManager shop, String stringID) {
        return !selling.isEmpty() && !collecting && shop != null && stringID != null && shop.sellingShop.getItem(stringID) != null;
    }

    /** Same as {@link #isSelling} for the items a shop buys. */
    public static boolean isBuying(ShopManager shop, String stringID) {
        return !buying.isEmpty() && !collecting && shop != null && stringID != null && shop.buyingShop.getItem(stringID) != null;
    }

    /** npcs mode: the NPC whose shop this NPC gets, or null. */
    public static String getNpcShop(String npcStringID) {
        return npcShops.get(npcStringID);
    }

    /** npcs mode, called before a shop NPC loads its save and before it's added to a level: hands it its new shop. */
    public static void ensureShop(HumanShop mob) {
        if (npcShops.isEmpty() || mob == null || mob.shop == null || installedShops.containsKey(mob.shop)) return;
        String from = mob.getStringID();
        String to = npcShops.get(from);
        if (to == null || to.equals(from)) return;
        try {
            Mob template = MobRegistry.getMob(MobRegistry.getMobID(to));
            if (!(template instanceof HumanShop)) return;
            ShopManager shop = ((HumanShop) template).shop;
            installedShops.put(shop, Boolean.TRUE);
            Reflect.set(HumanShop.class, mob, "shop", shop);
        } catch (Exception e) {
            SRS.warn("Could not give " + from + " the shop of " + to + ": " + e);
        }
    }

    private static float priceFactor(String kind, String itemStringID) {
        SRSSettings s = SRS.settings();
        if (!s.shopPrices || itemStringID == null || SRS.isItemExcluded(itemStringID)) return 1.0F;
        String key = kind + ":" + itemStringID;
        synchronized (priceFactors) {
            Float f = priceFactors.get(key);
            if (f == null) {
                f = SeedRandom.range("shops:price:" + key, s.shopPricesMin, s.shopPricesMax);
                priceFactors.put(key, f);
            }
            return f;
        }
    }

    private static int scale(int price, float factor) {
        return price <= 0 ? price : Math.max(1, Math.round(price * factor));
    }

    /** SellingShopItem.generatePrice exit. */
    public static IntRange modifySellingPrice(SellingShopItem item, IntRange price) {
        if (price == null || item == null) return price;
        float factor = priceFactor("sell", item.getStringID());
        if (factor == 1.0F) return price;
        return new IntRange(scale(price.min, factor), scale(price.max, factor));
    }

    /** BuyingShopItem.generatePrice exit. */
    public static int modifyBuyingPrice(BuyingShopItem item, int price) {
        if (item == null) return price;
        float factor = priceFactor("buy", item.getStringID());
        return factor == 1.0F ? price : scale(price, factor);
    }

}
