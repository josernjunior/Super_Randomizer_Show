package srs.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import srs.core.Reflect;
import srs.core.SRS;
import srs.core.SeedRandom;
import srs.core.Spoiler;

/**
 * Feature 3: shuffles ingredient lists between recipes. The result, amount and crafting station of every recipe
 * stay the same, only what it costs changes. By default ingredients only move between recipes of the same station,
 * which keeps progression roughly intact. The shuffle level (1% to 100%) is the share of recipes that take part.
 * The free revival potion option empties that recipe's ingredients first, so the shuffle leaves it out.
 */
public final class RecipeRandomizer {

    public static final String REVIVAL_POTION = "revivalpotion";

    private static final Map<Recipe, Ingredient[]> originalModIngredients = new IdentityHashMap<>();

    private RecipeRandomizer() {
    }

    /** Called with the list returned by Recipes.getDefaultRecipes(), right before the game indexes it. */
    public static void randomize(List<Recipe> defaultRecipes) {
        if (defaultRecipes == null) return;
        if (SRS.settings().recipesFreeRevivalPotion) makeFree(defaultRecipes, REVIVAL_POTION);
        if (SRS.settings().recipes) shuffle(defaultRecipes);
    }

    /** Every vanilla recipe for the item needs no ingredients (an empty array always counts as craftable). */
    private static void makeFree(List<Recipe> defaultRecipes, String itemStringID) {
        List<String> lines = Spoiler.section("3 - Receitas gratis");
        try {
            for (Recipe r : defaultRecipes) {
                if (!r.resultStringID.equals(itemStringID)) continue;
                lines.add(r.resultStringID + "@" + (r.tech == null ? "none" : r.tech.getStringID()) + ": sem ingredientes (antes: "
                        + describe(r.ingredients) + ")");
                Reflect.set(Recipe.class, r, "ingredients", new Ingredient[0]);
            }
        } catch (Exception e) {
            SRS.warn("Could not make " + itemStringID + " free: " + e);
        }
    }

    private static void shuffle(List<Recipe> defaultRecipes) {
        try {
            List<Recipe> all = new ArrayList<>(defaultRecipes);
            List<Recipe> modRecipes = Reflect.get(Recipes.class, null, "modRecipes");
            for (Recipe r : modRecipes) {
                originalModIngredients.computeIfAbsent(r, k -> k.ingredients);
                Reflect.set(Recipe.class, r, "ingredients", originalModIngredients.get(r));
                all.add(r);
            }

            // Stable key for every recipe: result + station + occurrence index
            Map<String, Integer> seen = new HashMap<>();
            Map<String, List<Keyed>> groups = new HashMap<>();
            for (Recipe r : all) {
                if (r.ingredients == null || r.ingredients.length == 0 || SRS.isItemExcluded(r.resultStringID)) continue;
                String tech = r.tech == null ? "none" : r.tech.getStringID();
                String base = r.resultStringID + "@" + tech;
                int index = seen.merge(base, 1, Integer::sum);
                String group = SRS.settings().recipesSameStation ? tech : "all";
                groups.computeIfAbsent(group, k -> new ArrayList<>()).add(new Keyed(base + "#" + index, r));
            }

            float level = Math.max(0.01F, Math.min(1.0F, SRS.settings().recipesShuffleLevel));
            List<String> lines = Spoiler.section("3 - Receitas (resultado @ estacao: novos ingredientes)");
            lines.add("Nivel de embaralhamento: " + Math.round(level * 100) + "% das receitas trocam de ingredientes.");
            List<String> groupNames = new ArrayList<>(groups.keySet());
            Collections.sort(groupNames);
            for (String group : groupNames) {
                List<Keyed> recipes = choose(groups.get(group), group, level);
                Collections.sort(recipes, (a, b) -> a.key.compareTo(b.key));
                List<Ingredient[]> pool = new ArrayList<>();
                for (Keyed k : recipes) pool.add(k.recipe.ingredients);
                Random random = SeedRandom.rng("recipes:" + group);
                Collections.shuffle(pool, random);
                fixSelfReferences(recipes, pool, random);
                for (int i = 0; i < recipes.size(); i++) {
                    Recipe r = recipes.get(i).recipe;
                    Reflect.set(Recipe.class, r, "ingredients", pool.get(i));
                    lines.add(recipes.get(i).key + ": " + describe(pool.get(i)));
                }
            }
        } catch (Exception e) {
            SRS.warn("Recipe randomization failed: " + e);
            e.printStackTrace();
        }
    }

    /** The share of a group's recipes (by shuffle level) that trade ingredients; the rest keep their own. */
    private static List<Keyed> choose(List<Keyed> recipes, String group, float level) {
        List<Keyed> out = new ArrayList<>(recipes);
        if (level >= 1.0F) return out;
        Collections.sort(out, (a, b) -> a.key.compareTo(b.key));
        Collections.shuffle(out, SeedRandom.rng("recipes:level:" + group));
        return new ArrayList<>(out.subList(0, Math.round(out.size() * level)));
    }

    /** Makes sure no recipe ends up needing its own result as an ingredient. */
    private static void fixSelfReferences(List<Keyed> recipes, List<Ingredient[]> pool, Random random) {
        int n = recipes.size();
        for (int i = 0; i < n; i++) {
            if (!uses(pool.get(i), recipes.get(i).recipe.resultStringID)) continue;
            for (int attempt = 0; attempt < n * 2; attempt++) {
                int j = random.nextInt(n);
                if (j == i) continue;
                if (!uses(pool.get(j), recipes.get(i).recipe.resultStringID) && !uses(pool.get(i), recipes.get(j).recipe.resultStringID)) {
                    Ingredient[] t = pool.get(i);
                    pool.set(i, pool.get(j));
                    pool.set(j, t);
                    break;
                }
            }
        }
    }

    private static boolean uses(Ingredient[] ingredients, String itemStringID) {
        for (Ingredient in : ingredients) {
            if (in.ingredientStringID.equals(itemStringID)) return true;
        }
        return false;
    }

    private static String describe(Ingredient[] ingredients) {
        StringBuilder sb = new StringBuilder();
        for (Ingredient in : ingredients) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(in.getIngredientAmount()).append("x ").append(in.ingredientStringID);
        }
        return sb.toString();
    }

    private static class Keyed {
        final String key;
        final Recipe recipe;

        Keyed(String key, Recipe recipe) {
            this.key = key;
            this.recipe = recipe;
        }
    }

}
