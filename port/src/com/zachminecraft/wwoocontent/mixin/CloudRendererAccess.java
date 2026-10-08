package com.zachminecraft.wwoocontent.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface CloudRendererAccess {
    @Accessor(value="field_4107", remap=false)
    void wwoo$regenerateClouds(boolean regenerate);
}
