package srs.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.engine.journal.JournalEntry;
import necesse.engine.registries.ClassIDDataContainer;
import necesse.engine.registries.JournalRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.FriendlyRopableMob;
import necesse.entity.mobs.friendly.human.HumanMob;

/**
 * Sorts every registered mob into the groups the randomizers swap between. A mob is only ever replaced by
 * another mob of the same group, so a cow never becomes a boss and a village elder never becomes a zombie.
 */
public final class MobPools {

    public enum Pool {
        HOSTILE, CRITTER, ANIMAL, BOSS, TOWN
    }

    /**
     * Boss "starter" mobs only spawn the real fighters, which are the ones that hit and get hit:
     * their damage and health randomization also has to reach those fighters.
     */
    private static final Map<String, String[]> STARTER_FIGHTERS = new HashMap<>();

    static {
        STARTER_FIGHTERS.put("sageandgrit", new String[]{"sage", "grit"});
        STARTER_FIGHTERS.put("nightswarm", new String[]{"nightswarmbat"});
    }

    private static final Map<Pool, List<String>> pools = new HashMap<>();
    private static final Map<String, Pool> poolOf = new HashMap<>();
    private static final Map<String, Class<?>> mobClasses = new HashMap<>();
    private static boolean built;

    private MobPools() {
    }

    public static synchronized void build() {
        if (built) return;
        built = true;
        for (Pool p : Pool.values()) pools.put(p, new ArrayList<>());

        Set<String> bossPool = SRS.parseList(SRS.settings().bossPool);
        Set<String> townPool = SRS.parseList(SRS.settings().townNPCPool);

        // Hostile mobs listed in the adventure journal are the ones that actually appear in the world.
        // Using them avoids pulling in raiders, spawner portals, boss minions and other special mobs.
        Set<String> journalHostiles = new HashSet<>();
        try {
            for (JournalEntry entry : JournalRegistry.getJournalEntries()) {
                for (JournalEntry.MobJournalData data : entry.mobsData) {
                    if (data.mob != null && data.mob.isHostile && !data.mob.isBoss()) {
                        journalHostiles.add(data.mob.getStringID());
                    }
                }
            }
        } catch (Exception e) {
            SRS.warn("Could not read journal mobs: " + e);
        }

        for (ClassIDDataContainer<Mob> container : MobRegistry.getMobs()) {
            String id = container.getIDData().getStringID();
            if (SRS.isMobExcluded(id)) continue;
            Mob mob;
            try {
                mob = container.newInstance();
            } catch (Throwable e) {
                continue;
            }
            if (mob == null) continue;
            mobClasses.put(id, mob.getClass());
            int numericID = MobRegistry.getMobID(id);
            Pool pool = null;
            if (bossPool.contains(id) && MobRegistry.isBossMob(numericID)) {
                pool = Pool.BOSS;
            } else if (townPool.contains(id) && mob instanceof HumanMob) {
                pool = Pool.TOWN;
            } else if (mob.isCritter && !mob.isHostile && !MobRegistry.isBossMob(numericID)) {
                pool = Pool.CRITTER;
            } else if (mob instanceof FriendlyRopableMob && !mob.isHostile && !(mob instanceof HumanMob) && !MobRegistry.isBossMob(numericID)) {
                // Farm and wild animals (sheep, cows, pigs, chickens, ostriches, penguins...), placed in herds at world generation
                pool = Pool.ANIMAL;
            } else if (mob.isHostile && !MobRegistry.isBossMob(numericID) && !(mob instanceof HumanMob) && !mob.isSummoned
                    && MobRegistry.countMobKillStat(numericID) && (journalHostiles.isEmpty() || journalHostiles.contains(id))) {
                pool = Pool.HOSTILE;
            }
            if (pool != null) {
                pools.get(pool).add(id);
                poolOf.put(id, pool);
            }
        }
        for (List<String> list : pools.values()) Collections.sort(list);
        SRS.log("Mob pools: hostile=" + pools.get(Pool.HOSTILE).size() + " critter=" + pools.get(Pool.CRITTER).size()
                + " animal=" + pools.get(Pool.ANIMAL).size() + " boss=" + pools.get(Pool.BOSS).size() + " town=" + pools.get(Pool.TOWN).size());
    }

    public static List<String> get(Pool pool) {
        build();
        return pools.get(pool);
    }

    public static Pool poolOf(String stringID) {
        build();
        return poolOf.get(stringID);
    }

    /** The mobs a starter boss spawns to fight, or null if the boss fights by itself. */
    public static String[] fighters(String bossID) {
        return STARTER_FIGHTERS.get(bossID);
    }

    public static Class<?> mobClass(String stringID) {
        build();
        return mobClasses.get(stringID);
    }

}
