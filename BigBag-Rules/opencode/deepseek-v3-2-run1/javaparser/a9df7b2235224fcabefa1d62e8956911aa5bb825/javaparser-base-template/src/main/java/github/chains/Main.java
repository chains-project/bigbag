package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileNotFoundException;
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
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Modified " + modifiedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Rule 1: Fix Representer.getProperties() method signature
        modified |= fixRepresenterGetProperties(cu);
        
        // Rule 2: Fix Yaml constructor calls (4-parameter with Resolver should become 5-parameter with LoaderOptions)
        modified |= fixYamlConstructors(cu);
        
        if (modified) {
            System.out.println("Modified: " + javaFile);
            // Write changes back to file
            try {
                Files.write(javaFile, cu.toString().getBytes());
            } catch (IOException e) {
                System.err.println("Error writing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        return modified;
    }
    
    private static boolean fixRepresenterGetProperties(CompilationUnit cu) {
        class IntrospectionExceptionVisitor extends VoidVisitorAdapter<Void> {
            boolean modified = false;
            
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                super.visit(md, arg);
                
                // Check if method has throws IntrospectionException
                boolean hasIntrospectionException = md.getThrownExceptions().stream()
                    .anyMatch(e -> e.asString().equals("IntrospectionException"));
                
                if (hasIntrospectionException) {
                    // Remove IntrospectionException from throws clause
                    md.getThrownExceptions().removeIf(e -> e.asString().equals("IntrospectionException"));
                    modified = true;
                    System.out.println("  - Removed throws IntrospectionException from " + md.getNameAsString() + " method");
                }
            }
        }
        
        IntrospectionExceptionVisitor visitor = new IntrospectionExceptionVisitor();
        visitor.visit(cu, null);
        return visitor.modified;
    }
    
    private static boolean fixYamlConstructors(CompilationUnit cu) {
        class YamlConstructorVisitor extends VoidVisitorAdapter<Void> {
            boolean modified = false;
            
            @Override
            public void visit(ObjectCreationExpr expr, Void arg) {
                super.visit(expr, arg);
                
                // Check if this is a Yaml constructor call
                if (expr.getType().asString().equals("Yaml")) {
                    // Check if it's a 4-parameter constructor
                    if (expr.getArguments().size() == 4) {
                        // In snakeyaml 1.32, the 4-param constructor Yaml(BaseConstructor, Representer, DumperOptions, Resolver) exists
                        // But maybe in older versions it was different, or maybe we should use the 5-param version
                        // with LoaderOptions for better forward compatibility
                        
                        // Add LoaderOptions as 4th parameter and shift Resolver to 5th
                        expr.getArguments().add(3, new ObjectCreationExpr(null, 
                            new ClassOrInterfaceType(null, "LoaderOptions"), new com.github.javaparser.ast.NodeList<>()));
                        modified = true;
                        System.out.println("  - Added LoaderOptions parameter to Yaml constructor");
                        
                        // Also need to add import for LoaderOptions if not already present
                        boolean hasLoaderOptionsImport = cu.getImports().stream()
                            .anyMatch(i -> i.getNameAsString().equals("org.yaml.snakeyaml.LoaderOptions"));
                        if (!hasLoaderOptionsImport) {
                            cu.addImport("org.yaml.snakeyaml.LoaderOptions");
                            System.out.println("  - Added import for LoaderOptions");
                        }
                    }
                }
            }
        }
        
        YamlConstructorVisitor visitor = new YamlConstructorVisitor();
        visitor.visit(cu, null);
        return visitor.modified;
    }
}