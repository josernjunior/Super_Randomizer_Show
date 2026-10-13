package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import necesse.entity.mobs.Mob;
import necesse.level.maps.Level;
import srs.SRSSettings;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 1: swaps which mob appears where.
 * <p>
 * The swap happens inside MobRegistry.getMob(String, Level), so the replacement is configured by the game code
 * exactly as the original would have been. It is only applied when the code asking for the mob is known to be a
 * spawn point that treats the result as a generic Mob/HumanMob (natural spawn tables, village presets, boss
 * summon items, incursion bosses, the queen spider's egg, the ancient totem). Everything else, including loading
 * saved mobs and client side network spawns, is left untouched, which keeps saves stable and avoids
 * ClassCastExceptions in game code that casts the mob. Bosses whose spawn casts them, or that wait in an arena,
 * are swapped later by {@link BossStandIn}.
 */
public final class MobSpawnRandomizer {

    private static final String SPAWN_ITEMS = "necesse.inventory.item.placeableItem.consumableItem.spawnItems.";
    /** Boss spawns that cast the boss to its own class or set it up after getMob: swapped when the boss is added to the level. */
    private static final String[] LATE_BOSS_CALLERS = {
            SPAWN_ITEMS + "PirateCaptainSpawnItem",
            SPAWN_ITEMS + "VoidWizardSpawnItem",
            "necesse.level.gameObject.TemplePedestalObject",
            "necesse.level.maps.levelData.CursedCroneArenasLevelData"};

    private static final Map<String, String> mapping = new HashMap<>();

    /** What the getMob patch does with one call. */
    public static final class Decision {
        /** The mob getMob creates: the replacement, or the original when it's swapped later. */
        public final String spawnID;
        final String to;
        final boolean boss;
        final boolean late;

        Decision(String original, String to, boolean boss, boolean late) {
            this.spawnID = late ? original : to;
            this.to = to;
            this.boss = boss;
            this.late = late;
        }
    }

    private MobSpawnRandomizer() {
    }

    public static void build() {
        mapping.clear();
        SRSSettings s = SRS.settings();
        if (!s.mobSpawns) return;
        List<String> lines = Spoiler.section("1 - Aparecimento de mobs (original -> novo)");
        addPool(Pool.HOSTILE, s.mobSpawnHostiles, lines);
        addPool(Pool.CRITTER, s.mobSpawnCritters, lines);
        addPool(Pool.ANIMAL, s.mobSpawnAnimals, lines);
        addPool(Pool.TOWN, s.mobSpawnTownNPCs, lines);
        // Which bosses swap is decided by bossPool (empty = none)
        addPool(Pool.BOSS, true, lines);
    }

    private static void addPool(Pool pool, boolean enabled, List<String> lines) {
        if (!enabled) return;
        Map<String, String> perm = SeedRandom.permutation("mobspawn:" + pool.name(), MobPools.get(pool));
        mapping.putAll(perm);
        List<String> keys = new ArrayList<>(perm.keySet());
        Collections.sort(keys);
        for (String k : keys) lines.add("[" + pool.name().toLowerCase() + "] " + k + " -> " + perm.get(k));
    }

    public static boolean isActive() {
        return !mapping.isEmpty();
    }

    public static String getMapping(String stringID) {
        return mapping.get(stringID);
    }

    /** True when the boss randomizer swaps at least one boss (spawns on and two or more bosses in its pool). */
    public static boolean bossesSwapped() {
        for (String id : MobPools.get(Pool.BOSS)) {
            if (bossReplacement(id) != null) return true;
        }
        return false;
    }

    /** The boss that takes this boss's place, or null when it stays. */
    public static String bossReplacement(String stringID) {
        if (stringID == null || mapping.isEmpty()) return null;
        String to = mapping.get(stringID);
        return to == null || to.equals(stringID) || MobPools.poolOf(stringID) != Pool.BOSS ? null : to;
    }

    /** Called on enter of the MobRegistry.getMob(String, Level) patch. Null leaves the call alone. */
    public static Decision decide(String stringID, Level level) {
        if (stringID == null || mapping.isEmpty() || level == null || !level.isServer()) return null;
        String to = mapping.get(stringID);
        if (to == null || to.equals(stringID)) return null;
        Pool pool = MobPools.poolOf(stringID);
        if (pool == null) return null;
        String caller = findCaller();
        if (pool == Pool.BOSS && isLateBossCaller(caller)) return new Decision(stringID, to, true, true);
        if (!allowedPools(caller).contains(pool)) return null;
        return new Decision(stringID, to, pool == Pool.BOSS, false);
    }

    /** Called on exit of the MobRegistry.getMob(String, Level) patch with the mob it created. */
    public static void created(Decision decision, Mob mob) {
        if (decision.late) BossStandIn.markPending(mob, decision.to);
        else if (decision.boss) BossStandIn.markReplacement(mob);
    }

    private static String findCaller() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        for (StackTraceElement e : trace) {
            String c = e.getClassName();
            if (c.startsWith("srs.") || c.equals("necesse.engine.registries.MobRegistry")) continue;
            return c;
        }
        return "";
    }

    private static EnumSet<Pool> allowedPools(String caller) {
        // Natural spawning (all biome/incursion spawn tables) only ever handles the result as a Mob
        if (caller.startsWith("necesse.level.maps.biomes.MobSpawnTable")) return EnumSet.of(Pool.HOSTILE, Pool.CRITTER, Pool.ANIMAL);
        // World generation: animal herds, creatures placed on the map, spider nests, crypts... (all handled as Mob)
        if (caller.startsWith("necesse.engine.world.biomeGenerator.GeneratorPlaceFactory")
                || caller.startsWith("necesse.level.maps.generationModules.GenerationTools")
                || caller.startsWith("necesse.level.maps.generationModules.CellAutomaton")
                || caller.startsWith("necesse.level.maps.generationModules.ModularGeneration")) {
            return EnumSet.of(Pool.HOSTILE, Pool.CRITTER, Pool.ANIMAL);
        }
        // Presets that cast the result to HumanMob or check instanceof
        if (caller.startsWith("necesse.level.maps.presets.modularPresets.vilagePresets.VillagePreset")
                || caller.equals("necesse.level.maps.presets.Preset")
                || caller.startsWith("necesse.level.maps.presets.ElderHousePreset")
                || caller.startsWith("necesse.level.maps.presets.DesertTavernPreset")
                || caller.startsWith("necesse.level.maps.presets.CaveHoboHomePreset")) {
            return EnumSet.of(Pool.TOWN);
        }
        // Bosses summoned by items, by incursions (also their second boss), by breaking the queen spider's egg or the ancient totem
        if (caller.startsWith(SPAWN_ITEMS)
                || caller.equals("necesse.entity.levelEvent.IncursionLevelEvent")
                || caller.startsWith("necesse.entity.levelEvent.incursionModifiers.DoubleBossChanceLevelEvent")
                || caller.equals("necesse.level.gameObject.RoyalEggObject")
                || caller.equals("necesse.level.gameObject.AncientTotemObject")) {
            return EnumSet.of(Pool.BOSS);
        }
        return EnumSet.noneOf(Pool.class);
    }

    private static boolean isLateBossCaller(String caller) {
        for (String c : LATE_BOSS_CALLERS) {
            if (caller.equals(c) || caller.startsWith(c + "$")) return true;
        }
        return false;
    }

}
