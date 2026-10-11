package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.gameDamageType.DamageType;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolItem;
import necesse.inventory.item.upgradeUtils.FloatUpgradeValue;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 5 (weapon damage) and the weapon half of feature 6 (crit chance).
 * Applied in ToolItem.getFlatAttackDamage, which every weapon attack, projectile and tooltip goes through.
 */
public final class WeaponRandomizer {

    private static final int SHUFFLE_WINDOW = 6;

    private static float[] damageFactor = new float[0];
    private static float[] critBonus = new float[0];

    private WeaponRandomizer() {
    }

    public static void build() {
        SRSSettings s = SRS.settings();
        int size = 0;
        for (Item item : ItemRegistry.getItems()) size = Math.max(size, item.getID() + 1);
        float[] dmg = new float[size];
        float[] crit = new float[size];
        java.util.Arrays.fill(dmg, 1.0F);
        if (!s.weaponDamage && !s.critChance) {
            damageFactor = dmg;
            critBonus = crit;
            return;
        }

        // Weapons grouped by damage type, with their base (tier 0) damage
        Map<String, List<String>> byType = new HashMap<>();
        Map<String, Float> baseDamage = new HashMap<>();
        Map<String, ToolItem> weapons = new HashMap<>();
        for (Item item : ItemRegistry.getItems()) {
            if (!(item instanceof ToolItem) || item instanceof ToolDamageItem) continue;
            if (item.getClass().getName().contains(".miscToolItem.")) continue;
            String id = item.getStringID();
            if (SRS.isItemExcluded(id) || !ItemRegistry.isObtainable(item.getID())) continue;
            try {
                FloatUpgradeValue attackDamage = Reflect.get(ToolItem.class, item, "attackDamage");
                float base = attackDamage.getValue(0.0F);
                if (base <= 0) continue;
                DamageType type = Reflect.get(ToolItem.class, item, "damageType");
                String typeName = type == null ? "none" : type.getStringID();
                byType.computeIfAbsent(typeName, k -> new ArrayList<>()).add(id);
                baseDamage.put(id, base);
                weapons.put(id, (ToolItem) item);
            } catch (Exception ignored) {
            }
        }

        String mode = s.weaponDamageMode == null ? "multiplier" : s.weaponDamageMode.toLowerCase();
        List<String> lines = Spoiler.section("5/6 - Armas (modo " + mode + ": dano base original -> novo, critico extra)");
        List<String> types = new ArrayList<>(byType.keySet());
        Collections.sort(types);
        for (String type : types) {
            List<String> ids = byType.get(type);
            Map<String, String> perm = mode.equals("chaos") ? SeedRandom.permutation("weapons:damage:" + type, ids)
                    : mode.equals("shuffle") ? similarStrengthPermutation("weapons:damage:" + type, ids, baseDamage) : null;
            List<String> sorted = new ArrayList<>(ids);
            Collections.sort(sorted);
            for (String id : sorted) {
                int numeric = weapons.get(id).getID();
                float base = baseDamage.get(id);
                float factor = 1.0F;
                if (s.weaponDamage) {
                    factor = perm != null ? baseDamage.get(perm.get(id)) / base : SeedRandom.range("weapons:mult:" + id, s.weaponDamageMin, s.weaponDamageMax);
                }
                float bonus = s.critChance ? SeedRandom.range("weapons:crit:" + id, s.weaponCritMin, s.weaponCritMax) : 0.0F;
                dmg[numeric] = factor;
                crit[numeric] = bonus;
                lines.add("[" + type + "] " + id + ": " + Math.round(base) + " -> " + Math.round(base * factor)
                        + (bonus != 0 ? ", critico +" + Math.round(bonus * 100) + "%" : ""));
            }
        }
        damageFactor = dmg;
        critBonus = crit;
    }

    /** Weapons sorted by base damage are cut into small windows and only swap damage inside their window. */
    private static Map<String, String> similarStrengthPermutation(String key, List<String> ids, Map<String, Float> baseDamage) {
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort((a, b) -> {
            int c = Float.compare(baseDamage.get(a), baseDamage.get(b));
            return c != 0 ? c : a.compareTo(b);
        });
        Map<String, String> out = new HashMap<>();
        for (int start = 0; start < sorted.size(); start += SHUFFLE_WINDOW) {
            List<String> window = sorted.subList(start, Math.min(sorted.size(), start + SHUFFLE_WINDOW));
            out.putAll(SeedRandom.permutation(key + ":" + start, window));
        }
        return out;
    }

    /** Called with the result of ToolItem.getFlatAttackDamage. */
    public static GameDamage modify(ToolItem item, GameDamage damage) {
        if (damage == null || item == null) return damage;
        int id = item.getID();
        if (id < 0 || id >= damageFactor.length) return damage;
        float factor = damageFactor[id];
        float crit = critBonus[id];
        if (factor == 1.0F && crit == 0.0F) return damage;
        return new GameDamage(damage.type, damage.damage * factor, damage.armorPen, damage.baseCritChance + crit,
                damage.playerDamageMultiplier, damage.finalDamageMultiplier);
    }

}
