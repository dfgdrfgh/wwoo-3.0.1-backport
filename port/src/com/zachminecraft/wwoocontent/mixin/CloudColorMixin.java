package com.zachminecraft.wwoocontent.mixin;

import com.zachminecraft.wwoocontent.CloudAttributes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class CloudColorMixin {
    // Explicit 1.21.1 intermediary selectors: no annotation processor/refmap required.
    @Inject(method="method_23785", at=@At("RETURN"), cancellable=true, remap=false)
    private void wwoo$cloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        var color = CloudAttributes.color();
        var rgb = cir.getReturnValue();
        cir.setReturnValue(new Vec3(rgb.x * color.red(), rgb.y * color.green(), rgb.z * color.blue()));
    }
}

