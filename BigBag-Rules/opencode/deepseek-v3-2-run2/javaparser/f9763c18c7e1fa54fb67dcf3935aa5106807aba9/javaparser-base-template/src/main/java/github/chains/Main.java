package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation for handling breaking API changes where:
 * 1. A class has been removed from a dependency
 * 2. The class was used as a wrapper around another object
 * 3. The wrapper class had a method to get the wrapped object
 * 
 * Transformation rules:
 * - Remove imports of the removed class
 * - Replace type references with a replacement type (e.g., Object)
 * - Replace object creation with the constructor argument
 * - Replace method calls to get the wrapped object with just the object
 */
public class Main {
    // Configuration - these should be parameterized for different breaking changes
    private static final String REMOVED_CLASS = "ScriptResult";
    private static final String REPLACEMENT_TYPE = "Object";
    private static final String GETTER_METHOD = "getJavaScriptResult";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Optional parameters: <removed-class> <replacement-type> <getter-method>");
            System.exit(1);
        }
        
        // Allow overriding default configuration via command line
        String removedClass = args.length > 1 ? args[1] : REMOVED_CLASS;
        String replacementType = args.length > 2 ? args[2] : REPLACEMENT_TYPE;
        String getterMethod = args.length > 3 ? args[3] : GETTER_METHOD;
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing directory: " + sourceDir);
        System.out.println("Removed class: " + removedClass);
        System.out.println("Replacement type: " + replacementType);
        System.out.println("Getter method: " + getterMethod);
        
        // Walk through all Java files
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> processFile(file, removedClass, replacementType, getterMethod));
        }
        
        System.out.println("Transformation complete.");
    }
    
    private static void processFile(Path file, String removedClass, String replacementType, String getterMethod) {
        try {
            System.out.println("Processing: " + file);
            
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> result = parser.parse(file);
            
            if (!result.isSuccessful()) {
                System.err.println("Failed to parse: " + file);
                return;
            }
            
            CompilationUnit cu = result.getResult().get();
            
            // Apply transformation
            boolean modified = false;
            
            // Remove imports of the removed class
            List<ImportDeclaration> imports = cu.getImports();
            for (Iterator<ImportDeclaration> it = imports.iterator(); it.hasNext(); ) {
                ImportDeclaration importDecl = it.next();
                if (importDecl.getNameAsString().equals(removedClass) || 
                    importDecl.getNameAsString().endsWith("." + removedClass)) {
                    it.remove();
                    modified = true;
                    System.out.println("  Removed import: " + importDecl.getNameAsString());
                }
            }
            
            // Apply visitor to transform usage of removed class
            ClassRemovalTransformer transformer = new ClassRemovalTransformer(removedClass, replacementType, getterMethod);
            cu.accept(transformer, null);
            if (transformer.isModified()) {
                modified = true;
            }
            
            if (modified) {
                // Write back the modified file
                Files.write(file, cu.toString().getBytes());
                System.out.println("  Updated file.");
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    private static class ClassRemovalTransformer extends ModifierVisitor<Void> {
        private final String removedClass;
        private final String replacementType;
        private final String getterMethod;
        private boolean modified = false;
        
        public ClassRemovalTransformer(String removedClass, String replacementType, String getterMethod) {
            this.removedClass = removedClass;
            this.replacementType = replacementType;
            this.getterMethod = getterMethod;
        }
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Replace removed class type references with replacement type
            if (n.getNameAsString().equals(removedClass)) {
                modified = true;
                return new ClassOrInterfaceType(replacementType);
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new RemovedClass(...) expression
            if (n.getType().getNameAsString().equals(removedClass)) {
                // Replace new RemovedClass(arg) with just arg
                // Since the class no longer exists
                if (n.getArguments().size() == 1) {
                    Expression argExpr = n.getArguments().get(0);
                    modified = true;
                    return argExpr.clone();
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for method calls to get the wrapped object
            // like wrapper.getWrappedObject()
            if (n.getNameAsString().equals(getterMethod)) {
                // Replace wrapper.getWrappedObject() with wrapper
                // Since the wrapped object is already accessible
                modified = true;
                return n.getScope().orElse(n).clone();
            }
            
            return super.visit(n, arg);
        }
    }
}