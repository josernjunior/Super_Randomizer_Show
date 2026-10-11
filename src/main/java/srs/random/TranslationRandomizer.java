package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import necesse.engine.localization.AbstractTranslationElement;
import necesse.engine.localization.Language;
import necesse.engine.localization.Localization;
import necesse.engine.localization.TranslationElement;
import necesse.engine.localization.fileLanguage.FileLanguage;
import necesse.engine.localization.fileLanguage.Translation;
import necesse.engine.localization.fileLanguage.TranslationCategory;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 13: shuffles translations.
 * <ul>
 * <li>entries: whole texts are swapped between keys of the same category that use the same placeholders
 * (so "&lt;name&gt; was killed" can only trade places with another text that also has &lt;name&gt;).</li>
 * <li>words: the words of every text in a category are shuffled between those texts, placeholders and
 * punctuation stay where they were.</li>
 * </ul>
 */
public final class TranslationRandomizer {

    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");
    private static final Pattern WORD = Pattern.compile("<[^>]+>|[\\p{L}\\p{N}']+");

    /** Categories whose original texts are kept, so the settings screen can show real names. */
    private static final Set<String> KEEP_ORIGINALS = new HashSet<>(Collections.singletonList("mob"));
    private static final Map<String, Map<String, String>> ORIGINALS = new HashMap<>();

    private TranslationRandomizer() {
    }

    /** The text before shuffling, in the current language (falls back to the current, possibly shuffled, text). */
    public static String originalText(String category, String key) {
        Language current = Localization.getCurrentLang();
        if (current != null) {
            Map<String, String> originals = ORIGINALS.get(current.stringID);
            String text = originals == null ? null : originals.get(category + "." + key);
            if (text != null) return text;
        }
        return Localization.translate(category, key);
    }

    /** Randomizes every loaded file language. */
    public static void randomizeAll() {
        if (!SRS.settings().translations) return;
        try {
            List<Language> languages = Reflect.get(Localization.class, null, "languages");
            int done = 0;
            for (Language language : languages) {
                if (language instanceof FileLanguage) {
                    randomize((FileLanguage) language);
                    done++;
                }
            }
            Spoiler.section("13 - Traducoes").add("Modo '" + SRS.settings().translationMode + "' aplicado a " + done + " idiomas.");
        } catch (Exception e) {
            SRS.warn("Translation randomization failed: " + e);
        }
    }

    /** Called after a single language (re)loads its files. */
    public static void randomize(FileLanguage language) {
        if (!SRS.settings().translations || !SRS.settingsApplied()) return;
        try {
            Translation translation = Reflect.get(FileLanguage.class, language, "translation");
            Map<String, TranslationCategory> categories = Reflect.get(Translation.class, translation, "categories");
            if (categories == null) return;
            boolean words = "words".equalsIgnoreCase(SRS.settings().translationMode);
            for (TranslationCategory category : categories.values()) {
                Map<String, AbstractTranslationElement> map = Reflect.get(TranslationCategory.class, category, "translations");
                if (KEEP_ORIGINALS.contains(category.name)) {
                    Map<String, String> originals = ORIGINALS.computeIfAbsent(language.stringID, k -> new HashMap<>());
                    for (Map.Entry<String, AbstractTranslationElement> e : map.entrySet()) {
                        if (e.getValue() != null && e.getValue().translation != null) {
                            originals.putIfAbsent(category.name + "." + e.getKey(), e.getValue().translation);
                        }
                    }
                }
                String key = "translations:" + language.stringID + ":" + category.name;
                if (words) {
                    shuffleWords(map, key);
                } else {
                    shuffleEntries(map, key);
                }
            }
        } catch (Exception e) {
            SRS.warn("Could not randomize language " + language.stringID + ": " + e);
        }
    }

    private static void shuffleEntries(Map<String, AbstractTranslationElement> map, String key) {
        Map<String, List<String>> bySignature = new HashMap<>();
        for (Map.Entry<String, AbstractTranslationElement> e : map.entrySet()) {
            if (e.getValue() == null || e.getValue().translation == null) continue;
            bySignature.computeIfAbsent(signature(e.getValue().translation), k -> new ArrayList<>()).add(e.getKey());
        }
        for (Map.Entry<String, List<String>> group : bySignature.entrySet()) {
            List<String> keys = group.getValue();
            if (keys.size() < 2) continue;
            Collections.sort(keys);
            List<String> texts = new ArrayList<>();
            for (String k : keys) texts.add(map.get(k).translation);
            Collections.shuffle(texts, SeedRandom.rng(key + ":" + group.getKey()));
            for (int i = 0; i < keys.size(); i++) {
                map.put(keys.get(i), new TranslationElement(texts.get(i)));
            }
        }
    }

    private static void shuffleWords(Map<String, AbstractTranslationElement> map, String key) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        List<String> words = new ArrayList<>();
        for (String k : keys) {
            AbstractTranslationElement el = map.get(k);
            if (el == null || el.translation == null) continue;
            Matcher m = WORD.matcher(el.translation);
            while (m.find()) {
                if (!m.group().startsWith("<")) words.add(m.group());
            }
        }
        if (words.size() < 2) return;
        Random random = SeedRandom.rng(key);
        Collections.shuffle(words, random);
        int next = 0;
        for (String k : keys) {
            AbstractTranslationElement el = map.get(k);
            if (el == null || el.translation == null) continue;
            Matcher m = WORD.matcher(el.translation);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(el.translation, last, m.start());
                if (m.group().startsWith("<")) {
                    sb.append(m.group());
                } else {
                    sb.append(words.get(next++));
                }
                last = m.end();
            }
            sb.append(el.translation.substring(last));
            map.put(k, new TranslationElement(sb.toString()));
        }
    }

    private static String signature(String text) {
        TreeSet<String> set = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) set.add(m.group());
        return String.join("", set);
    }

}
