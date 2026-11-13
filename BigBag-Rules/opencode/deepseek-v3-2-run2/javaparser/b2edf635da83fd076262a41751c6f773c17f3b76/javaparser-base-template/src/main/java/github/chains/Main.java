package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedCount = 0;
            for (Path filePath : javaFiles) {
                if (processFile(filePath)) {
                    modifiedCount++;
                }
            }
            
            System.out.println("Modified " + modifiedCount + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(sourceDir))) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        SnakeYamlConstructorFixVisitor visitor = new SnakeYamlConstructorFixVisitor();
        cu.accept(visitor, null);
        
        if (visitor.isModified()) {
            String newContent = cu.toString();
            Files.writeString(filePath, newContent);
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    static class SnakeYamlConstructorFixVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Constructor, SafeConstructor, or CustomClassLoaderConstructor creation
            String typeName = n.getType().asString();
            
            if (typeName.equals("Constructor") || 
                typeName.equals("SafeConstructor") || 
                typeName.equals("CustomClassLoaderConstructor")) {
                
                // Check if it has fewer arguments than required for snakeyaml 2.0
                List<Expression> arguments = n.getArguments();
                
                if (typeName.equals("Constructor")) {
                    // Constructor has multiple overloads in snakeyaml 2.0:
                    // - Constructor(LoaderOptions) - 1 arg
                    // - Constructor(Class, LoaderOptions) - 2 args  
                    // - Constructor(String, LoaderOptions) - 2 args
                    // - Constructor(TypeDescription, LoaderOptions) - 2 args
                    // - Constructor(TypeDescription, Collection, LoaderOptions) - 3 args
                    
                    // Based on snakeyaml 1.x -> 2.0 migration:
                    // All old constructors need LoaderOptions added as last parameter
                    // We'll add it if the current number of args doesn't match 2.0 patterns
                    
                    int argCount = arguments.size();
                    
                    // In snakeyaml 2.0, valid Constructor arg counts are 1, 2, or 3
                    // If we have 0 args or an arg count that doesn't end with LoaderOptions, add it
                    if (argCount == 0) {
                        // Constructor() -> Constructor(new LoaderOptions())
                        n.getArguments().add(createLoaderOptionsExpr());
                        modified = true;
                    } else if (argCount == 1) {
                        // Could be Constructor(Class) or Constructor(LoaderOptions)
                        // We need to check if last arg is LoaderOptions
                        if (!isLoaderOptionsArgument(arguments.get(0))) {
                            // Constructor(Class) -> Constructor(Class, new LoaderOptions())
                            n.getArguments().add(createLoaderOptionsExpr());
                            modified = true;
                        }
                    } else if (argCount == 2) {
                        // Could be Constructor(Class, LoaderOptions) or Constructor(Class, Class)
                        if (!isLoaderOptionsArgument(arguments.get(1))) {
                            // Constructor(Class, Class) -> Constructor(Class, Class, new LoaderOptions())
                            n.getArguments().add(createLoaderOptionsExpr());
                            modified = true;
                        }
                    }
                } else if (typeName.equals("SafeConstructor")) {
                    // SafeConstructor has only: SafeConstructor(LoaderOptions)
                    if (arguments.size() == 0) {
                        // SafeConstructor() -> SafeConstructor(new LoaderOptions())
                        n.getArguments().add(createLoaderOptionsExpr());
                        modified = true;
                    } else if (arguments.size() == 1 && !isLoaderOptionsArgument(arguments.get(0))) {
                        // SafeConstructor(Class) -> SafeConstructor(new LoaderOptions())
                        // Replace the argument
                        n.getArguments().set(0, createLoaderOptionsExpr());
                        modified = true;
                    }
                } else if (typeName.equals("CustomClassLoaderConstructor")) {
                    // CustomClassLoaderConstructor has:
                    // - CustomClassLoaderConstructor(LoaderOptions) - 1 arg
                    // - CustomClassLoaderConstructor(ClassLoader, LoaderOptions) - 2 args
                    // - CustomClassLoaderConstructor(Class, ClassLoader, LoaderOptions) - 3 args
                    
                    int argCount = arguments.size();
                    
                    if (argCount == 0) {
                        // CustomClassLoaderConstructor() -> CustomClassLoaderConstructor(new LoaderOptions())
                        n.getArguments().add(createLoaderOptionsExpr());
                        modified = true;
                    } else if (argCount == 1 && !isLoaderOptionsArgument(arguments.get(0))) {
                        // CustomClassLoaderConstructor(ClassLoader) -> CustomClassLoaderConstructor(ClassLoader, new LoaderOptions())
                        n.getArguments().add(createLoaderOptionsExpr());
                        modified = true;
                    } else if (argCount == 2 && !isLoaderOptionsArgument(arguments.get(1))) {
                        // CustomClassLoaderConstructor(Class, ClassLoader) -> CustomClassLoaderConstructor(Class, ClassLoader, new LoaderOptions())
                        n.getArguments().add(createLoaderOptionsExpr());
                        modified = true;
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private ObjectCreationExpr createLoaderOptionsExpr() {
            JavaParser parser = new JavaParser();
            return new ObjectCreationExpr(
                null,
                parser.parseClassOrInterfaceType("LoaderOptions").getResult().get(),
                new com.github.javaparser.ast.NodeList<>()
            );
        }
        
        private boolean isLoaderOptionsArgument(Expression expr) {
            // Simple heuristic: check if expression is "new LoaderOptions()" or similar
            if (expr instanceof ObjectCreationExpr) {
                ObjectCreationExpr oce = (ObjectCreationExpr) expr;
                return oce.getType().asString().equals("LoaderOptions");
            }
            return false;
        }
    }
}