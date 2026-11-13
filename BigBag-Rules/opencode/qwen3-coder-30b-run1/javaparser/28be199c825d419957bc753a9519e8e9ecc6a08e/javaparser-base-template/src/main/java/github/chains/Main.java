package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Find and transform buildDependencyGraph calls
            cu.accept(new DependencyGraphVisitor(), null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class DependencyGraphVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a buildDependencyGraph call on a DependencyGraphBuilder
            if (methodCall.getNameAsString().equals("buildDependencyGraph")) {
                // Check if it's called on a DependencyGraphBuilder instance
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    // Check if it's a call with 2 parameters (old signature)
                    if (methodCall.getArguments().size() == 2) {
                        // Create a new method call with 3 parameters (new signature)
                        NodeList<Expression> newArguments = new NodeList<>();
                        
                        // Replace first argument: project -> getBuildingRequest()
                        // We need to find if the first argument is a variable named 'project'
                        Expression firstArg = methodCall.getArgument(0);
                        Expression newFirstArg;
                        
                        if (firstArg instanceof NameExpr && "project".equals(((NameExpr) firstArg).getNameAsString())) {
                            // Replace 'project' with 'getBuildingRequest()'
                            newFirstArg = new MethodCallExpr(new ThisExpr(), "getBuildingRequest");
                        } else {
                            // If it's not 'project', just use the original
                            newFirstArg = firstArg;
                        }
                        newArguments.add(newFirstArg);
                        
                        // Second argument stays the same
                        newArguments.add(methodCall.getArgument(1));
                        
                        // Add the third argument: empty collection (null)
                        newArguments.add(new NullLiteralExpr());
                        
                        // Replace the method call with the new signature
                        methodCall.setArguments(newArguments);
                    }
                }
            }
        }
    }
}
