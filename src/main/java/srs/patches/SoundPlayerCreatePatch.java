package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.platforms.sharedOnPC.sound.LWJGLSoundManager;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.gameSound.GameSound;
import net.bytebuddy.asm.Advice;
import srs.random.SoundRandomizer;

/** Feature 16b: every sound, music track and weather loop is played through here, so the sound rotation swaps it here. */
@ModMethodPatch(target = LWJGLSoundManager.class, name = "createSoundPlayer", arguments = {GameSound.class, SoundEffect.class})
public class SoundPlayerCreatePatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) GameSound sound) {
        sound = SoundRandomizer.rotate(sound);
    }

}
