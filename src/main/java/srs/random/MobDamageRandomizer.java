package srs.random;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.registries.ClassIDDataContainer;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import srs.SRSSettings;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Features 5.2 (enemy damage) and 5.3 (boss damage).
 * <ul>
 * <li>multiplier: every enemy/boss type hits with a fixed random multiplier from the seed.</li>
 * <li>preswap: a mob that took another mob's place (feature 1) hits as hard as the mob it replaced.
 * The base damage of every type is measured once at startup and stored in {@link #baseDamage}.</li>
 * </ul>
 * In both modes the fixed multiplier from the min/max sliders is applied on top (x1.0 to x1.0 = no change).
 * Applied in Mob.isServerHit, which every server-side hit goes through (melee, projectiles, explosions...).
 */
public final class MobDamageRandomizer {

    public static final String MULTIPLIER = "multiplier";
    public static final String PRESWAP = "preswap";

    /** Measured base damage per mob type (median of the damage values found on the mob and its AI). */
    private static final Map<String, Float> baseDamage = new HashMap<>();
    private static final Map<String, Integer> maxHealth = new HashMap<>();
    private static final Map<String, Float> factors = new HashMap<>();
    private static float damagePerHealth = 0.2F;
    private static final Map<Pool, float[]> poolRange = new HashMap<>();

    private MobDamageRandomizer() {
    }

    public static void build() {
        factors.clear();
        SRSSettings s = SRS.settings();
        if (!s.enemyDamage && !s.bossDamage) return;
        measure();
        List<String> lines = Spoiler.section("5.2/5.3 - Dano de inimigos e chefes (tipo: dano base medido -> fator aplicado)");
        if (s.enemyDamage) addFactors(Pool.HOSTILE, s.enemyDamageMode, s.enemyDamageMin, s.enemyDamageMax, lines);
        if (s.bossDamage) addFactors(Pool.BOSS, s.bossDamageMode, s.bossDamageMin, s.bossDamageMax, lines);
    }

    private static void addFactors(Pool pool, String mode, float min, float max, List<String> lines) {
        boolean preswap = PRESWAP.equalsIgnoreCase(mode);
        lines.add("[" + pool.name().toLowerCase() + "] modo " + (preswap ? "dano antes da troca" : "multiplicador") + ", multiplicador " + min + " a " + max);
        List<String> ids = new ArrayList<>(MobPools.get(pool));
        Collections.sort(ids);
        Map<String, String> replacedBy = new HashMap<>();
        if (preswap) {
            // Spawn mapping is original -> replacement; we need replacement -> original
            for (String original : ids) {
                String replacement = MobSpawnRandomizer.getMapping(original);
                if (replacement != null) replacedBy.put(replacement, original);
            }
        }
        for (String id : ids) {
            // The sliders' multiplier applies in both modes; in preswap it scales the original mob's damage
            float multiplier = SeedRandom.range("mobdamage:" + pool.name() + ":" + id, min, max);
            float factor;
            String detail;
            if (preswap) {
                String original = replacedBy.get(id);
                if (original == null || original.equals(id)) {
                    factor = multiplier;
                    detail = "não trocou de lugar, multiplicador x" + format(multiplier);
                } else {
                    factor = preswapFactor(original, id) * multiplier;
                    detail = "no lugar de " + original + " (" + describe(original) + "), multiplicador x" + format(multiplier);
                }
            } else {
                factor = multiplier;
                detail = "multiplicador";
            }
            if (factor != 1.0F) {
                factors.put(id, factor);
                // The fighters a starter boss spawns are the ones that actually hit
                String[] parts = MobPools.fighters(id);
                if (parts != null) for (String part : parts) factors.put(part, factor);
            }
            lines.add(id + ": " + describe(id) + " -> x" + format(factor) + "  [" + detail + "]");
        }
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String describe(String id) {
        Float dmg = baseDamage.get(id);
        return dmg != null ? "dano " + Math.round(dmg) : "dano estimado " + Math.round(damageOf(id)) + " pela vida " + maxHealth.getOrDefault(id, 0);
    }

    /** How much harder/softer the replacement must hit to match the mob it replaced. */
    private static float preswapFactor(String original, String replacement) {
        float a = damageOf(original);
        float b = damageOf(replacement);
        float factor = a > 0 && b > 0 ? a / b : 1.0F;
        return Math.max(0.05F, Math.min(20.0F, factor));
    }

    /**
     * Measured base damage, or an estimate from max health for the few mobs whose damage couldn't be found,
     * kept within the range of damage measured for the same group.
     */
    private static float damageOf(String id) {
        Float measured = baseDamage.get(id);
        if (measured != null) return measured;
        int health = maxHealth.getOrDefault(id, 0);
        if (health <= 0) return 0.0F;
        float estimate = health * damagePerHealth;
        float[] range = poolRange.get(MobPools.poolOf(id));
        return range == null ? estimate : Math.max(range[0], Math.min(range[1], estimate));
    }

    // ===== Measuring =====

    private static synchronized void measure() {
        if (!baseDamage.isEmpty()) return;
        for (ClassIDDataContainer<Mob> container : MobRegistry.getMobs()) {
            String id = container.getIDData().getStringID();
            Pool pool = MobPools.poolOf(id);
            if (pool != Pool.HOSTILE && pool != Pool.BOSS) continue;
            Mob mob = newMob(id);
            if (mob == null) continue;
            try {
                maxHealth.put(id, mob.getMaxHealth());
            } catch (Throwable ignored) {
            }
            List<Float> values = new ArrayList<>();
            String[] sources = MobPools.fighters(id);
            if (sources == null) {
                collectMobDamage(mob, values);
            } else {
                for (String source : sources) {
                    Mob part = newMob(source);
                    if (part != null) collectMobDamage(part, values);
                }
            }
            if (!values.isEmpty()) {
                Collections.sort(values);
                baseDamage.put(id, values.get(values.size() / 2));
            }
        }
        for (Pool pool : new Pool[]{Pool.HOSTILE, Pool.BOSS}) {
            float min = Float.MAX_VALUE;
            float max = 0.0F;
            for (String id : MobPools.get(pool)) {
                Float d = baseDamage.get(id);
                if (d == null) continue;
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            if (max > 0) poolRange.put(pool, new float[]{min, max});
        }
        // Typical damage per point of health among the measured mobs, used to estimate the rest
        List<Float> ratios = new ArrayList<>();
        for (Map.Entry<String, Float> e : baseDamage.entrySet()) {
            int health = maxHealth.getOrDefault(e.getKey(), 0);
            if (health > 0) ratios.add(e.getValue() / health);
        }
        if (!ratios.isEmpty()) {
            Collections.sort(ratios);
            damagePerHealth = ratios.get(ratios.size() / 2);
        }
    }

    private static Mob newMob(String id) {
        try {
            return MobRegistry.getMob(MobRegistry.getMobID(id));
        } catch (Throwable e) {
            return null;
        }
    }

    /** Every damage value a mob uses: its fields, its AI (built in init) and the static damage fields of its class. */
    private static void collectMobDamage(Mob mob, List<Float> values) {
        try {
            mob.init();
        } catch (Throwable ignored) {
        }
        collect(mob, 0, new IdentityHashMap<>(), values);
        for (Class<?> c = mob.getClass(); c != null && c != Mob.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != GameDamage.class) continue;
                try {
                    f.setAccessible(true);
                    GameDamage d = (GameDamage) f.get(null);
                    if (d != null && d.damage > 0) values.add(d.damage);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Walks the mob and its AI objects looking for GameDamage values. */
    private static void collect(Object o, int depth, IdentityHashMap<Object, Boolean> visited, List<Float> values) {
        if (o == null || depth > 5 || visited.put(o, Boolean.TRUE) != null) return;
        if (o instanceof GameDamage) {
            if (((GameDamage) o).damage > 0) values.add(((GameDamage) o).damage);
            return;
        }
        if (o instanceof Collection) {
            for (Object e : (Collection<?>) o) collect(e, depth + 1, visited, values);
            return;
        }
        Class<?> type = o.getClass();
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) return;
            int length = Array.getLength(o);
            for (int i = 0; i < length; i++) collect(Array.get(o, i), depth + 1, visited, values);
            return;
        }
        // Only the mob itself and its AI: never wander into levels, items or other entities
        if (!type.getName().startsWith("necesse.entity.mobs.") || (depth > 0 && o instanceof Mob)) return;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    collect(f.get(o), depth + 1, visited, values);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ===== Applying =====

    private static final ThreadLocal<int[]> hitDepth = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Called when Mob.isServerHit starts. A hit on a mounted mob is passed on to its rider through isServerHit again
     * with the same damage, so only the outermost call scales it.
     */
    public static GameDamage enterHit(GameDamage damage, Attacker attacker, Mob target) {
        int depth = ++hitDepth.get()[0];
        return depth == 1 ? modify(damage, attacker, target) : damage;
    }

    /** Called when Mob.isServerHit ends (also when it throws). */
    public static void exitHit() {
        int[] depth = hitDepth.get();
        if (depth[0] > 0) depth[0]--;
    }

    /** The damage an attacker deals once enemy/boss damage randomization is applied. */
    public static GameDamage modify(GameDamage damage, Attacker attacker, Mob target) {
        if (factors.isEmpty() || damage == null || attacker == null) return damage;
        Mob owner;
        try {
            owner = attacker.getAttackOwner();
        } catch (Throwable e) {
            return damage;
        }
        if (owner == null || owner == target || owner.isPlayer) return damage;
        Float factor = factors.get(owner.getStringID());
        if (factor == null) return damage;
        return new GameDamage(damage.type, damage.damage * factor, damage.armorPen, damage.baseCritChance,
                damage.playerDamageMultiplier, damage.finalDamageMultiplier);
    }

    public static Float getBaseDamage(String id) {
        return baseDamage.get(id);
    }

    public static float getFactor(String id) {
        Float f = factors.get(id);
        return f == null ? 1.0F : f;
    }

    public static int measuredCount() {
        return baseDamage.size();
    }

}
