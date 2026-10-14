package com.zachminecraft.wwoocontent;

import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Check release hooks on real client classes with native camera and biome-manager sampling. */
public final class CloudBaselineProbe implements PreLaunchEntrypoint {
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static void near(double actual,double expected,String message) {
        require(Math.abs(actual-expected)<2e-7,message+": "+actual+" != "+expected);
    }
    private static Object unconstructed(Class<?> type) throws Exception {
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
    }
    private static Field field(Class<?> type,Class<?> valueType,boolean isStatic) throws Exception {
        for(Class<?> cursor=type;cursor!=null;cursor=cursor.getSuperclass())
            for(Field f:cursor.getDeclaredFields())if(f.getType()==valueType && Modifier.isStatic(f.getModifiers())==isStatic) {
                f.setAccessible(true);return f;
            }
        throw new NoSuchFieldException(type+" field of type "+valueType);
    }
    private static Method hook(Class<?> type,String name,int parameters) {
        var matches=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains(name)
            && m.getParameterCount()==parameters).toList();require(matches.size()==1,"one applied "+name+" hook");
        var method=matches.getFirst();method.setAccessible(true);return method;
    }
    @Override public void onPreLaunch() {
        try {
            check();
            System.out.println("CLOUD_BASELINE_RUNTIME_PASS: native camera/biome lookup; all 22 original biome RGB/alpha values; immediate crossings/reversal; alpha-buffer rebuild only when needed; standalone-presence bypass="
                +FabricLoader.getInstance().isModLoaded("biome_cloud_transitions"));
            System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static void check() throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        boolean bypass=FabricLoader.getInstance().isModLoaded("biome_cloud_transitions");
        require(CloudAttributes.enabled()!=bypass,"baseline yields to standalone mod presence");
        var palette=JsonParser.parseReader(new InputStreamReader(CloudBaselineProbe.class.getResourceAsStream(
            "/data/wwoocontent/biome_attributes.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        var registry=new MappedRegistry<Biome>(Registries.BIOME,Lifecycle.stable());
        for(var entry:palette.entrySet())if(entry.getValue().getAsJsonObject().getAsJsonObject("attributes").has("minecraft:visual/cloud_color"))
            Registry.register(registry,ResourceKey.create(Registries.BIOME,ResourceLocation.parse(entry.getKey())),(Biome)unconstructed(Biome.class));
        var unknownKey=ResourceKey.create(Registries.BIOME,ResourceLocation.parse("audit:no_cloud_color"));
        Registry.register(registry,unknownKey,(Biome)unconstructed(Biome.class));
        var current=new AtomicReference<Holder<Biome>>(registry.getHolderOrThrow(unknownKey));
        ClientLevel level=(ClientLevel)unconstructed(ClientLevel.class);
        field(ClientLevel.class,BiomeManager.class,false).set(level,new BiomeManager((x,y,z)->current.get(),0));
        var camera=new Camera();field(Camera.class,Entity.class,false).set(camera,unconstructed(Pig.class));
        var position=Arrays.stream(Camera.class.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),
            new Class<?>[]{double.class,double.class,double.class}) && m.getReturnType()==void.class).findFirst().orElseThrow();
        position.setAccessible(true);position.invoke(camera,0d,64d,0d);
        GameRenderer gameRenderer=(GameRenderer)unconstructed(GameRenderer.class);
        field(GameRenderer.class,Camera.class,false).set(gameRenderer,camera);
        Minecraft client=(Minecraft)unconstructed(Minecraft.class);client.level=level;
        field(Minecraft.class,GameRenderer.class,false).set(client,gameRenderer);
        field(Minecraft.class,Minecraft.class,true).set(null,client);

        // Class loading applies the actual release mixins and validates all target selectors.
        Class<?> rendererClass=Class.forName("net.minecraft.class_761");
        Object renderer=unconstructed(rendererClass);
        var tint=hook(ClientLevel.class,"wwoo$cloudColor",2);
        var alpha=hook(rendererClass,"wwoo$cloudAlpha",1);
        var refresh=hook(rendererClass,"wwoo$refreshCloudAlpha",1);
        var previous=rendererClass.getDeclaredField("wwoo$previousAlpha");previous.setAccessible(true);previous.setFloat(renderer,-1f);
        var rebuild=rendererClass.getDeclaredField("field_4107");rebuild.setAccessible(true);
        Vec3 base=new Vec3(.7,.8,.9);
        int checked=0;
        for(int pass=0;pass<2;pass++)for(var entry:palette.entrySet()) {
            var attr=entry.getValue().getAsJsonObject().getAsJsonObject("attributes").get("minecraft:visual/cloud_color");
            if(attr==null)continue;
            int encoded=attr.isJsonPrimitive()&&attr.getAsJsonPrimitive().isString()
                ?(int)Long.parseLong(attr.getAsString().substring(1),16):attr.getAsInt();
            current.set(registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME,ResourceLocation.parse(entry.getKey()))));
            require(CloudAttributes.color()==encoded,"current camera biome selects exact encoded palette immediately");
            var callback=new CallbackInfoReturnable<Vec3>("getCloudColor",true,base);
            tint.invoke(level,.5f,callback);var rgb=callback.getReturnValue();
            double[] expected=bypass?new double[]{base.x,base.y,base.z}:
                new double[]{base.x*((encoded>>16)&255)/255.0,base.y*((encoded>>8)&255)/255.0,base.z*(encoded&255)/255.0};
            near(rgb.x,expected[0],"actual cloud red hook");near(rgb.y,expected[1],"actual cloud green hook");near(rgb.z,expected[2],"actual cloud blue hook");
            require(callback.isCancelled()!=bypass,"only baseline owns the return value when active");
            float expectedAlpha=(encoded>>>24)/255f;
            near((float)alpha.invoke(renderer,.65f),bypass?.65f:expectedAlpha,"native alpha preserved under bypass; original encoded alpha otherwise");
            float old=previous.getFloat(renderer);rebuild.setBoolean(renderer,false);
            refresh.invoke(renderer,new CallbackInfo("renderClouds",false));
            require(rebuild.getBoolean(renderer)==(!bypass&&old!=expectedAlpha),"buffer rebuild exactly matches original alpha changes");
            rebuild.setBoolean(renderer,false);refresh.invoke(renderer,new CallbackInfo("renderClouds",false));
            require(!rebuild.getBoolean(renderer),"same biome never rebuilds clouds each frame");checked++;
        }
        require(checked==44,"all 22 cloud palettes exercised twice");
        for(String name:new String[]{"minecraft:pale_garden","minecraft:desert","minecraft:pale_garden","audit:no_cloud_color"}) {
            current.set(registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME,ResourceLocation.parse(name))));
            int expected=name.equals("minecraft:pale_garden")?0x66828282:name.equals("minecraft:desert")?0x11ffffff:0xccffffff;
            require(CloudAttributes.color()==expected,"crossing/reversal or missing palette uses immediate exact color");
        }
        client.level=null;require(CloudAttributes.color()==0xccffffff,"leaving world restores default palette");
    }
}
