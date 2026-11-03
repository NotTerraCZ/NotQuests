package cz.jirka.notquests.client.compat;

import cz.jirka.notquests.NotQuests;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** JEI plugin (found by JEI itself) that keeps the runtime so the quest book can open recipe screens. */
@JeiPlugin
public final class JeiCompat implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(NotQuests.MOD_ID, "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /**
     * Opens the recipes (or uses) of the item. When JEI has nothing for that view the other one is shown,
     * so clicking a log (no recipe makes it) still opens something useful.
     */
    static boolean show(ItemStack stack, boolean uses) {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return false;
        }
        IFocusFactory focuses = rt.getJeiHelpers().getFocusFactory();
        RecipeIngredientRole first = uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
        RecipeIngredientRole second = uses ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
        for (RecipeIngredientRole role : new RecipeIngredientRole[]{first, second}) {
            IFocus<ItemStack> focus = focuses.createFocus(role, VanillaTypes.ITEM_STACK, stack);
            if (rt.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().findAny().isPresent()) {
                rt.getRecipesGui().show(focus);
                return true;
            }
        }
        return false;
    }
}
