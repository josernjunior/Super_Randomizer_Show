package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.sound.SoundCooldown;
import necesse.engine.sound.gameSound.GameSound;
import net.bytebuddy.asm.Advice;
import srs.random.SoundRandomizer;

/** Feature 16: loads a different sound file than the one asked for. */
@ModMethodPatch(target = GameSound.class, name = "fromFileRaw", arguments = {String.class, SoundCooldown.class, boolean.class})
public class GameSoundFromFilePatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) String filePath) {
        filePath = SoundRandomizer.remap(filePath);
    }

}
