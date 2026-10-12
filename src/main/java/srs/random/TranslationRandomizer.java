package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
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
import necesse.gfx.gameFont.FontManager;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 15: shuffles translations.
 * <ul>
 * <li>entries: whole texts are swapped between keys of the same category that use the same placeholders
 * (so "&lt;name&gt; was killed" can only trade places with another text that also has &lt;name&gt;).</li>
 * <li>words: the words of every text in a category are shuffled between those texts, placeholders and
 * punctuation stay where they were.</li>
 * <li>entriesall: every text is replaced by a text of the same category (and placeholders) from any language.</li>
 * <li>words2, words3, wordsall: every word is replaced by a word of the same category from the language itself
 * and 1 or 2 other languages picked by the seed, or from every language.</li>
 * </ul>
 * The multi-language modes read the other languages' texts from a copy taken before anything was shuffled.
 */
public final class TranslationRandomizer {

    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");
    private static final Pattern WORD = Pattern.compile("<[^>]+>|[\\p{L}\\p{N}']+");

    /** Categories whose original texts are kept, so the settings screen can show real names. */
    private static final Set<String> KEEP_ORIGINALS = new HashSet<>(Collections.singletonList("mob"));
    private static final Map<String, Map<String, String>> ORIGINALS = new HashMap<>();

    /** Multi-language modes: language -> category -> key -> text before shuffling. */
    private static final Map<String, Map<String, Map<String, String>>> SOURCES = new TreeMap<>();
    /** Multi-language modes: "language|category" -> words / "language|category|placeholders" -> texts. */
    private static final Map<String, List<String>> WORD_LISTS = new HashMap<>();
    private static final Map<String, List<String>> TEXT_LISTS = new HashMap<>();

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

    private static String mode() {
        String mode = SRS.settings().translationMode;
        return mode == null ? "entries" : mode.toLowerCase();
    }

    private static boolean isMultiLanguage(String mode) {
        return mode.equals("entriesall") || mode.startsWith("words") && !mode.equals("words");
    }

    /** Randomizes every loaded file language. */
    public static void randomizeAll() {
        if (!SRS.settings().translations) return;
        try {
            List<Language> languages = Reflect.get(Localization.class, null, "languages");
            List<FileLanguage> files = new ArrayList<>();
            for (Language language : languages) {
                if (language instanceof FileLanguage) files.add((FileLanguage) language);
            }
            String mode = mode();
            if (isMultiLanguage(mode)) {
                for (FileLanguage language : files) snapshot(language);
            }
            for (FileLanguage language : files) randomize(language);
            List<String> lines = Spoiler.section("15 - Traducoes");
            lines.add("Modo '" + SRS.settings().translationMode + "' aplicado a " + files.size() + " idiomas.");
            if (mode.equals("words2") || mode.equals("words3")) {
                for (FileLanguage language : files) lines.add(language.stringID + ": palavras de " + String.join(", ", sourceLanguages(language.stringID, mode)));
            }
            if (isMultiLanguage(mode)) refreshFont();
        } catch (Exception e) {
            SRS.warn("Translation randomization failed: " + e);
        }
    }

