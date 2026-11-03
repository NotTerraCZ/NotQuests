package cz.jirka.notquests.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Function;

/** Small hooks the client fills in, so common quest code can show client-only information. */
public final class SidedHooks {
    public record AdvancementDisplay(Component title, ItemStack icon) {
    }

    public static Function<ResourceLocation, Optional<AdvancementDisplay>> advancementDisplay = rl -> Optional.empty();

    /** Selected game language, e.g. "en_us" or "cs_cz" (the server always uses English). */
    public static java.util.function.Supplier<String> languageCode = () -> "en_us";

    private SidedHooks() {
    }
}
