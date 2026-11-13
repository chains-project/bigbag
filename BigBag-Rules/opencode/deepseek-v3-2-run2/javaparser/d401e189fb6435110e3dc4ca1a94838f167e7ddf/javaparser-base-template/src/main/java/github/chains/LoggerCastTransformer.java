import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LoggerCastTransformer {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java LoggerCastTransformer <source-directory>");
            return;
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().get();
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(CastExpr n, Void arg) {
                    super.visit(n, arg);
                    
                    // Check if this is a cast to Logger
                    String castType = n.getType().asString();
                    if (castType.equals("Logger")) {
                        System.out.println("Found cast to Logger at: " + javaFile + " - " + n.getRange().get());
                        
                        // Check if it's calling setLevel
                        if (n.getExpression() instanceof MethodCallExpr) {
                            MethodCallExpr getLoggerCall = (MethodCallExpr) n.getExpression();
                            if (getLoggerCall.getNameAsString().equals("getLogger")) {
                                System.out.println("  Calling getLogger");
                            }
                        }
                    }
                }
            }, null);
        }
    }
}