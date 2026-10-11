package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.Server;
import net.bytebuddy.asm.Advice;
import srs.core.ServerTasks;

/** Runs tasks queued by the mod (like /srs test) on the server's tick thread. */
@ModMethodPatch(target = Server.class, name = "tick", arguments = {})
public class ServerTickPatch {

    @Advice.OnMethodExit
    static void onExit() {
        ServerTasks.runQueued();
    }

}
