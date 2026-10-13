package com.zachminecraft.wwoo.diagnostics;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zachminecraft.wwoocontent.CloudAttributes;
import com.zachminecraft.wwoocontent.CloudBlend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary local-only recorder: native cloud fragments before blending, plus the actual scene. */
public final class CloudDiagnostic {
    private static final Logger LOG=LoggerFactory.getLogger("WWOO-Cloud-Diagnostic");
    private static final long INTERVAL=500_000_000L, DURATION=60_000_000_000L;
    private static BufferedWriter writer;
    private static Path file;
    private static long started,nextSample,sample;
    private static boolean keyDown,captureFrame;
    private static Scene scene;
    private static String notice="",layer="unknown",style="unknown";
    private static final Map<String,String> hud=new LinkedHashMap<>();
    private static int fbo,colorTexture,depthBuffer,width,height;
    private static FloatBuffer before,after,raw;
    public record Scene(String dimension,String biome,double x,double y,double z,float yaw,float pitch,
            CloudBlend.Color tint,double skyR,double skyG,double skyB) {}
    private CloudDiagnostic() {}

    public static void tick(Minecraft client) {
        boolean down=InputConstants.isKeyDown(client.getWindow().getWindow(),GLFW.GLFW_KEY_F8);
        if(down&&!keyDown&&client.level!=null&&client.screen==null) {
            if(active())stop("Saved capture");else try {start(client.gameDirectory.toPath());}
            catch(IOException error) {notice="Capture could not start: "+error.getMessage();LOG.error(notice,error);}
            client.gui.getChat().addMessage(Component.literal("[Cloud diagnostic] "+notice));
        }
        keyDown=down;
        if(active()&&(client.level==null||System.nanoTime()-started>=DURATION)) {
            stop("Saved capture");
            if(client.gui!=null)client.gui.getChat().addMessage(Component.literal("[Cloud diagnostic] "+notice));
        }
    }
    public static void start(Path directory) throws IOException {
        if(active())stop("Saved capture");
        Path logs=directory.resolve("logs");Files.createDirectories(logs);
        file=logs.resolve("wwoo-cloud-diagnostics-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))+".csv");
        writer=Files.newBufferedWriter(file);
        writer.write("sample,elapsed_ms,layer,style,dimension,biome,camera_x,camera_y,camera_z,yaw,pitch,tint_r,tint_g,tint_b,sky_r,sky_g,sky_b,shader_r,shader_g,shader_b,input_alpha,time_opacity,sun_weight,rain,thunder,lightning,fog_start,fog_end,blend_enabled,blend_src_rgb,blend_dst_rgb,blend_src_alpha,blend_dst_alpha,depth_test,depth_write,screen_x,screen_y,covered,fragment_r,fragment_g,fragment_b,fragment_alpha,before_r,before_g,before_b,after_r,after_g,after_b\n");
        writer.flush();started=System.nanoTime();nextSample=0;sample=0;captureFrame=false;hud.clear();
        notice="Recording locally; F8 stops. "+file.getFileName();
        LOG.info("{}",notice);
    }
    public static boolean active() {return writer!=null;}
    public static Path output() {return file;}
    public static void stop(String reason) {
        captureFrame=false;
        if(writer!=null)try {writer.flush();writer.close();}catch(IOException error){LOG.error("Closing cloud capture",error);}
        writer=null;notice=reason+(file==null?"":": logs/"+file.getFileName());LOG.info("{}",notice);
        release();
    }
    public static void beginFrame(ClientLevel level,float partialTick,boolean depthOnly) {
        captureFrame=false;
        if(!active()||depthOnly||System.nanoTime()<nextSample)return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();var position=camera.getPosition();
        var biome=level.getBiome(camera.getBlockPosition()).unwrapKey().map(key->key.location().toString()).orElse("unregistered");
        var sky=level.getSkyColor(position,partialTick);
        beginFrame(new Scene(level.dimension().location().toString(),biome,position.x,position.y,position.z,
            camera.getYRot(),camera.getXRot(),CloudAttributes.color(),sky.x,sky.y,sky.z));
    }
    /** Also usable by the GPU regression with a synthetic scene and real rendering resources. */
    public static void beginFrame(Scene value) {
        captureFrame=active()&&System.nanoTime()>=nextSample;
        if(captureFrame){scene=value;sample++;nextSample=System.nanoTime()+INTERVAL;hud.clear();}
    }
    public static void layer(Object definition) {
        if(!captureFrame)return;
        try {var method=definition.getClass().getDeclaredMethod("style");method.setAccessible(true);style=String.valueOf(method.invoke(definition));}catch(ReflectiveOperationException ignored){style="unknown";}
        String value=definition.toString();int start=value.indexOf("kind=");
        layer=start<0?value:value.substring(start+5).split("[,\\]]",2)[0];
    }
    public static void layer(String value) {layer=value;style="test";}
    public static void finishFrame() {
        if(active()&&captureFrame)try {writer.flush();}catch(IOException error){fail(error);}
        captureFrame=false;
    }
    public static void drawHud(GuiGraphics graphics) {
        if(!active())return;
        var client=Minecraft.getInstance();
        graphics.drawString(client.font,String.format(Locale.ROOT,"CLOUD CAPTURE %.1fs / 60s | F8 stop",(System.nanoTime()-started)/1e9),8,8,0xffe080,true);
        if(scene!=null)graphics.drawString(client.font,scene.biome()+" | cloud RGB "+String.format(Locale.ROOT,"%.3f %.3f %.3f",scene.tint().red(),scene.tint().green(),scene.tint().blue()),8,20,0xffffff,true);
        int y=32;for(String value:hud.values()){graphics.drawString(client.font,value,8,y,0xffffff,true);y+=12;}
        graphics.drawString(client.font,"Aim the centre at clouds; GPU alpha is measured before sky blending.",8,y+2,0xb0dfff,true);
    }
    private static float uniform(ShaderInstance shader,String name) {
        var uniform=shader.getUniform(name);return uniform==null?Float.NaN:uniform.getFloatBuffer().get(0);
    }
    public static void draw(ShaderInstance shader,Runnable original,Runnable replay) {
        if(!captureFrame||!active()||!shader.getName().equals("cirrus:cirrus_clouds")){original.run();return;}
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int w=Math.min(128,viewport[2]),h=Math.min(72,viewport[3]);
        if(w<=0||h<=0){original.run();return;}
        int x=viewport[0]+(viewport[2]-w)/2,y=viewport[1]+(viewport[3]-h)/2;
        float[] shaderColor=RenderSystem.getShaderColor().clone();
        float time=uniform(shader,"CirrusTimeOpacity"),sun=uniform(shader,"CirrusSunWeight"),rain=uniform(shader,"CirrusRainLevel"),thunder=uniform(shader,"CirrusThunderLevel"),lightning=uniform(shader,"CirrusLightningFlash");
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        int[] blendState={GL11.glIsEnabled(GL11.GL_BLEND)?1:0,GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),GL11.glIsEnabled(GL11.GL_DEPTH_TEST)?1:0,GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)?1:0};
        // Allocation/readback failures must never prevent the normal draw.
        try {buffers(w,h);readScene(x,y,w,h,before);}catch(Throwable failure){original.run();fail(failure);return;}
        original.run();
        State state=State.capture();
        try {
            readScene(x,y,w,h,after);target(w,h);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            // Preserve the original viewport scale and sample exact central screen coordinates.
            GL11.glViewport(viewport[0]-x,viewport[1]-y,viewport[2],viewport[3]);
            GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);
            GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            replay.run();
            readPixels(0,0,w,h,raw);
            write(shaderColor,time,sun,rain,thunder,lightning,fogStart,fogEnd,blendState,x,y,w,h);
        } catch(Throwable failure) {fail(failure);}
        finally {state.restore();}
    }
    private static void readScene(int x,int y,int w,int h,FloatBuffer pixels) {
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);GL11.glReadBuffer(draw==0?GL11.GL_BACK:GL30.GL_COLOR_ATTACHMENT0);
            readPixels(x,y,w,h,pixels);
        } finally {GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glReadBuffer(readBuffer);}
    }
    private static void readPixels(int x,int y,int w,int h,FloatBuffer pixels) {
        int alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),rowLength=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);
            GL11.glReadPixels(x,y,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        } finally {GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,rowLength);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);}
    }
    private static void buffers(int w,int h) {
        if(width==w&&height==h&&before!=null)return;
        releaseBuffers();width=w;height=h;before=MemoryUtil.memAllocFloat(w*h*4);after=MemoryUtil.memAllocFloat(w*h*4);raw=MemoryUtil.memAllocFloat(w*h*4);
    }
    private static void target(int w,int h) {
        if(fbo!=0)return;
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),renderbuffer=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        fbo=GL30.glGenFramebuffers();colorTexture=GL11.glGenTextures();depthBuffer=GL30.glGenRenderbuffers();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glBindTexture(GL11.GL_TEXTURE_2D,colorTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colorTexture,0);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depthBuffer);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL14.GL_DEPTH_COMPONENT24,w,h);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,depthBuffer);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete cloud diagnostic framebuffer");
        } finally {GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,renderbuffer);}
    }
    private static void write(float[] color,float time,float sun,float rain,float thunder,float lightning,float fogStart,float fogEnd,int[] blendState,int x,int y,int w,int h) throws IOException {
        int covered=0;float min=Float.POSITIVE_INFINITY,max=0,sum=0;
        for(int gy=0;gy<4;gy++)for(int gx=0;gx<8;gx++) {
            int px=Math.min(w-1,(int)((gx+.5)*w/8)),py=Math.min(h-1,(int)((gy+.5)*h/4)),index=(py*w+px)*4;
            float alpha=raw.get(index+3);boolean present=alpha>0;
            if(present){covered++;min=Math.min(min,alpha);max=Math.max(max,alpha);sum+=alpha;}
            StringBuilder row=new StringBuilder();
            add(row,sample,(System.nanoTime()-started)/1_000_000,layer,style,scene.dimension(),scene.biome(),scene.x(),scene.y(),scene.z(),scene.yaw(),scene.pitch(),scene.tint().red(),scene.tint().green(),scene.tint().blue(),scene.skyR(),scene.skyG(),scene.skyB(),color[0],color[1],color[2],color[3],time,sun,rain,thunder,lightning,fogStart,fogEnd,blendState[0],blendState[1],blendState[2],blendState[3],blendState[4],blendState[5],blendState[6],x+px,y+py,present,raw.get(index),raw.get(index+1),raw.get(index+2),alpha,before.get(index),before.get(index+1),before.get(index+2),after.get(index),after.get(index+1),after.get(index+2));
            writer.write(row.append('\n').toString());
        }
        hud.put(layer,covered==0?layer+" "+style+": no cloud at sampled points":String.format(Locale.ROOT,"%s %s: input %.3f | GPU alpha %.3f..%.3f (mean %.3f) | %d/32 points",layer,style,color[3],min,max,sum/covered,covered));
    }
    private static void add(StringBuilder row,Object... values) {
        for(Object value:values){if(!row.isEmpty())row.append(',');String text=String.valueOf(value);if(text.indexOf(',')>=0||text.indexOf('"')>=0)row.append('"').append(text.replace("\"","\"\"")).append('"');else row.append(text);}
    }
    private static void fail(Throwable failure) {LOG.error("Cloud diagnostic stopped; normal rendering is retained",failure);stop("Capture failed: "+failure.getClass().getSimpleName());}
    private static void releaseBuffers() {
        if(before!=null)MemoryUtil.memFree(before);if(after!=null)MemoryUtil.memFree(after);if(raw!=null)MemoryUtil.memFree(raw);before=after=raw=null;
        if(fbo!=0){GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(colorTexture);GL30.glDeleteRenderbuffers(depthBuffer);fbo=colorTexture=depthBuffer=0;}
    }
    private static void release() {if(RenderSystem.isOnRenderThread())releaseBuffers();else RenderSystem.recordRenderCall(CloudDiagnostic::releaseBuffers);}

    private static final class State {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),drawBuffer=GL11.glGetInteger(GL11.GL_DRAW_BUFFER),readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        final int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),renderbuffer=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        final int[] viewport=new int[4];final float[] clear=new float[4];final byte[] masks=new byte[4];
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        static State capture(){return new State();}
        State(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);try(var stack=org.lwjgl.system.MemoryStack.stackPush()){ByteBuffer buffer=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,buffer);for(int i=0;i<4;i++)masks[i]=buffer.get(i);}}
        void restore(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glDrawBuffer(drawBuffer);GL11.glReadBuffer(readBuffer);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glClearColor(clear[0],clear[1],clear[2],clear[3]);GL11.glColorMask(masks[0]!=0,masks[1]!=0,masks[2]!=0,masks[3]!=0);GL11.glDepthMask(depthMask);
            flag(GL11.GL_BLEND,blend);flag(GL11.GL_DEPTH_TEST,depth);flag(GL11.GL_SCISSOR_TEST,scissor);GL30.glBindVertexArray(vao);GL20.glUseProgram(program);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,renderbuffer);
        }
        private static void flag(int key,boolean enabled){if(enabled)GL11.glEnable(key);else GL11.glDisable(key);}
    }
}
