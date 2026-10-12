import java.nio.file.*;
import java.util.*;
import net.fabricmc.mappingio.*;
import net.fabricmc.mappingio.tree.*;
import net.fabricmc.mappingio.adapter.*;
import net.fabricmc.mappingio.format.*;
import net.fabricmc.tinyremapper.*;
public class MappingTool {
  public static void main(String[] a) throws Exception {
    if(a[0].equals("mappings")) {
      MemoryMappingTree tree=new MemoryMappingTree();
      MappingReader.read(Path.of(a[1]), new MappingNsRenamer(tree, Map.of("source","named","target","official")));
      try(MappingWriter w=MappingWriter.create(Path.of(a[2]),MappingFormat.TINY_2_FILE)){tree.accept(w);}
    } else {
      TinyRemapper r=TinyRemapper.newRemapper().withMappings(TinyUtils.createTinyMappingProvider(Path.of(a[3]),a[4],a[5])).renameInvalidLocals(true).build();
      try(OutputConsumerPath o=new OutputConsumerPath.Builder(Path.of(a[2])).build()) {
        o.addNonClassFiles(Path.of(a[1]));r.readInputs(Path.of(a[1]));
        for(int i=6;i<a.length;i++)r.readClassPath(Path.of(a[i]));
        r.apply(o);
      } finally {r.finish();}
    }
  }
}
