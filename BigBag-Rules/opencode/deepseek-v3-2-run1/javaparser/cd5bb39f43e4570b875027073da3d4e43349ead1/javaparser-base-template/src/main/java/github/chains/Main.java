package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing the breaking change in plexus-utils 4.0.0
 * where Xpp3Dom was moved from org.codehaus.plexus.util.xml to org.apache.maven.shared.utils.xml.
 * 
 * This transformation can be applied to any Maven project affected by this breaking change.
 */
public class Main {
    
    // Old package pattern to match
    private static final String OLD_PACKAGE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String OLD_PACKAGE_PREFIX = "org.codehaus.plexus.util.xml.";
    
    // New package pattern to replace with
    private static final String NEW_PACKAGE = "org.apache.maven.shared.utils.xml.Xpp3Dom";
    private static final String NEW_PACKAGE_PREFIX = "org.apache.maven.shared.utils.xml.";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("  <source-directory>: Path to Java source files to transform");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            boolean fileChanged = transformFile(javaFile);
            if (fileChanged) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformation complete!");
        System.out.println("Transformed " + transformedFiles + " files with " + totalTransformations + " total changes.");
    }
    
    private static boolean transformFile(Path javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + javaFile)
        );
        
        boolean fileChanged = false;
        
        // 1. Transform import declarations
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            if (importName.equals(OLD_PACKAGE)) {
                // Exact match for Xpp3Dom import
                importDecl.setName(NEW_PACKAGE);
                fileChanged = true;
                System.out.println("  Updated import: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
            } else if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                // Also handle other classes from the same package if needed
                String className = importName.substring(OLD_PACKAGE_PREFIX.length());
                importDecl.setName(NEW_PACKAGE_PREFIX + className);
                fileChanged = true;
                System.out.println("  Updated import: " + importName + " -> " + (NEW_PACKAGE_PREFIX + className));
            }
        }
        
// Note: We're only transforming import declarations.
        // Fully-qualified references in code would need additional visitor logic,
        // but import transformation is the most common fix needed.
        
        // 3. Save the file if changes were made
        if (fileChanged) {
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String transformedCode = printer.print(cu);
            
            Files.write(javaFile, transformedCode.getBytes());
            System.out.println("Transformed: " + javaFile);
        }
        
        return fileChanged;
    }
}