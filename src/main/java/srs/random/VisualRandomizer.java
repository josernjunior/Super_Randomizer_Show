package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.res.ResourceEncoder;
import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.ArmorItem;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 13: swaps texture files.
 * <p>
 * Textures are grouped into families by name: "zombie", "zombiearms_left", "zombiearms_right" and "zombie_shadow"
 * are one family with the parts "", "arms_left", "arms_right" and "_shadow". Whole families are swapped with other
 * families from the same folder whose main texture has exactly the same pixel size, and every part is replaced by
 * the same part of the new family (only when that part exists with the same size). This way a mob becomes another
 * mob as a whole, an armor sheet another armor sheet, an item icon another item icon.
 * <p>
 * With rotation on, the seed makes 3 complete sets of swaps. Textures load with the first; every 6 minutes of world
 * play time the pictures of the textures the game holds are replaced with the next set's (same sizes by construction).
 */
public final class VisualRandomizer {

    private static final Pattern PART = Pattern.compile("^(.+?)(_?arms_left|_?arms_right|_shadow|_front|_back|_glow|_mask|_left|_right|_top|_bottom|_head|_body|_feet|_light|_lights)$");

    /** Rotation (13c): how many complete sets of swaps the seed makes, and how long each one stays, in world play time. */
    public static final int VARIANTS = 3;
    public static final long ROTATION_MILLIS = 6 * 60 * 1000L;

    /** The swaps textures are loaded with (variant 0). */
    private static final Map<String, String> mapping = new HashMap<>();
    /** Variants 1 and 2 when rotation is on. */
    private static final List<Map<String, String>> variants = new ArrayList<>();
    /** Every path some variant swaps. */
    private static final Set<String> tracked = new HashSet<>();
    /** Requested path -> the texture object the game holds for it, whose pixels the rotation replaces. */
    private static final Map<String, GameTexture> held = new HashMap<>();
    /** Requested path -> path whose pixels that texture currently shows. */
    private static final Map<String, String> shown = new HashMap<>();
    private static int currentVariant;
    private static volatile Thread decoder;
    private static volatile List<Decoded> decoded;
    private static int decodingVariant = -1;
    private static boolean built;

    private VisualRandomizer() {
    }

    public static synchronized void build() {
        if (built) return;
        built = true;
        mapping.clear();
        variants.clear();
        tracked.clear();
        boolean bugged = SRS.settings().funnyBuggedSwap;
        if ((!SRS.settings().visuals && !bugged) || !ResourceEncoder.isLoaded()) return;
        Set<String> folders = SRS.settings().visuals ? SRS.parseList(SRS.settings().visualFolders) : Collections.<String>emptySet();

        // folder -> family base -> part -> file info
        Map<String, Map<String, Map<String, Tex>>> families = new HashMap<>();
        for (Map.Entry<String, ?> entry : ResourceEncoder.getAllFiles()) {
            String path = entry.getKey();
            if (!path.endsWith(".png")) continue;
            int slash = path.lastIndexOf('/');
            if (slash < 0) continue;
            String folder = path.substring(0, slash);
            if (!matchesFolder(folder, folders) && !(bugged && isMobFolder(folder))) continue;
            String name = path.substring(slash + 1, path.length() - 4);
            String base = name;
            String part = "";
            Matcher m = PART.matcher(name);
            if (m.matches()) {
                base = m.group(1);
                part = m.group(2).startsWith("_") || !m.group(2).startsWith("arms") ? m.group(2) : "_" + m.group(2);
                part = part.replace("__", "_");
            }
            if (SRS.isItemExcluded(base) || SRS.isMobExcluded(base)) continue;
            int[] size = pngSize(path);
            if (size == null) continue;
            families.computeIfAbsent(folder, k -> new HashMap<>()).computeIfAbsent(base, k -> new HashMap<>())
                    .put(part, new Tex(path, size[0] + "x" + size[1]));
        }

        // Armor sheets are only swapped within the same slot, so a helmet never turns into boots
        Map<String, String> armorSlot = new HashMap<>();
        for (Item item : ItemRegistry.getItems()) {
            if (item instanceof ArmorItem && ((ArmorItem) item).textureName != null && ((ArmorItem) item).armorType != null) {
                armorSlot.put(((ArmorItem) item).textureName, ((ArmorItem) item).armorType.name());
            }
        }

        boolean rotation = SRS.settings().visualRotation;
        for (int v = 0; v < (rotation ? VARIANTS : 1); v++) {
            Map<String, String> variant = v == 0 ? mapping : new HashMap<>();
            List<String> lines = Spoiler.section(rotation ? "13 - Visuais, opcao " + (v + 1) + " de " + VARIANTS + " (textura original -> textura mostrada)"
                    : "13 - Visuais (textura original -> textura mostrada)");
            if (v == 0 && rotation) {
                lines.add("Rotacao ligada: as " + VARIANTS + " opcoes se alternam a cada " + (ROTATION_MILLIS / 60000) + " minutos de jogo.");
            }
            if (bugged) lines.add("Troca Bugada Divertida ligada: as partes dos mobs ([bugada]) trocam separadas umas das outras.");
            buildVariant(v, variant, families, armorSlot, bugged, lines);
            if (v > 0) variants.add(variant);
        }
        if (rotation) {
            tracked.addAll(mapping.keySet());
            for (Map<String, String> variant : variants) tracked.addAll(variant.keySet());
        }
        SRS.log("Visual randomizer swapped " + mapping.size() + " textures" + (rotation ? " (" + VARIANTS + " rotating variants)" : ""));
    }

