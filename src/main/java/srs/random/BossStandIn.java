package srs.random;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.packet.PacketChatMessage;
import necesse.engine.registries.BuffRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.entity.Entity;
import necesse.entity.levelEvent.FallenWizardRespawnEvent;
import necesse.entity.levelEvent.TempleEntranceEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ability.BooleanMobAbility;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.hostile.bosses.ChieftainMob;
import necesse.entity.mobs.hostile.bosses.EvilsProtectorMob;
import necesse.entity.mobs.hostile.bosses.FallenWizardMob;
import necesse.entity.mobs.hostile.bosses.SageAndGritStartMob;
import necesse.entity.mobs.hostile.bosses.TheCursedCroneMob;
import necesse.entity.mobs.hostile.bosses.VoidWizard;
import necesse.entity.mobs.hostile.pirates.PirateCaptainMob;
import necesse.level.maps.IncursionLevel;
import necesse.level.maps.Level;
import necesse.level.maps.biomes.dungeon.DungeonArenaLevel;
import necesse.level.maps.biomes.temple.TempleArenaLevel;
import srs.core.MobPools;
import srs.core.SRS;

/**
 * Feature 1, bosses the game doesn't spawn through a getMob it treats as a plain Mob: the boss is created as the
 * game asks and swapped for its mapped boss when its fight starts.
 * <ul>
 * <li>Summoned and then cast to their own class (Void Caller, pirate captain's map, Sage and Grit's temple pedestal,
 * the Cursed Crone's spirit urn): swapped on enter of EntityList.add ({@link #onAdd}). The original is never added,
 * so the summon messages sent for it are dropped and an incursion is told about the replacement instead.</li>
 * <li>Waiting in their arena (Void Wizard, Fallen Wizard, the Cursed Crone, the Runebound Chieftain once its
 * gauntlet is over, the pirate village's captain): swapped when they wake up ({@link #awaken}), before their own
 * wake up runs, so it never announces the original.</li>
 * </ul>
 * What the original's death does to the world is redone when its replacement dies: Sage and Grit open the temple
 * entrance and the pirate captain leaves its pirate behind. The Fallen Wizard's respawn is scheduled when it's swapped,
 * as it is when it dies. If the replacement of an arena boss is gone without dying, an {@link ArenaBossGuard} puts the
 * original back in its arena.
 * <p>
 * Bosses created by the randomizer are never swapped again. Those that would otherwise wait to be hit, or stay after
 * the fight, get the setup their own summoning code gives them ({@link #wake}).
 */
public final class BossStandIn {

    /** 10 minutes, the Fallen Wizard's own respawn time. */
    private static final long FALLEN_WIZARD_RESPAWN = 600000L;

