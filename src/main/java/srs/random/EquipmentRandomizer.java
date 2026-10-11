package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import necesse.engine.modifiers.Modifier;
import necesse.engine.modifiers.ModifierContainer;
import necesse.engine.modifiers.ModifierTooltip;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.EquipmentActiveBuff;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.ArmorBuff;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemStatTipList;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.armorItem.ArmorModifiers;
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
 * <li>Set bonuses: the set bonus buff of every set helmet is swapped with another set's.</li>
 * <li>Effects: trinket buffs are swapped between trinkets, and the stat modifiers of armor pieces are swapped
 * between pieces of the same slot (each piece keeps its own armor value).</li>
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
    private static final ThreadLocal<String> armorContext = new ThreadLocal<>();
    private static boolean transformValues;

    private EquipmentRandomizer() {
    }

    public static void build() {
        SRSSettings s = SRS.settings();
        armorEffectMapping.clear();
        transformValues = s.critChance || s.equipmentDamage;

        if (s.armorSetBonuses) swapSetBonuses();
        if (s.equipmentEffects) {
            swapTrinketEffects();
            buildArmorEffectMapping();
        }
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

    private static void swapSetBonuses() {
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

    private static void swapTrinketEffects() {
        Map<String, SimpleTrinketItem> trinkets = new HashMap<>();
        for (Item item : ItemRegistry.getItems()) {
            if (item instanceof SimpleTrinketItem && isCandidate(item)) {
                trinkets.put(item.getStringID(), (SimpleTrinketItem) item);
            }
        }
        List<String> ids = new ArrayList<>(trinkets.keySet());
        Collections.sort(ids);
        List<String[]> effects = new ArrayList<>();
        for (String id : ids) effects.add(Reflect.get(SimpleTrinketItem.class, trinkets.get(id), "buffStringIDs"));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) order.add(i);
        Collections.shuffle(order, SeedRandom.rng("equipment:trinkets"));
        List<String> lines = Spoiler.section("11 - Efeitos de acessorios (acessorio: efeito novo)");
        for (int i = 0; i < ids.size(); i++) {
            String[] newEffects = effects.get(order.get(i));
            Reflect.set(SimpleTrinketItem.class, trinkets.get(ids.get(i)), "buffStringIDs", newEffects);
            lines.add(ids.get(i) + ": " + String.join(", ", newEffects));
        }
    }

    private static void buildArmorEffectMapping() {
        Map<ArmorItem.ArmorType, List<String>> bySlot = new HashMap<>();
        for (Item item : ItemRegistry.getItems()) {
            if (!(item instanceof ArmorItem)) continue;
            ArmorItem armor = (ArmorItem) item;
            if (armor.isCosmetic || armor.armorType == null || !isCandidate(item)) continue;
            bySlot.computeIfAbsent(armor.armorType, k -> new ArrayList<>()).add(item.getStringID());
        }
        List<String> lines = Spoiler.section("11 - Efeitos de pecas de armadura (peca usada -> efeitos de)");
        for (ArmorItem.ArmorType slot : ArmorItem.ArmorType.values()) {
            List<String> ids = bySlot.get(slot);
            if (ids == null) continue;
            Map<String, String> perm = SeedRandom.permutation("equipment:armoreffects:" + slot.name(), ids);
            armorEffectMapping.putAll(perm);
            List<String> sorted = new ArrayList<>(ids);
            Collections.sort(sorted);
            for (String id : sorted) lines.add("[" + slot.name().toLowerCase() + "] " + id + " -> " + perm.get(id));
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
