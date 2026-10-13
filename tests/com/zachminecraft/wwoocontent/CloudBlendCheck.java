package com.zachminecraft.wwoocontent;

import com.google.gson.JsonParser;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

/** Compare the release sampler with Minecraft's actual sky weights inside the real Fabric client. */
public final class CloudBlendCheck {
    private static final int WHITE=0xccffffff, PALE=0x66828282, PURPLE=0x66888899;
    private static void require(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual,double expected,String message) {
        require(Math.abs(actual-expected)<1e-12,message+": "+actual+" != "+expected);
    }
    private static void same(CloudBlend.Color a,CloudBlend.Color b,String message) {
        near(a.red(),b.red(),message+" red");near(a.green(),b.green(),message+" green");near(a.blue(),b.blue(),message+" blue");
    }
    private static double skyWeight(double x,double y,double z,CloudBlend.BiomeColors mask) {
        // This is the unshaded biome contribution in Minecraft 1.21.1's getSkyColor.
        return CubicSampler.gaussianSampleVec3(new Vec3(x,y,z).subtract(2,2,2).scale(.25),
            (qx,qy,qz) -> mask.atQuart(qx,qy,qz)==0 ? Vec3.ZERO : new Vec3(1,1,1)).x;
    }
    private static void weighted(CloudBlend.Color actual,int a,int b,double weight,String message) {
        var source=CloudBlend.Color.fromArgb(a);var target=CloudBlend.Color.fromArgb(b);
        near(actual.red(),source.red()*(1-weight)+target.red()*weight,message+" red");
        near(actual.green(),source.green()*(1-weight)+target.green()*weight,message+" green");
        near(actual.blue(),source.blue()*(1-weight)+target.blue()*weight,message+" blue");
    }
    public static void check() throws Exception {
        var palettes=new ArrayList<Integer>();palettes.add(WHITE);
        try(var stream=CloudBlendCheck.class.getResourceAsStream("/data/wwoocontent/biome_attributes.json")) {
            require(stream!=null,"release biome attributes available");
            var attributes=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:attributes.entrySet()) {
                var value=entry.getValue().getAsJsonObject().getAsJsonObject("attributes").get("minecraft:visual/cloud_color");
                if(value!=null)palettes.add((int)Long.parseLong(value.getAsString().substring(1),16));
            }
        }
        for(int a:palettes) for(int b:palettes) {
            CloudBlend.BiomeColors volume=(x,y,z) -> x<0 ? a:b;
            same(CloudBlend.sample(-14,64,0,volume),CloudBlend.Color.fromArgb(a),"source interior exact");
            same(CloudBlend.sample(14,64,0,volume),CloudBlend.Color.fromArgb(b),"destination interior exact");
            weighted(CloudBlend.sample(0,64,0,volume),a,b,.5,"straight boundary midpoint");
            double previous=0;
            for(int step=0;step<=120;step++) {
                double x=-14+28*step/120.0;
                double weight=skyWeight(x,64,0,(qx,qy,qz)->qx<0 ? 0:1);
                require(weight>=previous-1e-12,"destination influence rises with movement");previous=weight;
                var color=CloudBlend.sample(x,64,0,volume);
                weighted(color,a,b,weight,"actual native sky blend weights");
                // Read the same position after visiting another biome: no timer or crossing history.
                CloudBlend.sample(100,64,0,volume);
                same(CloudBlend.sample(x,64,0,volume),color,"stationary/reversed sampling retains position color");
                for(int repeat=0;repeat<3;repeat++)same(CloudBlend.sample(x,64,0,volume),color,"same-frame coherence");
            }
        }
        var random=new Random(21115);
        for(int n=0;n<200;n++) {
            double x=random.nextDouble()*400-200,y=random.nextDouble()*80-40,z=random.nextDouble()*400-200;
            for(int axis=0;axis<3;axis++) {
                final int direction=axis;
                CloudBlend.BiomeColors mask=(qx,qy,qz) -> switch(direction) {
                    case 0 -> qx<0?0:1;
                    case 1 -> qy<0?0:1;
                    default -> qx+qz<0?0:1;
                };
                weighted(CloudBlend.sample(x,y,z,(qx,qy,qz)->mask.atQuart(qx,qy,qz)==0 ? PURPLE:PALE),
                    PURPLE,PALE,skyWeight(x,y,z,mask),"negative/vertical/diagonal sky coherence");
            }
            var first=CloudBlend.sample(x,y,z,(qx,qy,qz)->qx<0 ? 0x11ffffff:PALE);
            var alpha=CloudBlend.sample(x,y,z,(qx,qy,qz)->qx<0 ? WHITE:0xff828282);
            same(first,alpha,"encoded biome alpha never biases RGB weights");
            same(CloudBlend.sample(x,y,z,(qx,qy,qz)->PALE),CloudBlend.Color.fromArgb(PALE),"constant biome exact everywhere");
        }
        // The sky fetches 6^3 quart-biome samples. Sky-sized locality must remain bounded.
        int[] calls={0};CloudBlend.sample(.123,64.456,-.789,(x,y,z)->{calls[0]++;return WHITE;});
        require(calls[0]==216,"use the native sky's bounded 6x6x6 sampler");
        // Different client worlds / palettes cannot retain an old color because there is no stored fade.
        same(CloudBlend.sample(0,64,0,(x,y,z)->PALE),CloudBlend.Color.fromArgb(PALE),"new world palette immediate");
        System.out.println("CLOUD_SKY_BLEND_PASS: "+palettes.size()*palettes.size()+" biome palette pairs match native sky weights, movement/stop/reversal, negative/vertical/diagonal boundaries, exact interiors and alpha independence");
    }
}
