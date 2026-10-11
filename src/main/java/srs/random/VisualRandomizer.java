package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.res.ResourceEncoder;
import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.ArmorItem;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 10: swaps texture files.
 * <p>
 * Textures are grouped into families by name: "zombie", "zombiearms_left", "zombiearms_right" and "zombie_shadow"
 * are one family with the parts "", "arms_left", "arms_right" and "_shadow". Whole families are swapped with other
 * families from the same folder whose main texture has exactly the same pixel size, and every part is replaced by
 * the same part of the new family (only when that part exists with the same size). This way a mob becomes another
 * mob as a whole, an armor sheet another armor sheet, an item icon another item icon.
 */
public final class VisualRandomizer {

    private static final Pattern PART = Pattern.compile("^(.+?)(_?arms_left|_?arms_right|_shadow|_front|_back|_glow|_mask|_left|_right|_top|_bottom|_head|_body|_feet|_light|_lights)$");

    private static final Map<String, String> mapping = new HashMap<>();
    private static boolean built;

    private VisualRandomizer() {
    }

    public static synchronized void build() {
        if (built) return;
        built = true;
        mapping.clear();
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

        List<String> lines = Spoiler.section("10 - Visuais (textura original -> textura mostrada)");
        if (bugged) lines.add("Troca Bugada Divertida ligada: as partes dos mobs ([bugada]) trocam separadas umas das outras.");
        List<String> folderNames = new ArrayList<>(families.keySet());
        Collections.sort(folderNames);
        for (String folder : folderNames) {
            Map<String, Map<String, Tex>> byBase = families.get(folder);
            if (bugged && isMobFolder(folder)) {
                swapPartsSeparately(folder, byBase, lines);
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
                Map<String, String> perm = SeedRandom.permutation("visuals:" + folder + "|" + size, bases);
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
                        mapping.put(a.path, b.path);
                        lines.add(a.path + " -> " + b.path);
                    }
                }
            }
        }
        SRS.log("Visual randomizer swapped " + mapping.size() + " textures");
    }

    /**
     * Troca Bugada Divertida: every part (body, left arm, right arm, shadow...) swaps on its own with the same part of
     * other mobs of the same size, so a mob can end up with a slime's body and a zombie's arms.
     */
    private static void swapPartsSeparately(String folder, Map<String, Map<String, Tex>> byBase, List<String> lines) {
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
            Map<String, String> perm = SeedRandom.permutation("visuals-bugged:" + folder + "|" + key, files);
            List<String> sorted = new ArrayList<>(files);
            Collections.sort(sorted);
            for (String f : sorted) {
                String to = perm.get(f);
                if (to.equals(f)) continue;
                mapping.put(f, to);
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

    private static class Tex {
        final String path;
        final String size;

        Tex(String path, String size) {
            this.path = path;
            this.size = size;
        }
    }

}
