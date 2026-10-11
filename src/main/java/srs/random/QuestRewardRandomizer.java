package srs.random;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.engine.journal.JournalChallenge;
import necesse.engine.journal.MultiJournalChallenge;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.registries.StoryObjectiveRegistry;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.engine.util.GameRandom;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.lootTable.LootItemInterface;
import necesse.inventory.lootTable.LootList;
import necesse.inventory.lootTable.LootTable;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 17: the items given as quest rewards (Elder story objectives and adventure journal challenges) become
 * random items. Each reward table is wrapped once at startup, so the preview in the quest/journal window and the
 * items actually received always match. The replacement only depends on the seed, the quest and the original item.
 */
public final class QuestRewardRandomizer {

    public static final String ELDER = "Mestre Anciao";
    public static final String JOURNAL = "Diario de aventura";

    /**
     * Rewards the next objective asks for: they are still given, and the random item comes along.
     * ("equippingtrinket" only completes after equipping and using the Leather Dashers from "gettingstarted".)
     */
    private static final Set<String> KEPT = new HashSet<>(Arrays.asList(ELDER + ":gettingstarted:leatherdashers"));

    /** Every wrapped reward table: "group: quest" -> table. */
    private static final Map<String, LootTable> wrapped = new LinkedHashMap<>();
    private static final Map<String, Item> replacements = new HashMap<>();

    private QuestRewardRandomizer() {
    }

    public static void build() {
        wrapped.clear();
        replacements.clear();
        SRSSettings s = SRS.settings();
        if (!s.questRewards) return;
        if (DropRandomizer.randomItemPool().isEmpty()) return;
        if (s.questRewardsElder) wrapElderRewards();
        if (s.questRewardsJournal) wrapJournalRewards();
        if (!wrapped.isEmpty()) {
            List<String> lines = Spoiler.section("17 - Recompensas aleatorias");
            for (Map.Entry<String, LootTable> e : wrapped.entrySet()) {
                lines.add(e.getKey() + ": " + describe((Mapped) e.getValue().items.get(0)));
            }
        }
        SRS.log("Quest rewards: " + wrapped.size() + " reward tables randomized");
    }

