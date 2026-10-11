package srs;

import java.util.Random;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.GlobalData;
import necesse.engine.commands.CommandsManager;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.PacketRegistry;
import srs.commands.SRSCommand;
import srs.core.MobPools;
import srs.core.SRS;
import srs.core.Spoiler;
import srs.network.PacketSRSConfig;
import srs.random.DropRandomizer;
import srs.random.EquipmentRandomizer;
import srs.random.MobSpawnRandomizer;
import srs.random.ProjectileRandomizer;
import srs.random.ShopRandomizer;
import srs.random.SoundRandomizer;
import srs.random.TranslationRandomizer;
import srs.random.VisualRandomizer;
import srs.random.WeaponRandomizer;

/**
 * Super Randomizer Show.
 * <p>
 * Boot order (Necesse calls these in this order, with the cfg file applied to the settings before preInit):
 * <ol>
 * <li>initSettings: creates the settings object Necesse fills from cfg/mods/&lt;modid&gt;.cfg</li>
 * <li>preInit: makes sure there is a seed, shuffles translations (languages are already loaded)</li>
 * <li>init: registers the sync packet, the /srs command and, on clients, builds texture/sound swaps
 * (textures and sounds load between init and initResources)</li>
 * <li>postInit: all registries are closed, so mobs, drops, shops, weapons, equipment and projectiles are built</li>
 * <li>recipes are built by the game right after postInit (see RecipesGetDefaultRecipesPatch), then the spoiler is written</li>
 * </ol>
 */
@ModEntry
public class SuperRandomizerShow {

    public SRSSettings initSettings() {
        SRSSettings settings = new SRSSettings();
        SRS.setStored(settings);
        SRS.setMod(LoadedMod.getRunningMod());
        return settings;
    }

    public void preInit() {
        SRSSettings stored = SRS.stored();
        boolean save = !SRS.configFile().exists();
        if (stored.seed == 0) {
            long seed = 0;
            while (seed == 0) seed = new Random().nextLong();
            stored.seed = seed;
            save = true;
        }
        if (save) SRS.saveConfig();
        SRS.freeze();
        SRS.log("Seed " + SRS.seed());
        TranslationRandomizer.randomizeAll();
    }

    public void init() {
        PacketRegistry.registerPacket(PacketSRSConfig.class);
        CommandsManager.registerServerCommand(new SRSCommand());
        GameEvents.addListener(ServerClientConnectedEvent.class, new GameEventListener<ServerClientConnectedEvent>() {
            @Override
            public void onEvent(ServerClientConnectedEvent event) {
                if (event.client != null) event.client.sendPacket(new PacketSRSConfig(SRS.settings().toScript()));
            }
        });
        if (!GlobalData.isServer()) {
            VisualRandomizer.build();
            SoundRandomizer.build();
        }
    }

    public void initResources() {
    }

    public void postInit() {
        MobPools.build();
        MobSpawnRandomizer.build();
        DropRandomizer.build();
        ShopRandomizer.build();
        WeaponRandomizer.build();
        EquipmentRandomizer.build();
        ProjectileRandomizer.build();
    }

    /** Called once the game has finished building recipes, the last step of loading. */
    public static void onLoadingFinished() {
        if (SRS.settings().spoilerLog) Spoiler.write();
        SRS.log("Randomization ready (" + Spoiler.sectionCount() + " spoiler sections)");
    }

}
