package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.sound.SoundCooldown;
import necesse.engine.sound.gameSound.GameSound;
import net.bytebuddy.asm.Advice;
import srs.random.SoundRandomizer;

/**
 * Feature 16: loads a different sound file than the one asked for, and remembers which path each loaded sound was
 * asked for so the sound rotation can play another one later.
 */
@ModMethodPatch(target = GameSound.class, name = "fromFileRaw", arguments = {String.class, SoundCooldown.class, boolean.class})
public class GameSoundFromFilePatch {

    @Advice.OnMethodEnter
    static String onEnter(@Advice.Argument(value = 0, readOnly = false) String filePath) {
        String requested = filePath;
        filePath = SoundRandomizer.remap(filePath);
        return requested;
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.Enter String requested, @Advice.Return GameSound sound) {
        SoundRandomizer.onLoaded(requested, sound);
    }

}
