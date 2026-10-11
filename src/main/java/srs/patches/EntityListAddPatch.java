package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.Entity;
import necesse.entity.manager.EntityList;
import necesse.entity.manager.EntityManager;
import net.bytebuddy.asm.Advice;
import srs.random.ProjectileRandomizer;

/**
 * Feature 14: swaps freshly spawned projectiles. Network spawns use addHidden and are not affected.
 * If adding the replacement throws, the replacement is dropped and the exception swallowed.
 */
@ModMethodPatch(target = EntityList.class, name = "add", arguments = {Entity.class})
public class EntityListAddPatch {

    @Advice.OnMethodEnter
    static Entity onEnter(@Advice.Argument(value = 0, readOnly = false) Entity entity, @Advice.FieldValue("manager") EntityManager manager) {
        Entity original = entity;
        if (ProjectileRandomizer.isActive() && manager != null) {
            entity = ProjectileRandomizer.swap(entity, manager.level);
        }
        return original;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit(@Advice.Argument(0) Entity entity, @Advice.Enter Entity original, @Advice.Thrown(readOnly = false) Throwable thrown) {
        if (thrown != null && entity != original && ProjectileRandomizer.onSwapFailed(entity, thrown)) {
            thrown = null;
        }
    }

}
