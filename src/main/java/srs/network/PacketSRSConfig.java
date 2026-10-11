package srs.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import srs.SRSSettings;
import srs.core.SRS;

/**
 * Sent by the server to every client that connects, carrying the server's randomizer settings.
 * The randomization is built while the game boots, so a client with different settings can't switch on the fly:
 * it saves the server's settings to its own config and tells the player to restart.
 */
public class PacketSRSConfig extends Packet {

    public final String script;

    public PacketSRSConfig(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        script = reader.getNextString();
    }

    public PacketSRSConfig(String script) {
        this.script = script;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextString(script);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        SRSSettings server = new SRSSettings();
        if (!server.applyScript(script)) return;
        if (server.fingerprint().equals(SRS.settings().fingerprint())) return;
        server.spoilerLog = SRS.stored().spoilerLog;
        SRS.stored().applyScript(server.toScript());
        boolean saved = SRS.saveConfig();
        SRS.log("Server uses different randomizer settings (seed " + server.seed + "), saved=" + saved);
        if (client != null && client.chat != null) {
            client.chat.addMessage("[Super Randomizer Show] O servidor usa outra configuracao do randomizer (seed " + server.seed + ").");
            client.chat.addMessage(saved
                    ? "A configuracao do servidor foi salva no seu jogo. Reinicie o Necesse e entre de novo para jogar sincronizado."
                    : "Nao foi possivel salvar a configuracao do servidor. Copie a seed " + server.seed + " para o seu arquivo de configuracao.");
        }
    }

}
