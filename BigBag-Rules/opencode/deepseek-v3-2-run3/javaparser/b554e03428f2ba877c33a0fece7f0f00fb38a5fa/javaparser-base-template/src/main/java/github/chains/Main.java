package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for breaking API changes where a class is removed/renamed.
 * 
 * This rule handles:
 * 1. Import statement updates
 * 2. Type reference updates (variable declarations, method parameters, return types)
 * 3. Object creation expression updates
 * 
 * Configuration is provided via system properties:
 * - oldClassFqn: Fully qualified name of the old class (e.g., "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder")
 * - newClassFqn: Fully qualified name of the replacement class (e.g., "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder")
 * - constructorParams: Optional constructor parameters to add (e.g., "projectDependenciesResolver")
 * 
 * Usage: java -DoldClassFqn=OLD -DnewClassFqn=NEW -DconstructorParams=PARAM Main.java <source-dir>
 */
public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("System properties:");
            System.err.println("  -DoldClassFqn: Fully qualified name of old class to replace");
            System.err.println("  -DnewClassFqn: Fully qualified name of new class");
            System.err.println("  -DconstructorParams: Optional constructor parameters (comma-separated)");
            System.exit(1);
        }
        
        String oldClassFqn = System.getProperty("oldClassFqn");
        String newClassFqn = System.getProperty("newClassFqn");
        String constructorParams = System.getProperty("constructorParams", "");
        
        if (oldClassFqn == null || newClassFqn == null) {
            System.err.println("ERROR: Must specify -DoldClassFqn and -DnewClassFqn system properties");
            System.exit(1);
        }
        
        // Extract simple class names from FQNs
        String oldClass = extractSimpleClassName(oldClassFqn);
        String newClass = extractSimpleClassName(newClassFqn);
        
        System.out.println("Transformation configuration:");
        System.out.println("  Old class: " + oldClassFqn + " (" + oldClass + ")");
        System.out.println("  New class: " + newClassFqn + " (" + newClass + ")");
        if (!constructorParams.isEmpty()) {
            System.out.println("  Constructor params to add: " + constructorParams);
        }
        
        String sourceDir = args[0];
        System.out.println("\nProcessing files in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        int transformedImports = 0;
        int transformedCreations = 0;
        int transformedTypeRefs = 0;
        
        for (Path javaFile : javaFiles) {
            boolean fileModified = false;
            
            try {
                CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                // 1. Transform imports
                List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
                for (ImportDeclaration importDecl : imports) {
                    String importedName = importDecl.getNameAsString();
                    if (importedName.equals(oldClassFqn)) {
                        // Replace exact import match
                        importDecl.setName(newClassFqn);
                        transformedImports++;
                        fileModified = true;
                        System.out.println("  Updated import in " + javaFile + ": " + oldClassFqn + " -> " + newClassFqn);
                    } else if (importedName.endsWith("." + oldClass) && !importedName.equals(oldClass)) {
                        // Replace import with wildcard or different package structure
                        // Extract package and rebuild with new class
                        String packageName = importedName.substring(0, importedName.lastIndexOf('.'));
                        importDecl.setName(packageName + "." + newClass);
                        transformedImports++;
                        fileModified = true;
                        System.out.println("  Updated import in " + javaFile + ": " + importedName + " -> " + packageName + "." + newClass);
                    }
                }
                
                // 2. Transform type references (variable declarations, method parameters, return types, etc.)
                List<ClassOrInterfaceType> typeRefs = cu.findAll(ClassOrInterfaceType.class);
                for (ClassOrInterfaceType typeRef : typeRefs) {
                    if (typeRef.getNameAsString().equals(oldClass)) {
                        typeRef.setName(newClass);
                        transformedTypeRefs++;
                        fileModified = true;
                    }
                }
                
                // 3. Transform object creations (e.g., new OldClass())
                List<ObjectCreationExpr> creations = cu.findAll(ObjectCreationExpr.class);
                for (ObjectCreationExpr creation : creations) {
                    if (creation.getType().asClassOrInterfaceType().getNameAsString().equals(oldClass)) {
                        // Replace the type
                        creation.setType(new ClassOrInterfaceType(newClass));
                        transformedCreations++;
                        fileModified = true;
                        
                        // Add constructor parameters if specified
                        if (!constructorParams.isEmpty()) {
                            System.out.println("  WARNING: Object creation in " + javaFile + " needs manual update for constructor parameters: " + constructorParams);
                            // Add a comment to indicate manual intervention is needed
                            creation.setComment(new com.github.javaparser.ast.comments.LineComment(
                                " TODO: " + newClass + " constructor requires parameters: " + constructorParams
                            ));
                        }
                    }
                }
                
                if (fileModified) {
                    // Write back the transformed file
                    PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                    config.setPrintComments(true);
                    PrettyPrinter printer = new PrettyPrinter(config);
                    String transformedCode = printer.print(cu);
                    
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(transformedCode);
                    }
                    
                    transformedFiles++;
                }
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation summary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Imports updated: " + transformedImports);
        System.out.println("  Type references updated: " + transformedTypeRefs);
        System.out.println("  Object creations updated: " + transformedCreations);
        
        if (!constructorParams.isEmpty() && transformedCreations > 0) {
            System.out.println("\nIMPORTANT: " + newClass + " requires constructor parameters: " + constructorParams);
            System.out.println("Manual intervention is needed for constructor calls.");
        }
    }
    
    private static String extractSimpleClassName(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return "";
        }
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot >= 0) {
            return fqn.substring(lastDot + 1);
        }
        return fqn;
    }
}