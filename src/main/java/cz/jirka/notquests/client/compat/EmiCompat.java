package cz.jirka.notquests.client.compat;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;

/** Only loaded when EMI is installed. */
final class EmiCompat {
    private EmiCompat() {
    }

    /**
     * Opens the recipes (or uses) of the item. When EMI has nothing for that view the other one is shown,
     * so clicking a log (no recipe makes it) still opens something useful.
     */
    static boolean show(ItemStack stack, boolean uses) {
        EmiStack emiStack = EmiStack.of(stack);
        EmiRecipeManager recipes = EmiApi.getRecipeManager();
        boolean hasRecipes = !recipes.getRecipesByOutput(emiStack).isEmpty();
        boolean hasUses = !recipes.getRecipesByInput(emiStack).isEmpty();
        if (!hasRecipes && !hasUses) {
            return false;
        }
        if (uses && hasUses || !hasRecipes) {
            EmiApi.displayUses(emiStack);
        } else {
            EmiApi.displayRecipes(emiStack);
        }
        return true;
    }
}
