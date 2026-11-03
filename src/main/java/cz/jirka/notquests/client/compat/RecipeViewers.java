package cz.jirka.notquests.client.compat;

import com.mojang.logging.LogUtils;
import cz.jirka.notquests.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Optional EMI / JEI support: opens the recipes or uses of an item from the quest book.
 * The compat classes are only touched when the mod is installed, so nothing breaks without them.
 */
public final class RecipeViewers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Boolean emi;
    private static Boolean jei;

    // EMI's recipe screen can only sit on top of an inventory-like screen: it opens the player's inventory
    // under itself and goes back there when closed. These bring the player back to the quest book instead.
    private static boolean capturing;
    private static Screen emiParent;
    private static Screen returnTo;

    private RecipeViewers() {
    }

    private static boolean emi() {
        if (emi == null) {
            emi = ModList.get().isLoaded("emi");
        }
        return emi;
    }

    private static boolean jei() {
        if (jei == null) {
            jei = ModList.get().isLoaded("jei");
        }
        return jei;
    }

    /** True when EMI or JEI is installed. */
    public static boolean available() {
        return emi() || jei();
    }

    /**
     * Shows the recipes of the item (or, with {@code uses}, what it is used in); when there is nothing for that
     * view, the other one. EMI wins when both are installed, because it then provides the recipe screens.
     * Returns false when nothing could be shown.
     */
    public static boolean show(ItemStack stack, boolean uses) {
        if (stack.isEmpty() || ItemUtil.isMissing(stack)) {
            return false;
        }
        try {
            if (emi()) {
                return showEmi(stack, uses);
            }
            if (jei()) {
                return JeiCompat.show(stack, uses);
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not open the recipe viewer for {}", stack, t);
        }
        return false;
    }

    private static boolean showEmi(ItemStack stack, boolean uses) {
        Minecraft mc = Minecraft.getInstance();
        Screen before = mc.screen;
        emiParent = null;
        returnTo = null;
        boolean shown;
        capturing = true;
        try {
            shown = EmiCompat.show(stack, uses);
        } finally {
            capturing = false;
        }
        if (shown && before != null && emiParent != null && mc.screen != before && isEmiScreen(mc.screen)) {
            returnTo = before;
        } else {
            emiParent = null;
        }
        return shown;
    }

    private static boolean isEmiScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("dev.emi.");
    }

    /** Registered on the game event bus by the client setup. */
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (capturing) {
            // the inventory EMI opens under its recipe screen (in creative it becomes the creative inventory)
            if (next instanceof AbstractContainerScreen<?>) {
                emiParent = next;
            }
            return;
        }
        if (returnTo == null) {
            return;
        }
        if (next == emiParent) {
            // EMI was closed: back to the quest book - unless a recipe was just moved into the crafting grid with a click
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                event.setNewScreen(returnTo);
            }
            returnTo = null;
            emiParent = null;
        } else if (!isEmiScreen(next)) {
            returnTo = null;
            emiParent = null;
        }
    }
}
