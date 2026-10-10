import java.nio.file.*;
import java.util.*;
import net.fabricmc.mappingio.*;
import net.fabricmc.mappingio.tree.*;
import net.fabricmc.mappingio.adapter.*;
import net.fabricmc.mappingio.format.*;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.accesswidener.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Remapper;
import java.util.zip.*;
public class MappingTool {
  public static void main(String[] a) throws Exception {
    if(a[0].equals("mappings")) {
      MemoryMappingTree tree=new MemoryMappingTree();
      MappingReader.read(Path.of(a[1]), new MappingNsRenamer(tree, Map.of("source","named","target","official")));
      try(MappingWriter w=MappingWriter.create(Path.of(a[2]),MappingFormat.TINY_2_FILE)){tree.accept(w);}
    } else if(a[0].equals("widen")) {
      AccessWidener aw=new AccessWidener();
      new AccessWidenerReader(aw).read(Files.readAllBytes(Path.of(a[3])),"named");
      try(ZipFile input=new ZipFile(a[1]);ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(Path.of(a[2])))) {
        for(ZipEntry entry:Collections.list(input.entries())) {
          if(entry.isDirectory())continue;
          byte[] data=input.getInputStream(entry).readAllBytes();
          if(entry.getName().endsWith(".class")) {
            ClassWriter writer=new ClassWriter(0);
            new ClassReader(data).accept(AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9,writer,aw),0);
            data=writer.toByteArray();
          }
          output.putNextEntry(new ZipEntry(entry.getName()));output.write(data);output.closeEntry();
        }
      }
    } else if(a[0].equals("remap-aw")) {
      MemoryMappingTree tree=new MemoryMappingTree();MappingReader.read(Path.of(a[3]),tree);
      int src=tree.getNamespaceId(a[4]),dst=tree.getNamespaceId(a[5]);
      Remapper mapper=new Remapper() {
        public String map(String name) { String mapped=tree.mapClassName(name,src,dst);return mapped==null?name:mapped; }
      };
      AccessWidenerWriter writer=new AccessWidenerWriter();
      new AccessWidenerReader(new AccessWidenerRemapper(writer,mapper,a[4],a[5])).read(Files.readAllBytes(Path.of(a[1])));
      Files.write(Path.of(a[2]),writer.write());
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

