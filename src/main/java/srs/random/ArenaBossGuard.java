package srs.random;

import java.awt.Point;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.MobSave;
import necesse.engine.util.GameMath;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.hostile.bosses.ChieftainMob;
import necesse.entity.objectEntity.ChieftainsPedestalObjectEntity;
import necesse.entity.objectEntity.ObjectEntity;
import srs.core.SRS;

/**
 * Feature 1: keeps a boss that waited in its arena while the boss that replaced it fights. It's a server-only level
 * event saved with the arena's region, so it outlives the region unloading and server restarts.
 * <p>
 * When the replacement is gone without dying, the original is loaded back from the save it had when it woke up and
 * waits in its arena again, as it does in the game when the players flee. Gone means it was removed while its region
 * was loaded (it lost its targets and despawned), or it isn't a mob the game saves and its region unloaded or the
 * server restarted. A replacement that is saved and unloaded with its region is waited for. When the replacement dies,
 * the guard just ends: a defeated arena boss doesn't come back, as in the game.
 */
public class ArenaBossGuard extends LevelEvent {

    public static final String ID = "srsarenabossguard";
    private static final String ORIGINAL = "ORIGINAL";
    private static final int CHECK_TICKS = 20;
    /** Another boss of the original's type this close already took its place (the Fallen Wizard's respawn, a chieftain the altar created). */
    private static final float SAME_BOSS_RANGE = 40 * 32;

    private LoadData original;
    private String originalID;
    private float x;
    private float y;
    private int replacementUniqueID;
    private boolean replacementSaves;
    /** Null until it's found in the level again after a load or an unload. */
    private Mob replacement;
    private int ticker;

    /** Used by the registry and when loading. */
    public ArenaBossGuard() {
        this.shouldSave = true;
    }

    /** Must be created before the original is removed, and after the replacement is added (it needs its unique ID). */
    ArenaBossGuard(Mob original, Mob replacement) {
        this();
        this.original = MobSave.getSave(ORIGINAL, original).toLoadData();
        this.originalID = original.getStringID();
        this.x = original.x;
        this.y = original.y;
        this.replacementUniqueID = replacement.getUniqueID();
        this.replacementSaves = replacement.shouldSave();
        this.replacement = replacement;
    }

    public String getOriginalID() {
        return originalID;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        if (originalID != null) save.addUnsafeString("originalID", originalID);
        save.addFloat("x", x);
        save.addFloat("y", y);
        save.addInt("replacementUniqueID", replacementUniqueID);
        save.addBoolean("replacementSaves", replacementSaves);
        if (original != null) save.addSaveData(original.toSaveData());
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        originalID = save.getUnsafeString("originalID", null);
        x = save.getFloat("x", 0.0F);
        y = save.getFloat("y", 0.0F);
        replacementUniqueID = save.getInt("replacementUniqueID", 0);
        replacementSaves = save.getBoolean("replacementSaves", false);
        original = save.getFirstLoadDataByName(ORIGINAL);
    }

    @Override
    public Point getSaveToRegionPos() {
        return new Point(level.regionManager.getRegionCoordByTile(GameMath.getTileCoordinate(x)),
                level.regionManager.getRegionCoordByTile(GameMath.getTileCoordinate(y)));
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (++ticker < CHECK_TICKS) return;
        ticker = 0;
        check();
    }

    /** Ends the guard when the replacement died, puts the original back when the replacement is gone for good. */
    public void check() {
        if (isOver()) return;
        if (original == null || BossStandIn.wasDefeated(replacementUniqueID)) {
            over();
            return;
        }
        if (replacement == null) {
            Mob found = level.entityManager.mobs.get(replacementUniqueID, false);
            if (found != null && !found.removed()) {
                replacement = found;
            } else if (!replacementSaves) {
                restore();
            }
            return;
        }
        if (!replacement.removed()) return;
        if (replacement.hasDied()) {
            over();
        } else if (replacementSaves && !level.regionManager.isTileLoaded(replacement.getTileX(), replacement.getTileY())) {
            replacement = null;
        } else {
            restore();
        }
    }

    /** The original as it was saved, not added to the level. */
    public Mob loadOriginal() {
        return original == null ? null : MobSave.loadSave(original, level);
    }

    private void restore() {
        over();
        boolean taken = level.entityManager.mobs.stream().anyMatch(m -> !m.removed() && m.getStringID().equals(originalID)
                && GameMath.diagonalMoveDistance(m.x, m.y, x, y) < SAME_BOSS_RANGE);
        if (taken) return;
        Mob mob = loadOriginal();
        if (mob == null) return;
        try {
            level.entityManager.mobs.add(mob);
            mob.onLoadingComplete();
            if (mob instanceof ChieftainMob) pointPedestalAt((ChieftainMob) mob);
        } catch (Throwable e) {
            SRS.warn("Could not bring " + originalID + " back to its arena: " + e);
        }
    }

    /** The altar starts its gauntlet with the chieftain it remembers, or creates a second one. */
    private void pointPedestalAt(ChieftainMob chieftain) {
        if (chieftain.spawnPos == null) return;
        for (ObjectEntity entity : level.entityManager.objectEntities) {
            if (entity instanceof ChieftainsPedestalObjectEntity && chieftain.spawnPos.equals(((ChieftainsPedestalObjectEntity) entity).chieftainSpawnPos)) {
                ((ChieftainsPedestalObjectEntity) entity).chieftainMobID = chieftain.getUniqueID();
            }
        }
    }

}
