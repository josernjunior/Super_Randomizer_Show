package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.gfx.res.ResourceEncoder;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 16: swaps sound files inside the same category (music with music, animal sounds with animal sounds,
 * weapon sounds with weapon sounds, ...).
 */
public final class SoundRandomizer {

    private static final String[] WEAPON_WORDS = {"sword", "bow", "staff", "spear", "glaive", "gun", "rifle", "shotgun", "flintlock",
            "boomerang", "wand", "tome", "grimoire", "scythe", "whip", "katana", "hammer", "dagger", "knife", "axe", "cleaver",
            "nunchuck", "swing", "swoosh", "punch", "ninjastar", "minigun", "blaster", "lance", "slasher", "razor", "claw", "talon",
            "javelin", "sparkler", "rollingpin", "shears", "piercer", "executioner", "rebound", "pyromancy", "shield"};
    private static final String[] IMPACT_WORDS = {"hit", "bolt", "shot", "shoot", "explosion", "explode", "zap", "laser", "beam", "blast",
            "spit", "splash", "bounce", "pop", "crack", "shatter", "fizz", "snowball", "icicle", "fire", "magic", "spell", "flash",
            "glyph", "electric", "blackhole", "waterblob", "impact", "break", "stomp", "rumble", "shake", "volley", "fuse"};
    private static final String[] INTERFACE_WORDS = {"open", "close", "coins", "eat", "drink", "jingle", "tick", "tap", "teleport",
            "journal", "challenge", "camera", "shutter", "bookshuffle", "cling", "flick", "squeak", "place"};

    private static final String SOUND_FOLDER = "sound/";

    /** Full resource path ("sound/music/forestpath.ogg") -> full resource path of the sound played instead. */
    private static final Map<String, String> mapping = new HashMap<>();
    private static boolean built;

    private SoundRandomizer() {
    }

    public static synchronized void build() {
        if (built) return;
        built = true;
        mapping.clear();
        if (!SRS.settings().sounds || !ResourceEncoder.isLoaded()) return;
        Set<String> enabled = SRS.parseList(SRS.settings().soundCategories);
        Map<String, List<String>> groups = new HashMap<>();
        for (Map.Entry<String, ?> entry : ResourceEncoder.getAllFiles()) {
            String path = entry.getKey();
            if (!path.endsWith(".ogg") || !path.startsWith("sound/")) continue;
            String category = category(path);
            if (category != null && enabled.contains(category)) {
                groups.computeIfAbsent(category, k -> new ArrayList<>()).add(path);
            }
        }
        List<String> lines = Spoiler.section("16 - Sons (som original -> som tocado)");
        List<String> categories = new ArrayList<>(groups.keySet());
        Collections.sort(categories);
        for (String category : categories) {
            List<String> files = groups.get(category);
            Map<String, String> perm = SeedRandom.permutation("sounds:" + category, files);
            List<String> sorted = new ArrayList<>(files);
            Collections.sort(sorted);
            for (String f : sorted) {
                String to = perm.get(f);
                if (!to.equals(f)) {
                    mapping.put(f, to);
                    lines.add("[" + category + "] " + f + " -> " + to);
                }
            }
        }
        SRS.log("Sound randomizer swapped " + mapping.size() + " sounds");
    }

    /** Category of a sound path, or null if it should never be swapped. */
    public static String category(String path) {
        String rest = path.substring("sound/".length());
        if (rest.startsWith("temp/")) return null;
        if (rest.startsWith("music/")) return "music";
        if (rest.startsWith("mobs/animals/")) return "animals";
        if (rest.startsWith("mobs/")) return "creatures";
        if (rest.startsWith("environment/")) return "environment";
        if (rest.startsWith("weather/")) return "weather";
        if (rest.startsWith("notes/")) return "notes";
        if (rest.contains("/")) return "other";
        String name = rest.substring(0, rest.length() - 4);
        if (name.matches(".*(begin|death|hurt|stage)\\d*(final)?$") || name.contains("stage") || name.endsWith("roar")
                || name.endsWith("growl") || name.endsWith("laugh") || name.contains("groan") || name.endsWith("ambient")) {
            return "bosses";
        }
        if (containsAny(name, WEAPON_WORDS)) return "weapons";
        if (containsAny(name, IMPACT_WORDS)) return "impacts";
        if (containsAny(name, INTERFACE_WORDS)) return "interface";
        return "other";
    }

    private static boolean containsAny(String name, String[] words) {
        for (String w : words) {
            if (name.contains(w)) return true;
        }
        return false;
    }

    /**
     * Called from the GameSound.fromFileRaw patch. The game asks for paths relative to the sound folder
     * ("music/forestpath") and reads them from "sound/" + path, while the resource list has the full path.
     */
    public static String remap(String filePath) {
        if (filePath == null || !SRS.settingsApplied() || !SRS.settings().sounds) return filePath;
        if (!built) build();
        if (mapping.isEmpty()) return filePath;
        String key = SOUND_FOLDER + (filePath.endsWith(".ogg") ? filePath : filePath + ".ogg");
        String to = mapping.get(key);
        return to == null ? filePath : to.substring(SOUND_FOLDER.length());
    }

}
