package com.zachminecraft.wwoocontent;

import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.*;
import java.util.*;

/** The 26.3 floating-point noise sampler and state selection used by WWOO 3.0.1. */
public final class ModernNoiseProvider extends BlockStateProvider {
    static BlockStateProviderType<ModernNoiseProvider> TYPE;
    static final MapCodec<ModernNoiseProvider> MAP=Codec.PASSTHROUGH.xmap(d -> d.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),j -> new Dynamic<>(JsonOps.INSTANCE,j)).fieldOf("settings").xmap(ModernNoiseProvider::new,p -> p.data);
    private final JsonObject data;
    private final Sampler noise,slow;
    private final List<BlockState> states,low,high;
    private final BlockState fallback;
    ModernNoiseProvider(JsonObject data) {
        this.data=data;long seed=data.get("seed").getAsLong();noise=new Sampler(seed,data.getAsJsonObject("noise"));
        slow=data.has("slow_noise")?new Sampler(seed,data.getAsJsonObject("slow_noise")):null;
        states=parseStates(data,"states");low=parseStates(data,"low_states");high=parseStates(data,"high_states");
        fallback=data.has("default_state")?BlockState.CODEC.parse(JsonOps.INSTANCE,data.get("default_state")).getOrThrow(false, message -> { throw new IllegalStateException(message); }):null;
    }
    private static List<BlockState> parseStates(JsonObject data,String field) {
        if(!data.has(field))return List.of();
        return java.util.stream.StreamSupport.stream(data.getAsJsonArray(field).spliterator(), false).map(v -> BlockState.CODEC.parse(JsonOps.INSTANCE,v).getOrThrow(false, message -> { throw new IllegalStateException(message); })).toList();
    }
    @Override protected BlockStateProviderType<?> type() { return TYPE; }
    private float sample(Sampler n,BlockPos p,double scale) { return n.sample(p.getX()*scale,p.getY()*scale,p.getZ()*scale); }
    private float slow(BlockPos p) { float scale=data.get("slow_scale").getAsFloat();return slow.sample((float)p.getX()*scale,(float)p.getY()*scale,(float)p.getZ()*scale); }
    private BlockState choose(List<BlockState> values,float noise) { return values.get((int)(Mth.clamp((1F+noise)/2F,0F,.9999F)*values.size())); }
    @Override public BlockState getState(RandomSource r,BlockPos p) {
        String kind=data.get("kind").getAsString();float scale=data.get("scale").getAsFloat();
        if(kind.equals("noise_threshold")) {
            float value=sample(noise,p,scale);
            if(value<data.get("threshold").getAsFloat())return low.get(r.nextInt(low.size()));
            return r.nextFloat()<data.get("high_chance").getAsFloat()?high.get(r.nextInt(high.size())):fallback;
        }
        if(slow!=null) {
            var range=data.getAsJsonObject("variety");int min=range.get("min_inclusive").getAsInt(),max=range.get("max_inclusive").getAsInt();
            int count=(int)Mth.clampedMap((double)slow(p),-1D,1D,min,max+1D);
            var choices=new ArrayList<BlockState>(count);
            for(int i=0;i<count;i++)choices.add(choose(states,slow(p.offset(i*54545,0,i*34234))));
            return choose(choices,sample(noise,p,scale));
        }
        return choose(states,sample(noise,p,scale));
    }
    static final class Sampler {
        final List<Layer> layers=new ArrayList<>();
        Sampler(long seed,JsonObject cfg) {
            var random=new WorldgenRandom(new LegacyRandomSource(seed));var first=random.forkPositional();var second=random.forkPositional();
            int base=cfg.get("base_octave").getAsInt(),count=cfg.has("octave_count")?cfg.get("octave_count").getAsInt():1;
            double baseAmp=cfg.has("base_amplitude")?cfg.get("base_amplitude").getAsDouble():1D;
            boolean legacy=cfg.has("normalize")&&cfg.get("normalize").isJsonPrimitive()&&cfg.get("normalize").getAsString().equals("legacy");
            boolean normalized=!cfg.has("normalize")||legacy||cfg.get("normalize").getAsBoolean();
            double amp=baseAmp*(normalized?Math.pow(.5,-(count-1))/(Math.pow(.5,-count)-1):1),sum=0,variance=0;
            double[] weights=new double[count];int firstIndex=Integer.MAX_VALUE,lastIndex=Integer.MIN_VALUE;
            for(int i=0;i<count;i++) {
                double modifier=cfg.has("amplitude_modifiers")?cfg.getAsJsonArray("amplitude_modifiers").get(i).getAsDouble():1;
                weights[i]=amp*modifier;amp*=.5;sum+=Math.abs(weights[i]);variance+=Math.pow(.2702247831245211*Math.abs(weights[i]),2);
                if(modifier!=0) {firstIndex=Math.min(firstIndex,i);lastIndex=Math.max(lastIndex,i);}
            }
            double norm=variance==0?0:sum/3/(Math.sqrt(variance)*Math.sqrt(2));
            if(legacy&&norm!=0)norm=baseAmp*.5/3/(.1*(1+1D/(lastIndex-firstIndex+1)));
            for(int i=0;i<count;i++) if(weights[i]!=0) {
                double frequency=Math.pow(2,base+i);float amplitude=(float)(norm*weights[i]);String octave="octave_"+(base+i);
                layers.add(new Layer(new Perlin(first.fromHashOf(octave)),frequency,amplitude));
                layers.add(new Layer(new Perlin(second.fromHashOf(octave)),frequency*1.0181268882175227,amplitude));
            }
        }
        float sample(double x,double y,double z) { float result=0;for(var l:layers)result+=l.amplitude*l.noise.get(x*l.frequency,y*l.frequency,z*l.frequency);return result; }
    }
    record Layer(Perlin noise,double frequency,float amplitude) {}
    static final class Perlin {
        static final int[][] GRADS={{1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},{1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},{0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1},{1,1,0},{0,-1,1},{-1,1,0},{0,-1,-1}};
        final byte[] permutations=new byte[256];final double ox,oy,oz;
        Perlin(RandomSource r) { ox=r.nextDouble()*256;oy=r.nextDouble()*256;oz=r.nextDouble()*256;for(int i=0;i<256;i++)permutations[i]=(byte)i;for(int i=0;i<256;i++){int j=i+r.nextInt(256-i);byte v=permutations[i];permutations[i]=permutations[j];permutations[j]=v;} }
        int perm(int n) { return permutations[n&255]&255; }
        float dot(int hash,float x,float y,float z) { var g=GRADS[hash&15];return g[0]*x+g[1]*y+g[2]*z; }
        static double wrap(double v) { return v>=-Math.nextDown(16777216D)&&v<Math.nextDown(16777216D)?v:v-Math.floor(v/33554432D+.5)*33554432D; }
        static float smooth(float v) { return v*v*v*(v*(v*6F-15F)+10F); }
        static float lerp(float v,float a,float b) { return a+v*(b-a); }
        float get(double xx,double yy,double zz) {
            double x=wrap(xx)+ox,y=wrap(yy)+oy,z=wrap(zz)+oz;int ix=Mth.floor(x),iy=Mth.floor(y),iz=Mth.floor(z);
            float rx=(float)(x-ix),ry=(float)(y-iy),rz=(float)(z-iz),a=smooth(rx),b=smooth(ry),c=smooth(rz);
            int x0=perm(ix),x1=perm(ix+1),xy00=perm(x0+iy),xy01=perm(x0+iy+1),xy10=perm(x1+iy),xy11=perm(x1+iy+1);
            return lerp(c,lerp(b,lerp(a,dot(perm(xy00+iz),rx,ry,rz),dot(perm(xy10+iz),rx-1,ry,rz)),lerp(a,dot(perm(xy01+iz),rx,ry-1,rz),dot(perm(xy11+iz),rx-1,ry-1,rz))),lerp(b,lerp(a,dot(perm(xy00+iz+1),rx,ry,rz-1),dot(perm(xy10+iz+1),rx-1,ry,rz-1)),lerp(a,dot(perm(xy01+iz+1),rx,ry-1,rz-1),dot(perm(xy11+iz+1),rx-1,ry-1,rz-1))));
        }
    }
}
