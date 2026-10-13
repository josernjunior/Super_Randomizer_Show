package srs.random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.events.loot.MobLootTableDropsEvent;
import necesse.engine.journal.JournalEntry;
import necesse.engine.registries.BiomeRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.JournalRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.Mob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import srs.SRSSettings;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 1, so swapping bosses never soft locks the game: items the progression needs always drop from a seeded
 * pick of mobs while bosses swap.
 * <ul>
 * <li>Deep Cave Ladder: it only drops from the Pirate Captain, and crafting it takes tungsten, which is only found
 * in the deep caves. Picked among what comes before the deep caves.</li>
 * <li>Dragon Souls, which open Sage and Grit's pedestal (and through them the temple). They come from deep desert cave
 * chests (50% in chest rooms, 20% in ruins and trap rooms), a 0.4% drop of non-boss enemies killed in the deep desert
 * cave, and a recipe of ancient fossil bars, worm carapace and bone. The mod leaves the chests and the 0.4% alone, so
 * this is only an extra, with the dragonSoulsDrops option (off by default): the recipe can change when recipes
 * shuffle, and swapped drops move its ingredients. Picked among what comes before Sage and Grit.</li>
 * </ul>
 * For each item, three kinds of mob always drop one when killed:
 * <ul>
 * <li>the boss that appears in place of one of the bosses of that part of the game; for a boss whose starter drops
 * nothing (Sage and Grit), the mobs it fights through;</li>
 * <li>two enemies or animals that appear in that part of the game: the adventure journal's entries (surface, caves,
 * the dungeon, the pirate village, and the deep caves when they count), minus mobs only found in special places,
 * each one taken as the mob that now appears in its place.</li>
 * </ul>
 * A kill whose drops already have the item doesn't get a second one.
 */
public final class ProgressionDrops {

    /** The wiki's "Pre-Deep Cave Bosses". */
    private static final String[] PRE_DEEP_BOSSES = {"evilsprotector", "queenspider", "voidwizard", "chieftain", "swampguardian",
            "ancientvulture", "piratecaptain"};
    /** The bosses before Sage and Grit, who are only summoned with Dragon Souls. */
    private static final String[] PRE_TEMPLE_BOSSES = {"evilsprotector", "queenspider", "voidwizard", "chieftain", "swampguardian",
            "ancientvulture", "piratecaptain", "reaper", "cryoqueen", "thecursedcrone", "pestwarden"};
    /**
     * Only found in special places: ash left by fires, water, spider nests, goblin camps, bear caves, disguised as bushes,
     * witch huts, a champion's arena, puddles and fishian houses, mosquito eggs, one sand worm per area.
     */
    private static final Set<String> SPECIAL_PLACES = new HashSet<>(Arrays.asList("phoenix", "ashgolem", "shark", "fakeshark",
            "giantcavespider", "trenchcoatgoblinstacked", "grizzlybear", "polarbear", "stabbybush", "evilwitch",
            "skeletonminer", "agedchampion", "staticjellyfish", "fishianhookwarrior", "fishianhealer", "smallswampcavespider",
            "mosquito", "sandworm"));
    private static final int MOBS = 2;

    /** One item that a seeded boss and two seeded mobs always drop. */
    private static final class Guarantee {
        final String item;
        final String seedKey;
        final String title;
        final String what;
        final String before;
        final String[] bosses;
        final boolean deepCaves;
        /** Besides bosses swapping. */
        final Predicate<SRSSettings> enabled;
        final Set<String> droppers = new TreeSet<>();

        Guarantee(String item, String seedKey, String title, String what, String before, String[] bosses, boolean deepCaves,
                  Predicate<SRSSettings> enabled) {
            this.item = item;
            this.seedKey = seedKey;
            this.title = title;
            this.what = what;
            this.before = before;
            this.bosses = bosses;
            this.deepCaves = deepCaves;
            this.enabled = enabled;
        }
    }

    private static final Guarantee[] GUARANTEES = {
            new Guarantee("deepladderdown", "deepladder", "1.3 - Escada de Caverna Profunda", "uma escada",
                    "antes das cavernas profundas", PRE_DEEP_BOSSES, false, s -> true),
            new Guarantee("dragonsouls", "dragonsouls", "1.4 - Almas dos Dragoes", "1 Almas dos Dragoes",
                    "antes de Sage and Grit", PRE_TEMPLE_BOSSES, true, s -> s.dragonSoulsDrops)};

    private static boolean listenerAdded;

    private ProgressionDrops() {
    }

    public static void build() {
        boolean any = false;
        for (Guarantee g : GUARANTEES) {
            g.droppers.clear();
            if (!MobSpawnRandomizer.bossesSwapped() || !g.enabled.test(SRS.settings()) || !ItemRegistry.itemExists(g.item)) continue;
            build(g);
            any = true;
        }
        if (any && !listenerAdded) {
            listenerAdded = true;
            GameEvents.addListener(MobLootTableDropsEvent.class, new GameEventListener<MobLootTableDropsEvent>() {
                @Override
                public void onEvent(MobLootTableDropsEvent event) {
                    for (Guarantee g : GUARANTEES) addItem(g, event);
                }
            });
        }
    }

