package srs.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.recipe.Recipes;
import net.bytebuddy.asm.Advice;
import srs.SuperRandomizerShow;

/** Recipes are the last thing the game builds while loading, so this is where the spoiler gets written. */
@ModMethodPatch(target = Recipes.class, name = "closeModRecipeRegistry", arguments = {})
public class RecipesCloseRegistryPatch {

    @Advice.OnMethodExit
    static void onExit() {
        SuperRandomizerShow.onLoadingFinished();
    }

}
