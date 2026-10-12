package srs.random;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.engine.registries.GameRegistry;
import necesse.engine.registries.ProjectileRegistry;
import necesse.entity.Entity;
import necesse.entity.projectile.Projectile;
import necesse.level.maps.Level;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 14: whenever a projectile is spawned (by a weapon, an enemy or a boss) it is replaced by another projectile
 * type that flies from the same place, to the same target, with the same speed, range, damage and owner.
 * <p>
 * Projectiles that can't work when created generically (boomerangs, summoning projectiles, bombs, projectiles that
 * need a target or extra data) are left out. As a safety net, a replacement that throws while being added or ticked
 * is removed and its type is never used as a replacement again.
 * <p>
 * Optionally every projectile type also flies at a fixed speed multiplier from the seed.
 */
public final class ProjectileRandomizer {

    /** "Test": the developers' debug projectiles (mousetest draws debug numbers and "null", pathtest is invisible). */
    private static final String[] UNSAFE_NAME_PARTS = {"Spawn", "Summon", "Bomb", "Boomerang", "Reanimation", "SpiritOrb",
            "SpiritLeaf", "DryadBow", "Trap", "Hook", "Fishing", "Following", "Pet", "Golem", "Ascended", "Mount", "Bobber", "Test"};

    /** Found with /srs test: these break when created without the data their weapon/mob normally gives them. */
    private static final Set<String> KNOWN_BAD = new HashSet<>(Arrays.asList(
            "voidwizardhoming", "soundwave", "waterbolt", "trenchcoatgoblinspawn", "whipprojectile", "hexedbladegreatswordwave"));

    private static final Map<String, String> mapping = new HashMap<>();
    private static final Map<String, Float> speedFactors = new HashMap<>();
    private static final List<String> pool = new ArrayList<>();
    private static final Set<String> badTargets = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> reportedErrors = new HashSet<>();

    private ProjectileRandomizer() {
    }

    public static void build() {
        mapping.clear();
        pool.clear();
        speedFactors.clear();
        buildSpeeds();
        if (!SRS.settings().projectiles) return;
        int count = registrySize();
        for (int id = 0; id < count; id++) {
            String stringID = ProjectileRegistry.getProjectileStringID(id);
            if (stringID == null || KNOWN_BAD.contains(stringID)) continue;
            try {
                Projectile p = ProjectileRegistry.getProjectile(id);
                if (p == null || isUnsafe(p)) continue;
                pool.add(stringID);
            } catch (Throwable ignored) {
            }
        }
        Map<String, String> perm = SeedRandom.permutation("projectiles", pool);
        List<String> lines = Spoiler.section("14 - Projeteis (original -> novo)");
        List<String> keys = new ArrayList<>(perm.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            String to = perm.get(k);
            if (!to.equals(k)) {
                mapping.put(k, to);
                lines.add(k + " -> " + to);
            }
        }
    }

    /** Every projectile type gets a fixed speed multiplier, whether or not projectile swapping is on. */
    private static void buildSpeeds() {
        SRSSettings s = SRS.settings();
        if (!s.projectileSpeed || (s.projectileSpeedMin == 1.0F && s.projectileSpeedMax == 1.0F)) return;
        List<String> types = new ArrayList<>();
        int count = registrySize();
        for (int id = 0; id < count; id++) {
            String stringID = ProjectileRegistry.getProjectileStringID(id);
            if (stringID != null) types.add(stringID);
        }
        Collections.sort(types);
        List<String> lines = Spoiler.section("14 - Projeteis: velocidade (tipo: multiplicador)");
        for (String type : types) {
            float factor = Math.max(0.05F, SeedRandom.range("projectiles:speed:" + type, s.projectileSpeedMin, s.projectileSpeedMax));
            speedFactors.put(type, factor);
            lines.add(type + ": x" + String.format(java.util.Locale.ROOT, "%.2f", factor));
        }
    }

    public static float getSpeedFactor(String type) {
        Float factor = type == null ? null : speedFactors.get(type);
        return factor == null ? 1.0F : factor;
    }

    public static int registrySize() {
        try {
            Method size = GameRegistry.class.getDeclaredMethod("size");
            size.setAccessible(true);
            return (Integer) size.invoke(ProjectileRegistry.instance);
        } catch (Exception e) {
            SRS.warn("Could not read projectile registry size: " + e);
            return 0;
        }
    }

