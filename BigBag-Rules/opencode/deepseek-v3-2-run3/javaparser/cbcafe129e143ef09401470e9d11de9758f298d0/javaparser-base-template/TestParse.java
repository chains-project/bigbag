import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestParse {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Paths.get("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java"));
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        System.out.println("Parse successful: " + (cu != null));
    }
}
