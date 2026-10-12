package srs.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import net.bytebuddy.asm.Advice;
import srs.random.SoundRandomizer;
import srs.random.VisualRandomizer;

/** Features 13c and 16b: switches the visual and sound rotations on the render thread, which owns textures and sounds. */
@ModMethodPatch(target = Client.class, name = "frameTick", arguments = {TickManager.class})
public class ClientFrameTickPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Client client) {
        VisualRandomizer.tickRotation(client);
        SoundRandomizer.tickRotation(client);
    }

}
