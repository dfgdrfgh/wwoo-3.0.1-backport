package com.zachminecraft.wwoocontent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import java.lang.reflect.Modifier;
import java.io.File;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL14;
import com.google.gson.JsonParser;
import com.zachminecraft.wwoo.diagnostics.CloudDiagnostic;
import com.zachminecraft.wwoo.diagnostics.DiagnosticsClient;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Checks the actual transformed Cirrus renderer and its shader with the pack's layer/time settings. */
public final class CirrusCloudRenderProbe implements PreLaunchEntrypoint {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Object unconstructed(Class<?> type) throws Exception {
        var unsafe = Class.forName("sun.misc.Unsafe");
        var field = unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
    }
    private static Object config(Class<?> type,String name) throws Exception {
        Object value=type.getField(name).get(null);
        var getter=value.getClass().getMethod("get");getter.setAccessible(true);
        return getter.invoke(value);
    }
    @Override public void onPreLaunch() {
        try {
            check();
            System.out.println("CIRRUS_CLOUD_RENDER_PASS: exact pack renderer versions/settings, injected tint after Cirrus color, all draw passes share tint, real cloud shader pixels, three constant layer opacities and four time tints");
            System.exit(0);
        } catch(Throwable failure) {
            failure.printStackTrace();System.exit(1);
        }
    }
    private static void check() throws Exception {
        // Polytone's shader processor codecs use built-in registries, as in a normal client startup.
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        // Supply Polytone's config-path context without starting the game loop or replacing any MC classes.
        Object client=unconstructed(Minecraft.class);
        var instance=Arrays.stream(Minecraft.class.getDeclaredFields()).filter(f ->
            f.getType()==Minecraft.class && Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        var directory=Arrays.stream(Minecraft.class.getDeclaredFields()).filter(f ->
            f.getType()==File.class && !Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        directory.setAccessible(true);directory.set(client,Path.of(".").toFile());
        instance.setAccessible(true);instance.set(null,client);
        Class<?> type=Class.forName("com.jvn.cirrus.client.CirrusCloudRenderer");
        var hooks=Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains("wwoo$tintCirrusClouds")
            && m.getParameterCount()==1 && m.getParameterTypes()[0]==Vec3.class).toList();
        require(hooks.size()==1,"Cirrus renderer has exactly one applied tint hook");
        var hook=hooks.getFirst();hook.setAccessible(true);
        var transformed=new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class").resolve(type.getName().replace('.','/')+".class"))).accept(transformed,0);
        var render=transformed.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
        int originals=0,tints=0,draws=0,colorSlot=-1;boolean tintPending=false;
        for(var insn:render.instructions) {
            if(insn instanceof MethodInsnNode call) {
                if(call.owner.equals("com/jvn/cirrus/client/CirrusCloudTint") && call.name.equals("color"))originals++;
                if(call.name.contains("wwoo$tintCirrusClouds")) {
                    require(originals==1 && draws==0,"biome tint follows Cirrus's final weather/dimension/time tint");
                    tints++;tintPending=true;
                }
                if(call.name.equals("drawLayer")) {
                    require(tints==1 && colorSlot>=0,"every layer/pass uses the shared biome tint");
                    boolean shared=false;
                    for(var prior=call.getPrevious();prior!=null;prior=prior.getPrevious()) {
                        if(prior instanceof VarInsnNode v && v.getOpcode()==Opcodes.ALOAD && v.var==colorSlot) {shared=true;break;}
                        if(prior instanceof MethodInsnNode m && m.name.equals("drawLayer"))break;
                    }
                    require(shared,"layer receives the tinted color local");draws++;
                }
            }
            if(tintPending && insn instanceof VarInsnNode v && v.getOpcode()==Opcodes.ASTORE) {
                colorSlot=v.var;tintPending=false;
            }
        }
        require(originals==1 && tints==1 && draws==1,"one shared tint and draw call inside Cirrus's layer loop");
        int drawIndex=-1;boolean layerLoop=false;
        for(var insn:render.instructions) {
            if(insn instanceof MethodInsnNode call && call.name.equals("drawLayer"))drawIndex=render.instructions.indexOf(insn);
            if(drawIndex>=0 && insn instanceof JumpInsnNode jump && jump.getOpcode()==Opcodes.GOTO
                && render.instructions.indexOf(jump.label)<drawIndex)layerLoop=true;
        }
        require(layerLoop,"all enabled layers repeat the same tinted draw call");
        var frame=type.getDeclaredField("wwoo$frameColor");frame.setAccessible(true);
        Object renderer=unconstructed(type);
        Class<?> configType=Class.forName("com.jvn.cirrus.config.CirrusConfig");
        Object spec=configType.getField("SPEC").get(null);
        spec.getClass().getMethod("load",Path.class).invoke(spec,Path.of("config/cirrus-client.json"));
        require((Boolean)config(configType,"CUSTOM_CLOUDS_ENABLED"),"custom clouds enabled as in the pack");
        double[] opacity={((Number)config(configType,"LOWER_LAYER_OPACITY")).doubleValue(),
            ((Number)config(configType,"UPPER_LAYER_OPACITY")).doubleValue(),
            ((Number)config(configType,"TOP_LAYER_OPACITY")).doubleValue()};
        require(Arrays.equals(opacity,new double[]{.25,.5,.15}),"exact modpack layer opacities");
        var timeTint=Class.forName("com.jvn.cirrus.client.CirrusCloudTint").getMethod("timeTint",double.class);
        RenderSystem.initRenderThread();require(GLFW.glfwInit(),"initialize software window system");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS,8);
        long window=GLFW.glfwCreateWindow(32,32,"WWOO Cirrus shader check",0,0);
        require(window!=0,"create OpenGL window");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        // Polytone checks includes through the client resource manager, so use the same actual packs there.
        var gameResources=new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        var pack=new PathPackResources(new PackLocationInfo("cloud-check",Component.literal("Cloud check"),PackSource.BUILT_IN,Optional.empty()),Path.of("shader-resources"));
        var resourceField=Arrays.stream(Minecraft.class.getDeclaredFields()).filter(f ->
            f.getType()==ReloadableResourceManager.class).findFirst().orElseThrow();
        resourceField.setAccessible(true);resourceField.set(client,gameResources);
        gameResources.createReload(Runnable::run,Runnable::run,CompletableFuture.completedFuture(null),List.of(pack)).done().join();
        ResourceProvider resources=gameResources;
        // Fabric uses this subclass to load namespaced Cirrus shaders unchanged.
        try(var shader=(ShaderInstance)Class.forName("net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram")
                .getConstructor(ResourceProvider.class,ResourceLocation.class,VertexFormat.class)
                .newInstance(resources,ResourceLocation.fromNamespaceAndPath("cirrus","cirrus_clouds"),DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)) {
            RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);RenderSystem.setShaderFogColor(0,0,0,1);
            int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            ByteBuffer white=MemoryUtil.memAlloc(4);white.put(new byte[]{-1,-1,-1,-1}).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,white);
            MemoryUtil.memFree(white);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);shader.setSampler("Sampler0",texture);
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            int vbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            FloatBuffer vertices=MemoryUtil.memAllocFloat(36);
            vertices.put(new float[]{-1,-1,0, .5f,.5f, 1,1,1,1, 0,1,0,
                3,-1,0, .5f,.5f, 1,1,1,1, 0,1,0, -1,3,0, .5f,.5f, 1,1,1,1, 0,1,0}).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);MemoryUtil.memFree(vertices);
            // Test the enabled Cirrus shader away from the sun/moon, with stable noon lighting.
            shader.getUniform("CirrusEnabled").set(1f);
            shader.getUniform("CirrusLightViewDirection").set(0f,0f,1f);
            shader.getUniform("CirrusMoonViewDirection").set(0f,0f,-1f);
            RenderSystem.setShaderColor(1,1,1,1);
            shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,new Matrix4f(),new Matrix4f(),null);shader.apply();
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            String[] names={"Position","UV0","Color","Normal"};int[] sizes={3,2,4,3},offsets={0,12,20,36};
            for(int i=0;i<names.length;i++) {
                int attribute=GL20.glGetAttribLocation(program,names[i]);require(attribute>=0,"Cirrus shader attribute "+names[i]);
                GL20.glEnableVertexAttribArray(attribute);GL20.glVertexAttribPointer(attribute,sizes[i],GL11.GL_FLOAT,false,48,offsets[i]);
            }
            GL11.glViewport(0,0,32,32);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
            ByteBuffer pixel=MemoryUtil.memAlloc(4);
            double[] times={1000,6000,12000,18000};
            String[] phases={"DAY_CLOUD_TINT","NOON_CLOUD_TINT","EVENING_CLOUD_TINT","NIGHT_CLOUD_TINT"};
            for(int phase=0;phase<times.length;phase++) {
                Vec3 time=(Vec3)timeTint.invoke(null,times[phase]);
                Vec3 configured=Vec3.fromRGB24(((Number)config(configType,phases[phase])).intValue());
                require(time.distanceTo(configured)<1e-6,"actual Cirrus time tint uses pack settings");
                // Include normal weather color and the scalar brightness used by Cirrus's fog-whitening path.
                for(Vec3 base:new Vec3[]{new Vec3(.7,.8,.9),new Vec3(.8,.8,.8)}) {
                    Vec3 cirrus=base.multiply(time);
                    for(int[] pair:new int[][]{{0xccffffff,0x66828282},{0x66888899,0x66828282},{0x66828282,0x66888899}}) {
                        int previousRed=-1;
                        for(int step=0;step<=120;step++) {
                            var tint=CloudBlend.sample(-14+28*step/120.0,64,0,(x,y,z) -> x<0 ? pair[0]:pair[1]);frame.set(renderer,tint);
                            Vec3 color=(Vec3)hook.invoke(renderer,cirrus);
                            require(color.distanceTo(new Vec3(cirrus.x*tint.red(),cirrus.y*tint.green(),cirrus.z*tint.blue()))<1e-9,"preserve Cirrus base RGB");
                            for(double alpha:opacity) {
                                RenderSystem.setShaderColor((float)color.x,(float)color.y,(float)color.z,(float)alpha);
                                shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,new Matrix4f(),new Matrix4f(),null);shader.apply();
                                GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                                GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                                double[] expected={color.x,color.y,color.z,alpha};
                                for(int channel=0;channel<4;channel++)require(Math.abs((pixel.get(channel)&255)-255*expected[channel])<=2,"Cirrus rendered pixel matches tinted RGB and constant opacity "+channel);
                            }
                            if(pair[0]==0xccffffff && previousRed>=0)require((pixel.get(0)&255)<=previousRed,"white-to-gray pixels dim continuously");
                            previousRed=pixel.get(0)&255;
                        }
                    }
                }
            }
            checkOpacityAndComposition(shader,type,renderer,frame,hook,opacity,pixel);
            checkFastSunOpacity(shader,type,renderer,frame,hook,pixel);
            checkLiveDiagnostic(shader,type,pixel);
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"Cirrus checks have no GL errors");
            MemoryUtil.memFree(pixel);GL15.glDeleteBuffers(vbo);GL30.glDeleteVertexArrays(vao);GL11.glDeleteTextures(texture);
        } finally {
            gameResources.close();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    private static void drawPixel(ShaderInstance shader,ByteBuffer pixel,Vec3 color,float alpha) {
        RenderSystem.setShaderColor((float)color.x,(float)color.y,(float)color.z,alpha);
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,new Matrix4f(),new Matrix4f(),null);shader.apply();
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
    }
    private static void checkOpacityAndComposition(ShaderInstance shader,Class<?> type,Object renderer,
            Field frame,Method hook,double[] opacity,ByteBuffer pixel) throws Exception {
        var timeOpacity=Class.forName("com.jvn.cirrus.client.CirrusCloudTimeFade").getMethod("opacity",double.class);
        for(int time=-24000;time<=48000;time+=125)
            require((float)timeOpacity.invoke(null,(double)time)==1f,"pack's disabled time fades remain fully visible all day");
        String[] layerNames={"LOWER_LAYER","UPPER_LAYER","TOP_LAYER"};
        float[][] contexts={
            // sun alignment, sun weight, rain, thunder, lightning, time opacity
            {0,1,0,0,0,1}, {1,.5f,0,0,0,1}, {0,0,0,0,0,1},
            {0,1,1,.5f,0,1}, {0,1,0,0,1,1}, {0,1,0,0,0,.5f}
        };
        GL11.glDisable(GL11.GL_BLEND);GL11.glClearColor(0,0,0,0);
        for(int context=0;context<contexts.length;context++) {
            float[] state=contexts[context];
            shader.getUniform("CirrusLightViewDirection").set(state[0],state[0],1-state[0]);
            shader.getUniform("CirrusSunWeight").set(state[1]);
            shader.getUniform("CirrusRainLevel").set(state[2]);
            shader.getUniform("CirrusThunderLevel").set(state[3]);
            shader.getUniform("CirrusLightningFlash").set(state[4]);
            shader.getUniform("CirrusTimeOpacity").set(state[5]);
            for(String layerName:layerNames) {
                var field=type.getDeclaredField(layerName);field.setAccessible(true);Object layer=field.get(null);
                var nativeOpacity=layer.getClass().getDeclaredMethod("opacity",float.class,float.class);nativeOpacity.setAccessible(true);
                float alpha=(float)nativeOpacity.invoke(layer,state[2],state[3]);
                // At this pixel, an aligned sun contributes its configured weight; a white texture has no exposed lightning fold.
                float expected=(alpha+(1-alpha)*(state[0]*state[1]));
                expected=(expected+(1-expected)*state[4]*.1f)*state[5];
                int firstAlpha=-1;
                for(int step=0;step<=120;step++) {
                    var tint=CloudBlend.sample(-14+28*step/120.0,64,0,(x,y,z)->x<0 ? 0xccffffff:0x66828282);
                    frame.set(renderer,tint);Vec3 color=(Vec3)hook.invoke(renderer,new Vec3(.7,.8,.9));
                    drawPixel(shader,pixel,color,alpha);int actual=pixel.get(3)&255;
                    require(Math.abs(actual-expected*255)<=2,"native Cirrus alpha equation in context "+context+" layer "+layerName);
                    if(firstAlpha<0)firstAlpha=actual;
                    require(actual==firstAlpha,"biome RGB cannot change fragment alpha under any tested lighting/weather context");
                }
            }
        }
        // Restore ordinary daylight and composite the actual biome cloud palettes over their changing sky palettes.
        shader.getUniform("CirrusLightViewDirection").set(0f,0f,1f);
        shader.getUniform("CirrusSunWeight").set(1f);
        shader.getUniform("CirrusRainLevel").set(0f);shader.getUniform("CirrusThunderLevel").set(0f);
        shader.getUniform("CirrusLightningFlash").set(0f);shader.getUniform("CirrusTimeOpacity").set(1f);
        int sourceCloud,targetCloud,sourceSky,targetSky;
        try(var stream=CirrusCloudRenderProbe.class.getResourceAsStream("/cloud-probe-palettes.json")) {
            require(stream!=null,"cloud/sky palettes copied from the exact tested release");
            var palettes=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            sourceCloud=(int)palettes.getAsJsonObject("forest").get("cloud").getAsLong();
            targetCloud=(int)palettes.getAsJsonObject("pale_garden").get("cloud").getAsLong();
            sourceSky=palettes.getAsJsonObject("forest").get("sky").getAsInt();
            targetSky=palettes.getAsJsonObject("pale_garden").get("sky").getAsInt();
        }
        StringBuilder evidence=new StringBuilder();
        for(double alpha:opacity) {
            double first=0,last=0,lowest=Double.POSITIVE_INFINITY;
            for(int step=0;step<=120;step++) {
                double position=-14+28*step/120.0;
                var tint=CloudBlend.sample(position,64,0,(x,y,z)->x<0 ? sourceCloud:targetCloud);
                var sky=CloudBlend.sample(position,64,0,(x,y,z)->x<0 ? sourceSky:targetSky);
                frame.set(renderer,tint);Vec3 color=(Vec3)hook.invoke(renderer,new Vec3(1,1,1));
                GL11.glDisable(GL11.GL_BLEND);GL11.glClearColor(0,0,0,0);
                drawPixel(shader,pixel,color,(float)alpha);
                require(Math.abs((pixel.get(3)&255)-alpha*255)<=2,"raw cloud alpha unchanged before sky composition");
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glClearColor((float)sky.red(),(float)sky.green(),(float)sky.blue(),1);
                drawPixel(shader,pixel,color,(float)alpha);
                double[] background={sky.red(),sky.green(),sky.blue()},foreground={color.x,color.y,color.z};
                double contrast=0;
                for(int channel=0;channel<3;channel++) {
                    double expected=foreground[channel]*alpha+background[channel]*(1-alpha);
                    require(Math.abs((pixel.get(channel)&255)-expected*255)<=2,"actual shader/sky alpha composition matches RGB equation");
                    double difference=(pixel.get(channel)&255)/255.0-background[channel];contrast+=difference*difference;
                }
                require((pixel.get(3)&255)==255,"opaque sky hides source alpha in final video output");
                contrast=Math.sqrt(contrast);lowest=Math.min(lowest,contrast);
                if(step==0)first=contrast;if(step==120)last=contrast;
            }
            require(lowest<Math.min(first,last)*.5,"cloud/sky contrast dips then returns with absolutely constant cloud alpha");
            evidence.append(" layer=").append(alpha).append(" contrast=").append(first).append(" -> ").append(lowest).append(" -> ").append(last);
        }
        GL11.glDisable(GL11.GL_BLEND);
        System.out.println("CIRRUS_OPACITY_COMPOSITE_PASS: 6 native lighting/weather/time contexts keep biome alpha constant; pack time fades disabled; actual biome palettes reproduce contrast disappearance with alpha .25/.5/.15;"+evidence);
    }

    private static void checkLiveDiagnostic(ShaderInstance shader,Class<?> rendererType,ByteBuffer pixel) throws Exception {
        // Only the rendering measurement needs a custom mixin; controls use Fabric events.
        require(Arrays.stream(rendererType.getDeclaredMethods()).filter(m->m.getName().contains("wwoo_diag$measureCloudDraw")
            && !m.getName().contains("lambda$") && m.getParameterCount()==5).count()==1,"one applied diagnostic color draw hook");
        checkDiagnosticControls();
        var exported=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class").resolve(rendererType.getName().replace('.','/')+".class"))).accept(exported,0);
        var draw=exported.methods.stream().filter(m->m.name.equals("drawLayer")).findFirst().orElseThrow();
        int wrapped=0,untouched=0;
        for(var insn:draw.instructions)if(insn instanceof MethodInsnNode call) {
            if(call.name.contains("wwoo_diag$measureCloudDraw"))wrapped++;
            if(call.owner.equals("net/minecraft/class_291")&&call.name.equals("method_34427"))untouched++;
        }
        require(wrapped==1&&untouched==2,"only color pass is wrapped; fancy/debug depth passes unchanged");
        RenderSystem.setShaderColor(.7f,.8f,.9f,.25f);
        shader.getUniform("CirrusSunWeight").set(1f);shader.getUniform("CirrusLightViewDirection").set(0f,0f,1f);
        shader.getUniform("CirrusLightningFlash").set(0f);shader.getUniform("CirrusTimeOpacity").set(1f);
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,32,32);
        GL11.glClearColor(.2f,.3f,.4f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthMask(false);
        int[] counts={0,0};
        Runnable drawCloud=()->{shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,new Matrix4f(),new Matrix4f(),null);shader.apply();GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);shader.clear();};
        CloudDiagnostic.draw(shader,()->counts[0]++,()->counts[1]++);
        require(counts[0]==1&&counts[1]==0,"inactive recorder never replays or touches GL");counts[0]=counts[1]=0;
        Path directory=Path.of("live-diagnostic-test");CloudDiagnostic.start(directory);
        CloudDiagnostic.beginFrame(new CloudDiagnostic.Scene("minecraft:overworld","minecraft:forest",0,64,0,0,-80,CloudBlend.Color.fromArgb(0xccffffff),.2,.3,.4));
        var layerField=rendererType.getDeclaredField("LOWER_LAYER");layerField.setAccessible(true);CloudDiagnostic.layer(layerField.get(null));
        int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,8);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,40);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,1);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,2);
        CloudDiagnostic.draw(shader,()->{counts[0]++;drawCloud.run();},()->{counts[1]++;drawCloud.run();});
        CloudDiagnostic.finishFrame();Path output=CloudDiagnostic.output();CloudDiagnostic.stop("test saved");
        require(counts[0]==1&&counts[1]==1,"one screen draw and one offscreen replay on sampled frames");
        require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==drawFbo&&GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==readFbo,"framebuffers restored");
        int[] restored=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,restored);require(Arrays.equals(viewport,restored),"viewport restored");
        require(GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"blend, depth, scissor and depth-write states restored");
        require(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==vao,"VAO restored");
        require(GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT)==8&&GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH)==40&&GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)==1&&GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS)==2,"readback pixel packing restored");
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,4);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
        GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
        double[] expected={.7*.25+.2*.75,.8*.25+.3*.75,.9*.25+.4*.75,1};
        for(int c=0;c<4;c++)require(Math.abs((pixel.get(c)&255)-expected[c]*255)<=2,"offscreen capture leaves visible screen unchanged");
        var lines=Files.readAllLines(output);require(lines.size()==33,"CSV has one header and 32 central sample points");
        var columns=Arrays.asList(lines.getFirst().split(","));
        for(String row:lines.subList(1,lines.size())) {
            String[] values=row.split(",");require(values.length==columns.size(),"CSV row schema consistent");
            require(values[columns.indexOf("style")].equals("FAST"),"actual Cirrus fast style recorded");
            require(values[columns.indexOf("covered")].equals("true"),"cloud coverage measured");
            require(Math.abs(Double.parseDouble(values[columns.indexOf("fragment_alpha")])-.25)<.001,"recorded native shader alpha is real GPU result");
            require(Math.abs(Double.parseDouble(values[columns.indexOf("after_r")])-expected[0])<.01,"visible frame color logged");
        }
        GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        require(CloudDiagnostic.status().contains("rows: 32, cloud points: 32"),"status reports actual recorded samples");
        System.out.println("CLOUD_DIAGNOSTIC_PASS: standard Fabric controls, actual Cirrus hook, Fast style label, real post-shader alpha CSV, offscreen replay, unchanged visible pixels and restored GL/readback state");
    }

    private static void checkFastSunOpacity(ShaderInstance shader,Class<?> type,Object renderer,Field frame,Method tintHook,ByteBuffer pixel) throws Exception {
        require(shader.getUniform(CloudOpacity.UNIFORM)!=null,"actual Fabric shader constructor loads patched JSON and fragment uniform");
        Method layerHook=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("wwoo$cirrusLayerMode")).findFirst().orElseThrow();layerHook.setAccessible(true);
        Method modeHook=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("wwoo$cirrusOpacityMode")).findFirst().orElseThrow();modeHook.setAccessible(true);
        var exported=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class").resolve(type.getName().replace('.','/')+".class"))).accept(exported,0);
        var draw=exported.methods.stream().filter(m->m.name.equals("drawLayer")).findFirst().orElseThrow();
        long selectors=java.util.stream.StreamSupport.stream(draw.instructions.spliterator(),false).filter(i->i instanceof MethodInsnNode call&&call.name.contains("wwoo$cirrusOpacityMode")).count();
        require(selectors==3,"style opacity uniform applied to all actual Cirrus shader selections");
        var configType=Class.forName("com.jvn.cirrus.config.CirrusConfig");Object setting=configType.getField("LOWER_LAYER_STYLE").get(null);
        var getter=setting.getClass().getMethod("get");getter.setAccessible(true);Object originalStyle=getter.invoke(setting);
        var setter=setting.getClass().getMethod("set",Object.class);setter.setAccessible(true);
        Object[] styles=Class.forName("com.jvn.cirrus.config.CirrusConfig$CloudStyle").getEnumConstants();
        Object fast=Arrays.stream(styles).filter(s->((Enum<?>)s).name().equals("FAST")).findFirst().orElseThrow();
        Object fancy=Arrays.stream(styles).filter(s->((Enum<?>)s).name().equals("FANCY")).findFirst().orElseThrow();
        var layerField=type.getDeclaredField("LOWER_LAYER");layerField.setAccessible(true);Object layer=layerField.get(null);
        var nativeOpacity=layer.getClass().getDeclaredMethod("opacity",float.class,float.class);nativeOpacity.setAccessible(true);
        Object[] args=new Object[layerHook.getParameterCount()];
        for(int i=0;i<args.length;i++) {
            Class<?> p=layerHook.getParameterTypes()[i];
            if(p==double.class)args[i]=0d;else if(p==float.class)args[i]=0f;else if(p==long.class)args[i]=0L;else if(p==boolean.class)args[i]=false;
        }
        args[1]=layer;args[args.length-1]=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("drawLayer",false);
        GL11.glDisable(GL11.GL_BLEND);GL11.glClearColor(0,0,0,0);
        try {
            for(float sunWeight:new float[]{.5f,1f})for(float rain:new float[]{0,1})for(float time:new float[]{.5f,1f}) {
                shader.getUniform("CirrusLightViewDirection").set(1f,1f,0f);shader.getUniform("CirrusSunWeight").set(sunWeight);
                shader.getUniform("CirrusRainLevel").set(rain);shader.getUniform("CirrusThunderLevel").set(0f);
                shader.getUniform("CirrusLightningFlash").set(0f);shader.getUniform("CirrusTimeOpacity").set(time);
                float alpha=(float)nativeOpacity.invoke(layer,rain,0f);
                for(int step=0;step<=40;step++) {
                    var tint=CloudBlend.sample(-14+28*step/40.0,64,0,(x,y,z)->x<0?0xccffffff:0x66828282);
                    frame.set(renderer,tint);Vec3 color=(Vec3)tintHook.invoke(renderer,new Vec3(.7,.8,.9));
                    setter.invoke(setting,fancy);layerHook.invoke(renderer,args);modeHook.invoke(renderer,shader);
                    require(shader.getUniform(CloudOpacity.UNIFORM).getFloatBuffer().get(0)==0f,"Fancy keeps native sunlight alpha");
                    drawPixel(shader,pixel,color,alpha);int[] rgb={pixel.get(0)&255,pixel.get(1)&255,pixel.get(2)&255};
                    require(Math.abs((pixel.get(3)&255)-(alpha+(1-alpha)*sunWeight)*time*255)<=2,"Fancy sunlight opacity unchanged");
                    setter.invoke(setting,fast);layerHook.invoke(renderer,args);modeHook.invoke(renderer,shader);
                    require(shader.getUniform(CloudOpacity.UNIFORM).getFloatBuffer().get(0)==1f,"Fast layer selects configured-opacity mode");
                    drawPixel(shader,pixel,color,alpha);
                    require(Math.abs((pixel.get(3)&255)-alpha*time*255)<=2,"Fast opacity preserved near sun across biome blend, rain and time fade");
                    for(int c=0;c<3;c++)require((pixel.get(c)&255)==rgb[c],"Fast and Fancy retain identical RGB sunlight/twilight lighting");
                }
            }
            // Reusing the same shader after Fast must reset the mode for Fancy.
            setter.invoke(setting,fancy);layerHook.invoke(renderer,args);modeHook.invoke(renderer,shader);
            require(shader.getUniform(CloudOpacity.UNIFORM).getFloatBuffer().get(0)==0f,"Fast to Fancy resets shared shader uniform");
            System.out.println("CIRRUS_FAST_OPACITY_PASS: real shader constructor and all layer selections; configured Fast alpha near sun; original Fancy alpha; identical RGB across biome/weather/time contexts; style reset");
        } finally {
            setter.invoke(setting,originalStyle);layerHook.invoke(renderer,args);modeHook.invoke(renderer,shader);
            shader.getUniform("CirrusLightViewDirection").set(0f,0f,1f);shader.getUniform("CirrusSunWeight").set(1f);
            shader.getUniform("CirrusRainLevel").set(0f);shader.getUniform("CirrusThunderLevel").set(0f);
            shader.getUniform("CirrusLightningFlash").set(0f);shader.getUniform("CirrusTimeOpacity").set(1f);
        }
    }

    private static void checkDiagnosticControls() throws Exception {
        new DiagnosticsClient().onInitializeClient();
        KeyMapping mapping=DiagnosticsClient.captureKey();
        require(mapping!=null&&mapping.getCategory().equals("key.categories.wwoo_cloud_diagnostics"),"capture key registered in Controls");
        var client=Minecraft.getInstance();
        ClientLevel oldLevel=client.level;client.level=(ClientLevel)unconstructed(ClientLevel.class);
        var bound=KeyBindingHelper.getBoundKeyOf(mapping);
        try {
            // A tap completely between ticks must be retained after release.
            KeyMapping.click(bound);KeyMapping.set(bound,false);
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
            require(CloudDiagnostic.active(),"queued released F8 press starts capture through actual Fabric event");
            Path keyCapture=CloudDiagnostic.output();
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
            require(CloudDiagnostic.active(),"next tick does not retoggle a consumed press");
            KeyMapping.click(bound);KeyMapping.set(bound,false);
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
            require(!CloudDiagnostic.active()&&Files.exists(keyCapture),"second key press stops capture and saves CSV");
            // Remapping in Controls changes the key used by the input event path.
            mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F9));KeyMapping.resetMapping();
            KeyMapping.click(KeyBindingHelper.getBoundKeyOf(mapping));KeyMapping.set(KeyBindingHelper.getBoundKeyOf(mapping),false);
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
            require(CloudDiagnostic.active(),"remapped F9 starts capture");
            CloudDiagnostic.stop("key test saved");
            mapping.setKey(bound);KeyMapping.resetMapping();
            var feedback=new java.util.ArrayList<String>();
            var source=(FabricClientCommandSource)java.lang.reflect.Proxy.newProxyInstance(FabricClientCommandSource.class.getClassLoader(),
                new Class<?>[]{FabricClientCommandSource.class},(proxy,method,args)->switch(method.getName()) {
                    case "getClient"->client;
                    case "sendFeedback","sendError"->{feedback.add(((Component)args[0]).getString());yield null;}
                    case "toString"->"Diagnostic test source";
                    default->null;
                });
            var dispatcher=new CommandDispatcher<FabricClientCommandSource>();
            ClientCommandRegistrationCallback.EVENT.invoker().register(dispatcher,null);
            require(dispatcher.execute("wwoocloud capture",source)==1&&CloudDiagnostic.active(),"registered client command starts capture");
            require(dispatcher.execute("wwoocloud status",source)==1&&feedback.getLast().contains("recording"),"status command reports capture state");
            require(dispatcher.execute("wwoocloud capture",source)==1&&!CloudDiagnostic.active(),"client command stops capture");
            client.level=null;
            require(dispatcher.execute("wwoocloud capture",source)==0&&!CloudDiagnostic.active(),"command requires an active world");
            System.out.println("CLOUD_DIAGNOSTIC_CONTROLS_PASS: registered F8, press/release between ticks, one toggle per tap, Controls remap to F9, real client capture/status commands and world guard");
        } finally {
            if(CloudDiagnostic.active())CloudDiagnostic.stop("test cleanup");
            mapping.setKey(bound);KeyMapping.resetMapping();client.level=oldLevel;
        }
    }

}