    /** Story objectives keep their rewards in static LootTable fields ("reward" or "rewards"). */
    private static void wrapElderRewards() {
        List<?> elements;
        try {
            elements = Reflect.get(StoryObjectiveRegistry.class, null, "sortedElements");
        } catch (Exception e) {
            SRS.warn("Could not read the story objectives: " + e);
            return;
        }
        if (elements == null) return;
        for (Object o : elements) {
            StoryObjectiveRegistry.StoryObjectiveRegistryElement element = (StoryObjectiveRegistry.StoryObjectiveRegistryElement) o;
            String stringID = element.getStringID();
            Class<? extends StoryObjective> objectiveClass = element.getIDData().aClass;
            for (Field f : objectiveClass.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()) || f.getType() != LootTable.class) continue;
                try {
                    f.setAccessible(true);
                    LootTable original = (LootTable) f.get(null);
                    LootTable replacement = wrap(ELDER, stringID, original);
                    if (replacement != original) f.set(null, replacement);
                } catch (Exception e) {
                    SRS.warn("Could not randomize the rewards of " + stringID + ": " + e);
                }
            }
        }
    }

    private static void wrapJournalRewards() {
        for (JournalChallenge challenge : JournalChallengeRegistry.getChallenges()) {
            if (!(challenge instanceof MultiJournalChallenge)) continue;
            MultiJournalChallenge multi = (MultiJournalChallenge) challenge;
            try {
                multi.reward = wrap(JOURNAL, challenge.getStringID(), multi.reward);
            } catch (Exception e) {
                SRS.warn("Could not randomize the rewards of " + challenge.getStringID() + ": " + e);
            }
        }
    }

    private static LootTable wrap(String group, String quest, LootTable original) {
        if (original == null || original.items.isEmpty() || isWrapped(original)) return original;
        LootTable table = new LootTable(new Mapped(group + ":" + quest, original));
        wrapped.put(group + ": " + quest, table);
        return table;
    }

    public static boolean isWrapped(LootTable table) {
        return table != null && !table.items.isEmpty() && table.items.get(0) instanceof Mapped;
    }

    /** Every randomized reward table, keyed by "group: quest" (used by the self-test). */
    public static Map<String, LootTable> getWrapped() {
        return wrapped;
    }

    /** The item that replaces fromStringID in this quest's reward, or null to keep it. */
    public static Item replacementFor(String context, String fromStringID) {
        if (fromStringID == null || SRS.isItemExcluded(fromStringID)) return null;
        String key = context + ":" + fromStringID;
        synchronized (replacements) {
            if (replacements.containsKey(key)) return replacements.get(key);
            List<Item> pool = DropRandomizer.randomItemPool();
            Item to = pool.isEmpty() ? null : pool.get(SeedRandom.rng("questrewards:" + key).nextInt(pool.size()));
            replacements.put(key, to);
            return to;
        }
    }

    /**
     * Same amount as the original (up to a full stack). A reward that was a single unique item (a trinket, a tool)
     * becomes a tenth of a stack when the new item stacks, so it doesn't turn into a single stone.
     */
    public static int amountFor(InventoryItem original, Item to) {
        int stack = Math.max(1, to.getStackSize());
        if (stack == 1) return 1;
        int amount = original.getAmount();
        if (original.item.getStackSize() <= 1) amount = Math.max(1, stack / 10);
        return Math.max(1, Math.min(amount, stack));
    }

    private static String describe(Mapped mapped) {
        StringBuilder sb = new StringBuilder();
        LinkedHashSet<String> listed = new LinkedHashSet<>();
        try {
            List<InventoryItem> sample = new ArrayList<>();
            mapped.original.addItems(sample, new GameRandom(0), 1.0F);
            for (InventoryItem item : sample) {
                if (item == null || item.item == null) continue;
                String id = item.item.getStringID();
                if (!listed.add(id)) continue;
                InventoryItem to = mapped.map(item);
                if (sb.length() > 0) sb.append(", ");
                sb.append(id).append(" x").append(item.getAmount()).append(" -> ");
                if (to != item && mapped.keeps(id)) sb.append(id).append(" x").append(item.getAmount()).append(" + ");
                sb.append(to.item.getStringID()).append(" x").append(to.getAmount());
            }
        } catch (Exception ignored) {
            // Tables that need a player or settlement to roll are still listed below
        }
        try {
            LootList list = new LootList();
            mapped.original.addPossibleLoot(list);
            for (Item item : list.getItems()) {
                if (item == null || !listed.add(item.getStringID())) continue;
                Item to = replacementFor(mapped.context, item.getStringID());
                if (sb.length() > 0) sb.append(", ");
                sb.append(item.getStringID()).append(" -> ").append(to == null ? item.getStringID() : to.getStringID());
            }
        } catch (Exception ignored) {
        }
        return sb.length() == 0 ? "(sem itens)" : sb.toString();
    }

    /** Rolls the original reward and swaps every item it gives. */
    public static final class Mapped implements LootItemInterface {

        public final String context;
        public final LootTable original;

        Mapped(String context, LootTable original) {
            this.context = context;
            this.original = original;
        }

        InventoryItem map(InventoryItem item) {
            if (item == null || item.item == null) return item;
            Item to = replacementFor(context, item.item.getStringID());
            if (to == null) return item;
            return new InventoryItem(to, amountFor(item, to));
        }

        boolean keeps(String stringID) {
            return KEPT.contains(context + ":" + stringID);
        }

        /** The replacement, preceded by the original when the next objective needs it. */
        void addMapped(List<InventoryItem> out, InventoryItem item) {
            InventoryItem to = map(item);
            if (to != item && keeps(item.item.getStringID())) out.add(item);
            out.add(to);
        }

        @Override
        public void addPossibleLoot(LootList list, Object... extra) {
            LootList originalList = new LootList();
            original.addPossibleLoot(originalList, extra);
            for (Item item : originalList.getItems()) {
                if (item == null) continue;
                Item to = replacementFor(context, item.getStringID());
                if (to != null && keeps(item.getStringID())) list.add(item);
                list.add(to == null ? item : to);
            }
            List<InventoryItem> custom = new ArrayList<>();
            for (InventoryItem item : originalList.getCustomItems()) {
                addMapped(custom, item);
            }
            for (InventoryItem item : custom) {
                list.addCustom(item);
            }
            // Named collections ("Frog costume") only add a text, which no longer matches the new items: list them one by one
            List<?> named = Reflect.get(LootList.class, originalList, "customRewardStrings");
            if (named != null && !named.isEmpty()) {
                List<InventoryItem> rolled = new ArrayList<>();
                addItems(rolled, new GameRandom(0), 1.0F, extra);
                for (InventoryItem item : rolled) {
                    if (item != null && item.item != null) list.add(item.item);
                }
            }
        }

        @Override
        public void addItems(List<InventoryItem> list, GameRandom random, float lootMultiplier, Object... extra) {
            List<InventoryItem> rolled = new ArrayList<>();
            original.addItems(rolled, random, lootMultiplier, extra);
            for (InventoryItem item : rolled) {
                if (item == null || item.item == null) continue;
                addMapped(list, item);
            }
        }
    }

}
