package srs.core;

/**
 * The rotating swaps (visuals 13c, sounds 16b): the seed makes {@link #VARIANTS} complete sets of swaps, and the one
 * in use follows the world's play time, so everyone in a multiplayer world is on the same set.
 */
public final class Rotation {

    public static final int VARIANTS = 3;
    public static final long MILLIS = 6 * 60 * 1000L;

    private Rotation() {
    }

    /** The variant (0 to VARIANTS - 1) in use at this world play time. */
    public static int variantAt(long worldTime) {
        return (int) ((worldTime / MILLIS) % VARIANTS);
    }

    /** Seed key suffix of a variant: none for the first, so seeds without rotation keep their swaps. */
    public static String keySuffix(int variant) {
        return variant == 0 ? "" : "#" + (variant + 1);
    }

    /** Spoiler section title of a variant. */
    public static String sectionTitle(String number, String name, String columns, boolean rotation, int variant) {
        return number + " - " + name + (rotation ? ", opcao " + (variant + 1) + " de " + VARIANTS : "") + " (" + columns + ")";
    }

}
