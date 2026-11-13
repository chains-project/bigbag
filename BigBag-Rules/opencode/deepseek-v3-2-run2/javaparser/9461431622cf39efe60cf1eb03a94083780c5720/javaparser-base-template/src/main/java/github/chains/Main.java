package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.NodeList;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser javaParser = new JavaParser();
        int filesModified = 0;
        int typeReferencesUpdated = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean modified = false;
                
                // Find all ClassOrInterfaceType nodes that represent ManagedObject without type arguments
                List<ClassOrInterfaceType> managedObjectTypes = cu.findAll(ClassOrInterfaceType.class, type -> 
                    "ManagedObject".equals(type.getNameAsString()) && 
                    !type.getTypeArguments().isPresent()
                );
                
                for (ClassOrInterfaceType type : managedObjectTypes) {
                    // Check if this type is being used in an implements clause
                    boolean isInImplementsClause = type.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(classDecl -> classDecl.getImplementedTypes().contains(type))
                        .orElse(false);
                    
                    if (!isInImplementsClause) {
                        // Add wildcard type argument <?> to raw ManagedObject type references
                        // This fixes the breaking change where APIs now return ManagedObject<?>
                        type.setTypeArguments(new NodeList<>(new WildcardType()));
                        modified = true;
                        typeReferencesUpdated++;
                        System.out.println("  Updated type reference: ManagedObject to ManagedObject<?> at line " + 
                            type.getBegin().map(pos -> pos.line).orElse(0) + " in " + 
                            javaFile.getFileName());
                    } else {
                        // For implements clauses, leave as raw ManagedObject
                        // Different projects may need different type parameters
                        // Raw types will compile (with warnings) and are more generic
                        System.out.println("  Skipping implements clause at line " + 
                            type.getBegin().map(pos -> pos.line).orElse(0) + " in " + 
                            javaFile.getFileName() + " - raw type may need project-specific fix");
                    }
                }
                
                if (modified) {
                    // Write the modified file back
                    String newContent = cu.toString(new DefaultPrinterConfiguration());
                    Files.write(javaFile, newContent.getBytes());
                    filesModified++;
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Type references updated: " + typeReferencesUpdated);
        
        if (filesModified == 0) {
            System.out.println("\nNo files were modified. This could mean:");
            System.out.println("  1. No raw ManagedObject types were found");
            System.out.println("  2. The source directory doesn't contain Java files with this issue");
            System.out.println("  3. ManagedObject is already properly parameterized");
        }
    }
}