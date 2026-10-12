package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import necesse.engine.modifiers.Modifier;
import necesse.engine.modifiers.ModifierContainer;
import necesse.engine.modifiers.ModifierList;
import necesse.engine.registries.EnchantmentRegistry;
import necesse.inventory.enchants.ItemEnchantment;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 12 (scrolls): every enchantment (the effect an enchanting scroll or the enchanting table puts on an item)
 * gets a fixed multiplier from the seed, applied to how far each of its modifiers is from the default
 * (+10% damage with x1.5 becomes +15%). Enchantments live in the registry, so tooltips and items already enchanted
 * follow along, on the server and on every client.
 */
public final class EnchantmentRandomizer {

    private static int changed;

    private EnchantmentRandomizer() {
    }

    /** How many enchantments had values changed (used by the self-test). */
    public static int changedCount() {
        return changed;
    }

    public static void build() {
        SRSSettings s = SRS.settings();
        changed = 0;
        if (!s.enchantScrolls) return;
        List<ItemEnchantment> enchantments = new ArrayList<>(EnchantmentRegistry.getEnchantments());
        Collections.sort(enchantments, (a, b) -> a.getStringID().compareTo(b.getStringID()));
        List<String> lines = Spoiler.section("12 - Pergaminhos: encantamentos (encantamento: multiplicador, modificadores)");
        for (ItemEnchantment enchantment : enchantments) {
            String id = enchantment.getStringID();
            if (id == null) continue;
            float factor = SeedRandom.range("enchants:" + id, s.enchantScrollsMin, s.enchantScrollsMax);
            List<String> values = scale(enchantment, factor);
            if (!values.isEmpty()) {
                changed++;
                lines.add(id + ": x" + String.format(Locale.ROOT, "%.2f", factor) + " (" + String.join(", ", values) + ")");
            }
        }
    }

    /** Scales every numeric modifier away from its default. Returns "modifier old->new" for each change. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> scale(ModifierContainer container, float factor) {
        List<String> out = new ArrayList<>();
        ModifierList list = Reflect.get(ModifierContainer.class, container, "list");
        for (Modifier modifier : list) {
            Object value = container.getModifier(modifier);
            Object def = modifier.defaultBuffValue;
            if (value == null || value.equals(def)) continue;
            Object scaled;
            if (value instanceof Float && def instanceof Float) {
                float d = (Float) def;
                scaled = d + ((Float) value - d) * factor;
            } else if (value instanceof Integer && def instanceof Integer) {
                int d = (Integer) def;
                scaled = d + Math.round(((Integer) value - d) * factor);
            } else {
                continue;
            }
            container.setModifier(modifier, scaled);
            out.add((modifier.stringID == null ? "#" + modifier.index : modifier.stringID) + " " + format(value) + "->" + format(scaled));
        }
        return out;
    }

    private static String format(Object value) {
        return value instanceof Float ? String.format(Locale.ROOT, "%.3f", (Float) value) : String.valueOf(value);
    }

}
