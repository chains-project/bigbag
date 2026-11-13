import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.stmt.Statement;

public class TestJavaParser {
    public static void main(String[] args) {
        Statement stmt = JavaParser.parseStatement("throw new IOException(\"test\");");
        System.out.println(stmt);
    }
}
