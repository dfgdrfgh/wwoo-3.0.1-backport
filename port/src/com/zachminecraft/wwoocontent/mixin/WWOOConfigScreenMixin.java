package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.zachminecraft.wwoocontent.BiomeSizeConfig;
import com.zachminecraft.wwoocontent.BiomeSizeScreen;
import de.cristelknight.cristellib.config.client.extension.ConfigScreenExtension;
import de.cristelknight.cristellib.config.client.simple.ClientConfigRegistry;
import de.cristelknight.cristellib.config.client.simple.SimpleConfigScreen;
import de.cristelknight.wwoo.config.WWOOConfig;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import java.util.ArrayList;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Give WWOO's setting a readable label while retaining its saved key. */
@Mixin(targets = "de.cristelknight.cristellib.config.client.extension.extensions.SimpleConfigExtension", remap = false)
public abstract class WWOOConfigScreenMixin {
    @Shadow private <T> void updateFieldValue(Class<T> config, String fieldName, Object value) {
        throw new AssertionError();
    }

    @Shadow private <T> void addConfigToCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
            SimpleConfigScreen screen) {
        throw new AssertionError();
    }

    @Inject(method = "addToBuilder", at = @At("HEAD"), cancellable = true, remap = false)
    private void wwoo$orderedSections(ConfigBuilder builder, ConfigEntryBuilder entries, CallbackInfo ci) {
        if (!((ConfigScreenExtension) (Object) this).modId.equals("wwoo")) return;

        builder.setTitle(Component.literal("WWOO Config"));
        builder.setAlwaysShowTabs(false);

        // Cristel Lib stores screens in an unordered set. Sort the display only,
        // retaining the separate config files and normal save callbacks.
        var screens = new ArrayList<>(ClientConfigRegistry.getScreens("wwoo"));
        screens.sort(Comparator.comparingInt(screen -> {
            if (screen.simpleConfig() == BiomeSizeConfig.class) return 0;
            if (screen.simpleConfig() == WWOOConfig.class) return 1;
            return 2;
        }));
        for (SimpleConfigScreen screen : screens) {
            addConfigToCategory(builder, entries, screen);
        }
        ci.cancel();
    }

    @Inject(method = "addConfigToCategory", at = @At("HEAD"), cancellable = true, remap = false)
    private void wwoo$biomeSection(ConfigBuilder builder, ConfigEntryBuilder entries,
            SimpleConfigScreen screen, CallbackInfo ci) {
        if (screen.simpleConfig() == BiomeSizeConfig.class) {
            BiomeSizeScreen.add(builder, entries, ConfigRegistry.get(BiomeSizeConfig.class),
                (field, value) -> updateFieldValue(BiomeSizeConfig.class, field, value));
            ci.cancel();
        }
    }

    @ModifyArg(
        method = "addField",
        at = @At(value = "INVOKE", target = "Lde/cristelknight/cristellib/config/client/simple/custom/ConfigFieldFactory;create(Lme/shedaniel/clothconfig2/api/ConfigEntryBuilder;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Lme/shedaniel/clothconfig2/impl/builders/AbstractFieldBuilder;", remap = false),
        index = 1,
        remap = false
    )
    private String wwoo$materialPatchLabel(String name, @Local(argsOnly = true, ordinal = 0) Class<?> config) {
        if (config.getName().equals("de.cristelknight.wwoo.config.WWOOConfig") && name.equals("removeOres")) {
            return "Remove Underground Patches";
        }
        if (config.getName().equals("com.zachminecraft.wwoocontent.BiomeSizeConfig") && name.equals("sizeMultiplier")) {
            return "Biome Size Multiplier";
        }
        return name;
    }
}
