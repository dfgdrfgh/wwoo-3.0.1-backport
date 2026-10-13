package com.zachminecraft.wwoocontent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
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
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"Cirrus checks have no GL errors");
            MemoryUtil.memFree(pixel);GL15.glDeleteBuffers(vbo);GL30.glDeleteVertexArrays(vao);GL11.glDeleteTextures(texture);
        } finally {
            gameResources.close();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
}
