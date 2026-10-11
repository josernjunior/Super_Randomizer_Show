package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.gameTexture.GameTexture;
import net.bytebuddy.asm.Advice;
import srs.random.VisualRandomizer;

/** Feature 10: loads a different texture file than the one asked for. */
@ModMethodPatch(target = GameTexture.class, name = "fromFileRawUnknown", arguments = {String.class, boolean.class, boolean.class})
public class GameTextureFromFilePatch {

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(value = 0, readOnly = false) String filePath, @Advice.Argument(1) boolean outsideGame) {
        filePath = VisualRandomizer.remap(filePath, outsideGame);
    }

}
