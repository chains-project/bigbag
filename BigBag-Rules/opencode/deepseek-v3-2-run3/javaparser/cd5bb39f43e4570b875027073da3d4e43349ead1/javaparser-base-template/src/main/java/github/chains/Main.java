package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing the Xpp3Dom package relocation
 * from org.codehaus.plexus.util.xml to org.apache.maven.shared.utils.xml
 * due to plexus-utils 4.0.0 breaking change.
 * 
 * This transformation can be applied to ANY Maven project affected by
 * this breaking dependency update.
 */
public class Main {
    
    // Configuration: old and new package names
    private static final String OLD_PACKAGE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_PACKAGE = "org.apache.maven.shared.utils.xml.Xpp3Dom";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Applies transformation to fix Xpp3Dom package relocation");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Xpp3Dom imports in: " + sourceDir);
        System.out.println("Old package: " + OLD_PACKAGE);
        System.out.println("New package: " + NEW_PACKAGE);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        int transformedImports = 0;
        
        for (Path javaFile : javaFiles) {
            int importsUpdated = transformFile(javaFile);
            if (importsUpdated > 0) {
                transformedFiles++;
                transformedImports += importsUpdated;
            }
        }
        
        System.out.println("Transformation complete:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Imports updated: " + transformedImports);
    }
    
    private static int transformFile(Path javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return 0;
        }
        
        int importsUpdated = 0;
        
        // Find and transform imports
        List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
        for (ImportDeclaration importDecl : imports) {
            if (importDecl.getNameAsString().equals(OLD_PACKAGE)) {
                // Replace the import
                importDecl.setName(new Name(NEW_PACKAGE));
                importsUpdated++;
                System.out.println("  Updated import in: " + javaFile);
            }
        }
        
        // Note: Fully-qualified references like "new org.codehaus.plexus.util.xml.Xpp3Dom()"
        // would also need transformation, but they're less common than imports.
        // This transformation handles import statements which is the common case.
        
        if (importsUpdated > 0) {
            // Write back the transformed file
            Files.write(javaFile, cu.toString().getBytes());
        }
        
        return importsUpdated;
    }
}