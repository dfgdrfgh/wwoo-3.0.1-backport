package com.zachminecraft.wwoocontent.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Give WWOO's setting a readable label while retaining its saved key. */
@Mixin(targets = "de.cristelknight.cristellib.config.client.extension.extensions.SimpleConfigExtension", remap = false)
public abstract class WWOOConfigScreenMixin {
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
        return name;
    }
}
