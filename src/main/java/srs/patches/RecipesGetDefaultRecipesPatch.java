package srs.patches;

import java.util.ArrayList;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import net.bytebuddy.asm.Advice;
import srs.random.RecipeRandomizer;

/** Feature 3: shuffles ingredients before the game indexes its recipe list. */
@ModMethodPatch(target = Recipes.class, name = "getDefaultRecipes", arguments = {})
public class RecipesGetDefaultRecipesPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.Return ArrayList<Recipe> recipes) {
        RecipeRandomizer.randomize(recipes);
    }

}
