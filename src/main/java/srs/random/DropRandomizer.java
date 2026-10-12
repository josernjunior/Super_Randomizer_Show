package srs.random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.events.loot.MobLootTableDropsEvent;
import necesse.engine.events.loot.MobPrivateLootTableDropsEvent;
import necesse.engine.events.loot.ObjectLootTableDropsEvent;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.lootTable.LootTable;
import necesse.level.gameObject.FlowerPatchObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SeedObject;
import necesse.level.gameObject.SingleRockObject;
import necesse.level.gameObject.SingleRockSmall;
import necesse.level.gameObject.WallObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelBuffManager.LevelModifiers;
import srs.SRSSettings;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 2: who drops what and how much (every dropped item gets a fixed amount multiplier from the seed).
 * Only the groups checked in dropGroups take part. In "group" mode, enemy/boss/critter/animal loot tables are swapped
 * inside their group, and so are the drops of rocks, trees, plants, crops (with flowers) and walls. In "all" mode every checked
 * group is one big group, so a mob can drop like an object and an object like a mob.
 * Objects a player placed that give themselves back are left alone.
 */
public final class DropRandomizer {

    /** Prefixes of the mapping's keys and values, so a mob and an object with the same string ID never mix. */
    public static final String MOB = "mob:";
    public static final String OBJECT = "object:";

    private static final Pool[] MOB_POOLS = {Pool.HOSTILE, Pool.BOSS, Pool.CRITTER, Pool.ANIMAL};
    /** dropGroups entry of each MOB_POOLS pool. */
    private static final String[] MOB_POOL_GROUPS = {"enemies", "bosses", "critters", "animals"};
    /** dropGroups entry of each object group (fruit bushes count as plants). */
    private static final Map<String, String> OBJECT_GROUP_SETTINGS = new HashMap<>();

    static {
        OBJECT_GROUP_SETTINGS.put("rochas", "rocks");
        OBJECT_GROUP_SETTINGS.put("arvores", "trees");
        OBJECT_GROUP_SETTINGS.put("plantas", "plants");
        OBJECT_GROUP_SETTINGS.put("arbustos", "plants");
        OBJECT_GROUP_SETTINGS.put("plantacoes", "crops");
        OBJECT_GROUP_SETTINGS.put("paredes", "walls");
    }

    /** Who drops like whom: "mob:id" or "object:id" to "mob:id" or "object:id". */
    private static final Map<String, String> mapping = new HashMap<>();
    /** Everything that could take part (key to its dropGroups entry), also when its group is off. */
    private static final Map<String, String> candidates = new HashMap<>();
    private static final Map<String, Float> quantityCache = new HashMap<>();
    private static final ThreadLocal<ArrayDeque<Mob>> dying = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> suppress = ThreadLocal.withInitial(() -> 0);
    private static boolean listenersAdded;
    private static List<Item> randomItemPool;

    private DropRandomizer() {
    }

    public static void build() {
        mapping.clear();
        candidates.clear();
        quantityCache.clear();
        SRSSettings s = SRS.settings();
        if (s.drops) buildMapping(s);
        if (s.dropQuantities) {
            Spoiler.section("2 - Drops: quantidade").add("Cada item recebe um multiplicador fixo entre " + s.dropQuantityMin
                    + " e " + s.dropQuantityMax + " (calculado pela seed + ID do item). Itens nao empilhaveis nao mudam.");
        }
        if (s.randomItemDrop) {
            Spoiler.section("2 - Drops: item aleatorio").add("Cada mob morto tem " + formatPercent(s.randomItemDropChance)
                    + " de chance de dropar tambem um item aleatorio entre " + randomItemPool().size() + " itens.");
        }
        if (!listenersAdded && (s.drops || s.dropQuantities || s.randomItemDrop)) {
            listenersAdded = true;
            GameEvents.addListener(ObjectLootTableDropsEvent.class, new GameEventListener<ObjectLootTableDropsEvent>() {
                @Override
                public void onEvent(ObjectLootTableDropsEvent event) {
                    onObjectDrops(event);
                }
            });
            GameEvents.addListener(MobLootTableDropsEvent.class, new GameEventListener<MobLootTableDropsEvent>() {
                @Override
                public void onEvent(MobLootTableDropsEvent event) {
                    if (SRS.settings().dropQuantities) applyQuantities(event.drops);
                    addRandomItem(event);
                }
            });
            GameEvents.addListener(MobPrivateLootTableDropsEvent.class, new GameEventListener<MobPrivateLootTableDropsEvent>() {
                @Override
                public void onEvent(MobPrivateLootTableDropsEvent event) {
                    if (SRS.settings().dropQuantities) applyQuantities(event.drops);
                }
            });
        }
    }

