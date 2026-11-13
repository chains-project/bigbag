package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A generic JavaParser transformation for migrating package imports.
 * This tool can be used to fix breaking changes in dependencies where
 * packages have been moved or renamed.
 * 
 * Example: When assertj-core removed its internal bytebuddy re-export,
 * projects needed to update imports from:
 *   org.assertj.core.internal.bytebuddy.*
 * to:
 *   net.bytebuddy.*
 */
public class Main {
    // Configuration: Make these configurable via system properties or command line
    private static final String OLD_PACKAGE_PREFIX;
    private static final String NEW_PACKAGE_PREFIX;
    
    static {
        // Allow configuration via system properties or use defaults
        String oldPrefix = System.getProperty("old.package.prefix");
        String newPrefix = System.getProperty("new.package.prefix");
        
        if (oldPrefix != null && newPrefix != null) {
            OLD_PACKAGE_PREFIX = oldPrefix;
            NEW_PACKAGE_PREFIX = newPrefix;
        } else {
            // Default transformation for the assertj-core bytebuddy migration
            OLD_PACKAGE_PREFIX = "org.assertj.core.internal.bytebuddy";
            NEW_PACKAGE_PREFIX = "net.bytebuddy";
        }
    }
    
    // Track transformations across visitor callbacks
    private static class TransformationCounter {
        int count = 0;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming package imports:");
        System.out.println("  From: " + OLD_PACKAGE_PREFIX);
        System.out.println("  To:   " + NEW_PACKAGE_PREFIX);
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println();
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (Path javaFile : javaFiles) {
                int transformations = transformFile(javaFile);
                if (transformations > 0) {
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("  Transformed " + transformations + " references in " + 
                                     javaFile.toString().replace(sourceDir, ""));
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  Total transformations: " + totalTransformations);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java -cp target/classes github.chains.Main <source-directory>");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  # Use default transformation (assertj-core bytebuddy migration)");
        System.err.println("  java -cp target/classes github.chains.Main /path/to/project/src");
        System.err.println();
        System.err.println("  # Use custom package transformation");
        System.err.println("  java -Dold.package.prefix=old.package -Dnew.package.prefix=new.package \\");
        System.err.println("       -cp target/classes github.chains.Main /path/to/project/src");
        System.err.println();
        System.err.println("Default transformation:");
        System.err.println("  " + OLD_PACKAGE_PREFIX + " -> " + NEW_PACKAGE_PREFIX);
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws Exception {
        try (Stream<Path> stream = Files.walk(startDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static int transformFile(Path javaFile) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        TransformationCounter counter = new TransformationCounter();
        
        // Transform regular imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                importDecl.setName(newImportName);
                counter.count++;
            }
        }
        
        // Use a visitor to transform fully-qualified names in the code
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(Name n, Void arg) {
                String nameStr = n.asString();
                if (nameStr.startsWith(OLD_PACKAGE_PREFIX)) {
                    String newName = nameStr.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                    counter.count++;
                    return new Name(newName);
                }
                return super.visit(n, arg);
            }
        }, null);
        
        if (counter.count > 0) {
            // Write back the transformed file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinterVisitor visitor = new DefaultPrettyPrinterVisitor(config);
            cu.accept(visitor, null);
            String transformedCode = visitor.toString();
            
            Files.write(javaFile, transformedCode.getBytes());
        }
        
        return counter.count;
    }
    
    /**
     * Helper method to demonstrate the transformation pattern.
     * This shows the abstract pattern of the breaking change.
     */
    public static class BreakingChangePattern {
        /**
         * OLD API PATTERN:
         *   import org.assertj.core.internal.bytebuddy.ByteBuddy;
         *   import org.assertj.core.internal.bytebuddy.TypeCache;
         *   import static org.assertj.core.internal.bytebuddy.matcher.ElementMatchers.any;
         *   
         *   ByteBuddy byteBuddy = new ByteBuddy();
         *   TypeCache<SimpleKey> cache = new TypeCache.WithInlineExpunction<>(TypeCache.Sort.SOFT);
         */
        
        /**
         * NEW API PATTERN:
         *   import net.bytebuddy.ByteBuddy;
         *   import net.bytebuddy.TypeCache;
         *   import static net.bytebuddy.matcher.ElementMatchers.any;
         *   
         *   ByteBuddy byteBuddy = new ByteBuddy();
         *   TypeCache<SimpleKey> cache = new TypeCache.WithInlineExpunction<>(TypeCache.Sort.SOFT);
         */
        
        /**
         * STRUCTURAL TRANSFORMATION:
         *   Replace all occurrences of:
         *     "org.assertj.core.internal.bytebuddy"
         *   with:
         *     "net.bytebuddy"
         *   
         *   Applies to:
         *     - Import declarations
         *     - Static imports  
         *     - Fully-qualified type names in code
         *     - Any string literal that might contain the package name
         */
    }
}