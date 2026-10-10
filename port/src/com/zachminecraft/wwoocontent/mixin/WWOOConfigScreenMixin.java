package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.zachminecraft.wwoocontent.BiomeSizeConfig;
import com.zachminecraft.wwoocontent.BiomeSizeScreen;
import de.cristelknight.cristellib.config.client.extension.ConfigScreenExtension;
import de.cristelknight.cristellib.config.client.simple.SimpleConfigScreen;
import de.cristelknight.cristellib.config.simple.ConfigRegistry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(method = "addToBuilder", at = @At("HEAD"), remap = false)
    private void wwoo$title(ConfigBuilder builder, ConfigEntryBuilder entries, CallbackInfo ci) {
        if (((ConfigScreenExtension) (Object) this).modId.equals("wwoo")) {
            builder.setTitle(Component.literal("WWOO Config"));
        }
    }

    @Inject(method = "addConfigToCategory", at = @At("HEAD"), cancellable = true, remap = false)
    private void wwoo$biomeTab(ConfigBuilder builder, ConfigEntryBuilder entries,
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
