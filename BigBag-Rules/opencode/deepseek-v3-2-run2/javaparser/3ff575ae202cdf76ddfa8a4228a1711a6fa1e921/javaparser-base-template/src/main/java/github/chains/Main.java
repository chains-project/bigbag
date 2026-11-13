package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking API changes in Plexus Container.
 * 
 * This transformation addresses the breaking change where getLoggerManager() method
 * was moved from PlexusContainer interface to MutablePlexusContainer interface.
 * 
 * Transformation: Cast PlexusContainer to MutablePlexusContainer before calling getLoggerManager()
 * 
 * Old pattern: container.getLoggerManager()
 * New pattern: ((MutablePlexusContainer) container).getLoggerManager()
 */
public class Main {
    
    // Configuration parameters - these should be parameterized for different breaking changes
    private static final String OLD_INTERFACE_NAME = "org.codehaus.plexus.PlexusContainer";
    private static final String NEW_INTERFACE_NAME = "org.codehaus.plexus.MutablePlexusContainer";
    private static final String METHOD_NAME = "getLoggerManager";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation will fix: " + OLD_INTERFACE_NAME + "." + METHOD_NAME + "() calls");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Fixing: " + OLD_INTERFACE_NAME + "." + METHOD_NAME + "() -> (" + NEW_INTERFACE_NAME + ") expr." + METHOD_NAME + "()");
        
        try {
            int filesProcessed = processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed. Processed " + filesProcessed + " files.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processDirectory(Path directory) throws IOException {
        final int[] filesProcessed = {0};
        
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    if (processFile(path)) {
                        filesProcessed[0]++;
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file " + path + ": " + e.getMessage());
                }
            });
        
        return filesProcessed[0];
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        JavaParser parser = new JavaParser(new ParserConfiguration());
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
        
        boolean modified = false;
        
        // Find all method calls to getLoggerManager()
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class, 
            methodCall -> METHOD_NAME.equals(methodCall.getNameAsString()));
        
        for (MethodCallExpr methodCall : methodCalls) {
            // Check if this is a call on a PlexusContainer instance
            if (isPlexusContainerCall(methodCall)) {
                System.out.println("Found call to fix in " + filePath + ": " + methodCall);
                
                // Apply the transformation: wrap with cast
                applyTransformation(methodCall);
                modified = true;
            }
        }
        
        if (modified) {
            // Write the modified content back to the file
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
            String newContent = printer.print(cu);
            Files.write(filePath, newContent.getBytes());
            System.out.println("Fixed file: " + filePath);
        }
        
        return modified;
    }
    
    /**
     * Check if this method call is on a PlexusContainer instance.
     * This uses structural matching rather than type resolution for generality.
     */
    private static boolean isPlexusContainerCall(MethodCallExpr methodCall) {
        // Check if the scope of the method call could be a PlexusContainer
        // We look for patterns like: container.getLoggerManager() or getContainer().getLoggerManager()
        if (!methodCall.hasScope()) {
            return false;
        }
        
        Expression scope = methodCall.getScope().get();
        
        // Pattern 1: Direct field/variable access
        if (scope instanceof NameExpr) {
            return true; // Assume any variable could be a PlexusContainer
        }
        
        // Pattern 2: Method call that returns PlexusContainer (like getContainer())
        if (scope instanceof MethodCallExpr) {
            MethodCallExpr scopeCall = (MethodCallExpr) scope;
            String methodName = scopeCall.getNameAsString().toLowerCase();
            // Common patterns for getting containers
            return methodName.contains("container") || 
                   methodName.contains("plexus") ||
                   methodName.equals("get") ||
                   methodName.startsWith("get");
        }
        
        // Pattern 3: Field access (like this.container)
        if (scope instanceof FieldAccessExpr) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Apply the transformation: wrap the scope with a cast to MutablePlexusContainer
     */
    private static void applyTransformation(MethodCallExpr methodCall) {
        if (!methodCall.hasScope()) {
            return;
        }
        
        Expression scope = methodCall.getScope().get();
        
        // Create the cast expression
        ClassOrInterfaceType castType = new ClassOrInterfaceType(null, NEW_INTERFACE_NAME);
        CastExpr castExpr = new CastExpr(castType, scope);
        
        // Replace the scope with the cast expression
        methodCall.setScope(castExpr);
    }
}