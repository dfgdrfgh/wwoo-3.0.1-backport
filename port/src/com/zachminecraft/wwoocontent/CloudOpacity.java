package com.zachminecraft.wwoocontent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.LoggerFactory;

/** Limit Cirrus's sun opacity override to its original Fancy path, retaining all RGB lighting. */
public final class CloudOpacity {
    public static final String UNIFORM="WwooFastOpacity";
    private static final String SUN_ALPHA="color.a = mix(color.a, 1.0, sunIllumination * CirrusSunWeight);";
    private static final ClassValue<Method> STYLE=new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try {Method method=type.getDeclaredMethod("style");method.setAccessible(true);return method;}
            catch(ReflectiveOperationException error){throw new IllegalArgumentException(error);}
        }
    };
    private CloudOpacity() {}
    public static boolean fast(Object definition) {
        try {return "FAST".equals(String.valueOf(STYLE.get(definition.getClass()).invoke(definition)));}
        catch(ReflectiveOperationException|IllegalArgumentException error){return false;}
    }
    public static ResourceProvider resources(ResourceProvider original) {
        return id->{
            var found=original.getResource(id);
            if(!id.getNamespace().equals("cirrus"))return found;
            String path=id.getPath();
            if(!path.equals("shaders/core/cirrus_clouds.json")&&!path.equals("shaders/core/cirrus_clouds.fsh"))return found;
            return found.map(resource->new Resource(resource.source(),()->{
                String text;
                try(var input=resource.open()){text=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
                return new ByteArrayInputStream(patch(path,text).getBytes(StandardCharsets.UTF_8));
            },resource::metadata));
        };
    }
    public static String patch(String path,String source) {
        if(path.endsWith(".json")) {
            var json=JsonParser.parseString(source).getAsJsonObject();var uniforms=json.getAsJsonArray("uniforms");
            if(uniforms==null)return source;
            for(var entry:uniforms)if(UNIFORM.equals(entry.getAsJsonObject().get("name").getAsString()))return source;
            var uniform=new JsonObject();uniform.addProperty("name",UNIFORM);uniform.addProperty("type","float");uniform.addProperty("count",1);
            var values=new com.google.gson.JsonArray();values.add(0.0f);uniform.add("values",values);uniforms.add(uniform);
            return json.toString();
        }
        if(source.contains("uniform float "+UNIFORM+";"))return source;
        if(!source.contains("uniform float CirrusSunWeight;")||!source.contains(SUN_ALPHA)) {
            LoggerFactory.getLogger("WWOO").warn("Cirrus cloud shader differs from the supported source; retaining its original opacity behavior");return source;
        }
        return source.replace("uniform float CirrusSunWeight;","uniform float CirrusSunWeight;\nuniform float "+UNIFORM+";")
            .replace(SUN_ALPHA,"color.a = mix(color.a, 1.0, sunIllumination * CirrusSunWeight * (1.0 - "+UNIFORM+"));");
    }
}
