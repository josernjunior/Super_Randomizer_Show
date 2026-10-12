package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.gameTexture.GameTexture;
import net.bytebuddy.asm.Advice;
import srs.random.VisualRandomizer;

/**
 * Feature 13: loads a different texture file than the one asked for, and remembers which texture object the game
 * got for each path so the visual rotation can change its picture later.
 */
@ModMethodPatch(target = GameTexture.class, name = "fromFileRawUnknown", arguments = {String.class, boolean.class, boolean.class})
public class GameTextureFromFilePatch {

    @Advice.OnMethodEnter
    static String onEnter(@Advice.Argument(value = 0, readOnly = false) String filePath, @Advice.Argument(1) boolean outsideGame) {
        String requested = filePath;
        filePath = VisualRandomizer.remap(filePath, outsideGame);
        return requested;
    }

    @Advice.OnMethodExit
    static void onExit(@Advice.Enter String requested, @Advice.Argument(1) boolean outsideGame, @Advice.Return GameTexture texture) {
        VisualRandomizer.onLoaded(requested, texture, outsideGame);
    }

}
