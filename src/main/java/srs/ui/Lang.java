package srs.ui;

import necesse.engine.localization.Language;
import necesse.engine.localization.Localization;

/**
 * Picks the settings screen's text for the game's current language:
 * Brazilian Portuguese when the game is in Portuguese, English for every other language.
 */
public final class Lang {

    private Lang() {
    }

    public static boolean portuguese() {
        Language current = Localization.getCurrentLang();
        return current != null && current.stringID != null && current.stringID.toLowerCase().startsWith("pt");
    }

    /** The Portuguese or the English text, for the current language. */
    public static String t(String portuguese, String english) {
        return portuguese() ? portuguese : english;
    }

    /** {key, Portuguese, English} rows turned into {key, text} pairs for the current language. */
    public static String[][] pairs(String[][] rows) {
        int column = portuguese() ? 1 : 2;
        String[][] out = new String[rows.length][];
        for (int i = 0; i < rows.length; i++) out[i] = new String[]{rows[i][0], rows[i][column]};
        return out;
    }

}
