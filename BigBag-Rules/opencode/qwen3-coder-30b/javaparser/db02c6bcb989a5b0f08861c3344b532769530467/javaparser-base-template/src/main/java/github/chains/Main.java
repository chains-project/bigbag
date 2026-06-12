package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic JavaParser transformation to fix Content.From constructor calls.
 * This fixes breaking changes in asto-core v1.15.3 where Content.From(long, Publisher) 
 * constructor signature changed to Content.From(Optional&lt;Long&gt;, Publisher).
 */
public class Main {
    
    /**
     * Entry point for the transformation.
     * 
     * @param args Command line arguments (source directory)
     * @throws IOException if there's an I/O error
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        fixContentFromConstructors(sourceDir);
    }
    
    /**
     * Visitor that fixes Content.From constructor calls.
     */
    static class ContentFromFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Content.From constructor call
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("Content.From".equals(type.asString())) {
                    // Check if this matches the old signature: Content.From(long, Publisher)
                    List<Expression> args = n.getArguments();
                    if (args.size() == 2) {
                        // Fix the call by wrapping the first argument with Optional.of()
                        return fixContentFromCall(n);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private ObjectCreationExpr fixContentFromCall(ObjectCreationExpr original) {
            List<Expression> args = original.getArguments();
            
            // Create new object creation with Optional.of(long) as first argument
            ObjectCreationExpr newExpr = new ObjectCreationExpr();
            newExpr.setType("Content.From");
            
            // Wrap first argument with Optional.of()
            Expression firstArg = args.get(0);
            Expression optionalCall = StaticJavaParser.parseExpression("Optional.of(" + firstArg.toString() + ")");
            newExpr.addArgument(optionalCall);
            
            // Add the second argument as is
            newExpr.addArgument(args.get(1));
            
            return newExpr;
        }
    }
    
    /**
     * Fix Content.From constructor calls with the appropriate signature.
     * 
     * @param sourceDir The source directory to process
     * @throws IOException if there's an I/O error
     */
    public static void fixContentFromConstructors(String sourceDir) throws IOException {
        Path dir = Paths.get(sourceDir);
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(path.toFile()));
                    cu.accept(new ContentFromFixVisitor(), null);
                    
                    // Save the modified file
                    Files.write(path, cu.toString().getBytes());
                } catch (Exception e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            });
    }
}