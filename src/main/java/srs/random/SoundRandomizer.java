package srs.random;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.engine.network.client.Client;
import necesse.engine.platforms.Platform;
import necesse.engine.sound.SoundManager;
import necesse.engine.sound.gameSound.GameSound;
import necesse.gfx.res.ResourceEncoder;
import srs.core.Rotation;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 16: swaps sound files inside the same category (music with music, animal sounds with animal sounds,
 * weapon sounds with weapon sounds, ...).
 * <p>
 * With rotation on, the seed makes 3 complete sets of swaps. Sounds load with the first; every 6 minutes of world play
 * time, the sound a player is created for is replaced by the one the current set picks.
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

    /** Full resource path ("sound/music/forestpath.ogg") -> full resource path of the sound played instead (variant 0, used when loading). */
    private static final Map<String, String> mapping = new HashMap<>();
    /** Variants 1 and 2 when rotation is on. */
    private static final List<Map<String, String>> variants = new ArrayList<>();
    /** Rotation: loaded sound -> full path the game asked for. */
    private static final Map<GameSound, String> requestedOf = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Rotation: full path -> a loaded sound holding that file. */
    private static final Map<String, GameSound> byPath = Collections.synchronizedMap(new HashMap<>());
    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    private static volatile int currentVariant;
    /** The client (world session) the rotation last ran for, weakly held so a left world can be freed. */
    private static WeakReference<Client> session;
    private static boolean built;

    private SoundRandomizer() {
    }

    public static synchronized void build() {
        if (built) return;
        built = true;
        mapping.clear();
        variants.clear();
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
        boolean rotation = SRS.settings().soundRotation;
        List<String> categories = new ArrayList<>(groups.keySet());
        Collections.sort(categories);
        for (int v = 0; v < (rotation ? Rotation.VARIANTS : 1); v++) {
            Map<String, String> variant = v == 0 ? mapping : new HashMap<>();
            List<String> lines = Spoiler.section(Rotation.sectionTitle("16", "Sons", "som original -> som tocado", rotation, v));
            if (v == 0 && rotation) {
                lines.add("Rotacao ligada: as " + Rotation.VARIANTS + " opcoes se alternam a cada " + (Rotation.MILLIS / 60000) + " minutos de jogo.");
            }
            for (String category : categories) {
                List<String> files = groups.get(category);
                // Variant 0 keeps the old keys, so seeds without rotation keep their sounds
                Map<String, String> perm = SeedRandom.permutation("sounds" + Rotation.keySuffix(v) + ":" + category, files);
                List<String> sorted = new ArrayList<>(files);
                Collections.sort(sorted);
                for (String f : sorted) {
                    String to = perm.get(f);
                    if (!to.equals(f)) {
                        variant.put(f, to);
                        lines.add("[" + category + "] " + f + " -> " + to);
                    }
                }
            }
            if (v > 0) variants.add(variant);
        }
        SRS.log("Sound randomizer swapped " + mapping.size() + " sounds" + (rotation ? " (" + Rotation.VARIANTS + " rotating variants)" : ""));
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
        String key = fullPath(filePath);
        String to = mapping.get(key);
        return to == null ? filePath : to.substring(SOUND_FOLDER.length());
    }

    private static String fullPath(String gamePath) {
        return SOUND_FOLDER + (gamePath.endsWith(".ogg") ? gamePath : gamePath + ".ogg");
    }

    // ===== Rotation (16b) =====

    /** Called from the GameSound.fromFileRaw patch with the path the game asked for and the sound it got back. */
    public static void onLoaded(String requested, GameSound sound) {
        if (variants.isEmpty() || requested == null || sound == null) return;
        requestedOf.put(sound, fullPath(requested));
        byPath.put(fullPath(sound.path), sound);
    }

    /**
     * Called every client frame on the render thread: picks the variant from the world's play time, like the visual
     * rotation. When it changes, the music changes track right away so the new option is heard.
     */
    public static void tickRotation(Client client) {
        if (variants.isEmpty() || client == null || client.worldEntity == null) return;
        // Joining a world sets its variant quietly: the world's own music starts right after anyway
        boolean sameSession = session != null && session.get() == client;
        if (!sameSession) session = new WeakReference<>(client);
        int wanted = Rotation.variantAt(client.worldEntity.getTime());
        if (wanted == currentVariant) return;
        currentVariant = wanted;
        SRS.log("Sound rotation: option " + (wanted + 1) + " of " + Rotation.VARIANTS);
        if (!sameSession) return;
        try {
            if (SRS.parseList(SRS.settings().soundCategories).contains("music")) SoundManager.forceChangeMusic();
        } catch (Throwable t) {
            SRS.warn("Sound rotation could not change the music track: " + t);
        }
    }

    /**
     * Called whenever a sound starts playing (effects, music and weather all create a player): returns the sound the
     * current variant plays instead. It's usually already loaded, because every file of a category is some sound's
     * target in variant 0; otherwise it's loaded once, the same way the game loads it.
     */
    public static GameSound rotate(GameSound sound) {
        if (currentVariant == 0 || sound == null || variants.isEmpty()) return sound;
        String requested = requestedOf.get(sound);
        if (requested == null) return sound;
        String target = variants.get(currentVariant - 1).getOrDefault(requested, requested);
        if (target.equals(fullPath(sound.path))) return sound;
        GameSound replacement = byPath.get(target);
        if (replacement != null) return replacement;
        if (failed.contains(target)) return sound;
        try {
            replacement = Platform.getSoundManager().createGameSound(target.substring(SOUND_FOLDER.length()), sound.cooldown, sound.isMusic());
            replacement.setVolumeModifier(sound.getVolumeModifier());
            byPath.put(target, replacement);
            return replacement;
        } catch (Throwable e) {
            failed.add(target);
            SRS.warn("Sound rotation could not load " + target + ": " + e);
            return sound;
        }
    }

}
