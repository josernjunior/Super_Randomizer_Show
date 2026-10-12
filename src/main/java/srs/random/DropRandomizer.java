package srs.random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import necesse.level.gameObject.SingleRockObject;
import necesse.level.gameObject.SingleRockSmall;
import necesse.level.gameObject.WallObject;
import necesse.level.maps.Level;
import srs.SRSSettings;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 2: who drops what (mob/boss/critter loot tables are swapped inside their group, and so are the drops of
 * rocks, trees, plants, flowers and walls) and how much (every dropped item gets a fixed amount multiplier from the seed).
 * Objects a player placed that give themselves back are left alone.
 */
public final class DropRandomizer {

    private static final Map<String, String> mobMapping = new HashMap<>();
    private static final Map<String, String> objectMapping = new HashMap<>();
    private static final Map<String, Float> quantityCache = new HashMap<>();
    private static final ThreadLocal<ArrayDeque<Mob>> dying = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> suppress = ThreadLocal.withInitial(() -> 0);
    private static boolean listenersAdded;
    private static List<Item> randomItemPool;

    private DropRandomizer() {
    }

    public static void build() {
        mobMapping.clear();
        objectMapping.clear();
        quantityCache.clear();
        SRSSettings s = SRS.settings();
        if (s.drops) {
            List<String> lines = Spoiler.section("2 - Drops: tabela de loot (quem morre -> dropa como)");
            for (Pool pool : new Pool[]{Pool.HOSTILE, Pool.BOSS, Pool.CRITTER, Pool.ANIMAL}) {
                // Mobs that drop something only swap among themselves, so a croppler never drops like a butterfly (nothing)
                List<String> withLoot = new ArrayList<>();
                List<String> withoutLoot = new ArrayList<>();
                for (String id : MobPools.get(pool)) (hasLoot(id) ? withLoot : withoutLoot).add(id);
                Map<String, String> perm = SeedRandom.permutation("drops:mob:" + pool.name(), withLoot);
                perm.putAll(SeedRandom.permutation("drops:mob:" + pool.name() + ":noloot", withoutLoot));
                mobMapping.putAll(perm);
                List<String> keys = new ArrayList<>(perm.keySet());
                Collections.sort(keys);
                for (String k : keys) {
                    lines.add("[" + pool.name().toLowerCase() + "] " + k + " -> " + perm.get(k) + (withoutLoot.contains(k) ? " (sem drop)" : ""));
                }
            }
            Map<String, List<String>> objectGroups = new HashMap<>();
            for (GameObject object : ObjectRegistry.getObjects()) {
                String id = object.getStringID();
                if (id == null || SRS.isItemExcluded(id)) continue;
                String group = objectGroup(object);
                if (group != null) objectGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(id);
            }
            List<String> groups = new ArrayList<>(objectGroups.keySet());
            Collections.sort(groups);
            for (String group : groups) {
                Map<String, String> perm = SeedRandom.permutation("drops:object:" + group, objectGroups.get(group));
                objectMapping.putAll(perm);
                List<String> keys = new ArrayList<>(perm.keySet());
                Collections.sort(keys);
                for (String k : keys) lines.add("[" + group + "] " + k + " -> " + perm.get(k));
            }
        }
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
     */
    private static String objectGroup(GameObject object) {
        if (object.getClass().getSimpleName().endsWith("RObject")) return null;
        if (object.getClass() == WallObject.class) return dropsOwnItem(object) ? "paredes" : null;
        if (object instanceof FlowerPatchObject) return dropsOwnItem(object) ? "flores" : null;
        Class<?> c = object.getClass();
        while (c != null && c != GameObject.class) {
            String name = c.getSimpleName();
            if (name.contains("Sapling") || name.contains("Seed") || name.contains("Wall") || name.contains("Door")) return null;
            if (name.equals("RockObject") || name.equals("RockOreObject") || name.startsWith("SingleRock") || name.startsWith("SingleOreRock")
                    || name.startsWith("CrystalCluster")) {
                return isDecorativeRock(object) ? null : "rochas";
            }
            if (name.equals("TreeObject") || name.equals("FruitTreeObject")) return "arvores";
            if (name.contains("WildFlower") || name.contains("Mushroom")) return "flores";
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

    /** Mob whose loot the given mob drops, or null (used by the self-test). */
    public static String getMobMapping(String mobStringID) {
        return mobMapping.get(mobStringID);
    }

    /** Object stringID -> object whose drops it gives (used by the self-test). */
    public static Map<String, String> getObjectMapping() {
        return objectMapping;
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
        if (mobMapping.isEmpty() || suppress.get() > 0 || extra == null || extra.length == 0) return null;
        ArrayDeque<Mob> stack = dying.get();
        if (stack.isEmpty()) return null;
        Mob mob = stack.peek();
        if (extra[0] != mob) return null;
        boolean isPrivate = extra.length == 2 && extra[1] instanceof ServerClient;
        if (extra.length != 1 && !isPrivate) return null;
        String from = mob.getStringID();
        String to = mobMapping.get(from);
        if (to == null || to.equals(from)) return null;
        suppress.set(suppress.get() + 1);
        try {
            Mob template = MobRegistry.getMob(MobRegistry.getMobID(to), mob.getLevel());
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
        if (!objectMapping.isEmpty() && event.object != null) {
            String from = event.object.getStringID();
            String to = objectMapping.get(from);
            if (to != null && !to.equals(from)) {
                try {
                    GameObject replacement = ObjectRegistry.getObject(to);
                    if (replacement != null) {
                        event.objectDrops = replacement.getObjectDroppedItems(event.level, event.layerID, event.tileX, event.tileY, "onDestroyed");
                    }
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
