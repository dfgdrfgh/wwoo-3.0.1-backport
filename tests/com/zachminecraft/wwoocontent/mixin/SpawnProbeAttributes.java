package com.zachminecraft.wwoocontent.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attribute registration for the isolated test entities; never shipped in WWOO. */
@Mixin(DefaultAttributes.class)
public abstract class SpawnProbeAttributes {
    @Inject(method="method_26873",at=@At("HEAD"),cancellable=true,remap=false)
    private static void audit$attributes(EntityType<?> type, CallbackInfoReturnable<AttributeSupplier> cir) {
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (id.equals("audit:animal") || id.equals("minecraft:creaking")) cir.setReturnValue(Pig.createAttributes().build());
    }
}
