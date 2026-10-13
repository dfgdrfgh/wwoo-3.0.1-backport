package com.zachminecraft.wwoocontent.mixin;

import com.zachminecraft.wwoocontent.CloudOpacity;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Wrap only Cirrus's cloud shader resources at construction, without overriding other packs/shaders. */
@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram",remap=false)
public abstract class CirrusShaderProgramMixin {
    @ModifyArgs(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/class_5944;<init>(Lnet/minecraft/class_5912;Ljava/lang/String;Lnet/minecraft/class_293;)V",remap=false),remap=false)
    private static void wwoo$cirrusShaderResources(Args args) {
        if("cirrus:cirrus_clouds".equals(args.get(1)))args.set(0,CloudOpacity.resources((ResourceProvider)args.get(0)));
    }
}
