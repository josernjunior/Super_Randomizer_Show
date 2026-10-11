package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.MobRegistry;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import srs.random.MobSpawnRandomizer;

/** Feature 1: swaps the requested mob ID at known spawn points. */
@ModMethodPatch(target = MobRegistry.class, name = "getMob", arguments = {String.class, Level.class})
public class MobRegistryGetMobPatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) String stringID, @Advice.Argument(1) Level level) {
        stringID = MobSpawnRandomizer.remap(stringID, level);
    }

}
