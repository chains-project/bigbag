import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestAST {
    public static void main(String[] args) throws IOException {
        Path file = Paths.get("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java");
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        
        // Print the entire AST to understand the structure
        System.out.println("Full AST for line 51:");
        System.out.println(cu.toString());
    }
}