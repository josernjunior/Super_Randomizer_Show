package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.manager.LevelEventsManager;
import net.bytebuddy.asm.Advice;
import srs.core.SpawnOrder;

/** Spawn order fix: an ability event a mob starts while it's being added waits until the mob has been sent to the clients. */
@ModMethodPatch(target = LevelEventsManager.class, name = "add", arguments = {LevelEvent.class})
public class LevelEventsAddPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.This LevelEventsManager events, @Advice.Argument(0) LevelEvent event) {
        return SpawnOrder.deferEvent(events, event);
    }

}
