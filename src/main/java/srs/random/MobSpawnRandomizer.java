package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * summon items, incursion bosses). Everything else, including loading saved mobs and client side network spawns,
 * is left untouched, which keeps saves stable and avoids ClassCastExceptions in game code that casts the mob.
 */
public final class MobSpawnRandomizer {

    private static final Map<String, String> mapping = new HashMap<>();

    private MobSpawnRandomizer() {
    }

    public static void build() {
        mapping.clear();
        SRSSettings s = SRS.settings();
        if (!s.mobSpawns) return;
        List<String> lines = Spoiler.section("1 - Aparecimento de mobs (original -> novo)");
        addPool(Pool.HOSTILE, s.mobSpawnHostiles, lines);
        addPool(Pool.CRITTER, s.mobSpawnCritters, lines);
        addPool(Pool.TOWN, s.mobSpawnTownNPCs, lines);
        addPool(Pool.BOSS, s.mobSpawnBosses, lines);
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

    /** Called from the MobRegistry.getMob(String, Level) patch. */
    public static String remap(String stringID, Level level) {
        if (stringID == null || mapping.isEmpty() || level == null || !level.isServer()) return stringID;
        String to = mapping.get(stringID);
        if (to == null || to.equals(stringID)) return stringID;
        Pool pool = MobPools.poolOf(stringID);
        if (pool == null || !allowedPools(findCaller()).contains(pool)) return stringID;
        return to;
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
        if (caller.startsWith("necesse.level.maps.biomes.MobSpawnTable")) return EnumSet.of(Pool.HOSTILE, Pool.CRITTER);
        // Presets that cast the result to HumanMob or check instanceof
        if (caller.startsWith("necesse.level.maps.presets.modularPresets.vilagePresets.VillagePreset")
                || caller.equals("necesse.level.maps.presets.Preset")
                || caller.startsWith("necesse.level.maps.presets.ElderHousePreset")
                || caller.startsWith("necesse.level.maps.presets.DesertTavernPreset")
                || caller.startsWith("necesse.level.maps.presets.CaveHoboHomePreset")) {
            return EnumSet.of(Pool.TOWN);
        }
        // Boss summon items (the pirate captain item casts to PirateCaptainMob, so it's left alone)
        if (caller.startsWith("necesse.inventory.item.placeableItem.consumableItem.spawnItems.")
                && !caller.contains("PirateCaptainSpawnItem")) {
            return EnumSet.of(Pool.BOSS);
        }
        if (caller.equals("necesse.entity.levelEvent.IncursionLevelEvent")) return EnumSet.of(Pool.BOSS);
        return EnumSet.noneOf(Pool.class);
    }

}
