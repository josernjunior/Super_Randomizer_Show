package srs.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic randomness derived from the configured seed.
 * Every decision is keyed by a string (feature name + stable string IDs), never by registry numeric IDs or
 * iteration order, so the same seed gives the same result on every machine with the same game/mod content.
 */
public final class SeedRandom {

    private SeedRandom() {
    }

    public static long mix(long seed, String key) {
        long z = seed ^ ((long) key.hashCode() * 0x9E3779B97F4A7C15L) ^ ((long) key.length() << 32);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static Random rng(String key) {
        return new Random(mix(SRS.seed(), key));
    }

    /** A fixed value in [min, max] for this key. */
    public static float range(String key, float min, float max) {
        if (max < min) {
            float t = min;
            min = max;
            max = t;
        }
        return min + (max - min) * rng(key).nextFloat();
    }

    /**
     * Shuffles the given keys and returns key -> replacement. Input order does not matter, it is sorted first.
     */
    public static <T extends Comparable<T>> Map<T, T> permutation(String key, List<T> keys) {
        List<T> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        List<T> shuffled = new ArrayList<>(sorted);
        Collections.shuffle(shuffled, rng(key));
        Map<T, T> out = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            out.put(sorted.get(i), shuffled.get(i));
        }
        return out;
    }

    /** Sorted copy shuffled with this key. */
    public static <T extends Comparable<T>> List<T> shuffled(String key, List<T> values) {
        List<T> out = new ArrayList<>(values);
        Collections.sort(out);
        Collections.shuffle(out, rng(key));
        return out;
    }

}
