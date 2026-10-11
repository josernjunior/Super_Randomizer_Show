package srs.patches;

import java.util.List;
import java.util.function.Consumer;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.manager.EntityList;
import net.bytebuddy.asm.Advice;
import srs.core.SafeTick;

/** Safety net: a projectile or mob that throws while ticking is removed instead of crashing the server. */
@ModMethodPatch(target = EntityList.class, name = "serverTick", arguments = {Consumer.class, List.class})
public class EntityListServerTickPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) Consumer tick, @Advice.FieldValue("entityName") String entityName) {
        tick = SafeTick.wrap(entityName, tick);
    }

}
