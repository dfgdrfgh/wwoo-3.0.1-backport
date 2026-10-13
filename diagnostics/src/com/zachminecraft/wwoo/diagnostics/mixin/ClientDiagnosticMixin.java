package com.zachminecraft.wwoo.diagnostics.mixin;

import com.zachminecraft.wwoo.diagnostics.CloudDiagnostic;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientDiagnosticMixin {
    @Inject(method="method_1574",at=@At("TAIL"),remap=false)
    private void wwoo_diag$tick(CallbackInfo ci) {CloudDiagnostic.tick((Minecraft)(Object)this);}
}
