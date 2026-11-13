import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.stmt.Statement;

public class TestParse {
    public static void main(String[] args) {
        JavaParser parser = new JavaParser();
        var result = parser.parseStatement("throw new IOException(\"test\");");
        if (result.isSuccessful()) {
            Statement stmt = result.getResult().get();
            System.out.println("Parsed: " + stmt);
        } else {
            System.out.println("Failed to parse");
        }
    }
}
