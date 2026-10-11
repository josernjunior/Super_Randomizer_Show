package srs.random;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.Mob;
import srs.SRSSettings;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Features 7 (defense = armor) and 8 (knockback taken) for enemies, villagers, critters, animals and bosses.
 * <ul>
 * <li>multiplier: the stat is multiplied by a fixed factor from the seed, between the min/max sliders.</li>
 * <li>preswap: a mob that took another mob's place gets the base value of the mob it replaced, times the slider
 * multiplier. Values are replaced, not scaled, because many mobs have 0 armor.</li>
 * </ul>
 * Applied to the final getters (Mob.getArmor and Mob.getKnockbackModifier): nothing is stored, so nothing accumulates.
 */
public final class MobStatRandomizer {

    public enum Stat {
        ARMOR("Armor", "7", "Defesa", "armadura"),
        KNOCKBACK("Knockback", "8", "Empurrao", "empurrao");

        /** Suffix of the settings fields: enemyArmor, enemyArmorMode, enemyArmorMin, enemyArmorMax... */
        public final String key;
        public final String number;
        public final String title;
        public final String word;

        Stat(String key, String number, String title, String word) {
            this.key = key;
            this.number = number;
            this.title = title;
            this.word = word;
        }
    }

    /** The groups every stat is randomized for, with the prefix of their settings fields. */
    public static final Object[][] GROUPS = {
            {Pool.HOSTILE, "enemy", "inimigos"}, {Pool.TOWN, "villager", "aldeoes"}, {Pool.CRITTER, "critter", "bichos"},
            {Pool.ANIMAL, "animal", "animais"}, {Pool.BOSS, "boss", "chefes"}};

    private static final Map<Stat, float[]> mult = new HashMap<>();
    private static final Map<Stat, float[]> add = new HashMap<>();
    private static final Map<String, Float> baseCache = new HashMap<>();

    private MobStatRandomizer() {
    }

    public static void build() {
        for (Stat stat : Stat.values()) buildStat(stat);
    }

    private static void buildStat(Stat stat) {
        SRSSettings s = SRS.settings();
        int size = MobRegistry.getMobs().size();
        float[] m = new float[size];
        float[] a = new float[size];
        java.util.Arrays.fill(m, 1.0F);
        boolean any = false;
        List<String> lines = null;
        for (int g = 0; g < GROUPS.length; g++) {
            Pool pool = (Pool) GROUPS[g][0];
            String prefix = (String) GROUPS[g][1];
            String field = prefix + stat.key;
            if (!Boolean.TRUE.equals(setting(s, field))) continue;
            if (lines == null) lines = Spoiler.section(stat.number + " - " + stat.title + " (tipo: " + stat.word + " base -> nova)");
            any = true;
            String mode = (String) setting(s, field + "Mode");
            float min = (Float) setting(s, field + "Min");
            float max = (Float) setting(s, field + "Max");
            boolean preswap = "preswap".equalsIgnoreCase(mode);
            String label = stat.number + "." + (g + 1) + " " + GROUPS[g][2];
            lines.add("[" + label + "] modo " + (preswap ? stat.word + " antes da troca" : "multiplicador") + ", multiplicador " + min + " a " + max);

            List<String> ids = new ArrayList<>(MobPools.get(pool));
            Collections.sort(ids);
            Map<String, String> replacedBy = new HashMap<>();
            for (String original : ids) {
                String replacement = MobSpawnRandomizer.getMapping(original);
                if (replacement != null) replacedBy.put(replacement, original);
            }
            for (String id : ids) {
                float multiplier = SeedRandom.range("mobstat:" + stat.name() + ":" + pool.name() + ":" + id, min, max);
                float own = base(stat, id);
                float mValue = multiplier;
                float aValue = 0.0F;
                float result = own * multiplier;
                String detail = "multiplicador x" + format(multiplier);
                String original = preswap ? replacedBy.get(id) : null;
                if (original != null && !original.equals(id)) {
                    float target = base(stat, original) * multiplier;
                    mValue = 1.0F;
                    aValue = target - own;
                    result = target;
                    detail = "no lugar de " + original + " (" + stat.word + " " + format(base(stat, original)) + "), multiplicador x" + format(multiplier);
                } else if (preswap) {
                    detail = "não trocou de lugar, multiplicador x" + format(multiplier);
                }
                apply(m, a, id, mValue, aValue);
                String[] fighters = MobPools.fighters(id);
                if (fighters != null) for (String fighter : fighters) apply(m, a, fighter, mValue, aValue);
                lines.add(id + ": " + stat.word + " " + format(own) + " -> " + format(Math.max(0.0F, result)) + "  [" + detail + "]");
            }
        }
        mult.put(stat, any ? m : new float[0]);
        add.put(stat, any ? a : new float[0]);
    }

    private static void apply(float[] m, float[] a, String id, float mValue, float aValue) {
        int numericID = MobRegistry.getMobID(id);
        if (numericID < 0 || numericID >= m.length) return;
        m[numericID] = mValue;
        a[numericID] = aValue;
    }

    private static Object setting(SRSSettings s, String field) {
        try {
            Field f = SRSSettings.class.getField(field);
            return f.get(s);
        } catch (Exception e) {
            throw new IllegalStateException("Missing setting " + field, e);
        }
    }

    /** Base value of a stat for a mob type (starter bosses: average of their fighters). */
    private static float base(Stat stat, String id) {
        String key = stat.name() + ":" + id;
        Float cached = baseCache.get(key);
        if (cached != null) return cached;
        float value;
        String[] fighters = MobPools.fighters(id);
        if (fighters != null) {
            float sum = 0.0F;
            for (String fighter : fighters) sum += rawBase(stat, fighter);
            value = sum / fighters.length;
        } else {
            value = rawBase(stat, id);
        }
        baseCache.put(key, value);
        return value;
    }

    private static float rawBase(Stat stat, String id) {
        try {
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(id));
            if (mob == null) return 0.0F;
            if (stat == Stat.ARMOR) return mob.getArmorFlat();
            Float knockback = Reflect.get(Mob.class, mob, "knockbackModifier");
            return knockback == null ? 1.0F : knockback;
        } catch (Throwable e) {
            return stat == Stat.ARMOR ? 0.0F : 1.0F;
        }
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static float modify(Stat stat, Mob mob, float value) {
        float[] m = mult.get(stat);
        if (m == null || m.length == 0 || mob == null) return value;
        int id = mob.getID();
        if (id < 0 || id >= m.length) return value;
        float[] a = add.get(stat);
        return Math.max(0.0F, value * m[id] + a[id]);
    }

    /** Called with the result of Mob.getArmor(). */
    public static float modifyArmor(Mob mob, float armor) {
        return modify(Stat.ARMOR, mob, armor);
    }

    /** Called with the result of Mob.getKnockbackModifier(). */
    public static float modifyKnockback(Mob mob, float knockback) {
        return modify(Stat.KNOCKBACK, mob, knockback);
    }

}
