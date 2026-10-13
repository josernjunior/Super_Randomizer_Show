package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.Packet;
import necesse.engine.network.server.network.ServerNetwork;
import necesse.level.maps.regionSystem.RegionPositionGetter;
import net.bytebuddy.asm.Advice;
import srs.random.BossStandIn;

/** Feature 1: drops packets sent for a summoned boss that was replaced before being added (its summon message names the original). */
@ModMethodPatch(target = ServerNetwork.class, name = "sendToClientsWithEntity", arguments = {Packet.class, RegionPositionGetter.class})
public class ServerNetworkSendWithEntityPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean onEnter(@Advice.Argument(1) RegionPositionGetter getter) {
        return BossStandIn.isNeverAdded(getter);
    }

}
