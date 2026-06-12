package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path directory = Paths.get(sourceDirectory);
        
        try {
            Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to replace FilterBuilder.apply() with FilterBuilder.test()
            FilterBuilderApplyReplacer replacer = new FilterBuilderApplyReplacer();
            cu.accept(replacer, null);
            
            Files.writeString(filePath, cu.toString());
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    static class FilterBuilderApplyReplacer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCallExpr, Void arg) {
            // Check if this is a call to apply() 
            if (methodCallExpr.getNameAsString().equals("apply")) {
                // Check if the scope is a FilterBuilder expression
                if (methodCallExpr.getScope().isPresent()) {
                    Expression scope = methodCallExpr.getScope().get();
                    // Check if the method call we're looking at is on a FilterBuilder object
                    if (isFilterBuilderMethodCall(scope)) {
                        // Replace apply with test
                        methodCallExpr.setName("test");
                    }
                }
            }
            return super.visit(methodCallExpr, arg);
        }
        
        private boolean isFilterBuilderMethodCall(Expression expr) {
            // Check if the expression is a FilterBuilder method call
            if (expr.isMethodCallExpr()) {
                MethodCallExpr methodCall = expr.asMethodCallExpr();
                if (methodCall.getNameAsString().equals("include") ||
                    methodCall.getNameAsString().equals("exclude") ||
                    methodCall.getNameAsString().equals("includePackage") ||
                    methodCall.getNameAsString().equals("excludePackage") ||
                    methodCall.getNameAsString().equals("includePattern") ||
                    methodCall.getNameAsString().equals("excludePattern")) {
                    return true;
                }
            }
            return false;
        }
    }
}
