package srs.random;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import necesse.engine.modifiers.Modifier;
import necesse.engine.modifiers.ModifierContainer;
import necesse.engine.modifiers.ModifierTooltip;
import necesse.engine.network.gameNetworkData.GNDItemInventoryItem;
import necesse.engine.registries.BuffRegistry;
import necesse.engine.registries.DamageTypeRegistry;
import necesse.engine.registries.GameRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.EquipmentBuffManager;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobGenericEvent;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.EquipmentActiveBuff;
import necesse.entity.mobs.buffs.staticBuffs.Buff;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.ArmorBuff;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.setBonusBuffs.SetBonusBuff;
import necesse.entity.mobs.gameDamageType.DamageType;
import necesse.gfx.GameColor;
import necesse.gfx.gameTooltips.DefaultColoredGameTooltips;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemStatTipList;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.armorItem.ArmorModifiers;
import necesse.inventory.item.armorItem.HelmetArmorItem;
import necesse.inventory.item.armorItem.SetHelmetArmorItem;
import necesse.inventory.item.trinketItem.SimpleTrinketItem;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Features 9 (equipment crit), 10 (armor set bonuses), 11 (equipment effects) and 12 (equipment damage %).
 * <ul>
 * <li>Set bonuses: the set bonus buff of every set helmet is swapped with another set's. In double mode every set
 * keeps its own bonus and also gets another set's, applied as a second buff next to the real one.</li>
 * <li>Effects: trinket buffs are swapped between trinkets, and the stat modifiers of armor pieces are swapped
 * between pieces of the same slot (each piece keeps its own armor value). The class and progression modes only swap
 * within the same damage class, or among neighbors when sorted by progression (rarity and value; armor value for armor).</li>
 * <li>Crit and damage %: every crit/damage modifier that armor, trinkets and set bonuses give is multiplied by a fixed
 * factor from the seed, and every piece gets a small random crit bonus.</li>
 * </ul>
 */
public final class EquipmentRandomizer {

    private static final Modifier<?>[] CRIT_MODIFIERS = {BuffModifiers.CRIT_CHANCE, BuffModifiers.MELEE_CRIT_CHANCE,
            BuffModifiers.RANGED_CRIT_CHANCE, BuffModifiers.MAGIC_CRIT_CHANCE, BuffModifiers.SUMMON_CRIT_CHANCE};
    private static final Modifier<?>[] DAMAGE_MODIFIERS = {BuffModifiers.ALL_DAMAGE, BuffModifiers.MELEE_DAMAGE,
            BuffModifiers.RANGED_DAMAGE, BuffModifiers.MAGIC_DAMAGE, BuffModifiers.SUMMON_DAMAGE};

    private static final Map<String, String> armorEffectMapping = new HashMap<>();
    private static final Map<String, String> extraSetBonus = new HashMap<>();
    /** The extra set bonus each equipment manager currently has on (identity keys: the manager doesn't override equals). */
    private static final Map<EquipmentBuffManager, Buff> activeExtraSetBonus = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<String> armorContext = new ThreadLocal<>();
    private static boolean transformValues;

    private EquipmentRandomizer() {
    }

    public static void build() {
        SRSSettings s = SRS.settings();
        armorEffectMapping.clear();
        extraSetBonus.clear();
        // Trinket classes are read from their buffs' modifiers, before those get randomized
        transformValues = false;

        if (s.armorSetBonuses) swapSetBonuses("double".equalsIgnoreCase(s.armorSetBonusMode));
        if (s.equipmentEffects) {
            String mode = s.equipmentEffectsMode == null ? "all" : s.equipmentEffectsMode.toLowerCase();
            swapTrinketEffects(mode);
            buildArmorEffectMapping(mode);
        }
        transformValues = s.critChance || s.equipmentDamage;
        if (transformValues) {
            List<String> lines = Spoiler.section("9/12 - Critico e % de dano de equipamentos");
            if (s.critChance) {
                lines.add("Critico ja existente multiplicado por " + s.equipmentCritMultMin + " a " + s.equipmentCritMultMax
                        + "; cada peca/acessorio/bonus de conjunto ganha +0% a +" + Math.round(s.equipmentCritBonusMax * 100) + "% de critico.");
            }
            if (s.equipmentDamage) {
                lines.add("Bonus de dano multiplicados por " + s.equipmentDamageMultMin + " a " + s.equipmentDamageMultMax + ".");
            }
            lines.add("Os valores finais aparecem na descricao de cada item dentro do jogo.");
        }
    }