    /**
     * Splits the checked groups into swap groups (one per seed key) and shuffles each one. The keys of "group" mode are
     * the same as before modes existed: a shared prefix doesn't change the sort order, so old seeds give the same swaps.
     */
    private static void buildMapping(SRSSettings s) {
        Set<String> enabled = SRS.parseList(s.dropGroups);
        boolean all = "all".equalsIgnoreCase(s.dropsMode);
        Map<String, List<String>> swaps = new TreeMap<>();
        Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < MOB_POOLS.length; i++) {
            for (String id : MobPools.get(MOB_POOLS[i])) {
                String key = MOB + id;
                candidates.put(key, MOB_POOL_GROUPS[i]);
                if (!enabled.contains(MOB_POOL_GROUPS[i])) continue;
                // Mobs that drop something only swap with things that drop something, so a croppler never drops like a butterfly (nothing)
                String suffix = hasLoot(id) ? "" : ":noloot";
                swaps.computeIfAbsent((all ? "drops:all" : "drops:mob:" + MOB_POOLS[i].name()) + suffix, k -> new ArrayList<>()).add(key);
                labels.put(key, MOB_POOLS[i].name().toLowerCase());
            }
        }
        for (GameObject object : ObjectRegistry.getObjects()) {
            String id = object.getStringID();
            if (id == null || SRS.isItemExcluded(id)) continue;
            String group = objectGroup(object);
            if (group == null) continue;
            String key = OBJECT + id;
            candidates.put(key, OBJECT_GROUP_SETTINGS.get(group));
            if (!enabled.contains(OBJECT_GROUP_SETTINGS.get(group))) continue;
            swaps.computeIfAbsent(all ? "drops:all" : "drops:object:" + group, k -> new ArrayList<>()).add(key);
            labels.put(key, group);
        }

