package srs.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import necesse.entity.Entity;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.levelEvent.mobAbilityLevelEvent.MobAbilityLevelEvent;
import necesse.entity.manager.EntityManager;
import necesse.entity.manager.LevelEventsManager;
import necesse.entity.mobs.Mob;

/**
 * Fixes a spawn order race in the game: EntityList.add runs the mob's init() before sending the mob to the clients,
 * so an ability event a mob starts in init() (the furnace golem's heat aura) reaches the clients before its owner.
 * They log "Could not find owner for level event" and ask for the event again. Common furnace golems made this
 * happen all the time in caves. On the server, such events are now added right after the mob has been sent.
 */
public final class SpawnOrder {

    /** Server-side mobs whose EntityList.add is running on this thread (adds can nest: a mob may add another in init). */
    private static final ThreadLocal<ArrayDeque<Mob>> adding = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<List<Object[]>> deferred = ThreadLocal.withInitial(ArrayList::new);
    private static volatile int deferredCount;

    private SpawnOrder() {
    }

    /** EntityList.add enter. */
    public static void enterAdd(Entity entity, EntityManager manager) {
        if (entity instanceof Mob && manager != null && manager.level != null && manager.level.isServer()) adding.get().push((Mob) entity);
    }

    /** EntityList.add exit (also after a throw): once the outermost add is done, adds the events that waited for it. */
    public static void exitAdd(Entity entity) {
        ArrayDeque<Mob> stack = adding.get();
        if (stack.isEmpty() || stack.peek() != entity) return;
        stack.pop();
        if (!stack.isEmpty()) return;
        List<Object[]> waiting = deferred.get();
        if (waiting.isEmpty()) return;
        List<Object[]> events = new ArrayList<>(waiting);
        waiting.clear();
        for (Object[] e : events) {
            try {
                ((LevelEventsManager) e[0]).add((LevelEvent) e[1]);
            } catch (Throwable t) {
                SRS.warn("Could not add level event " + e[1].getClass().getSimpleName() + " after its owner spawned: " + t);
            }
        }
    }

    /** LevelEventsManager.add enter: true (skip the add for now) for an ability event owned by a mob still being added. */
    public static boolean deferEvent(LevelEventsManager events, LevelEvent event) {
        if (!(event instanceof MobAbilityLevelEvent)) return false;
        ArrayDeque<Mob> stack = adding.get();
        if (stack.isEmpty()) return false;
        int owner = ((MobAbilityLevelEvent) event).ownerID;
        for (Mob mob : stack) {
            if (mob.getUniqueID() == owner) {
                deferred.get().add(new Object[]{events, event});
                deferredCount++;
                return true;
            }
        }
        return false;
    }

    /** How many events waited for their owner (used by the self-test). */
    public static int deferredCount() {
        return deferredCount;
    }

}