    /** One complete set of swaps. Variant 0 uses the original keys, so seeds without rotation keep their visuals. */
    private static void buildVariant(int v, Map<String, String> out, Map<String, Map<String, Map<String, Tex>>> families,
                                     Map<String, String> armorSlot, boolean bugged, List<String> lines) {
        String suffix = v == 0 ? "" : "#" + (v + 1);
        List<String> folderNames = new ArrayList<>(families.keySet());
        Collections.sort(folderNames);
        for (String folder : folderNames) {
            Map<String, Map<String, Tex>> byBase = families.get(folder);
            if (bugged && isMobFolder(folder)) {
                swapPartsSeparately("visuals-bugged" + suffix + ":", folder, byBase, out, lines);
                continue;
            }
            // Families only swap with families made of the same parts with the same sizes
            Map<String, List<String>> groups = new HashMap<>();
            for (Map.Entry<String, Map<String, Tex>> e : byBase.entrySet()) {
                String key = signature(e.getValue());
                if (folder.startsWith("player/armor")) key += "|" + armorSlot.getOrDefault(e.getKey(), "?");
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getKey());
            }
            List<String> groupKeys = new ArrayList<>(groups.keySet());
            Collections.sort(groupKeys);
            for (String size : groupKeys) {
                List<String> bases = groups.get(size);
                if (bases.size() < 2) continue;
                Map<String, String> perm = SeedRandom.permutation("visuals" + suffix + ":" + folder + "|" + size, bases);
                List<String> sorted = new ArrayList<>(bases);
                Collections.sort(sorted);
                for (String base : sorted) {
                    String target = perm.get(base);
                    if (target.equals(base)) continue;
                    Map<String, Tex> from = byBase.get(base);
                    Map<String, Tex> to = byBase.get(target);
                    List<String> parts = new ArrayList<>(from.keySet());
                    Collections.sort(parts);
                    for (String part : parts) {
                        Tex a = from.get(part);
                        Tex b = to.get(part);
                        out.put(a.path, b.path);
                        lines.add(a.path + " -> " + b.path);
                    }
                }
            }
        }
    }

    /**
     * Troca Bugada Divertida: every part (body, left arm, right arm, shadow...) swaps on its own with the same part of
     * other mobs of the same size, so a mob can end up with a slime's body and a zombie's arms.
     */
    private static void swapPartsSeparately(String keyPrefix, String folder, Map<String, Map<String, Tex>> byBase, Map<String, String> out,
                                            List<String> lines) {
        Map<String, List<String>> groups = new HashMap<>();
        for (Map<String, Tex> parts : byBase.values()) {
            for (Map.Entry<String, Tex> part : parts.entrySet()) {
                groups.computeIfAbsent(part.getKey() + "|" + part.getValue().size, k -> new ArrayList<>()).add(part.getValue().path);
            }
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            List<String> files = groups.get(key);
            if (files.size() < 2) continue;
            Map<String, String> perm = SeedRandom.permutation(keyPrefix + folder + "|" + key, files);
            List<String> sorted = new ArrayList<>(files);
            Collections.sort(sorted);
            for (String f : sorted) {
                String to = perm.get(f);
                if (to.equals(f)) continue;
                out.put(f, to);
                lines.add("[bugada] " + f + " -> " + to);
            }
        }
    }

    private static boolean isMobFolder(String folder) {
        return folder.equals("mobs") || folder.startsWith("mobs/");
    }

    private static String signature(Map<String, Tex> parts) {
        List<String> keys = new ArrayList<>(parts.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(k).append('=').append(parts.get(k).size).append(';');
        return sb.toString();
    }

    private static boolean matchesFolder(String folder, Set<String> folders) {
        for (String f : folders) {
            if (folder.equals(f) || folder.startsWith(f + "/")) return true;
        }
        return false;
    }

    private static int[] pngSize(String path) {
        try {
            byte[] b = ResourceEncoder.getResourceBytes(path);
            if (b.length < 24) return null;
            int w = ((b[16] & 0xff) << 24) | ((b[17] & 0xff) << 16) | ((b[18] & 0xff) << 8) | (b[19] & 0xff);
            int h = ((b[20] & 0xff) << 24) | ((b[21] & 0xff) << 16) | ((b[22] & 0xff) << 8) | (b[23] & 0xff);
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        }
    }

    /** Called from the GameTexture.fromFileRawUnknown patch. */
    public static String remap(String filePath, boolean outsideGame) {
        if (outsideGame || filePath == null || !SRS.settingsApplied() || (!SRS.settings().visuals && !SRS.settings().funnyBuggedSwap)) return filePath;
        if (!built) build();
        if (mapping.isEmpty()) return filePath;
        String key = filePath.endsWith(".png") ? filePath : filePath + ".png";
        String to = mapping.get(key);
        return to == null ? filePath : to;
    }

    // ===== Rotation (13c) =====

    /** Called from the GameTexture.fromFileRawUnknown patch with the path the game asked for and what it got back. */
    public static void onLoaded(String requested, GameTexture texture, boolean outsideGame) {
        if (outsideGame || requested == null || texture == null || tracked.isEmpty()) return;
        String key = requested.endsWith(".png") ? requested : requested + ".png";
        if (!tracked.contains(key)) return;
        synchronized (held) {
            if (held.containsKey(key)) return;
            held.put(key, texture);
            shown.put(key, mapping.getOrDefault(key, key));
        }
    }

    private static Map<String, String> variant(int v) {
        return v == 0 ? mapping : variants.get(v - 1);
    }

    /**
     * Called every client frame on the render thread. The variant follows the world's play time, so everyone in a
     * multiplayer world sees the same one. Textures for the next variant are decoded on a background thread and
     * uploaded here once they're ready (only the textures whose picture actually changes).
     */
    public static void tickRotation(Client client) {
        if (tracked.isEmpty() || client == null || client.worldEntity == null) return;
        try {
            rotate(client);
        } catch (Throwable t) {
            // A failing rotation must never take the game down: it stops and the current pictures stay
            tracked.clear();
            SRS.warn("Visual rotation stopped: " + t);
        }
    }

    private static void rotate(Client client) {
        int wanted = (int) ((client.worldEntity.getTime() / ROTATION_MILLIS) % VARIANTS);
        List<Decoded> ready = decoded;
        if (ready != null) {
            decoded = null;
            decoder = null;
            apply(ready);
            currentVariant = decodingVariant;
        }
        if (wanted == currentVariant || decoder != null) return;
        final int target = wanted;
        final Map<String, String> swaps = variant(target);
        final List<Map.Entry<String, GameTexture>> jobs = new ArrayList<>();
        synchronized (held) {
            for (Map.Entry<String, GameTexture> e : held.entrySet()) {
                if (!swaps.getOrDefault(e.getKey(), e.getKey()).equals(shown.get(e.getKey()))) jobs.add(e);
            }
        }
        decodingVariant = target;
        Thread thread = new Thread(() -> {
            List<Decoded> out = new ArrayList<>();
            for (Map.Entry<String, GameTexture> e : jobs) {
                String path = swaps.getOrDefault(e.getKey(), e.getKey());
                try {
                    GameTexture pixels = new GameTexture("srs-rotation " + path, ResourceEncoder.getResourceBytes(path));
                    out.add(new Decoded(e.getKey(), path, e.getValue(), pixels));
                } catch (Throwable t) {
                    SRS.warn("Visual rotation could not read " + path + ": " + t);
                }
            }
            decoded = out;
        }, "SRS visual rotation");
        thread.setDaemon(true);
        decoder = thread;
        thread.start();
    }

    /** Puts the decoded pictures into the textures the game holds, the way GameTexture.tryReloadTexture does. */
    private static void apply(List<Decoded> ready) {
        int done = 0;
        for (Decoded d : ready) {
            GameTexture texture = d.texture;
            if (texture.getWidth() != d.pixels.getWidth() || texture.getHeight() != d.pixels.getHeight()) continue;
            try {
                boolean wasFinal = texture.isFinal();
                // A final texture only lives on the GPU; it gets the new pixels and is uploaded again
                if (wasFinal) Reflect.set(GameTexture.class, texture, "isFinal", false);
                texture.buffer = d.pixels.buffer;
                texture.resetTexture();
                if (wasFinal) texture.makeFinal();
                synchronized (held) {
                    shown.put(d.requested, d.path);
                }
                done++;
            } catch (Throwable t) {
                SRS.warn("Visual rotation could not update " + d.requested + ": " + t);
            }
        }
        SRS.log("Visual rotation: option " + (decodingVariant + 1) + " of " + VARIANTS + " (" + done + " textures changed)");
    }

    private static class Decoded {
        final String requested;
        final String path;
        final GameTexture texture;
        final GameTexture pixels;

        Decoded(String requested, String path, GameTexture texture, GameTexture pixels) {
            this.requested = requested;
            this.path = path;
            this.texture = texture;
            this.pixels = pixels;
        }
    }

    private static class Tex {
        final String path;
        final String size;

        Tex(String path, String size) {
            this.path = path;
            this.size = size;
        }
    }

}
