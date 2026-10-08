package com.zachminecraft.wwoocontent;
import com.google.gson.*;
import java.nio.file.*;
public class BackportNoise {
 public static void main(String[] args) throws Exception {
  var configs=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var out=new JsonArray();
  for(var row:configs){var v=row.getAsJsonObject();var n=new ModernNoiseProvider.Sampler(v.get("seed").getAsLong(),v.getAsJsonObject("noise"));var values=new JsonArray();
   for(int i=0;i<500;i++){double x=(i-250)*1234.567,y=(i%384)-64.789,z=(i-270)*-765.432;values.add(Float.floatToIntBits(n.sample(x,y,z)));}out.add(values);
  }Files.writeString(Path.of(args[1]),out.toString());
 }
}
