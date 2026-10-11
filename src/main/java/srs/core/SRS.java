package srs.core;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import necesse.engine.GameLog;
import necesse.engine.GlobalData;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.save.SaveData;
import srs.SRSSettings;

/**
 * Global access point for the randomizer: settings, seed, mod identity, config file and logging.
 */
public final class SRS {

    public static final String FALLBACK_MOD_ID = "josezito.srs";
    public static final String LOG_PREFIX = "[SuperRandomizerShow] ";

    /** The object Necesse loads from and saves to the cfg file. Commands edit this one. */
    private static SRSSettings stored = new SRSSettings();
    /** Snapshot taken when the game boots. Everything that randomizes reads this one. */
    private static SRSSettings active;
    private static LoadedMod mod;
    private static Set<String> excludedMobs;
    private static Set<String> excludedItems;

    private SRS() {
    }

    /** The settings the current randomization was built with. */
    public static SRSSettings settings() {
        return active != null ? active : stored;
    }

    /** The settings saved in the cfg file, including changes that only apply after a restart. */
    public static SRSSettings stored() {
        return stored;
    }

    public static void setStored(SRSSettings newSettings) {
        stored = newSettings;
    }

    /** Called in preInit, after Necesse applied the cfg file: freezes the settings used for this session. */
    public static void freeze() {
        SRSSettings snapshot = new SRSSettings();
        snapshot.applyScript(stored.toScript());
        active = snapshot;
        excludedMobs = null;
        excludedItems = null;
    }

    /** True once the settings for this session are known (from preInit on). */
    public static boolean settingsApplied() {
        return active != null;
    }

    public static long seed() {
        return settings().seed;
    }

    public static void setMod(LoadedMod loadedMod) {
        mod = loadedMod;
    }

    public static LoadedMod mod() {
        if (mod == null) {
            for (LoadedMod m : ModLoader.getEnabledMods()) {
                if (m.id.equals(FALLBACK_MOD_ID)) mod = m;
            }
        }
        return mod;
    }

    public static String modID() {
        LoadedMod m = mod();
        return m == null ? FALLBACK_MOD_ID : m.id;
    }

    public static File configDir() {
        return new File(GlobalData.cfgPath(), "mods");
    }

    public static File configFile() {
        return new File(configDir(), modID() + ".cfg");
    }

    /** Writes the stored settings to the mod cfg file in the same format Necesse uses. */
    public static boolean saveConfig() {
        return saveConfig(stored);
    }

    /** Writes the given settings to the mod cfg file without changing the settings in use. */
    public static boolean saveConfig(SRSSettings toSave) {
        try {
            File file = configFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            SaveData root = new SaveData("");
            SaveData data = new SaveData("SETTINGS");
            toSave.addSaveData(data);
            root.addSaveData(data);
            root.saveScript(file);
            return true;
        } catch (Exception e) {
            warn("Could not save config: " + e);
            return false;
        }
    }

    public static Set<String> parseList(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (String s : csv.split("[,;\\s]+")) {
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static boolean isMobExcluded(String stringID) {
        if (excludedMobs == null) excludedMobs = parseList(settings().excludeMobs);
        return excludedMobs.contains(stringID);
    }

    public static boolean isItemExcluded(String stringID) {
        if (excludedItems == null) excludedItems = parseList(settings().excludeItems);
        return excludedItems.contains(stringID);
    }

    public static void log(String message) {
        GameLog.out.println(LOG_PREFIX + message);
    }

    public static void warn(String message) {
        GameLog.warn.println(LOG_PREFIX + message);
    }

}
