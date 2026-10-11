package srs.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ClassIDDataContainer;
import necesse.engine.registries.MobRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import srs.core.MobPools;
import srs.core.SRS;
import srs.random.TranslationRandomizer;

/**
 * The bosses and town NPCs offered as checkboxes in the settings screen, as {id, readable name} pairs.
 */
public final class MobChoices {

    /** Boss mobs that are only parts of another boss and must never be offered on their own. */
    private static final Set<String> BOSS_PARTS = new HashSet<>(Arrays.asList(
            "pestwardenbody", "crystaldragonbody", "mutanthydraneck", "mutanthydrahead", "grit", "sage"));
    /**
     * Vanilla bosses in the order they are fought: surface, caves, deep caves, then incursions.
     * Matches the game's registration order and the bosses' base health.
     */
    private static final List<String> BOSS_PROGRESSION = Arrays.asList(
            "evilsprotector", "queenspider", "voidwizard", "chieftain", "swampguardian", "ancientvulture", "piratecaptain",
            "reaper", "cryoqueen", "thecursedcrone", "pestwarden", "sageandgrit", "fallenwizard",
            "motherslime", "nightswarm", "spiderempress", "sunlightchampion", "moonlightdancer", "crystaldragon", "mutanthydra",
            "ascendedwizard", "thevoid");
    /** Human mobs that aren't town NPCs (hostile, special or story-only helpers). */
    private static final Set<String> NOT_TOWN_NPCS = new HashSet<>(Arrays.asList(
            "stealthbaskethuman", "possessedsettler", "unknownraider", "humanraider", "makeshiftrobotsettler"));

    private MobChoices() {
    }

    /** Every boss that can be swapped, plus any extra IDs already in the pool, in game progression order. */
    public static String[][] bosses(String currentPool) {
        Set<String> ids = new LinkedHashSet<>();
        for (ClassIDDataContainer<Mob> container : MobRegistry.getMobs()) {
            String id = container.getIDData().getStringID();
            int numericID = MobRegistry.getMobID(id);
            // Boss body parts (swamp guardian body/tail, flying spirits body, ...) don't count as kills
            if (MobRegistry.isBossMob(numericID) && MobRegistry.countMobKillStat(numericID) && !BOSS_PARTS.contains(id)) ids.add(id);
        }
        ids.addAll(SRS.parseList(currentPool));
        String[][] out = named(ids);
        // Vanilla bosses follow the progression; bosses from other mods come after them, by name
        Arrays.sort(out, (a, b) -> {
            int pa = progression(a[0]);
            int pb = progression(b[0]);
            return pa != pb ? Integer.compare(pa, pb) : a[1].compareToIgnoreCase(b[1]);
        });
        return out;
    }

    private static int progression(String id) {
        int index = BOSS_PROGRESSION.indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /** Every human NPC that can live in a village, plus any extra IDs already in the pool. */
    public static String[][] townNPCs(String currentPool) {
        Set<String> ids = new LinkedHashSet<>();
        for (ClassIDDataContainer<Mob> container : MobRegistry.getMobs()) {
            String id = container.getIDData().getStringID();
            Class<?> mobClass = MobPools.mobClass(id);
            if (mobClass != null && HumanMob.class.isAssignableFrom(mobClass) && !NOT_TOWN_NPCS.contains(id)) ids.add(id);
        }
        ids.addAll(SRS.parseList(currentPool));
        return named(ids);
    }

    /** {id, name} pairs sorted by name. Duplicate names get the ID appended. */
    private static String[][] named(Set<String> ids) {
        List<String[]> out = new ArrayList<>();
        Map<String, Integer> nameCount = new HashMap<>();
        for (String id : ids) {
            String name = mobName(id);
            out.add(new String[]{id, name});
            nameCount.merge(name, 1, Integer::sum);
        }
        for (String[] entry : out) {
            if (nameCount.get(entry[1]) > 1) entry[1] = entry[1] + " (" + entry[0] + ")";
        }
        out.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        return out.toArray(new String[0][]);
    }

    /** The mob's name in the current language, from before the translation shuffle so it stays readable. */
    public static String mobName(String id) {
        try {
            if (!MobRegistry.mobExists(id)) return id;
            GameMessage message = MobRegistry.getLocalization(id);
            if (message instanceof LocalMessage) {
                LocalMessage local = (LocalMessage) message;
                String name = TranslationRandomizer.originalText(local.category, local.key);
                if (name != null && !name.isEmpty()) return name;
            }
            String name = message.translate();
            return name == null || name.isEmpty() ? id : name;
        } catch (Exception e) {
            return id;
        }
    }

}
