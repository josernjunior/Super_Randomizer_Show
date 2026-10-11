package srs.core;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import necesse.entity.Entity;
import necesse.entity.projectile.Projectile;
import srs.random.MobSpawnRandomizer;
import srs.random.ProjectileRandomizer;

/**
 * Safety net around the per-entity tick of the projectile and mob lists. Swapped projectiles and swapped mobs can
 * end up in situations their code never expected (missing owner, wrong level...). Without this, one exception in an
 * entity tick makes Necesse stop the server ("Stuck in crash loop"). With it, the entity that threw is removed and
 * the problem is logged once.
 */
public final class SafeTick<T> implements Consumer<T> {

    private static final Set<String> reported = new HashSet<>();

    private final String listName;
    private final Consumer<T> inner;

    private SafeTick(String listName, Consumer<T> inner) {
        this.listName = listName;
        this.inner = inner;
    }

    /** Called from the EntityList.serverTick/clientTick patches with the list's name and its per-entity tick. */
    public static <T> Consumer<T> wrap(String listName, Consumer<T> tick) {
        if (tick == null || tick instanceof SafeTick || !isGuarded(listName)) return tick;
        return new SafeTick<>(listName, tick);
    }

    /** Called from the EntityList.frameTick patch: movement is where projectiles hit things and spawn their effects. */
    public static <T> BiConsumer<T, Float> wrapMovement(String listName, BiConsumer<T, Float> movement) {
        if (movement == null || movement instanceof SafeMovement || !isGuarded(listName)) return movement;
        return new SafeMovement<>(listName, movement);
    }

    private static boolean isGuarded(String listName) {
        boolean randomized = ProjectileRandomizer.isActive() || MobSpawnRandomizer.isActive();
        return randomized && ("projectile".equals(listName) || "mob".equals(listName));
    }

    @Override
    public void accept(T entity) {
        try {
            inner.accept(entity);
        } catch (Throwable e) {
            handle(listName, entity, e);
        }
    }

    private static void handle(String listName, Object entity, Throwable e) {
        if (entity instanceof Projectile) ProjectileRandomizer.onTickError((Projectile) entity, e);
        report(listName, entity, e);
        if (entity instanceof Entity) {
            try {
                ((Entity) entity).remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class SafeMovement<T> implements BiConsumer<T, Float> {
        private final String listName;
        private final BiConsumer<T, Float> inner;

        SafeMovement(String listName, BiConsumer<T, Float> inner) {
            this.listName = listName;
            this.inner = inner;
        }

        @Override
        public void accept(T entity, Float delta) {
            try {
                inner.accept(entity, delta);
            } catch (Throwable e) {
                handle(listName, entity, e);
            }
        }
    }

    private static void report(String listName, Object entity, Throwable e) {
        String type = entity == null ? "null" : entity.getClass().getSimpleName();
        String key = listName + type + e.getClass().getName();
        synchronized (reported) {
            if (!reported.add(key)) return;
        }
        StackTraceElement[] trace = e.getStackTrace();
        String where = trace.length > 0 ? " at " + trace[0] : "";
        SRS.warn("A " + listName + " (" + type + ") broke while ticking and was removed instead of crashing the game: " + e + where);
    }

}
