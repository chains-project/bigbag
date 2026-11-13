package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic transformation rule for Jetty SelectChannelConnector to ServerConnector migration.
 * This transformation handles the breaking change in Jetty 11+ where SelectChannelConnector
 * was removed and replaced with ServerConnector.
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_CONNECTOR_CLASS = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_CLASS = "org.eclipse.jetty.server.ServerConnector";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jetty connector usage in: " + sourceDir);
        
        try {
            transformDirectory(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir) throws IOException {
        Path start = Paths.get(sourceDir);
        try (Stream<Path> paths = Files.walk(start)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            File file = filePath.toFile();
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + filePath));
            
            boolean modified = false;
            
            // Transform imports
            if (cu.getImports() != null) {
                for (ImportDeclaration importDecl : cu.getImports()) {
                    if (importDecl.getNameAsString().equals(OLD_CONNECTOR_CLASS)) {
                        importDecl.setName(NEW_CONNECTOR_CLASS);
                        modified = true;
                        System.out.println("Updated import in " + filePath + ": " + OLD_CONNECTOR_CLASS + " -> " + NEW_CONNECTOR_CLASS);
                    }
                }
            }
            
            // Transform object creation expressions
            JettyConnectorTransformer transformer = new JettyConnectorTransformer();
            CompilationUnit transformed = (CompilationUnit) cu.accept(transformer, null);
            if (transformer.wasModified()) {
                modified = true;
                cu = transformed;
            }
            
            if (modified) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(cu.toString().getBytes());
                    System.out.println("Successfully transformed " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to transform " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms SelectChannelConnector constructor calls to ServerConnector.
     * This handles the structural change where ServerConnector requires the Server instance
     * as a constructor parameter.
     */
    private static class JettyConnectorTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a SelectChannelConnector constructor call
            if (n.getType().asString().equals("SelectChannelConnector") ||
                n.getType().asString().equals(OLD_CONNECTOR_CLASS)) {
                
                // Replace with ServerConnector constructor
                // ServerConnector requires the Server instance as first parameter
                // We need to find the Server variable in the current context
                // For now, we'll use a placeholder and let the user fix manually if needed
                
                System.out.println("Found SelectChannelConnector constructor call");
                
                // Create new ServerConnector constructor call
                ObjectCreationExpr newConstructor = new ObjectCreationExpr();
                newConstructor.setType(NEW_CONNECTOR_CLASS);
                
                // Note: This transformation requires the Server instance to be available
                // In practice, this would need more sophisticated analysis to find the Server variable
                // For now, we'll leave this as a manual fix requirement
                System.out.println("WARNING: SelectChannelConnector constructor found in " + 
                    n.getRange().map(r -> r.begin.line).orElse(-1) + 
                    ". ServerConnector requires Server instance parameter. Manual fix may be needed.");
                
                modified = true;
                return newConstructor;
            }
            
            return super.visit(n, arg);
        }
    }
}