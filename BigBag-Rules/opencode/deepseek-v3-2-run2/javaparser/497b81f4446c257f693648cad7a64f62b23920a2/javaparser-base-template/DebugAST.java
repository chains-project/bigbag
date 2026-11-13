import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DebugAST {
    public static void main(String[] args) throws IOException {
        Path file = Paths.get("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java");
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public MethodCallExpr visit(MethodCallExpr n, Void arg) {
                System.out.println("MethodCall: " + n.getNameAsString() + " at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                if (n.getScope().isPresent()) {
                    com.github.javaparser.ast.Node scope = n.getScope().get();
                    System.out.println("  Scope type: " + scope.getClass().getSimpleName());
                    System.out.println("  Scope: " + scope);
                    
                    if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objCreation = (ObjectCreationExpr) scope;
                        System.out.println("    ObjectCreation type: " + objCreation.getType().getNameAsString());
                        System.out.println("    Full ObjectCreation: " + objCreation);
                    }
                }
                return (MethodCallExpr) super.visit(n, arg);
            }
            
            @Override
            public ObjectCreationExpr visit(ObjectCreationExpr n, Void arg) {
                System.out.println("ObjectCreation: " + n.getType().getNameAsString() + " at line " + n.getRange().map(r -> r.begin.line).orElse(-1));
                System.out.println("  Full ObjectCreation: " + n);
                return (ObjectCreationExpr) super.visit(n, arg);
            }
        }, null);
    }
}