    private static void build(Guarantee g) {
        List<String> lines = Spoiler.section(g.title + " (" + g.item + ")");
        lines.add("Com a troca de chefes ligada, estes sempre dropam " + g.what + " ao morrer (para o jogo nunca travar " + g.before + "):");

        List<String> bosses = new ArrayList<>();
        for (String id : g.bosses) {
            if (MobRegistry.mobExists(id)) bosses.add(id);
        }
        if (!bosses.isEmpty()) {
            String place = SeedRandom.shuffled(g.seedKey + ":boss", bosses).get(0);
            String boss = appearsAs(place);
            // The night swarm's starter drops its loot when the last bat dies; Sage and Grit's doesn't, the last of the two does
            String[] fighters = dropsLoot(boss) ? null : MobPools.fighters(boss);
            List<String> ids = fighters == null ? Collections.singletonList(boss) : Arrays.asList(fighters);
            g.droppers.addAll(ids);
            lines.add("chefe: " + String.join(" e ", ids) + (fighters == null ? "" : " (" + boss + ")")
                    + (place.equals(boss) ? "" : ", que aparece no lugar de " + place));
        }

        // Dropper -> the first place (original mob) it appears in
        TreeMap<String, String> mobs = new TreeMap<>();
        for (String original : mobsOf(g.deepCaves)) {
            String mob = appearsAs(original);
            Pool pool = MobPools.poolOf(mob);
            if ((pool == Pool.HOSTILE || pool == Pool.ANIMAL) && !g.droppers.contains(mob) && diesWithLoot(mob)) mobs.putIfAbsent(mob, original);
        }
        lines.add("(inimigos e animais sorteados entre " + mobs.size() + " que aparecem " + g.before + ": " + String.join(", ", mobs.keySet()) + ")");
        int picked = 0;
        for (String mob : SeedRandom.shuffled(g.seedKey + ":mobs", new ArrayList<>(mobs.keySet()))) {
            if (picked++ >= MOBS) break;
            g.droppers.add(mob);
            String place = mobs.get(mob);
            lines.add((MobPools.poolOf(mob) == Pool.ANIMAL ? "animal: " : "inimigo: ") + mob
                    + (place.equals(mob) ? "" : ", que aparece no lugar de " + place));
        }
    }

    /** Item -> the mob types that always drop it (empty while bosses don't swap). */
    public static Map<String, Set<String>> droppers() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Guarantee g : GUARANTEES) {
            if (!g.droppers.isEmpty()) out.put(g.item, Collections.unmodifiableSet(g.droppers));
        }
        return out;
    }

    private static void addItem(Guarantee g, MobLootTableDropsEvent event) {
        if (g.droppers.isEmpty() || event.mob == null || event.drops == null) return;
        if (event.mob.isSummoned || event.mob.isFollowing() || !g.droppers.contains(event.mob.getStringID())) return;
        for (InventoryItem item : event.drops) {
            if (item != null && item.item != null && g.item.equals(item.item.getStringID())) return;
        }
        Item item = ItemRegistry.getItem(g.item);
        if (item != null) event.drops.add(new InventoryItem(item, 1));
    }

    /** The mob that now spawns where this one did (itself when its group doesn't swap). */
    private static String appearsAs(String id) {
        String to = MobSpawnRandomizer.getMapping(id);
        return to == null ? id : to;
    }

    /** Mobs of the journal's surface, caves, dungeon and pirate village, plus the deep caves if asked. Never the temple or incursions. */
    private static Set<String> mobsOf(boolean deepCaves) {
        Set<String> out = new TreeSet<>();
        for (JournalEntry entry : JournalRegistry.getJournalEntries()) {
            try {
                // The dungeon and the temple have no level identifier
                boolean deep = entry.levelIdentifier != null && entry.levelIdentifier.equals(LevelIdentifier.DEEP_CAVE_IDENTIFIER);
                if (entry.incursionBiome != null || deep && !deepCaves || entry.biome == BiomeRegistry.TEMPLE) continue;
                for (JournalEntry.MobJournalData data : entry.mobsData) {
                    if (data.mob == null) continue;
                    String id = data.mob.getStringID();
                    if (!SPECIAL_PLACES.contains(id) && !SRS.isMobExcluded(id)) out.add(id);
                }
            } catch (Exception e) {
                SRS.warn("Could not read journal entry " + entry.getStringID() + ": " + e);
            }
        }
        return out;
    }

    /**
     * True for a mob whose death fires the loot event: it drops loot and has something to drop. Start mobs (the evil
     * witch) spawn the real fighters and remove themselves without dying, even when they have a loot table.
     */
    private static boolean diesWithLoot(String id) {
        Class<?> mobClass = MobPools.mobClass(id);
        if (mobClass == null || mobClass.getSimpleName().endsWith("StartMob")) return false;
        return dropsLoot(id) && DropRandomizer.hasLoot(id);
    }

    private static boolean dropsLoot(String id) {
        try {
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(id));
            return mob != null && mob.dropsLoot();
        } catch (Throwable e) {
            return false;
        }
    }

}
