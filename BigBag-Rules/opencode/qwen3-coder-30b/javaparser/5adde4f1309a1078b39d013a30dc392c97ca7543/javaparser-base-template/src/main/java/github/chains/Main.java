package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

public class Main {
    public static void main(String[] args) {
        // This is a placeholder for the transformation logic
        // The actual transformation will be implemented in a more targeted way
        System.out.println("Transformation rule template created for addEnabledLanguages API change");
    }
    
    public static class TransformVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Match calls to addEnabledLanguages with a Set parameter  
            if (methodCall.getNameAsString().equals("addEnabledLanguages") 
                && methodCall.getArguments().size() == 1) {
                
                // Get the scope (the builder object)
                var scope = methodCall.getScope().orElse(null);
                
                // Create a new method call with the correct method name
                var newMethodCall = new MethodCallExpr(
                    scope,
                    new SimpleName("addEnabledLanguage"),
                    methodCall.getArguments()
                );
                
                return newMethodCall;
            }
            
            return super.visit(methodCall, arg);
        }
    }
}
