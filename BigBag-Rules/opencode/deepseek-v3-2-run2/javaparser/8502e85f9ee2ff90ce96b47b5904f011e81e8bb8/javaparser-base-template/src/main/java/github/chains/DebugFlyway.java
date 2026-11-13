package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DebugFlyway {
    public static void main(String[] args) throws Exception {
        Path filePath = Paths.get("/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java");
        
        JavaParser parser = new JavaParser(new ParserConfiguration());
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
        
        cu.walk(MethodCallExpr.class, call -> {
            if (call.getNameAsString().equals("configure") && 
                call.getScope().isPresent() && 
                call.getScope().get().toString().equals("Flyway")) {
                System.out.println("Found Flyway.configure() call:");
                System.out.println("  Full expression: " + call.toString());
                
                // Find the parent assignment to see the full chain
                call.findAncestor(com.github.javaparser.ast.expr.AssignExpr.class).ifPresent(assign -> {
                    System.out.println("  Assignment: " + assign.toString());
                });
                
                // Look for method calls that have this configure call as their scope
                cu.walk(MethodCallExpr.class, childCall -> {
                    if (childCall.getScope().isPresent() && childCall.getScope().get().equals(call)) {
                        System.out.println("  Direct child call: " + childCall.getNameAsString());
                    }
                });
                
                System.out.println();
            }
        });
    }
}