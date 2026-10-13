package com.zachminecraft.wwoo.diagnostics.mixin;

import com.zachminecraft.wwoo.diagnostics.CloudDiagnostic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiDiagnosticMixin {
    @Inject(method="method_1753",at=@At("TAIL"),remap=false)
    private void wwoo_diag$hud(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci) {CloudDiagnostic.drawHud(graphics);}
}