    private static boolean isCandidate(Item item) {
        return !SRS.isItemExcluded(item.getStringID()) && ItemRegistry.isObtainable(item.getID());
    }

    private static void swapSetBonuses(boolean doubleBonus) {
        Map<String, SetHelmetArmorItem> helmets = new HashMap<>();
        List<String> buffs = new ArrayList<>();
        for (Item item : ItemRegistry.getItems()) {
            if (item instanceof SetHelmetArmorItem && isCandidate(item)) {
                SetHelmetArmorItem helmet = (SetHelmetArmorItem) item;
                if (helmet.buffType == null) continue;
                helmets.put(item.getStringID(), helmet);
            }
        }
        List<String> ids = new ArrayList<>(helmets.keySet());
        Collections.sort(ids);
        if (doubleBonus) {
            // Each set keeps its bonus and also gets the next one in a shuffled ring, so never its own
            List<String> ring = SeedRandom.shuffled("equipment:setbonus:double", ids);
            List<String> lines = Spoiler.section("10 - Bonus de conjunto duplo (capacete: bonus proprio + bonus extra)");
            Map<String, String> extras = new HashMap<>();
            for (int i = 0; i < ring.size(); i++) {
                String own = helmets.get(ring.get(i)).buffType;
                String extra = helmets.get(ring.get((i + 1) % ring.size())).buffType;
                if (!extra.equals(own) && BuffRegistry.getBuff(extra) instanceof SetBonusBuff) extras.put(ring.get(i), extra);
            }
            extraSetBonus.putAll(extras);
            for (String id : ids) lines.add(id + ": " + helmets.get(id).buffType + " + " + (extras.containsKey(id) ? extras.get(id) : "nenhum"));
            return;
        }
        for (String id : ids) buffs.add(helmets.get(id).buffType);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) order.add(i);
        Collections.shuffle(order, SeedRandom.rng("equipment:setbonus"));
        List<String> lines = Spoiler.section("10 - Bonus de conjunto (capacete: bonus original -> novo)");
        for (int i = 0; i < ids.size(); i++) {
            String newBuff = buffs.get(order.get(i));
            SetHelmetArmorItem helmet = helmets.get(ids.get(i));
            lines.add(ids.get(i) + ": " + helmet.buffType + " -> " + newBuff);
            Reflect.set(SetHelmetArmorItem.class, helmet, "buffType", newBuff);
        }
    }

    private static void swapTrinketEffects(String mode) {
        Map<String, SimpleTrinketItem> trinkets = new HashMap<>();
        for (Item item : ItemRegistry.getItems()) {
            if (item instanceof SimpleTrinketItem && isCandidate(item)) {
                trinkets.put(item.getStringID(), (SimpleTrinketItem) item);
            }
        }
        List<String> ids = new ArrayList<>(trinkets.keySet());
        Collections.sort(ids);
        Map<String, String[]> effects = new HashMap<>();
        for (String id : ids) effects.put(id, Reflect.get(SimpleTrinketItem.class, trinkets.get(id), "buffStringIDs"));
        List<String> lines = Spoiler.section("11 - Efeitos de acessorios (acessorio: efeito novo)");

        // trinket -> trinket whose effects it gets
        Map<String, String> source = new HashMap<>();
        Map<String, String> groupOf = new HashMap<>();
        if (mode.equals("class") || mode.equals("progression")) {
            Map<String, List<String>> groups = new TreeMap<>();
            if (mode.equals("class")) {
                for (String id : ids) groups.computeIfAbsent(trinketClass(effects.get(id)), k -> new ArrayList<>()).add(id);
            } else {
                List<Item> sorted = new ArrayList<>();
                for (String id : ids) sorted.add(trinkets.get(id));
                groups.putAll(progressionWindows(sorted, EquipmentRandomizer::rarityOrder, EquipmentRandomizer::progressValue));
            }
            for (Map.Entry<String, List<String>> g : groups.entrySet()) {
                source.putAll(SeedRandom.permutation("equipment:trinkets:" + mode + ":" + g.getKey(), g.getValue()));
                for (String id : g.getValue()) groupOf.put(id, g.getKey());
            }
        } else {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) order.add(i);
            Collections.shuffle(order, SeedRandom.rng("equipment:trinkets"));
            for (int i = 0; i < ids.size(); i++) source.put(ids.get(i), ids.get(order.get(i)));
        }
        for (String id : ids) {
            String[] newEffects = effects.get(source.get(id));
            Reflect.set(SimpleTrinketItem.class, trinkets.get(id), "buffStringIDs", newEffects);
            lines.add((groupOf.containsKey(id) ? "[" + groupOf.get(id) + "] " : "") + id + ": " + String.join(", ", newEffects)
                    + (source.get(id).equals(id) ? "" : " (de " + source.get(id) + ")"));
        }
    }

    private static void buildArmorEffectMapping(String mode) {
        Map<String, List<String>> groups = new TreeMap<>();
        Map<ArmorItem.ArmorType, List<Item>> bySlot = new HashMap<>();
        Map<String, String> classes = mode.equals("class") ? armorClasses() : Collections.<String, String>emptyMap();
        for (Item item : ItemRegistry.getItems()) {
            if (!(item instanceof ArmorItem)) continue;
            ArmorItem armor = (ArmorItem) item;
            if (armor.isCosmetic || armor.armorType == null || !isCandidate(item)) continue;
            String slot = armor.armorType.name().toLowerCase();
            if (mode.equals("class")) {
                String c = classes.get(item.getStringID());
                groups.computeIfAbsent(slot + "|" + (c == null ? CLASS_GENERAL : c), k -> new ArrayList<>()).add(item.getStringID());
            } else if (mode.equals("progression")) {
                bySlot.computeIfAbsent(armor.armorType, k -> new ArrayList<>()).add(item);
            } else {
                groups.computeIfAbsent(armor.armorType.name(), k -> new ArrayList<>()).add(item.getStringID());
            }
        }
        for (Map.Entry<ArmorItem.ArmorType, List<Item>> e : bySlot.entrySet()) {
            Map<String, List<String>> windows = progressionWindows(e.getValue(), EquipmentRandomizer::baseArmorValue, EquipmentRandomizer::rarityOrder);
            for (Map.Entry<String, List<String>> w : windows.entrySet()) groups.put(e.getKey().name().toLowerCase() + "|" + w.getKey(), w.getValue());
        }
        List<String> lines = Spoiler.section("11 - Efeitos de pecas de armadura (peca usada -> efeitos de)");
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            // "all" keeps the old keys (one group per slot), so seeds keep their results
            String key = mode.equals("class") || mode.equals("progression") ? "equipment:armoreffects:" + mode + ":" + g.getKey() : "equipment:armoreffects:" + g.getKey();
            Map<String, String> perm = SeedRandom.permutation(key, g.getValue());
            armorEffectMapping.putAll(perm);
            List<String> sorted = new ArrayList<>(g.getValue());
            Collections.sort(sorted);
            for (String id : sorted) lines.add("[" + g.getKey().toLowerCase() + "] " + id + " -> " + perm.get(id));
        }
    }

    // ===== Classes and progression (feature 11 modes) =====

    private static final String CLASS_GENERAL = "geral";
    /** How many items one progression window holds: items only swap with their neighbors in progression order. */
    private static final int PROGRESSION_WINDOW = 8;

    private static String className(DamageType type) {
        if (type == DamageTypeRegistry.MELEE) return "corpoacorpo";
        if (type == DamageTypeRegistry.RANGED) return "distancia";
        if (type == DamageTypeRegistry.MAGIC) return "magia";
        if (type == DamageTypeRegistry.SUMMON) return "invocacao";
        return CLASS_GENERAL;
    }

    /** A trinket's class is the damage class most of its buffs' modifiers are about (melee damage, mana, summons...). */
    private static String trinketClass(String[] buffIDs) {
        Map<String, Integer> counts = new TreeMap<>();
        Mob dummy = null;
        try {
            dummy = MobRegistry.getMob(MobRegistry.getMobID("human"));
        } catch (Throwable ignored) {
        }
        if (dummy == null || buffIDs == null) return CLASS_GENERAL;
        for (String buffID : buffIDs) {
            Buff buff = BuffRegistry.getBuff(buffID);
            if (buff == null) continue;
            try {
                ActiveBuff ab = new ActiveBuff(buff, dummy, 1, null);
                ab.getGndData().setBoolean("tooltipInit", true);
                ab.init(NO_EVENTS);
                for (Modifier<?> m : BuffModifiers.LIST) {
                    String c = m.stringID == null ? null : modifierClass(m.stringID.toLowerCase());
                    // Only bonuses count: the melee focus gives +melee damage and takes from every other class
                    if (c != null && isBonus(m.stringID.toLowerCase(), ab.getModifier(m), m.defaultBuffValue)) counts.merge(c, 1, Integer::sum);
                }
            } catch (Throwable ignored) {
            }
        }
        String best = CLASS_GENERAL;
        int bestCount = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
                tie = false;
            } else if (e.getValue() == bestCount) {
                tie = true;
            }
        }
        return tie ? CLASS_GENERAL : best;
    }

    /** The class a buff modifier is about, from its ID ("rangedamage", "arrowusage", "manausage", "maxsummons"...), or null. */
    private static String modifierClass(String id) {
        if (id.startsWith("melee")) return "corpoacorpo";
        if (id.startsWith("range") || id.startsWith("arrow") || id.startsWith("bullet") || id.startsWith("projectile") || id.startsWith("throwing")) {
            return "distancia";
        }
        if (id.startsWith("magic") || id.contains("mana")) return "magia";
        if (id.startsWith("summon") || id.equals("maxsummons")) return "invocacao";
        return null;
    }

    /** True when the value helps: higher than the default, or lower for the "usage" modifiers (less mana, fewer arrows). */
    private static boolean isBonus(String id, Object value, Object defaultValue) {
        int sign = id.endsWith("usage") ? -1 : 1;
        if (value instanceof Float && defaultValue instanceof Float) return Float.compare((Float) value, (Float) defaultValue) * sign > 0;
        if (value instanceof Integer && defaultValue instanceof Integer) return Integer.compare((Integer) value, (Integer) defaultValue) * sign > 0;
        return false;
    }

    /** Armor piece -> class: helmets have one; chests and boots take the class of the set helmets that use them. */
    private static Map<String, String> armorClasses() {
        Map<String, java.util.Set<String>> found = new HashMap<>();
        for (Item item : ItemRegistry.getItems()) {
            if (!(item instanceof HelmetArmorItem)) continue;
            String c = className(((HelmetArmorItem) item).damageClass);
            found.computeIfAbsent(item.getStringID(), k -> new java.util.TreeSet<>()).add(c);
            if (item instanceof SetHelmetArmorItem) {
                SetHelmetArmorItem helmet = (SetHelmetArmorItem) item;
                if (helmet.setChestStringID != null) found.computeIfAbsent(helmet.setChestStringID, k -> new java.util.TreeSet<>()).add(c);
                if (helmet.setBootsStringID != null) found.computeIfAbsent(helmet.setBootsStringID, k -> new java.util.TreeSet<>()).add(c);
            }
        }
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, java.util.Set<String>> e : found.entrySet()) {
            out.put(e.getKey(), e.getValue().size() == 1 ? e.getValue().iterator().next() : CLASS_GENERAL);
        }
        return out;
    }

    /**
     * Sorts the items by progression (first key, then second key, then ID) and cuts the list into windows of
     * {@link #PROGRESSION_WINDOW} neighbors. The last window absorbs a small remainder so no item is left alone.
     */
    private static Map<String, List<String>> progressionWindows(List<Item> items, java.util.function.ToDoubleFunction<Item> first,
                                                                java.util.function.ToDoubleFunction<Item> second) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            int c = Double.compare(first.applyAsDouble(a), first.applyAsDouble(b));
            if (c == 0) c = Double.compare(second.applyAsDouble(a), second.applyAsDouble(b));
            return c != 0 ? c : a.getStringID().compareTo(b.getStringID());
        });
        int windows = Math.max(1, Math.round(sorted.size() / (float) PROGRESSION_WINDOW));
        Map<String, List<String>> out = new TreeMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            int w = Math.min(windows - 1, i * windows / Math.max(1, sorted.size()));
            out.computeIfAbsent(String.format(java.util.Locale.ROOT, "fase%02d", w + 1), k -> new ArrayList<>()).add(sorted.get(i).getStringID());
        }
        return out;
    }

    /** The value the item was registered with (rises with progression); computed-from-recipe values are stored negative. */
    private static double progressValue(Item item) {
        try {
            Method getElement = GameRegistry.class.getDeclaredMethod("getElement", int.class);
            getElement.setAccessible(true);
            Object element = getElement.invoke(ItemRegistry.instance, item.getID());
            Float value = Reflect.get(element, "setBrokerValue");
            return Math.abs(value);
        } catch (Exception e) {
            return ItemRegistry.getBrokerValue(item.getID());
        }
    }

    private static double rarityOrder(Item item) {
        Item.Rarity rarity = item.getRarity(new InventoryItem(item));
        return rarity == null ? 0 : rarity.ordinal();
    }

    private static double baseArmorValue(Item item) {
        try {
            return ((ArmorItem) item).getTotalArmorValue(new InventoryItem(item), null);
        } catch (Throwable e) {
            return 0;
        }
    }

    // ===== Hooks =====

    private static InventoryItem proxyFor(InventoryItem original) {
        if (original == null || !(original.item instanceof ArmorItem)) return null;
        String to = armorEffectMapping.get(original.item.getStringID());
        if (to == null || to.equals(original.item.getStringID())) return null;
        Item replacement = ItemRegistry.getItem(to);
        if (!(replacement instanceof ArmorItem)) return null;
        InventoryItem proxy = new InventoryItem(replacement, 1);
        proxy.setGndData(original.getGndData());
        return proxy;
    }

    /** EquipmentActiveBuff.addArmorBuff enter. Returns the item to use for the armor's effects. */
    public static InventoryItem beforeAddArmorBuff(InventoryItem armor) {
        armorContext.set(armor == null || armor.item == null ? null : armor.item.getStringID());
        InventoryItem proxy = proxyFor(armor);
        return proxy == null ? armor : proxy;
    }

    /** EquipmentActiveBuff.addArmorBuff exit. Restores the original piece's armor value when its effects were swapped. */
    public static void afterAddArmorBuff(EquipmentActiveBuff buff, Mob mob, InventoryItem original, InventoryItem used) {
        armorContext.remove();
        if (original == null || used == null || original == used) return;
        try {
            int delta = ((ArmorItem) original.item).getTotalArmorValue(original, mob) - ((ArmorItem) used.item).getTotalArmorValue(used, mob);
            if (delta != 0) buff.addModifier(BuffModifiers.ARMOR_FLAT, delta);
        } catch (Exception e) {
            SRS.warn("Could not fix armor value: " + e);
        }
    }

    /**
     * Replaces ArmorItem.addModifierTooltips so the tooltip shows the swapped and randomized modifiers.
     * Returns true when it handled the tooltip.
     */
    public static boolean addModifierTooltips(ArmorItem self, ItemStatTipList list, InventoryItem current, InventoryItem last, Mob perspective) {
        if (armorEffectMapping.isEmpty() && !transformValues) return false;
        if (current == null) return false;
        String previous = armorContext.get();
        try {
            InventoryItem currentProxy = proxyFor(current);
            InventoryItem currentUsed = currentProxy == null ? current : currentProxy;
            armorContext.set(current.item.getStringID());
            ArmorModifiers modifiers = ((ArmorItem) currentUsed.item).getArmorModifiers(currentUsed, perspective);
            ArmorModifiers lastModifiers = null;
            if (last != null && last.item instanceof ArmorItem) {
                InventoryItem lastProxy = proxyFor(last);
                InventoryItem lastUsed = lastProxy == null ? last : lastProxy;
                armorContext.set(last.item.getStringID());
                lastModifiers = ((ArmorItem) lastUsed.item).getArmorModifiers(lastUsed, perspective);
            }
            if (modifiers != null) {
                for (ModifierTooltip tooltip : modifiers.getModifierTooltips(lastModifiers)) {
                    list.add(200, tooltip.tip);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (previous == null) {
                armorContext.remove();
            } else {
                armorContext.set(previous);
            }
        }
    }

    // ===== Double set bonus (feature 10) =====

    private static final BuffEventSubscriber NO_EVENTS = new BuffEventSubscriber() {
        @Override
        public <T extends MobGenericEvent> void subscribeEvent(Class<T> eventClass, Consumer<T> onEvent) {
        }
    };

    /** Extra set bonus buff of each set helmet ("double" mode). */
    public static String getExtraSetBonus(String helmetStringID) {
        return extraSetBonus.get(helmetStringID);
    }

    /** EquipmentBuffManager.updateArmorBuffs enter: removes the extra set bonus given last time. */
    public static void beforeUpdateArmorBuffs(EquipmentBuffManager manager, Mob owner) {
        if (extraSetBonus.isEmpty() || owner == null) return;
        Buff previous = activeExtraSetBonus.remove(manager);
        if (previous != null) owner.buffManager.removeBuff(previous, false);
    }

    /** EquipmentBuffManager.updateArmorBuffs exit: while the set bonus is on, adds the helmet's extra set bonus next to it. */
    public static void afterUpdateArmorBuffs(EquipmentBuffManager manager, Mob owner, Object activeSetBonus) {
        if (extraSetBonus.isEmpty() || owner == null || activeSetBonus == null) return;
        InventoryItem head = manager.getArmorItem(0);
        if (head == null || !(head.item instanceof ArmorItem)) return;
        String extra = extraSetBonus.get(head.item.getStringID());
        Buff buff = extra == null ? null : BuffRegistry.getBuff(extra);
        if (!(buff instanceof SetBonusBuff) || owner.buffManager.hasBuff(buff)) return;
        try {
            InventoryItem chest = manager.getArmorItem(1);
            InventoryItem feet = manager.getArmorItem(2);
            ActiveBuff ab = new ActiveBuff(buff, owner, 0, null);
            ab.getGndData().setItem("headItem", new GNDItemInventoryItem(head));
            ab.getGndData().setItem("chestItem", new GNDItemInventoryItem(chest));
            ab.getGndData().setItem("feetItem", new GNDItemInventoryItem(feet));
            ((ArmorItem) head.item).setupSetBuff(head, chest, feet, owner, ab, false);
            owner.buffManager.addBuff(ab, false);
            activeExtraSetBonus.put(manager, buff);
        } catch (Exception e) {
            SRS.warn("Could not add extra set bonus " + extra + ": " + e);
        }
    }

    /** ArmorItem.getPostEnchantmentTooltips exit: shows the extra set bonus under the helmet's own. */
    public static void addExtraSetBonusTooltip(ArmorItem self, ListGameTooltips tooltips, InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        if (extraSetBonus.isEmpty() || tooltips == null || item == null || perspective == null || blackboard == null) return;
        if (blackboard.getBoolean("isCosmeticSlot")) return;
        String extra = extraSetBonus.get(self.getStringID());
        Buff buff = extra == null ? null : BuffRegistry.getBuff(extra);
        if (!(buff instanceof SetBonusBuff)) return;
        try {
            InventoryItem helmet = blackboard.get(InventoryItem.class, "setHelmet");
            InventoryItem chest = blackboard.get(InventoryItem.class, "setChest");
            InventoryItem boots = blackboard.get(InventoryItem.class, "setBoots");
            boolean hasSet = helmet != null && chest != null && boots != null && self.hasSet(helmet, chest, boots);
            ActiveBuff ab = new ActiveBuff(buff, perspective, 1, null);
            ab.getGndData().setBoolean("tooltipInit", true);
            self.setupSetBuff(item, chest, boots, perspective, ab, false);
            ab.init(NO_EVENTS);
            GameBlackboard buffBlackboard = new GameBlackboard();
            buffBlackboard.set("setItem", item);
            if (hasSet) {
                tooltips.add("Bônus duplo:");
                tooltips.add(ab.getTooltips(buffBlackboard));
            } else {
                tooltips.add(new StringTooltips("Bônus duplo:", GameColor.GRAY));
                tooltips.add(new DefaultColoredGameTooltips(ab.getTooltips(buffBlackboard), GameColor.GRAY));
            }
        } catch (Exception ignored) {
        }
    }

    /** ArmorModifiers constructor exit: randomizes the values of the piece currently being equipped/inspected. */
    public static void onArmorModifiersCreated(ArmorModifiers modifiers) {
        String context = armorContext.get();
        if (!transformValues || context == null) return;
        transform(modifiers, "armor:" + context);
    }

    /** ActiveBuff.init exit: randomizes trinket, set bonus and armor buffs. */
    public static void onBuffInit(ActiveBuff buff) {
        if (!transformValues || buff == null || !(buff.buff instanceof ArmorBuff)) return;
        transform(buff, "buff:" + buff.buff.getStringID());
    }

    private static void transform(ModifierContainer container, String key) {
        SRSSettings s = SRS.settings();
        if (s.critChance) {
            for (Modifier<?> m : CRIT_MODIFIERS) {
                float v = (Float) container.getModifier(m);
                if (v != 0.0F) setRaw(container, m, v * SeedRandom.range(key + ":crit:" + m.stringID, s.equipmentCritMultMin, s.equipmentCritMultMax));
            }
            float bonus = SeedRandom.range(key + ":critbonus", 0.0F, s.equipmentCritBonusMax);
            if (bonus > 0.0F) {
                setRaw(container, BuffModifiers.CRIT_CHANCE, (Float) container.getModifier(BuffModifiers.CRIT_CHANCE) + bonus);
            }
        }
        if (s.equipmentDamage) {
            for (Modifier<?> m : DAMAGE_MODIFIERS) {
                float v = (Float) container.getModifier(m);
                if (v != 0.0F) setRaw(container, m, v * SeedRandom.range(key + ":damage:" + m.stringID, s.equipmentDamageMultMin, s.equipmentDamageMultMax));
            }
        }
    }

    /** Writes a modifier value without triggering ActiveBuff's buff manager update (the owner may not exist yet). */
    private static void setRaw(ModifierContainer container, Modifier<?> modifier, Object value) {
        Object[] values = Reflect.get(ModifierContainer.class, container, "modifiers");
        HashSet<Integer> nonDefault = Reflect.get(ModifierContainer.class, container, "nonDefaultModifiers");
        values[modifier.index] = value;
        if (value.equals(modifier.defaultBuffValue)) {
            nonDefault.remove(modifier.index);
        } else {
            nonDefault.add(modifier.index);
        }
    }

}
