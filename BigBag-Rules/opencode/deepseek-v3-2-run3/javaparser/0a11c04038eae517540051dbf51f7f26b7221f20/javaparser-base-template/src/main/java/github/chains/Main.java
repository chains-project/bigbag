package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path file) {
        try {
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse: " + file);
                return;
            }
            CompilationUnit cu = parseResult.getResult().orElseThrow();
            boolean[] needsLoaderOptionsImport = {false};
            
            ModifierVisitor<Void> visitor = new ModifierVisitor<>() {
                @Override
                public Visitable visit(ObjectCreationExpr expr, Void arg) {
                    // Check if this is a snakeyaml BaseConstructor subclass object creation
                    String typeName = expr.getType().asString();
                    
                    // List of BaseConstructor subclasses in snakeyaml 2.0 that require LoaderOptions
                    String[] snakeyamlConstructors = {
                        "Constructor",
                        "org.yaml.snakeyaml.constructor.Constructor",
                        "SafeConstructor", 
                        "org.yaml.snakeyaml.constructor.SafeConstructor",
                        "CustomClassLoaderConstructor",
                        "org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor",
                        "EnvScalarConstructor",
                        "org.yaml.snakeyaml.env.EnvScalarConstructor",
                        "CompactConstructor",
                        "org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor",
                        "PackageCompactConstructor",
                        "org.yaml.snakeyaml.extensions.compactnotation.PackageCompactConstructor"
                    };
                    
                    for (String constructorType : snakeyamlConstructors) {
                        if (typeName.equals(constructorType)) {
                            // In snakeyaml 2.0, all BaseConstructor subclasses require LoaderOptions parameter
                            // Old pattern: Constructor(Class) or Constructor() or Constructor(String) etc.
                            // New pattern: Constructor(..., new LoaderOptions())
                            
                            // Check argument count to determine if transformation is needed
                            // If 0 or 1 arguments, we need to add LoaderOptions
                            if (expr.getArguments().size() == 0) {
                                // No arguments → need LoaderOptions as first/only argument
                                ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                                loaderOptions.setType(new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"));
                                expr.getArguments().add(loaderOptions);
                                needsLoaderOptionsImport[0] = true;
                            } else if (expr.getArguments().size() == 1) {
                                // One argument → need to add LoaderOptions as second argument
                                ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                                loaderOptions.setType(new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"));
                                expr.getArguments().add(loaderOptions);
                                needsLoaderOptionsImport[0] = true;
                            }
                            // Note: If there are already 2+ arguments, we assume it's already correct
                            // (e.g., Constructor(Class, LoaderOptions) or Constructor(TypeDescription, LoaderOptions))
                            break;
                        }
                    }
                    return super.visit(expr, arg);
                }
            };
            
            cu.accept(visitor, null);
            
            // Add import if needed
            if (needsLoaderOptionsImport[0]) {
                boolean hasLoaderOptionsImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.yaml.snakeyaml.LoaderOptions"));
                
                if (!hasLoaderOptionsImport) {
                    ImportDeclaration importDecl = new ImportDeclaration("org.yaml.snakeyaml.LoaderOptions", false, false);
                    cu.addImport(importDecl);
                }
            }
            
            // Write back the modified file
            Files.write(file, cu.toString().getBytes());
            System.out.println("Processed: " + file);
            
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
}