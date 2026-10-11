package srs.patches;

import java.util.function.BiConsumer;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.manager.EntityList;
import net.bytebuddy.asm.Advice;
import srs.core.SafeTick;

/** Safety net: a projectile or mob that throws while moving (where hits happen) is removed instead of crashing the game. */
@ModMethodPatch(target = EntityList.class, name = "frameTick", arguments = {TickManager.class, BiConsumer.class})
public class EntityListFrameTickPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 1, readOnly = false) BiConsumer movement, @Advice.FieldValue("entityName") String entityName) {
        movement = SafeTick.wrapMovement(entityName, movement);
    }

}
