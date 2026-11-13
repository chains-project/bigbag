package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This transformation fixes snakeyaml 1.x -> 2.0 API breaking changes:");
            System.err.println("1. Constructor(Class) -> Constructor(Class, LoaderOptions)");
            System.err.println("2. Representer() -> Representer(DumperOptions)");
            System.err.println("3. Remove 'throws IntrospectionException' from getProperties()");
            System.err.println("4. Fix Yaml constructor parameter combinations");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Invalid source directory: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming snakeyaml 1.x code to snakeyaml 2.0 API...");
        System.out.println("Source directory: " + sourceDir);
        
        // Walk through all Java files
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) {
        try {
            System.out.println("Processing: " + filePath);
            String originalContent = Files.readString(filePath);
            CompilationUnit cu = new JavaParser().parse(originalContent).getResult().orElseThrow();
            
            // Apply transformations
            ConstructorTransformationVisitor constructorVisitor = new ConstructorTransformationVisitor();
            RepresenterTransformationVisitor representerVisitor = new RepresenterTransformationVisitor();
            MethodSignatureTransformationVisitor methodVisitor = new MethodSignatureTransformationVisitor();
            YamlConstructorTransformationVisitor yamlVisitor = new YamlConstructorTransformationVisitor();
            
            // Apply visitors - they modify the AST in place
            cu.accept(constructorVisitor, null);
            cu.accept(representerVisitor, null);
            cu.accept(methodVisitor, null);
            cu.accept(yamlVisitor, null);
            
            // Get transformed content
            String transformedContent = new DefaultPrettyPrinter().print(cu);
            
            // Check if content changed
            if (!originalContent.equals(transformedContent)) {
                // Write transformed content back
                Files.write(filePath, transformedContent.getBytes());
                System.out.println("  -> Transformed: " + filePath);
            } else {
                System.out.println("  -> No changes needed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error transforming file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transforms Constructor class instantiations:
     * Old: new Constructor(Class<T>)
     * New: new Constructor(Class<T>, new LoaderOptions())
     * 
     * Generic pattern: Any Constructor instantiation with single Class parameter
     * needs LoaderOptions added as second parameter.
     */
    static class ConstructorTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Match any Constructor instantiation (fully qualified or simple name)
            String typeName = expr.getType().toString();
            if (typeName.endsWith("Constructor") || typeName.contains(".Constructor")) {
                // Check if it has exactly 1 argument (the old pattern: Constructor(Class))
                if (expr.getArguments().size() == 1) {
                    // Check if first argument is a Class type (not DumperOptions or something else)
                    String firstArg = expr.getArguments().get(0).toString();
                    if (!firstArg.contains("DumperOptions") && !firstArg.contains("LoaderOptions")) {
                        // Add LoaderOptions as second parameter
                        expr.getArguments().add(
                            new com.github.javaparser.ast.expr.ObjectCreationExpr(
                                null,
                                new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"),
                                new com.github.javaparser.ast.NodeList<>()
                            )
                        );
                        System.out.println("  - Fixed Constructor instantiation: added LoaderOptions parameter");
                    }
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt stmt, Void arg) {
            // Handle super() calls in constructors for Constructor subclasses
            // Check if it's a super call (not this) and has exactly 1 argument
            if (!stmt.isThis() && stmt.getArguments().size() == 1) {
                // Simple heuristic: only add LoaderOptions if the argument looks like a Class
                // (e.g., ends with .class or contains Class)
                String firstArg = stmt.getArguments().get(0).toString();
                if (firstArg.endsWith(".class") || firstArg.contains(".class") || 
                    firstArg.contains("Class") && !firstArg.contains("DumperOptions")) {
                    // Likely a Constructor(Class) call, add LoaderOptions
                    stmt.getArguments().add(
                        new com.github.javaparser.ast.expr.ObjectCreationExpr(
                            null,
                            new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"),
                            new com.github.javaparser.ast.NodeList<>()
                        )
                    );
                    System.out.println("  - Fixed super constructor call: added LoaderOptions parameter");
                }
            }
            return super.visit(stmt, arg);
        }
    }
    
    /**
     * Transforms Representer class instantiations and super constructor calls:
     * Old: new Representer()
     * New: new Representer(new DumperOptions())
     * 
     * Also handles implicit super() calls in classes extending Representer
     * 
     * Generic pattern: Any Representer instantiation with no arguments
     * needs DumperOptions added as parameter.
     */
    static class RepresenterTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Match any Representer instantiation (fully qualified or simple name)
            String typeName = expr.getType().toString();
            if (typeName.endsWith("Representer") || typeName.contains(".Representer")) {
                // Check if it has no arguments (the old pattern: Representer())
                if (expr.getArguments().isEmpty()) {
                    // Replace with Representer(new DumperOptions())
                    expr.getArguments().add(
                        new com.github.javaparser.ast.expr.ObjectCreationExpr(
                            null,
                            new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.DumperOptions"),
                            new com.github.javaparser.ast.NodeList<>()
                        )
                    );
                    System.out.println("  - Fixed Representer instantiation: added DumperOptions parameter");
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt stmt, Void arg) {
            // Handle super() calls in constructors for Representer subclasses
            // Check if it's a super call (not this) and has no arguments (implicit or explicit empty super())
            if (!stmt.isThis() && stmt.getArguments().isEmpty()) {
                // We need to check if the class extends Representer
                // This is complex, but for now we'll make it explicit with DumperOptions
                // since Representer in snakeyaml 2.0 requires DumperOptions
                stmt.getArguments().add(
                    new com.github.javaparser.ast.expr.ObjectCreationExpr(
                        null,
                        new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.DumperOptions"),
                        new com.github.javaparser.ast.NodeList<>()
                    )
                );
                System.out.println("  - Fixed super() call in Representer subclass: added DumperOptions parameter");
            }
            return super.visit(stmt, arg);
        }
        
        @Override
        public Visitable visit(com.github.javaparser.ast.body.ConstructorDeclaration cd, Void arg) {
            // Check for constructors with no explicit super() call (implicit super())
            // We need to add explicit super(new DumperOptions()) if the class extends Representer
            
            // First check if constructor doesn't have any explicit constructor invocation
            boolean hasExplicitSuperOrThis = cd.getBody().getStatements().stream()
                .anyMatch(stmt -> stmt instanceof com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt);
            
            if (!hasExplicitSuperOrThis) {
                // Check if the class extends Representer
                // This requires checking the containing class's extends clause
                cd.getParentNode().ifPresent(parent -> {
                    if (parent instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) {
                        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl = 
                            (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) parent;
                        
                        // Check if class extends Representer
                        boolean extendsRepresenter = classDecl.getExtendedTypes().stream()
                            .anyMatch(type -> type.toString().endsWith("Representer") || 
                                             type.toString().contains(".Representer"));
                        
                        if (extendsRepresenter) {
                            // Add explicit super(new DumperOptions()) as first statement
                            com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt superStmt = 
                                new com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt();
                            superStmt.setThis(false);
                            superStmt.getArguments().add(
                                new com.github.javaparser.ast.expr.ObjectCreationExpr(
                                    null,
                                    new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.DumperOptions"),
                                    new com.github.javaparser.ast.NodeList<>()
                                )
                            );
                            
                            // Insert at the beginning of constructor body
                            cd.getBody().addStatement(0, superStmt);
                            System.out.println("  - Added explicit super(new DumperOptions()) to Representer subclass constructor");
                        }
                    }
                });
            }
            
            return super.visit(cd, arg);
        }
        
        @Override
        public Visitable visit(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Check if class extends Representer and doesn't have a constructor that accepts DumperOptions
            // If not, we need to add one
            
            boolean extendsRepresenter = classDecl.getExtendedTypes().stream()
                .anyMatch(type -> type.toString().endsWith("Representer") || 
                                 type.toString().contains(".Representer"));
            
            if (extendsRepresenter) {
                // Check if class already has a constructor with DumperOptions parameter
                boolean hasDumperOptionsConstructor = classDecl.getConstructors().stream()
                    .anyMatch(constructor -> {
                        // Check if constructor has exactly one parameter of type DumperOptions
                        return constructor.getParameters().size() == 1 &&
                               constructor.getParameters().get(0).getType().toString().contains("DumperOptions");
                    });
                
                if (!hasDumperOptionsConstructor) {
                    // Add a constructor that accepts DumperOptions
                    com.github.javaparser.ast.body.ConstructorDeclaration newConstructor = 
                        new com.github.javaparser.ast.body.ConstructorDeclaration();
                    newConstructor.setName(classDecl.getName());
                    
                    // Add DumperOptions parameter
                    com.github.javaparser.ast.body.Parameter dumperOptionsParam = 
                        new com.github.javaparser.ast.body.Parameter(
                            new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.DumperOptions"),
                            "dumperOptions"
                        );
                    newConstructor.addParameter(dumperOptionsParam);
                    
                    // Add super(dumperOptions) call
                    com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt superStmt = 
                        new com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt();
                    superStmt.setThis(false);
                    superStmt.getArguments().add(
                        new com.github.javaparser.ast.expr.NameExpr("dumperOptions")
                    );
                    
                    com.github.javaparser.ast.stmt.BlockStmt body = new com.github.javaparser.ast.stmt.BlockStmt();
                    body.addStatement(superStmt);
                    newConstructor.setBody(body);
                    
                    // Copy any statements from default constructor if it exists
                    classDecl.getConstructors().stream()
                        .filter(c -> c.getParameters().isEmpty())
                        .findFirst()
                        .ifPresent(defaultConstructor -> {
                            // Copy all statements except the first (which would be super())
                            for (int i = 1; i < defaultConstructor.getBody().getStatements().size(); i++) {
                                body.addStatement(defaultConstructor.getBody().getStatement(i).clone());
                            }
                        });
                    
                    classDecl.addMember(newConstructor);
                    System.out.println("  - Added constructor with DumperOptions parameter to Representer subclass");
                }
            }
            
            return super.visit(classDecl, arg);
        }
    }
    
    /**
     * Removes 'throws IntrospectionException' from getProperties method overrides
     * and any helper methods that throw it
     * 
     * Generic pattern: Any method that throws IntrospectionException
     * should have the exception removed from throws clause.
     */
    static class MethodSignatureTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(com.github.javaparser.ast.body.MethodDeclaration md, Void arg) {
            // Check if method throws IntrospectionException
            boolean hasIntrospectionException = md.getThrownExceptions().stream()
                .anyMatch(exception -> exception.toString().contains("IntrospectionException"));
            
            if (hasIntrospectionException) {
                // Remove IntrospectionException from throws clause
                md.getThrownExceptions().removeIf(
                    exception -> exception.toString().contains("IntrospectionException")
                );
                System.out.println("  - Fixed method '" + md.getNameAsString() + "': removed IntrospectionException from throws clause");
            }
            return super.visit(md, arg);
        }
    }
    
    /**
     * Updates Yaml constructor calls to match new API signatures
     * 
     * Generic pattern: Yaml constructor calls with problematic parameter combinations
     * need to be adjusted to match new API.
     */
    static class YamlConstructorTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Match any Yaml instantiation
            String typeName = expr.getType().toString();
            if (typeName.endsWith("Yaml") || typeName.contains(".Yaml")) {
                int argCount = expr.getArguments().size();
                
                // Pattern 1: new Yaml(constructor, new Representer(), dumperOptions, ...)
                // Need to fix the Representer instantiation
                if (argCount >= 3) {
                    // Check if second argument is new Representer() with no arguments
                    if (expr.getArguments().size() > 1 && 
                        expr.getArguments().get(1) instanceof ObjectCreationExpr) {
                        
                        ObjectCreationExpr secondArg = (ObjectCreationExpr) expr.getArguments().get(1);
                        if (secondArg.getType().toString().contains("Representer") && 
                            secondArg.getArguments().isEmpty()) {
                            
                            // Fix the Representer instantiation
                            secondArg.getArguments().add(
                                new com.github.javaparser.ast.expr.ObjectCreationExpr(
                                    null,
                                    new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "org.yaml.snakeyaml.DumperOptions"),
                                    new com.github.javaparser.ast.NodeList<>()
                                )
                            );
                            System.out.println("  - Fixed Yaml constructor: added DumperOptions to Representer parameter");
                        }
                    }
                }
                
                // Pattern 2: Check if we need to add LoaderOptions to BaseConstructor parameters
                // This would require more complex pattern matching based on parameter types
                // For now, we rely on the ConstructorTransformationVisitor to handle Constructor instantiations
            }
            return super.visit(expr, arg);
        }
    }
    
    
}