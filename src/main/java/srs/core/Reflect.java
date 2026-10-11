package srs.core;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Small reflection helpers for reading and writing game fields that aren't public (or are final).
 */
public final class Reflect {

    private static final Map<String, Field> cache = new HashMap<>();

    private Reflect() {
    }

    public static synchronized Field field(Class<?> owner, String name) {
        String key = owner.getName() + "#" + name;
        Field f = cache.get(key);
        if (f == null) {
            Class<?> c = owner;
            while (c != null && f == null) {
                try {
                    f = c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            if (f == null) throw new IllegalArgumentException("No field " + name + " in " + owner.getName());
            f.setAccessible(true);
            cache.put(key, f);
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String name) {
        try {
            return (T) field(target.getClass(), name).get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<?> owner, Object target, String name) {
        try {
            return (T) field(owner, name).get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void set(Object target, String name, Object value) {
        try {
            field(target.getClass(), name).set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void set(Class<?> owner, Object target, String name, Object value) {
        try {
            field(owner, name).set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