    private static final Set<Mob> replacements = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Mob, Boolean>()));
    /** Summoned bosses waiting for their EntityList.add, with the boss that takes their place. */
    private static final Map<Mob, String> pending = Collections.synchronizedMap(new WeakHashMap<Mob, String>());
    /** Summoned bosses that were swapped before being added, with their replacement. Weak values, or the two maps would keep each other alive. */
    private static final Map<Mob, WeakReference<Mob>> neverAdded = Collections.synchronizedMap(new WeakHashMap<Mob, WeakReference<Mob>>());
    /** Replacement -> the original it stands in for, read when the replacement dies. */
    private static final Map<Mob, Mob> originals = Collections.synchronizedMap(new WeakHashMap<Mob, Mob>());
    /** Arena bosses that woke up while their replacement would flee ({@link #fleesNow}): they fight as themselves until they wait again. */
    private static final Set<Mob> ownFight = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Mob, Boolean>()));
    /** Unique IDs of replacements that died. */
    private static final Set<Integer> defeated = Collections.synchronizedSet(new HashSet<Integer>());

    private BossStandIn() {
    }

    /** MobRegistry.getMob exit, for a boss created in place of another: it's never swapped again and starts its fight right away. */
    public static void markReplacement(Mob mob) {
        if (mob == null) return;
        replacements.add(mob);
        wake(mob);
    }

    /** MobRegistry.getMob exit, for a boss its caller casts to its own class: it's swapped when added to the level. */
    public static void markPending(Mob mob, String replacementID) {
        if (mob != null) pending.put(mob, replacementID);
    }

    /** EntityList.add enter: the replacement to add instead of a pending summoned boss, or the entity itself. */
    public static Entity onAdd(Entity entity) {
        if (!(entity instanceof Mob) || pending.isEmpty()) return entity;
        Mob original = (Mob) entity;
        String to = pending.remove(original);
        if (to == null) return entity;
        Mob replacement;
        try {
            replacement = create(to, original);
        } catch (Throwable e) {
            SRS.warn("Could not create " + to + " in place of " + original.getStringID() + ": " + e);
            return entity;
        }
        if (replacement == null) return entity;
        neverAdded.put(original, new WeakReference<>(replacement));
        announce(replacement, "bosssummon");
        return replacement;
    }

    /**
     * A boss waiting in its arena wakes up: it's replaced by its mapped boss, which starts the fight in its place.
     * True when swapped, so the caller skips the original's own wake up.
     */
    public static boolean awaken(Mob original) {
        if (original == null || original.removed() || !original.isServer()) return false;
        String to = MobSpawnRandomizer.bossReplacement(original.getStringID());
        if (to == null || replacements.contains(original) || ownFight.contains(original) || !isWaiting(original)) return false;
        if (fleesNow(to, original.getLevel())) {
            ownFight.add(original);
            return false;
        }
        return swapNow(original, to, true) != null;
    }

    /** An arena boss went back to waiting (players fled): its next wake up may swap it again. */
    public static void backToWaiting(Mob mob) {
        if (!ownFight.isEmpty()) ownFight.remove(mob);
    }

    /**
     * Evil's Protector flees as soon as it's day. By day an arena boss keeps its own fight: swapped, its replacement
     * would flee and the {@link ArenaBossGuard} bring the original back, over and over.
     */
    public static boolean fleesNow(String id, Level level) {
        Class<?> mobClass = MobPools.mobClass(id);
        return mobClass != null && EvilsProtectorMob.class.isAssignableFrom(mobClass) && level != null && !level.getWorldEntity().isNight();
    }

    /** Replaces a boss that is in the level by a new boss of the given type. Returns the replacement, or null if it couldn't be added. */
    public static Mob swapNow(Mob original, String to) {
        return swapNow(original, to, false);
    }

    /** With guard, an {@link ArenaBossGuard} brings the original back if the replacement is gone without dying. */
    private static Mob swapNow(Mob original, String to, boolean guard) {
        Level level = original.getLevel();
        Mob replacement;
        try {
            replacement = create(to, original);
            if (replacement == null) return null;
            level.entityManager.mobs.add(replacement);
        } catch (Throwable e) {
            SRS.warn("Could not swap " + original.getStringID() + " for " + to + ": " + e);
            return null;
        }
        if (guard) {
            try {
                // Server only: add would send it to the clients, which never hear that it ended
                level.entityManager.events.addHidden(new ArenaBossGuard(original, replacement));
            } catch (Throwable e) {
                SRS.warn("Could not keep " + original.getStringID() + " for its arena: " + e);
            }
        }
        if (original instanceof FallenWizardMob) scheduleFallenWizard(level);
        original.remove();
        // The Void Wizard announces itself when it wakes up
        if (!(replacement instanceof VoidWizard)) announce(replacement, "bossawoke");
        return replacement;
    }

    /**
     * BooleanMobAbility.runAndSend enter: the Cursed Crone waking up or the pirate captain starting a fight. True when
     * swapped. Turning off (an arena boss going back to waiting) clears its own fight.
     */
    public static boolean onBooleanAbility(BooleanMobAbility ability, boolean value) {
        Mob mob = ability.getMob();
        if (!value) {
            backToWaiting(mob);
            return false;
        }
        if (mob instanceof TheCursedCroneMob) return ability == ((TheCursedCroneMob) mob).changeHostileAbility && !mob.isHostile && awaken(mob);
        if (mob instanceof PirateCaptainMob) return ability == ((PirateCaptainMob) mob).setInCombatAbility && awaken(mob);
        return false;
    }

    /** IncursionLevelEvent.onBossSummoned enter: the boss the incursion must track (the replacement of a swapped summoned boss). */
    public static Mob current(Mob mob) {
        if (mob == null || neverAdded.isEmpty()) return mob;
        WeakReference<Mob> ref = neverAdded.get(mob);
        Mob replacement = ref == null ? null : ref.get();
        return replacement == null ? mob : replacement;
    }

    /** ServerNetwork.sendToClientsWithEntity enter: true to drop a packet sent for a summoned boss that was never added (its summon message names the original). */
    public static boolean isNeverAdded(Object getter) {
        return getter instanceof Mob && !neverAdded.isEmpty() && neverAdded.containsKey(getter);
    }

    /** True when the replacement with this unique ID died this session, read by an {@link ArenaBossGuard} that was unloaded meanwhile. */
    public static boolean wasDefeated(int uniqueID) {
        return defeated.contains(uniqueID);
    }

    /** Mob.onDeath exit: redoes what the original's death does to the world. */
    public static void onDeath(Mob mob) {
        if (originals.isEmpty()) return;
        if (originals.containsKey(mob)) defeated.add(mob.getUniqueID());
        Mob original = originals.remove(mob);
        if (original == null || !mob.isServer()) return;
        try {
            if (original instanceof SageAndGritStartMob) openTemple(mob, ((SageAndGritStartMob) original).pedestalPosition);
            else if (original instanceof PirateCaptainMob) freePirate(mob, ((PirateCaptainMob) original).getSettlerSeed());
        } catch (Throwable e) {
            SRS.warn("Could not apply the death of " + original.getStringID() + " to " + mob.getStringID() + ": " + e);
        }
    }

    /** A boss the world placed to wait for the players, as opposed to one summoned into a fight. */
    private static boolean isWaiting(Mob mob) {
        if (mob instanceof ChieftainMob) return ((ChieftainMob) mob).spawnPos != null;
        if (mob instanceof VoidWizard) return mob.getLevel() instanceof DungeonArenaLevel;
        if (mob instanceof FallenWizardMob) return ((FallenWizardMob) mob).isInArena();
        // Only a crone that belongs to an arena is saved
        if (mob instanceof TheCursedCroneMob) return mob.shouldSave();
        if (mob instanceof PirateCaptainMob) return ((PirateCaptainMob) mob).baseTile != null;
        return false;
    }

    /** A new boss of the given type where the original is. */
    private static Mob create(String id, Mob original) {
        Level level = original.getLevel();
        // The numeric overload isn't patched, so the replacement is never remapped
        Mob mob = level == null ? null : MobRegistry.getMob(MobRegistry.getMobID(id), level);
        if (mob == null) return null;
        mob.setPos(original.x, original.y, true);
        mob.isSecondaryIncursionBoss = original.isSecondaryIncursionBoss;
        replacements.add(mob);
        originals.put(mob, original);
        wake(mob);
        return mob;
    }

    /** What each boss's own summoning code does, so it fights right away instead of waiting to be hit, and leaves when the fight is lost. */
    private static void wake(Mob mob) {
        if (mob instanceof VoidWizard) {
            ((VoidWizard) mob).makeItemSpawned();
        } else if (mob instanceof PirateCaptainMob) {
            ((PirateCaptainMob) mob).markItemSummoned();
        } else if (mob instanceof FallenWizardMob) {
            mob.isHostile = true;
        } else if (mob instanceof TheCursedCroneMob) {
            mob.isHostile = true;
            ((TheCursedCroneMob) mob).startAnimation(TheCursedCroneMob.AnimationState.DEFAULT_FLYING, 500, true);
        }
    }

    private static void announce(Mob mob, String key) {
        Level level = mob.getLevel();
        if (level == null || level.getServer() == null) return;
        level.getServer().network.sendToClientsWithEntity(new PacketChatMessage(new LocalMessage("misc", key, "name", mob.getLocalization())), mob);
    }

    /** The game only schedules it when the Fallen Wizard dies, and skips its own if one is already waiting. */
    private static void scheduleFallenWizard(Level level) {
        if (!(level instanceof TempleArenaLevel) || level.entityManager.events.stream().anyMatch(e -> e instanceof FallenWizardRespawnEvent)) return;
        Point2D.Float pos = TempleArenaLevel.getBossPosition();
        level.entityManager.events.add(new FallenWizardRespawnEvent(pos.x, pos.y, level.getWorldEntity().getWorldTime() + FALLEN_WIZARD_RESPAWN));
    }

    /** Sage and Grit summoned at the temple pedestal open the temple entrance when they die. */
    private static void openTemple(Mob mob, Point pedestal) {
        Level level = mob.getLevel();
        if (pedestal == null || level == null || level instanceof IncursionLevel) return;
        Point entrance = new Point(pedestal.x, pedestal.y + 2);
        if (level.getLevelObject(entrance.x, entrance.y).getMultiTile().getMasterObject().getStringID().equals("templeentrance")) return;
        level.entityManager.events.add(new TempleEntranceEvent(entrance.x, entrance.y));
    }

    /** The pirate captain leaves its pirate behind, trapped, to be recruited. */
    private static void freePirate(Mob mob, int settlerSeed) {
        Level level = mob.getLevel();
        Mob pirate = level == null ? null : MobRegistry.getMob(MobRegistry.getMobID("piratehuman"), level);
        if (!(pirate instanceof HumanMob)) return;
        HumanMob human = (HumanMob) pirate;
        human.buffManager.addBuff(new ActiveBuff(BuffRegistry.INVULNERABLE_ACTIVE, human, 2.0F, null), false);
        human.setSettlerSeed(settlerSeed, true);
        human.setTrapped();
        level.entityManager.addMob(human, mob.x, mob.y);
    }

}