        List<String> lines = Spoiler.section("2 - Drops: tabela de loot (quem morre ou quebra -> dropa como)");
        lines.add("Modo: " + (all ? "totalmente aleatorio (todos os grupos marcados juntos)" : "troca dentro do mesmo grupo")
                + " | grupos: " + String.join(",", enabled));
        for (Map.Entry<String, List<String>> e : swaps.entrySet()) {
            Map<String, String> perm = SeedRandom.permutation(e.getKey(), e.getValue());
            mapping.putAll(perm);
            String note = e.getKey().endsWith(":noloot") ? " (sem drop)" : "";
            List<String> keys = new ArrayList<>(perm.keySet());
            Collections.sort(keys);
            for (String k : keys) {
                String to = perm.get(k);
                lines.add(all ? idOf(k) + " [" + labels.get(k) + "] -> " + idOf(to) + " [" + labels.get(to) + "]" + note
                        : "[" + labels.get(k) + "] " + idOf(k) + " -> " + idOf(to) + note);
            }
        }
    }

    /** The string ID in a mapping key ("mob:zombie" -> "zombie"). */
    public static String idOf(String key) {
        return key.substring(key.indexOf(':') + 1);
    }

    /** True when the mob's public or private (boss) loot table has anything in it. */
    public static boolean hasLoot(String mobStringID) {
        try {
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(mobStringID));
            if (mob == null) return false;
            LootTable loot = mob.getLootTable();
            LootTable privateLoot = mob.getPrivateLootTable();
            return (loot != null && !loot.items.isEmpty()) || (privateLoot != null && !privateLoot.items.isEmpty());
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Objects only swap drops with objects that drop the same kind of thing. Objects that drop nothing on their own
     * stay out: the second tile of 2-tile rocks and crystal clusters ("...r", its drops come from the first tile),
     * and plain grass, withered grass, cobwebs and other grass that doesn't drop its own item.
     * Walls and flower patches drop themselves, so they only join when that item can be obtained.
     * Every growth stage of a crop is its own SeedObject: only the grown one joins (seeds + produce), the others
     * only give the seed back.
     */
    private static String objectGroup(GameObject object) {
        if (object.getClass().getSimpleName().endsWith("RObject")) return null;
        if (object.getClass() == WallObject.class) return dropsOwnItem(object) ? "paredes" : null;
        if (object instanceof FlowerPatchObject) return dropsOwnItem(object) ? "plantacoes" : null;
        if (object instanceof SeedObject) return ((SeedObject) object).isLastStage() ? "plantacoes" : null;
        Class<?> c = object.getClass();
        while (c != null && c != GameObject.class) {
            String name = c.getSimpleName();
            if (name.contains("Sapling") || name.contains("Seed") || name.contains("Wall") || name.contains("Door")) return null;
            if (name.equals("RockObject") || name.equals("RockOreObject") || name.startsWith("SingleRock") || name.startsWith("SingleOreRock")
                    || name.startsWith("CrystalCluster")) {
                return isDecorativeRock(object) ? null : "rochas";
            }
            if (name.equals("TreeObject") || name.equals("FruitTreeObject")) return "arvores";
            if (name.contains("WildFlower") || name.contains("Mushroom")) return "plantacoes";
            if (name.equals("FruitBushObject")) return "arbustos";
            if (name.equals("GrassObject")) return dropsOwnItem(object) ? "plantas" : null;
            c = c.getSuperclass();
        }
        return null;
    }

    /** Rocks registered with no natural drops (e.g. slimecaverocksmall), when they use their base class's loot rule. */
    private static boolean isDecorativeRock(GameObject object) {
        Class<?> base = object instanceof SingleRockObject ? SingleRockObject.class : object instanceof SingleRockSmall ? SingleRockSmall.class : null;
        if (base == null) return false;
        try {
            if (object.getClass().getMethod("getLootTable", Level.class, int.class, int.class, int.class).getDeclaringClass() != base) return false;
            int maxDrop = Reflect.<Integer>get(base, object, "maxDropAmount");
            Object type = Reflect.get(base, object, "type");
            return maxDrop <= 0 || type == null || Reflect.get(type, "droppedStone") == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** True when the object uses GameObject's default loot: its own item, which exists and can be obtained. */
    private static boolean dropsOwnItem(GameObject object) {
        String id = object.getStringID();
        if (!ItemRegistry.itemExists(id) || !ItemRegistry.isObtainable(ItemRegistry.getItemID(id))) return false;
        try {
            return object.getClass().getMethod("getLootTable", Level.class, int.class, int.class, int.class).getDeclaringClass() == GameObject.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Every swap, "mob:id"/"object:id" keys and values (used by the self-test). */
    public static Map<String, String> getMapping() {
        return Collections.unmodifiableMap(mapping);
    }

    /** The dropGroups entry a "mob:id"/"object:id" key belongs to, or null when it can't take part (used by the self-test). */
    public static String candidateGroup(String key) {
        return candidates.get(key);
    }

    /** True when the mob has loot, or the object drops something at that tile. */
    public static boolean dropsSomething(String key, Level level, int tileX, int tileY) {
        if (key.startsWith(MOB)) return hasLoot(idOf(key));
        GameObject object = ObjectRegistry.getObject(idOf(key));
        return object != null && !object.getLootTable(level, 0, tileX, tileY).items.isEmpty();
    }

    /** Rolls the drops of a "mob:id"/"object:id" key the way a swap does (public for the self-test). */
    public static ArrayList<InventoryItem> rollDrops(String key, Level level, int layerID, int tileX, int tileY) {
        return key.startsWith(MOB) ? mobDrops(idOf(key), level) : objectDrops(idOf(key), level, layerID, tileX, tileY);
    }

    private static ArrayList<InventoryItem> objectDrops(String objectID, Level level, int layerID, int tileX, int tileY) {
        GameObject object = ObjectRegistry.getObject(objectID);
        return object == null ? null : object.getObjectDroppedItems(level, layerID, tileX, tileY, "onDestroyed");
    }

    /**
     * A mob's public and per-player loot, rolled once for a broken object. There's no player, so per-player
     * conditions (boss hearts, quest items) are skipped and loot rotations pick at random.
     */
    private static ArrayList<InventoryItem> mobDrops(String mobID, Level level) {
        Mob template = MobRegistry.getMob(MobRegistry.getMobID(mobID), level);
        if (template == null) return null;
        float multiplier = level.buffManager.getModifier(LevelModifiers.LOOT);
        ArrayList<InventoryItem> drops = new ArrayList<>();
        LootTable loot = template.getLootTable();
        if (loot != null) drops.addAll(loot.getNewList(GameRandom.globalRandom, multiplier, template));
        LootTable privateLoot = template.getPrivateLootTable();
        if (privateLoot != null) drops.addAll(privateLoot.getNewList(GameRandom.globalRandom, multiplier, template));
        return drops;
    }

    // ===== Mob loot (called from patches) =====

    public static void enterDeath(Mob mob) {
        dying.get().push(mob);
    }

    public static void exitDeath(Mob mob) {
        ArrayDeque<Mob> stack = dying.get();
        if (!stack.isEmpty()) stack.pop();
    }

    /**
     * Called at the start of LootTable.getNewList. While a mob is dying, its own loot table is the one asked for
     * with the mob as the only extra argument (public loot) or the mob and a client (private boss loot).
     * Returns the replacement list, or null to let the original run.
     */
    public static ArrayList<InventoryItem> replaceMobLoot(LootTable table, GameRandom random, float lootMultiplier, Object[] extra) {
        if (mapping.isEmpty() || suppress.get() > 0 || extra == null || extra.length == 0) return null;
        ArrayDeque<Mob> stack = dying.get();
        if (stack.isEmpty()) return null;
        Mob mob = stack.peek();
        if (extra[0] != mob) return null;
        boolean isPrivate = extra.length == 2 && extra[1] instanceof ServerClient;
        if (extra.length != 1 && !isPrivate) return null;
        String from = mob.getStringID();
        String to = mapping.get(MOB + from);
        if (to == null || to.equals(MOB + from)) return null;
        suppress.set(suppress.get() + 1);
        try {
            if (to.startsWith(OBJECT)) {
                // An object drops once: its drops replace the public loot, and a boss's per-player loot becomes nothing
                return isPrivate ? new ArrayList<>() : objectDrops(idOf(to), mob.getLevel(), 0, mob.getTileX(), mob.getTileY());
            }
            Mob template = MobRegistry.getMob(MobRegistry.getMobID(idOf(to)), mob.getLevel());
            if (template == null) return null;
            LootTable replacement = isPrivate ? template.getPrivateLootTable() : template.getLootTable();
            if (replacement == null) return new ArrayList<>();
            return replacement.getNewList(random, lootMultiplier, extra);
        } catch (Exception e) {
            SRS.warn("Could not swap loot of " + from + " with " + to + ": " + e);
            return null;
        } finally {
            suppress.set(suppress.get() - 1);
        }
    }

    // ===== Object loot and quantities =====

    /** Handles the drops of a destroyed object (public for the self-test). */
    public static void onObjectDrops(ObjectLootTableDropsEvent event) {
        if (event.level == null || !event.level.isServer()) return;
        // Something the player placed gives itself back, like in vanilla: no swap (that would turn walls or rocks into
        // other items, or into nothing when the other object has no item) and no amount change
        if (givesItselfBack(event)) return;
        if (!mapping.isEmpty() && event.object != null) {
            String from = OBJECT + event.object.getStringID();
            String to = mapping.get(from);
            if (to != null && !to.equals(from)) {
                try {
                    ArrayList<InventoryItem> drops = rollDrops(to, event.level, event.layerID, event.tileX, event.tileY);
                    if (drops != null) event.objectDrops = drops;
                } catch (Exception e) {
                    SRS.warn("Could not swap object drops of " + from + " with " + to + ": " + e);
                }
            }
        }
        if (SRS.settings().dropQuantities) {
            applyQuantities(event.objectDrops);
        }
    }

    /** True when the object was placed by a player and drops exactly its own item. */
    private static boolean givesItselfBack(ObjectLootTableDropsEvent event) {
        if (event.object == null || event.objectDrops == null || event.objectDrops.isEmpty()) return false;
        try {
            if (!event.level.objectLayer.isPlayerPlaced(event.layerID, event.tileX, event.tileY)) return false;
        } catch (Exception e) {
            return false;
        }
        String own = event.object.getStringID();
        for (InventoryItem item : event.objectDrops) {
            if (item == null || item.item == null || !item.item.getStringID().equals(own)) return false;
        }
        return true;
    }

    // ===== Random item =====

    /** Every obtainable item that can be dropped as the random bonus item. */
    public static synchronized List<Item> randomItemPool() {
        if (randomItemPool == null) {
            List<Item> pool = new ArrayList<>();
            for (Item item : ItemRegistry.getItems()) {
                if (item == null || item.type == Item.Type.QUEST || !ItemRegistry.isObtainable(item.getID())) continue;
                if (SRS.isItemExcluded(item.getStringID())) continue;
                pool.add(item);
            }
            randomItemPool = pool;
        }
        return randomItemPool;
    }

    private static void addRandomItem(MobLootTableDropsEvent event) {
        SRSSettings s = SRS.settings();
        if (!s.randomItemDrop || event.drops == null || event.mob == null) return;
        if (event.mob.isPlayer || event.mob.isSummoned || event.mob.isFollowing()) return;
        if (GameRandom.globalRandom.nextFloat() >= s.randomItemDropChance) return;
        List<Item> pool = randomItemPool();
        if (pool.isEmpty()) return;
        event.drops.add(new InventoryItem(pool.get(GameRandom.globalRandom.nextInt(pool.size())), 1));
    }

    private static String formatPercent(float chance) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", chance * 100);
    }

    public static float quantityFactor(String itemStringID) {
        synchronized (quantityCache) {
            Float f = quantityCache.get(itemStringID);
            if (f == null) {
                SRSSettings s = SRS.settings();
                f = SeedRandom.range("drops:quantity:" + itemStringID, s.dropQuantityMin, s.dropQuantityMax);
                quantityCache.put(itemStringID, f);
            }
            return f;
        }
    }

    public static void applyQuantities(List<InventoryItem> drops) {
        if (drops == null) return;
        for (InventoryItem item : drops) {
            if (item == null || item.item == null) continue;
            String id = item.item.getStringID();
            if (item.item.getStackSize() <= 1 || SRS.isItemExcluded(id)) continue;
            float exact = item.getAmount() * quantityFactor(id);
            int amount = (int) Math.floor(exact);
            if (GameRandom.globalRandom.nextFloat() < exact - amount) amount++;
            // Never round a drop away: small drops (1-2 rune stones, crystals...) would often give nothing
            amount = Math.max(1, Math.min(amount, item.item.getStackSize() * 10));
            item.setAmount(amount);
        }
    }

}
