import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.nio.file.*;
public class ReferenceNoise {
 public static void main(String[] args) throws Exception {
  var configs=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var out=new JsonArray();
  for(var row:configs){var v=row.getAsJsonObject();var params=NormalNoise.DIRECT_CODEC.parse(JsonOps.INSTANCE,v.get("noise")).getOrThrow();var n=params.create(new WorldgenRandom(new LegacyRandomSource(v.get("seed").getAsLong())));var values=new JsonArray();
   for(int i=0;i<500;i++){double x=(i-250)*1234.567,y=(i%384)-64.789,z=(i-270)*-765.432;values.add(Float.floatToIntBits(n.get(x,y,z)));}out.add(values);
  }Files.writeString(Path.of(args[1]),out.toString());
 }
}
