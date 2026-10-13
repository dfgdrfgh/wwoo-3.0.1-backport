package com.zachminecraft.wwoocontent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/** Uses the exact Sodium hook and its actual cloud shader on a software OpenGL context. */
public final class SodiumCloudRenderProbe implements PreLaunchEntrypoint {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class ColorArgs extends Args {
        ColorArgs() { super(new Object[]{.7f,.8f,.9f,.8f}); }
        @Override public <T> void set(int index, T value) { values[index] = (Float)value; }
        @Override public void setAll(Object... newValues) {
            require(newValues.length == 4, "four shader components");
            System.arraycopy(newValues,0,values,0,4);
        }
    }
    private static Object unconstructed(Class<?> type) throws Exception {
        var unsafe = Class.forName("sun.misc.Unsafe");
        var field = unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
    }
    @Override public void onPreLaunch() {
        try {
            check();
            System.out.println("SODIUM_CLOUD_RENDER_PASS: exact Sodium/Sodium Extra modpack versions, injected RGB hook, actual cloud shader pixels, constant opacity and original reset");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();System.exit(1);
        }
    }
    private static void check() throws Exception {
        Class<?> type=Class.forName("net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer");
        var hooks=Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains("wwoo$tintCloudColor")
            && m.getParameterCount()==1 && m.getParameterTypes()[0]==Args.class).toList();
        require(hooks.size()==1,"Sodium renderer has exactly one applied tint hook");
        var hook=hooks.getFirst();hook.setAccessible(true);
        var transformed=new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class").resolve(type.getName().replace('.','/')+".class"))).accept(transformed,0);
        var render=transformed.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
        int tintCalls=0,shaderCalls=0;
        for(var insn:render.instructions) if(insn instanceof MethodInsnNode call) {
            if(call.name.contains("wwoo$tintCloudColor")) { require(shaderCalls==0,"tint runs before cloud color upload");tintCalls++; }
            if(call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("setShaderColor")) {
                require(tintCalls==1,"actual Sodium render uploads the modified color");shaderCalls++;
            }
        }
        require(tintCalls==1 && shaderCalls==2,"modify cloud color once and preserve Sodium's independent reset");
        var frame=type.getDeclaredField("wwoo$frameColor");frame.setAccessible(true);
        Object renderer=unconstructed(type);
        RenderSystem.initRenderThread();
        require(GLFW.glfwInit(),"initialize software window system");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS,8);
        long window=GLFW.glfwCreateWindow(32,32,"WWOO cloud shader check",0,0);
        require(window!=0,"create OpenGL window");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        var pack=new VanillaPackResourcesBuilder().build(new PackLocationInfo("cloud-check",Component.literal("Cloud check"),PackSource.BUILT_IN,Optional.empty()));
        ResourceProvider resources=id -> {
            Path path=Path.of("shader-resources/assets").resolve(id.getNamespace()).resolve(id.getPath());
            return Files.exists(path) ? Optional.of(new Resource(pack,() -> Files.newInputStream(path))) : Optional.empty();
        };
        try(var shader=new ShaderInstance(resources,"clouds",DefaultVertexFormat.POSITION_COLOR)) {
            RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);
            RenderSystem.setShaderFogColor(0,0,0,1);
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            int vbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            FloatBuffer vertices=MemoryUtil.memAllocFloat(21);
            vertices.put(new float[]{-1,-1,0,1,1,1,1, 3,-1,0,1,1,1,1, -1,3,0,1,1,1,1}).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);MemoryUtil.memFree(vertices);
            RenderSystem.setShaderColor(1,1,1,.8f);
            shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,new Matrix4f(),new Matrix4f(),null);shader.apply();
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int position=GL20.glGetAttribLocation(program,"Position"),color=GL20.glGetAttribLocation(program,"Color");
            require(position>=0 && color>=0,"actual Sodium shader attributes");
            GL20.glEnableVertexAttribArray(position);GL20.glVertexAttribPointer(position,3,GL11.GL_FLOAT,false,28,0L);
            GL20.glEnableVertexAttribArray(color);GL20.glVertexAttribPointer(color,4,GL11.GL_FLOAT,false,28,12L);
            GL11.glViewport(0,0,32,32);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
            ByteBuffer pixel=MemoryUtil.memAlloc(4);
            for(int[] pair:new int[][]{{0xccffffff,0x66828282},{0x66888899,0x66828282},{0x66828282,0x66888899}}) {
                int previousRed=-1;
                for(int step=0;step<=120;step++) {
                    var tint=CloudBlend.sample(-14+28*step/120.0,64,0,(x,y,z) -> x<0 ? pair[0]:pair[1]);frame.set(renderer,tint);
                    var args=new ColorArgs();hook.invoke(renderer,args);
                    require((float)args.get(3)==.8f,"Sodium opacity unchanged");
                    RenderSystem.setShaderColor(args.get(0),args.get(1),args.get(2),args.get(3));
                    shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,new Matrix4f(),new Matrix4f(),null);shader.apply();
                    GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                    GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                    double[] expected={.7*.7*tint.red(),.7*.8*tint.green(),.7*.9*tint.blue(),.8};
                    for(int channel=0;channel<4;channel++)
                        require(Math.abs((pixel.get(channel)&255)-255*expected[channel])<=2,"Sodium shader pixel matches biome color channel "+channel);
                    if(pair[0]==0xccffffff && previousRed>=0)require((pixel.get(0)&255)<=previousRed,"white-to-gray pixels dim continuously");
                    previousRed=pixel.get(0)&255;
                }
            }
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"shader checks have no GL errors");
            MemoryUtil.memFree(pixel);GL15.glDeleteBuffers(vbo);GL30.glDeleteVertexArrays(vao);
        } finally {
            GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
}