    /** Called after a single language (re)loads its files. */
    public static void randomize(FileLanguage language) {
        if (!SRS.settings().translations || !SRS.settingsApplied()) return;
        try {
            Map<String, TranslationCategory> categories = categories(language);
            if (categories == null) return;
            String mode = mode();
            boolean multi = isMultiLanguage(mode);
            // A reload brings back the language's own texts: they become its source again
            if (multi) snapshot(language);
            List<String> sources = multi ? sourceLanguages(language.stringID, mode) : Collections.<String>emptyList();
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
                if (mode.equals("words")) {
                    shuffleWords(map, key);
                } else if (mode.equals("entriesall")) {
                    entriesFromLanguages(map, category.name, sources, key);
                } else if (multi) {
                    wordsFromLanguages(map, category.name, sources, key);
                } else {
                    shuffleEntries(map, key);
                }
            }
        } catch (Exception e) {
            SRS.warn("Could not randomize language " + language.stringID + ": " + e);
        }
    }

    private static Map<String, TranslationCategory> categories(FileLanguage language) {
        Translation translation = Reflect.get(FileLanguage.class, language, "translation");
        return Reflect.get(Translation.class, translation, "categories");
    }

    // ===== Single language =====

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
        replaceWords(map, keys, words);
    }

    /** Writes the given words, in order, over the words of the texts (placeholders and punctuation stay). */
    private static void replaceWords(Map<String, AbstractTranslationElement> map, List<String> keys, List<String> words) {
        int next = 0;
        for (String k : keys) {
            AbstractTranslationElement el = map.get(k);
            if (el == null || el.translation == null) continue;
            Matcher m = WORD.matcher(el.translation);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(el.translation, last, m.start());
                if (m.group().startsWith("<") || next >= words.size()) {
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

    // ===== Several languages =====

    /** Keeps a copy of a language's texts, read by the multi-language modes of every language. */
    private static void snapshot(FileLanguage language) {
        Map<String, TranslationCategory> categories = categories(language);
        if (categories == null) return;
        Map<String, Map<String, String>> copy = new HashMap<>();
        for (TranslationCategory category : categories.values()) {
            Map<String, AbstractTranslationElement> map = Reflect.get(TranslationCategory.class, category, "translations");
            Map<String, String> texts = new HashMap<>();
            for (Map.Entry<String, AbstractTranslationElement> e : map.entrySet()) {
                if (e.getValue() != null && e.getValue().translation != null) texts.put(e.getKey(), e.getValue().translation);
            }
            copy.put(category.name, texts);
        }
        synchronized (SOURCES) {
            SOURCES.put(language.stringID, copy);
            String prefix = language.stringID + "|";
            WORD_LISTS.keySet().removeIf(k -> k.startsWith(prefix));
            TEXT_LISTS.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** The language itself plus the others its words/texts come from (sorted IDs, picked by the seed for words2/words3). */
    private static List<String> sourceLanguages(String language, String mode) {
        List<String> others;
        synchronized (SOURCES) {
            others = new ArrayList<>(SOURCES.keySet());
        }
        others.remove(language);
        int extra = mode.equals("words2") ? 1 : mode.equals("words3") ? 2 : others.size();
        List<String> out = new ArrayList<>();
        out.add(language);
        out.addAll(SeedRandom.shuffled("translations:languages:" + language, others).subList(0, Math.min(extra, others.size())));
        return out;
    }

    /** Sorted words of a category in a language's original texts. */
    private static List<String> words(String language, String category) {
        synchronized (SOURCES) {
            String cacheKey = language + "|" + category;
            List<String> words = WORD_LISTS.get(cacheKey);
            if (words == null) {
                words = new ArrayList<>();
                Map<String, String> texts = SOURCES.containsKey(language) ? SOURCES.get(language).get(category) : null;
                if (texts != null) {
                    for (String k : new TreeSet<>(texts.keySet())) {
                        Matcher m = WORD.matcher(texts.get(k));
                        while (m.find()) {
                            if (!m.group().startsWith("<")) words.add(m.group());
                        }
                    }
                }
                WORD_LISTS.put(cacheKey, words);
            }
            return words;
        }
    }

    /** Sorted texts of a category in a language's original texts that use exactly these placeholders. */
    private static List<String> texts(String language, String category, String signature) {
        synchronized (SOURCES) {
            String cacheKey = language + "|" + category + "|" + signature;
            List<String> list = TEXT_LISTS.get(cacheKey);
            if (list == null) {
                list = new ArrayList<>();
                Map<String, String> texts = SOURCES.containsKey(language) ? SOURCES.get(language).get(category) : null;
                if (texts != null) {
                    for (String k : new TreeSet<>(texts.keySet())) {
                        if (signature(texts.get(k)).equals(signature)) list.add(texts.get(k));
                    }
                }
                TEXT_LISTS.put(cacheKey, list);
            }
            return list;
        }
    }

    /** entriesall: each text becomes a text with the same placeholders from a random source language. */
    private static void entriesFromLanguages(Map<String, AbstractTranslationElement> map, String category, List<String> sources, String key) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        Random random = SeedRandom.rng(key + ":all");
        for (String k : keys) {
            AbstractTranslationElement el = map.get(k);
            if (el == null || el.translation == null) continue;
            String signature = signature(el.translation);
            String language = sources.get(random.nextInt(sources.size()));
            List<String> pool = texts(language, category, signature);
            if (pool.isEmpty()) pool = texts(sources.get(0), category, signature);
            if (!pool.isEmpty()) map.put(k, new TranslationElement(pool.get(random.nextInt(pool.size()))));
        }
    }

    /** words2/words3/wordsall: each word becomes a word of the same category from a random source language. */
    private static void wordsFromLanguages(Map<String, AbstractTranslationElement> map, String category, List<String> sources, String key) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        int slots = 0;
        for (String k : keys) {
            AbstractTranslationElement el = map.get(k);
            if (el == null || el.translation == null) continue;
            Matcher m = WORD.matcher(el.translation);
            while (m.find()) {
                if (!m.group().startsWith("<")) slots++;
            }
        }
        Random random = SeedRandom.rng(key + ":langs");
        List<String> chosen = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            List<String> pool = words(sources.get(random.nextInt(sources.size())), category);
            if (pool.isEmpty()) pool = words(sources.get(0), category);
            if (pool.isEmpty()) return;
            chosen.add(pool.get(random.nextInt(pool.size())));
        }
        replaceWords(map, keys, chosen);
    }

    /**
     * Texts from other alphabets need their glyphs in the font. Fonts load before the shuffle, so the current language's
     * new characters are added here. They go in the font's extra characters because the game never adds English's own.
     */
    private static void refreshFont() {
        try {
            Language current = Localization.getCurrentLang();
            if (!(current instanceof FileLanguage)) return;
            Set<Integer> known = new HashSet<>();
            FontManager.additionalFontCharacters.codePoints().forEach(known::add);
            StringBuilder extra = new StringBuilder(FontManager.additionalFontCharacters);
            ((FileLanguage) current).getCharactersUsed().codePoints().forEach(c -> {
                if (c > 127 && known.add(c)) extra.appendCodePoint(c);
            });
            FontManager.additionalFontCharacters = extra.toString();
            if (FontManager.isLoaded()) FontManager.updateFont(current);
        } catch (Throwable e) {
            SRS.warn("Could not update the font for the mixed translations: " + e);
        }
    }

    private static String signature(String text) {
        TreeSet<String> set = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) set.add(m.group());
        return String.join("", set);
    }

}
