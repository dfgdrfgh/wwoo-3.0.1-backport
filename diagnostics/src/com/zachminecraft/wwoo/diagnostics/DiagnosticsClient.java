package com.zachminecraft.wwoo.diagnostics;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/** Standard Fabric controls, independent of optional renderer mixins. */
public final class DiagnosticsClient implements ClientModInitializer {
    private static KeyMapping captureKey;
    @Override public void onInitializeClient() {
        if(captureKey!=null)return;
        captureKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.wwoo_cloud_diagnostics.capture",
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.wwoo_cloud_diagnostics"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            consumeClicks(captureKey,client.level!=null&&client.screen==null,()->{
                String message=CloudDiagnostic.toggle(client.gameDirectory.toPath());
                if(client.gui!=null)client.gui.getChat().addMessage(Component.literal("[Cloud diagnostic] "+message));
            });
            CloudDiagnostic.tick(client);
        });
        HudRenderCallback.EVENT.register((graphics,delta)->CloudDiagnostic.drawHud(graphics));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->registerCommands(dispatcher));
        LoggerFactory.getLogger("WWOO-Cloud-Diagnostic").info("Cloud diagnostic 0.3 loaded: Controls > WWOO Cloud Diagnostics, or /wwoocloud capture");
    }
    public static KeyMapping captureKey() {return captureKey;}
    public static void consumeClicks(KeyMapping mapping,boolean inGame,Runnable capture) {
        while(mapping.consumeClick())if(inGame)capture.run();
    }
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("wwoocloud")
            .executes(context->{context.getSource().sendFeedback(Component.literal(CloudDiagnostic.status()));return 1;})
            .then(ClientCommandManager.literal("status").executes(context->{
                context.getSource().sendFeedback(Component.literal(CloudDiagnostic.status()));return 1;
            }))
            .then(ClientCommandManager.literal("capture").executes(context->{
                Minecraft client=context.getSource().getClient();
                if(client.level==null) {
                    context.getSource().sendError(Component.literal("Join a world before capturing clouds."));return 0;
                }
                context.getSource().sendFeedback(Component.literal(CloudDiagnostic.toggle(client.gameDirectory.toPath())));return 1;
            })));
    }
}
