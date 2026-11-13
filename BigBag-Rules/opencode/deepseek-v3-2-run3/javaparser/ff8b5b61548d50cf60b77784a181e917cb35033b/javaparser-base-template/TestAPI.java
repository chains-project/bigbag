import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;

public class TestAPI {
    static class TestVisitor extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            return super.visit(n, arg);
        }
    }
    
    public static void main(String[] args) {
        System.out.println("Test compiled");
    }
}
