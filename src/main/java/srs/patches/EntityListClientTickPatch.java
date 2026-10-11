package srs.patches;

import java.util.List;
import java.util.function.Consumer;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.manager.EntityList;
import net.bytebuddy.asm.Advice;
import srs.core.SafeTick;

/** Safety net: a projectile or mob that throws while ticking is removed instead of crashing the client. */
@ModMethodPatch(target = EntityList.class, name = "clientTick", arguments = {Consumer.class, List.class})
public class EntityListClientTickPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) Consumer tick, @Advice.FieldValue("entityName") String entityName) {
        tick = SafeTick.wrap(entityName, tick);
    }

}
