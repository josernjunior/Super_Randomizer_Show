package srs.patches;

import java.util.ArrayList;
import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.entity.levelEvent.fishingEvent.FishingEvent;
import necesse.entity.levelEvent.fishingEvent.ReelFishingPhase;
import necesse.entity.levelEvent.fishingEvent.WaitFishingPhase;
import net.bytebuddy.asm.Advice;
import srs.random.FishingRandomizer;

/** Feature 18: keeps the "catch an icefish" journal challenge possible when the icefish became another item. */
@ModConstructorPatch(target = ReelFishingPhase.class, arguments = {FishingEvent.class, ArrayList.class})
public class ReelFishingPhaseConstructorPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.Argument(0) FishingEvent event, @Advice.Argument(1) ArrayList<WaitFishingPhase.FishingLure> hooks) {
        FishingRandomizer.onReel(event, hooks);
    }

}
