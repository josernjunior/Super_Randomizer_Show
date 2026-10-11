package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import necesse.engine.journal.JournalChallenge;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.entity.levelEvent.fishingEvent.FishingEvent;
import necesse.entity.levelEvent.fishingEvent.WaitFishingPhase;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.baitItem.BaitItem;
import necesse.inventory.item.matItem.FishItemInterface;
import necesse.inventory.item.placeableItem.fishingRodItem.FishingRodItem;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 18: fishing power of baits and rods, and what is caught.
 * Powers are written into the items' fields at startup (tooltips read the same field). Catches are swapped when
 * FishingLootTable.getRandomItem returns: "fish" swaps every fish with another fish, "all" turns every catch into
 * an item from the whole game. Both are fixed per seed.
 */
public final class FishingRandomizer {

    /** Catches that aren't fish, listed in the spoiler of the "all" mode. */
    private static final String[] OTHER_CATCHES = {"fins"};

    private static final Map<String, Integer> originalPower = new HashMap<>();
    private static final Map<String, String> fishMapping = new HashMap<>();
    private static final Map<String, Item> allMapping = new HashMap<>();
    private static boolean allItems;

    private FishingRandomizer() {
    }

    public static void build() {
        fishMapping.clear();
        allMapping.clear();
        SRSSettings s = SRS.settings();
        allItems = "all".equalsIgnoreCase(s.fishingCatchesMode);

        TreeMap<String, Item> baits = new TreeMap<>();
        TreeMap<String, Item> rods = new TreeMap<>();
        List<String> fish = new ArrayList<>();
        for (Item item : ItemRegistry.getItems()) {
            if (item == null || !ItemRegistry.isObtainable(item.getID())) continue;
            String id = item.getStringID();
            if (SRS.isItemExcluded(id)) continue;
            if (item instanceof BaitItem) baits.put(id, item);
            else if (item instanceof FishingRodItem) rods.put(id, item);
            if (item instanceof FishItemInterface && item.type != Item.Type.QUEST) fish.add(id);
        }

        if (s.fishingBaitPower) randomizePower("18 - Pesca: poder das iscas", "fishing:bait:", baits, s.fishingBaitPowerVariation);
        if (s.fishingRodPower) randomizePower("18 - Pesca: poder das varas", "fishing:rod:", rods, s.fishingRodPowerVariation);

        if (s.fishingCatches) {
            List<String> lines = Spoiler.section("18 - Pesca: o que e pescado (" + (allItems ? "todos os itens" : "peixes") + ")");
            if (allItems) {
                List<String> catches = new ArrayList<>(fish);
                for (String other : OTHER_CATCHES) {
                    if (ItemRegistry.itemExists(other) && !SRS.isItemExcluded(other)) catches.add(other);
                }
                Collections.sort(catches);
                for (String id : catches) {
                    Item to = catchFor(id);
                    lines.add(id + " -> " + (to == null ? id : to.getStringID()));
                }
                lines.add("(qualquer outra coisa pescada tambem vira um item fixo para cada seed)");
            } else {
                fishMapping.putAll(SeedRandom.permutation("fishing:catch:fish", fish));
                for (Map.Entry<String, String> e : new TreeMap<>(fishMapping).entrySet()) {
                    lines.add(e.getKey() + " -> " + e.getValue());
                }
            }
        }
    }

    /** Multiplies every item's power by a fixed factor in [1 - variation, 1 + variation], at least 1%. */
    private static void randomizePower(String title, String key, TreeMap<String, Item> items, float variation) {
        List<String> lines = Spoiler.section(title);
        variation = Math.max(0, variation);
        for (Map.Entry<String, Item> e : items.entrySet()) {
            String id = e.getKey();
            Item item = e.getValue();
            try {
                int original = originalPower.containsKey(id) ? originalPower.get(id) : getPower(item);
                originalPower.put(id, original);
                float factor = SeedRandom.range(key + id, 1 - variation, 1 + variation);
                int power = Math.max(1, Math.round(original * factor));
                setPower(item, power);
                lines.add(id + ": " + original + "% -> " + power + "%");
            } catch (Exception ex) {
                SRS.warn("Could not change the fishing power of " + id + ": " + ex);
            }
        }
    }

    public static int getPower(Item item) {
        if (item instanceof BaitItem) return ((BaitItem) item).fishingPower;
        return Reflect.<Integer>get(FishingRodItem.class, item, "fishingPower");
    }

    private static void setPower(Item item, int power) {
        if (item instanceof BaitItem) ((BaitItem) item).fishingPower = power;
        else Reflect.set(FishingRodItem.class, item, "fishingPower", power);
    }

    /** The fishing power the item had before the randomizer, or -1 if it wasn't changed. */
    public static int originalPower(String stringID) {
        Integer power = originalPower.get(stringID);
        return power == null ? -1 : power;
    }

    // ===== Catches (called from patches) =====

    /** What a catch of this item becomes, or null to keep it. */
    public static Item catchFor(String stringID) {
        if (stringID == null || !SRS.settings().fishingCatches || SRS.isItemExcluded(stringID)) return null;
        if (!allItems) {
            String to = fishMapping.get(stringID);
            return to == null ? null : ItemRegistry.getItem(to);
        }
        synchronized (allMapping) {
            if (allMapping.containsKey(stringID)) return allMapping.get(stringID);
            List<Item> pool = DropRandomizer.randomItemPool();
            Item to = pool.isEmpty() ? null : pool.get(SeedRandom.rng("fishing:catch:all:" + stringID).nextInt(pool.size()));
            allMapping.put(stringID, to);
            return to;
        }
    }

    /** Called when FishingLootTable.getRandomItem returns. */
    public static InventoryItem mapCatch(InventoryItem caught) {
        if (caught == null || caught.item == null) return caught;
        Item to = catchFor(caught.item.getStringID());
        if (to == null || to == caught.item) return caught;
        return new InventoryItem(to, Math.max(1, Math.min(caught.getAmount(), to.getStackSize())));
    }

    /**
     * The journal challenge "catch an icefish" checks the item caught. When the icefish became something else,
     * catching that also completes it, so the challenge is still possible.
     */
    public static void onReel(FishingEvent event, ArrayList<WaitFishingPhase.FishingLure> hooks) {
        if (hooks == null || !event.getMob().isPlayer || !event.level.isServer()) return;
        Item target = catchFor("icefish");
        if (target == null || target.getStringID().equals("icefish")) return;
        for (WaitFishingPhase.FishingLure line : hooks) {
            InventoryItem caught = line.getCatch();
            if (caught == null || caught.item != target) continue;
            ServerClient serverClient = ((PlayerMob) event.getMob()).getServerClient();
            if (serverClient == null) return;
            JournalChallenge challenge = JournalChallengeRegistry.getChallenge(JournalChallengeRegistry.CATCH_ICEFISH_ID);
            if (!challenge.isCompleted(serverClient) && challenge.isJournalEntryDiscovered(serverClient)) {
                challenge.markCompleted(serverClient);
                serverClient.forceCombineNewStats();
            }
            return;
        }
    }

}
