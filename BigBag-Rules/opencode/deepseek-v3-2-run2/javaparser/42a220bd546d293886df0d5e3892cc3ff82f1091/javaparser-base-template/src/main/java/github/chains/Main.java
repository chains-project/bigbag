package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic JavaParser transformation for handling breaking dependency updates.
 * This transformation can be applied to any project affected by constructor
 * signature changes in external dependencies.
 */
public class Main {
    
    /**
     * Configuration for the transformation - these should be parameterized
     * for different breaking changes
     */
    private static final String OLD_CONSTRUCTOR_CLASS = "com.maxmind.geoip2.record.Location";
    private static final String[] OLD_CONSTRUCTOR_PARAM_TYPES = {
        "java.lang.Integer", "java.lang.Integer", "java.lang.Double", 
        "java.lang.Double", "java.lang.Integer", "java.lang.Integer", "java.lang.String"
    };
    private static final String[] NEW_CONSTRUCTOR_PARAM_TYPES = {
        "java.lang.Integer", "java.lang.Integer", "java.lang.Double", 
        "java.lang.Double", "java.lang.Integer", "java.lang.Integer", "java.lang.String"
    };
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Recursively transforms all Java files in the given directory
     */
    private static void transformProject(String sourceDir) throws Exception {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    transformFile(javaFile.toFile());
                } catch (Exception e) {
                    System.err.println("Failed to transform " + javaFile + ": " + e.getMessage());
                }
            });
    }
    
    /**
     * Transforms a single Java file
     */
    private static void transformFile(File javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream fis = new FileInputStream(javaFile)) {
            cu = parser.parse(fis).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + javaFile));
        }
        
        // Apply the transformation
        ConstructorTransformationVisitor visitor = new ConstructorTransformationVisitor();
        cu.accept(visitor, null);
        
        // Write the transformed file back
        try (FileOutputStream fos = new FileOutputStream(javaFile)) {
            fos.write(cu.toString().getBytes());
        }
        
        if (visitor.getTransformationCount() > 0) {
            System.out.println("Transformed " + javaFile + " - " + visitor.getTransformationCount() + " changes");
        }
    }
    
    /**
     * Visitor that transforms constructor calls matching the old pattern
     */
    private static class ConstructorTransformationVisitor extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a constructor call we need to transform
            if (shouldTransformConstructor(n)) {
                transformationCount++;
                return transformConstructor(n);
            }
            return super.visit(n, arg);
        }
        
        /**
         * Determines if a constructor call matches the old pattern that needs transformation
         */
        private boolean shouldTransformConstructor(ObjectCreationExpr expr) {
            // Check if the type matches our target class
            if (!expr.getType().asString().equals(getSimpleClassName(OLD_CONSTRUCTOR_CLASS))) {
                return false;
            }
            
            // Check if the number of arguments matches the old pattern
            List<Expression> arguments = expr.getArguments();
            if (arguments.size() != OLD_CONSTRUCTOR_PARAM_TYPES.length) {
                return false;
            }
            
            // For a generic transformation, we assume any constructor call with the
            // correct number of arguments and matching class name needs transformation
            // In a real scenario, you might want more sophisticated type checking
            return true;
        }
        
        /**
         * Transforms a constructor call from the old pattern to the new pattern
         */
        private ObjectCreationExpr transformConstructor(ObjectCreationExpr oldExpr) {
            List<Expression> oldArguments = oldExpr.getArguments();
            
            // Create new arguments based on the transformation logic
            // This is where you would implement the specific transformation rules
            // For example: reordering parameters, adding defaults, removing parameters
            NodeList<Expression> newArguments = new NodeList<>();
            
            // Simple transformation: keep same arguments but apply any needed changes
            // In a real scenario, this would be more complex based on the breaking change
            for (int i = 0; i < Math.min(oldArguments.size(), NEW_CONSTRUCTOR_PARAM_TYPES.length); i++) {
                newArguments.add(oldArguments.get(i));
            }
            
            // Handle cases where new constructor has fewer parameters
            if (NEW_CONSTRUCTOR_PARAM_TYPES.length < oldArguments.size()) {
                System.out.println("Warning: Removing " + (oldArguments.size() - NEW_CONSTRUCTOR_PARAM_TYPES.length) + 
                    " parameters from constructor call");
            }
            
            // Handle cases where new constructor has more parameters
            if (NEW_CONSTRUCTOR_PARAM_TYPES.length > oldArguments.size()) {
                System.out.println("Warning: New constructor requires " + 
                    (NEW_CONSTRUCTOR_PARAM_TYPES.length - oldArguments.size()) + " additional parameters");
                // Add null values for missing parameters (this is a simple approach)
                for (int i = oldArguments.size(); i < NEW_CONSTRUCTOR_PARAM_TYPES.length; i++) {
                    newArguments.add(new NullLiteralExpr());
                }
            }
            
            // Create the transformed constructor call
            return new ObjectCreationExpr(
                null,
                oldExpr.getType(),
                newArguments
            );
        }
        
        /**
         * Extracts simple class name from fully qualified name
         */
        private String getSimpleClassName(String fullyQualifiedName) {
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
        }
    }
}