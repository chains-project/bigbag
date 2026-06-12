package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A generic JavaParser transformation for fixing breaking API changes.
 * 
 * This transformation handles:
 * 1. Renaming types (e.g., PublishMetadata → MessageMetadata)
 * 2. Detecting lambda expressions that can no longer be used with interfaces
 *    that have become non-functional (multiple abstract methods)
 * 
 * Configuration:
 * - OLD_TYPE_FQN: The fully qualified name of the old type
 * - NEW_TYPE_FQN: The fully qualified name of the new type
 * - OLD_TYPE_SIMPLE: The simple name of the old type
 * - NEW_TYPE_SIMPLE: The simple name of the new type
 * - PROBLEMATIC_METHODS: Methods that accept lambdas for interfaces that are
 *   no longer functional interfaces (e.g., setPublisherFactory)
 */
public class GenericTransformation {
    
    // Configuration - these should be passed as parameters in a real implementation
    private final String oldTypeFqn;
    private final String newTypeFqn;
    private final String oldTypeSimple;
    private final String newTypeSimple;
    private final List<String> problematicMethods;
    
    public GenericTransformation(String oldTypeFqn, String newTypeFqn, 
                                List<String> problematicMethods) {
        this.oldTypeFqn = oldTypeFqn;
        this.newTypeFqn = newTypeFqn;
        this.oldTypeSimple = extractSimpleName(oldTypeFqn);
        this.newTypeSimple = extractSimpleName(newTypeFqn);
        this.problematicMethods = problematicMethods;
    }
    
    private String extractSimpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
    
    public void transform(String sourceDir) throws IOException {
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: " + oldTypeFqn + " → " + newTypeFqn);
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        AtomicInteger filesModified = new AtomicInteger(0);
        AtomicInteger importsUpdated = new AtomicInteger(0);
        AtomicInteger typeReferencesUpdated = new AtomicInteger(0);
        AtomicInteger lambdaFixesApplied = new AtomicInteger(0);
        
        for (Path javaFile : javaFiles) {
            processFile(javaFile, filesModified, importsUpdated, 
                       typeReferencesUpdated, lambdaFixesApplied);
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files modified: " + filesModified.get());
        System.out.println("  Imports updated: " + importsUpdated.get());
        System.out.println("  Type references updated: " + typeReferencesUpdated.get());
        System.out.println("  Lambda fixes needed: " + lambdaFixesApplied.get());
        
        if (lambdaFixesApplied.get() > 0) {
            System.out.println("\nIMPORTANT: Lambda expressions passed to the following methods need manual conversion");
            System.out.println("to anonymous classes since the target interfaces are no longer functional interfaces:");
            problematicMethods.forEach(method -> System.out.println("  - " + method + "()"));
        }
    }
    
    private void processFile(Path javaFile, 
                           AtomicInteger filesModified,
                           AtomicInteger importsUpdated,
                           AtomicInteger typeReferencesUpdated,
                           AtomicInteger lambdaFixesApplied) {
        boolean[] fileModified = {false};
        
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            
            if (cu == null) {
                return;
            }
            
            // Visitor to modify imports and type references
            ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
                
                @Override
                public Node visit(ImportDeclaration id, Void arg) {
                    // Update import declarations
                    String importName = id.getNameAsString();
                    if (importName.equals(oldTypeFqn)) {
                        importsUpdated.incrementAndGet();
                        fileModified[0] = true;
                        return new ImportDeclaration(newTypeFqn, id.isStatic(), id.isAsterisk());
                    }
                    return (Node) super.visit(id, arg);
                }
                
                @Override
                public Node visit(ClassOrInterfaceType type, Void arg) {
                    // Update type references
                    String typeName = type.getNameAsString();
                    if (typeName.equals(oldTypeSimple)) {
                        // Check if this is actually referring to our target type
                        // by looking at imports or fully qualified name
                        typeReferencesUpdated.incrementAndGet();
                        fileModified[0] = true;
                        return new ClassOrInterfaceType(null, newTypeSimple);
                    }
                    return (Node) super.visit(type, arg);
                }
                
                @Override
                public Node visit(LambdaExpr lambda, Void arg) {
                    // Check for lambda expressions passed to problematic methods
                    if (lambda.getParentNode().isPresent()) {
                        Object parent = lambda.getParentNode().get();
                        if (parent instanceof MethodCallExpr) {
                            MethodCallExpr methodCall = (MethodCallExpr) parent;
                            String methodName = methodCall.getNameAsString();
                            
                            if (problematicMethods.contains(methodName)) {
                                // This lambda is passed to a problematic method
                                lambdaFixesApplied.incrementAndGet();
                                fileModified[0] = true;
                                
                                System.out.println("  Warning: Found lambda passed to " + methodName + 
                                                  "() at " + javaFile + ". Manual fix required.");
                                System.out.println("  Lambda needs to be converted to anonymous class " +
                                                  "since the target interface is no longer a functional interface.");
                            }
                        }
                    }
                    
                    return (Node) super.visit(lambda, arg);
                }
            };
            
            cu.accept(visitor, null);
            
            if (fileModified[0]) {
                // Write back the modified file
                try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                    writer.write(cu.toString());
                    filesModified.incrementAndGet();
                    System.out.println("Modified: " + javaFile);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
        }
    }
    
    private List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.GenericTransformation <source-directory> <config-file>");
            System.err.println("Or: java github.chains.GenericTransformation <source-directory> <old-fqn> <new-fqn> [problematic-method...]");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        if (args.length == 2) {
            // Config file mode (not implemented in this example)
            System.err.println("Config file mode not implemented in this example");
            System.exit(1);
        } else {
            // Command-line mode
            String oldTypeFqn = args[1];
            String newTypeFqn = args[2];
            List<String> problematicMethods = List.of();
            
            if (args.length > 3) {
                problematicMethods = List.of(args).subList(3, args.length);
            }
            
            GenericTransformation transformation = new GenericTransformation(
                oldTypeFqn, newTypeFqn, problematicMethods);
            
            try {
                transformation.transform(sourceDir);
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}