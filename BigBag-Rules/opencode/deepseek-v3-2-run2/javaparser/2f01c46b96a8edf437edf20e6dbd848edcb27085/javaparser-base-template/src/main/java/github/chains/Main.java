package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing plexus-utils 4.0.0 breaking change.
 * 
 * This transformation addresses the removal of org.codehaus.plexus.util.xml.Xpp3Dom
 * by replacing it with org.apache.maven.plugin.descriptor.Xpp3Dom.
 * 
 * The transformation is generic and can be applied to any project affected by
 * this breaking dependency update.
 */
public class Main {
    
    // Configuration parameters - these can be changed to apply to different projects
    private static final String OLD_XPP3DOM_IMPORT = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_XPP3DOM_IMPORT = "org.apache.maven.shared.utils.xml.Xpp3Dom";
    private static final String OLD_XPP3DOM_TYPE = "Xpp3Dom";
    private static final String NEW_XPP3DOM_TYPE = "Xpp3Dom";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        try {
            transformProject(sourceDirectory);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Applies the transformation to all Java files in the specified directory.
     */
    private static void transformProject(String sourceDirectory) throws IOException {
        List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Successfully transformed " + transformedFiles + " files");
    }
    
    /**
     * Transforms a single Java file by:
     * 1. Removing old Xpp3Dom imports from plexus-utils
     * 2. Adding new Xpp3Dom imports from Maven plugin API
     * 3. Replacing type references to use the new import
     */
    private static boolean transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        
        // Parse the Java file
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = parser.parse(content);
        if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        CompilationUnit cu = parseResult.getResult().get();
        boolean wasModified = false;
        
        // Check if file uses the old Xpp3Dom import
        boolean hasOldImport = cu.getImports().stream()
                .anyMatch(importDecl -> importDecl.getNameAsString().equals(OLD_XPP3DOM_IMPORT));
        
        if (!hasOldImport) {
            // File doesn't use the old import, skip it
            return false;
        }
        
        System.out.println("Transforming: " + javaFile);
        
        // Step 1: Remove old imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration importDecl = imports.get(i);
            if (importDecl.getNameAsString().equals(OLD_XPP3DOM_IMPORT)) {
                importDecl.remove();
                wasModified = true;
            }
        }
        
        // Step 2: Add new import if not already present
        boolean hasNewImport = cu.getImports().stream()
                .anyMatch(importDecl -> importDecl.getNameAsString().equals(NEW_XPP3DOM_IMPORT));
        
        if (!hasNewImport && wasModified) {
            ImportDeclaration newImport = new ImportDeclaration(
                NEW_XPP3DOM_IMPORT, false, false
            );
            cu.addImport(newImport);
        }
        
        // Step 3: Replace type references in the code
        // Note: Since the simple name "Xpp3Dom" remains the same, we don't need to change
        // type references - only the import needs to be updated.
        
        if (wasModified) {
            // Write the transformed content back to the file
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String transformedContent = printer.print(cu);
            Files.writeString(javaFile, transformedContent);
        }
        
        return wasModified;
    }
}