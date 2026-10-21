import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Syntax-only verification; this deliberately does not claim Android SDK compilation. */
public final class SourceCheck {
    public static void main(String[] args)throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager files=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)){
            List<java.io.File> sources=new ArrayList<>();try(var walk=Files.walk(Path.of("app/src/main/java"))){walk.filter(p->p.toString().endsWith(".java")).forEach(p->sources.add(p.toFile()));}
            JavacTask task=(JavacTask)compiler.getTask(null,files,diagnostics,List.of("-proc:none"),null,files.getJavaFileObjectsFromFiles(sources));
            task.parse();for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(d.toString());
            System.out.println("PASS: Java syntax parsed for "+sources.size()+" native source files (Android SDK type/build checks require SDK).");
        }
    }
}
