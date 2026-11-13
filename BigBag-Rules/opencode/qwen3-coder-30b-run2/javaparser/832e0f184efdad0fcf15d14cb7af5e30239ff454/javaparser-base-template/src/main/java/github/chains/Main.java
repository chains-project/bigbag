package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the input directory
        String inputDir = args.length > 0 ? args[0] : "/workspace/WorldwideChat/src";
        
        Files.walk(Paths.get(inputDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to replace parseEnchantment calls
            cu.accept(new ParseEnchantmentTransformationVisitor(), null);
            
            // Save the modified file
            String code = cu.toString();
            Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ParseEnchantmentTransformationVisitor extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to parseEnchantment()
            if (methodCall.getNameAsString().equals("parseEnchantment")) {
                // Get the expression that calls parseEnchantment
                Expression scope = methodCall.getScope().orElse(null);
                
                // Check if it's a method call expression ending with ".get()"
                if (scope instanceof MethodCallExpr) {
                    MethodCallExpr scopeMethodCall = (MethodCallExpr) scope;
                    
                    // If the scope method is get() and it's a call to matchXEnchantment
                    if (scopeMethodCall.getNameAsString().equals("get") && 
                        scopeMethodCall.getScope().isPresent()) {
                        
                        Expression scopeOfScope = scopeMethodCall.getScope().get();
                        
                        // Look for the pattern: XEnchantment.matchXEnchantment(...).get().parseEnchantment()
                        if (scopeOfScope instanceof MethodCallExpr) {
                            MethodCallExpr parentCall = (MethodCallExpr) scopeOfScope;
                            
                            if (parentCall.getNameAsString().equals("matchXEnchantment") &&
                                parentCall.getScope().isPresent()) {
                                
                                Expression parentScope = parentCall.getScope().get();
                                
                                // Check if it's XEnchantment class call
                                if (parentScope instanceof NameExpr) {
                                    NameExpr nameExpr = (NameExpr) parentScope;
                                    if (nameExpr.getNameAsString().equals("XEnchantment")) {
                                        // Replace parseEnchantment with getEnchant
                                        methodCall.setName("getEnchant");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
