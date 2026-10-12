package srs.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import net.bytebuddy.asm.Advice;
import srs.random.VisualRandomizer;

/** Feature 13c: switches the visual rotation on the render thread, which owns the textures. */
@ModMethodPatch(target = Client.class, name = "frameTick", arguments = {TickManager.class})
public class ClientFrameTickPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This Client client) {
        VisualRandomizer.tickRotation(client);
    }

}