    private static boolean isUnsafe(Projectile p) {
        String name = p.getClass().getSimpleName();
        for (String part : UNSAFE_NAME_PARTS) {
            if (name.contains(part)) return true;
        }
        try {
            Boolean boomerang = Reflect.get(Projectile.class, p, "isBoomerang");
            if (boomerang != null && boomerang) return true;
        } catch (Exception ignored) {
        }
        return hasMissingData(p);
    }

    /**
     * A swap creates the replacement with its empty constructor and only fills the common flight data. Projectiles
     * whose own fields stay empty that way (their own owner or damage, items, arrays...) only work when created by
     * their weapon or mob, e.g. bone spikes would create spike mobs without an owner and crash the server.
     * Sounds, trails, textures and the homing target are filled in later or handled by the game, so they are fine.
     */
    private static boolean hasMissingData(Projectile p) {
        for (Class<?> c = p.getClass(); c != null && c != Projectile.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(p) != null) continue;
                } catch (Exception e) {
                    return true;
                }
                if (!isFilledLater(f)) return true;
            }
        }
        return false;
    }

    private static boolean isFilledLater(Field f) {
        String type = f.getType().getSimpleName();
        if (type.equals("SoundPlayer") || type.equals("Trail") || type.equals("GameTextureSection") || type.equals("Chain")) return true;
        // Homing projectiles look for a target themselves
        return (f.getName().equals("target") && type.equals("Entity")) || (f.getName().equals("targetPos") && type.equals("Point"));
    }

    public static boolean isActive() {
        return !mapping.isEmpty() || !speedFactors.isEmpty();
    }

    public static String getMapping(String stringID) {
        return mapping.get(stringID);
    }

    /** Projectile types that can be used as replacements. */
    public static List<String> getPool() {
        return Collections.unmodifiableList(pool);
    }

    /** Creates a projectile of the given type the same way a swap does, copying the flight data of the original. */
    public static Projectile createLike(String type, Projectile original, Level level) {
        Projectile replacement = ProjectileRegistry.getProjectile(ProjectileRegistry.getProjectileID(type), level);
        if (replacement == null) return null;
        replacement.applyData(original.x, original.y, original.targetX, original.targetY, original.speed, original.distance,
                original.getDamage(), original.knockback, original.getOwner());
        replacement.setUniqueID(original.getUniqueID());
        return replacement;
    }

    /**
     * Called from the EntityList.add patch for every entity added to a level: swaps the projectile type, then applies
     * the speed multiplier of the type that will actually fly. Network spawns (addHidden) already carry the server's speed.
     */
    public static Entity swap(Entity entity, Level level) {
        if (!(entity instanceof Projectile) || level == null) return entity;
        Projectile original = (Projectile) entity;
        String from;
        try {
            from = original.getStringID();
        } catch (Exception e) {
            return entity;
        }
        Projectile result = original;
        String type = from;
        String to = from == null ? null : mapping.get(from);
        if (to != null && !badTargets.contains(to)) {
            try {
                Projectile replacement = createLike(to, original, level);
                if (replacement != null) {
                    result = replacement;
                    type = to;
                }
            } catch (Throwable e) {
                markBad(to, "create", e);
            }
        }
        float speed = getSpeedFactor(type);
        if (speed != 1.0F) result.speed *= speed;
        return result;
    }

    /**
     * Called when EntityList.add threw after a swap. Removes the replacement, stops using its type and returns
     * true so the exception is swallowed (the original caller never knew about the replacement).
     */
    public static boolean onSwapFailed(Entity replacement, Throwable error) {
        if (!(replacement instanceof Projectile)) return false;
        String type = null;
        try {
            type = ((Projectile) replacement).getStringID();
            replacement.remove();
        } catch (Throwable ignored) {
        }
        markBad(type, "add", error);
        return true;
    }

    /** Called by {@link srs.core.SafeTick} when a projectile threw while ticking: its type stops being used as a replacement. */
    public static void onTickError(Projectile projectile, Throwable e) {
        String type = null;
        try {
            type = projectile.getStringID();
        } catch (Throwable ignored) {
        }
        markBad(type, "tick", e);
    }

    private static void markBad(String type, String what, Throwable e) {
        if (type != null) badTargets.add(type);
        synchronized (reportedErrors) {
            if (reportedErrors.add(what + type + e.getClass().getName())) {
                SRS.warn("Projectile randomizer: " + type + " failed on " + what + " (" + e + "); it won't be used as a replacement anymore");
            }
        }
    }

}
