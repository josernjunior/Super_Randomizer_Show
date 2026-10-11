package srs.random;

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
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 6: max health of enemies (6.1), villagers (6.2), critters (6.3), animals (6.4) and bosses (6.5).
 * <ul>
 * <li>multiplier: every type gets a fixed multiplier from the seed, between the min/max sliders.</li>
 * <li>preswap: a mob that took another mob's place (feature 1) gets the max health of the mob it replaced,
 * times the slider multiplier (x1.0 to x1.0 = exactly the original's health).</li>
 * </ul>
 * Applied to the result of Mob.getMaxHealth(), which only reads the stored value, so nothing accumulates across
 * saves, loads or the game's own difficulty adjustments (those use getMaxHealthFlat).
 */
public final class MobHealthRandomizer {

    public static final String PRESWAP = "preswap";

    /** Factor per numeric mob ID; getMaxHealth is called constantly, so this has to be a plain array lookup. */
    private static float[] factors = new float[0];
    private static final Map<String, Integer> baseHealth = new HashMap<>();
    /**
     * Sage and Grit's starter has a placeholder health; the boss really is Sage + Grit. (The night swarm starter's
     * health already is the whole swarm's: bat health times the starting bat count.)
     */
    private static final Map<String, String[]> HEALTH_FROM_FIGHTERS = new HashMap<>();

    static {
        HEALTH_FROM_FIGHTERS.put("sageandgrit", new String[]{"sage", "grit"});
    }

    private MobHealthRandomizer() {
    }

    public static void build() {
        SRSSettings s = SRS.settings();
        float[] out = new float[0];
        if (!s.enemyHealth && !s.villagerHealth && !s.critterHealth && !s.animalHealth && !s.bossHealth) {
            factors = out;
            return;
        }
        out = new float[MobRegistry.getMobs().size()];
        java.util.Arrays.fill(out, 1.0F);
        List<String> lines = Spoiler.section("6 - Vida (tipo: vida base -> fator aplicado)");
        if (s.enemyHealth) addPool(out, Pool.HOSTILE, "6.1 inimigos", s.enemyHealthMode, s.enemyHealthMin, s.enemyHealthMax, lines);
        if (s.villagerHealth) addPool(out, Pool.TOWN, "6.2 aldeoes", s.villagerHealthMode, s.villagerHealthMin, s.villagerHealthMax, lines);
        if (s.critterHealth) addPool(out, Pool.CRITTER, "6.3 bichos", s.critterHealthMode, s.critterHealthMin, s.critterHealthMax, lines);
        if (s.animalHealth) addPool(out, Pool.ANIMAL, "6.4 animais", s.animalHealthMode, s.animalHealthMin, s.animalHealthMax, lines);
        if (s.bossHealth) addPool(out, Pool.BOSS, "6.5 chefes", s.bossHealthMode, s.bossHealthMin, s.bossHealthMax, lines);
        factors = out;
    }

    private static void addPool(float[] out, Pool pool, String label, String mode, float min, float max, List<String> lines) {
        boolean preswap = PRESWAP.equalsIgnoreCase(mode);
        lines.add("[" + label + "] modo " + (preswap ? "vida antes da troca" : "multiplicador") + ", multiplicador " + min + " a " + max);
        List<String> ids = new ArrayList<>(MobPools.get(pool));
        Collections.sort(ids);
        Map<String, String> replacedBy = new HashMap<>();
        for (String original : ids) {
            String replacement = MobSpawnRandomizer.getMapping(original);
            if (replacement != null) replacedBy.put(replacement, original);
        }
        for (String id : ids) {
            int numericID = MobRegistry.getMobID(id);
            if (numericID < 0 || numericID >= out.length) continue;
            float multiplier = SeedRandom.range("mobhealth:" + pool.name() + ":" + id, min, max);
            float factor = multiplier;
            String detail = "multiplicador x" + format(multiplier);
            if (preswap) {
                String original = replacedBy.get(id);
                if (original != null && !original.equals(id)) {
                    int a = health(original);
                    int b = health(id);
                    float ratio = a > 0 && b > 0 ? (float) a / b : 1.0F;
                    factor = Math.max(0.02F, Math.min(50.0F, ratio)) * multiplier;
                    detail = "no lugar de " + original + " (vida " + a + "), multiplicador x" + format(multiplier);
                } else {
                    detail = "não trocou de lugar, multiplicador x" + format(multiplier);
                }
            }
            out[numericID] = factor;
            // A starter boss (sage and grit, night swarm) is fought through the mobs it spawns
            String[] fighters = MobPools.fighters(id);
            if (fighters != null) {
                for (String fighter : fighters) {
                    int fighterID = MobRegistry.getMobID(fighter);
                    if (fighterID >= 0 && fighterID < out.length) out[fighterID] = factor;
                }
            }
            lines.add(id + ": vida " + health(id) + " -> x" + format(factor) + " = " + Math.max(1, Math.round(health(id) * factor)) + "  [" + detail + "]");
        }
    }

    /** Base max health of a type, read from a fresh mob before any factor exists. */
    private static int health(String id) {
        Integer cached = baseHealth.get(id);
        if (cached != null) return cached;
        int value = 0;
        String[] parts = HEALTH_FROM_FIGHTERS.get(id);
        if (parts != null) {
            for (String part : parts) value += flatHealth(part);
        } else {
            value = flatHealth(id);
        }
        baseHealth.put(id, value);
        return value;
    }

    private static int flatHealth(String id) {
        try {
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(id));
            return mob == null ? 0 : mob.getMaxHealthFlat();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** Called with the result of Mob.getMaxHealth(). */
    public static int modify(Mob mob, int maxHealth) {
        float[] f = factors;
        if (f.length == 0 || mob == null) return maxHealth;
        int id = mob.getID();
        if (id < 0 || id >= f.length) return maxHealth;
        float factor = f[id];
        return factor == 1.0F ? maxHealth : Math.max(1, Math.round(maxHealth * factor));
    }

    public static float getFactor(String stringID) {
        int id = MobRegistry.getMobID(stringID);
        return id >= 0 && id < factors.length ? factors[id] : 1.0F;
    }

}
