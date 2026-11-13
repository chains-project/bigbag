package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic transformation rule for FOP 2.2 breaking API change.
 * 
 * Old API pattern: FopFactory.newInstance()
 * New API pattern: FopFactory.newInstance(new URI("."))
 * 
 * This transformation replaces parameterless FopFactory.newInstance() calls
 * with calls that provide a default base URI.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming FOP API calls in: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            var parseResult = parser.parse(content);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                boolean modified = cu.accept(new FopFactoryTransformer(), null) != null;
                
                if (modified) {
                    Files.writeString(filePath, cu.toString());
                    System.out.println("Transformed: " + filePath);
                }
            } else {
                System.err.println("Failed to parse file " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms FopFactory.newInstance() calls to use the new API.
     */
    private static class FopFactoryTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a FopFactory.newInstance() call
            if (isFopFactoryNewInstanceCall(n)) {
                System.out.println("Found FopFactory.newInstance() call to transform");
                
                // Use URI.create() instead of new URI() to avoid checked exceptions
                // URI.create(".") creates a URI without throwing URISyntaxException
                MethodCallExpr uriCreation = new MethodCallExpr();
                uriCreation.setScope(new NameExpr("java.net.URI"));
                uriCreation.setName("create");
                uriCreation.getArguments().add(new com.github.javaparser.ast.expr.StringLiteralExpr("."));
                
                // Create the new method call with URI parameter
                MethodCallExpr newCall = new MethodCallExpr();
                newCall.setScope(n.getScope().orElse(null));
                newCall.setName("newInstance");
                newCall.getArguments().add(uriCreation);
                
                return newCall;
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Checks if the method call matches the pattern FopFactory.newInstance()
         */
        private boolean isFopFactoryNewInstanceCall(MethodCallExpr call) {
            // Check if method name is "newInstance"
            if (!"newInstance".equals(call.getNameAsString())) {
                return false;
            }
            
            // Check if there are no arguments (parameterless call)
            if (!call.getArguments().isEmpty()) {
                return false;
            }
            
            // Check if the scope is FopFactory (either qualified or imported)
            return call.getScope()
                .map(scope -> {
                    if (scope instanceof NameExpr) {
                        String scopeName = ((NameExpr) scope).getNameAsString();
                        return "FopFactory".equals(scopeName);
                    }
                    return false;
                })
                .orElse(false);
        }
    }
}