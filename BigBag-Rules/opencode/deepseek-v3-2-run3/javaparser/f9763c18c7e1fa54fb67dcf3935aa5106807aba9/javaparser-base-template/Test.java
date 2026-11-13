import com.github.javaparser.ast.visitor.ModifierVisitor;

public class Test {
    static class MyVisitor extends ModifierVisitor<Void> {
        public Node visit(ObjectCreationExpr expr, Void arg) {
            Visitable result = super.visit(expr, arg);
            return (Node) result;
        }
    }
}
