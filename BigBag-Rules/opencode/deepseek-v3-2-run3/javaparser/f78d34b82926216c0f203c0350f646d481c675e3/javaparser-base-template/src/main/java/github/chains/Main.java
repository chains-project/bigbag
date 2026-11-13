package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation for handling breaking dependency updates.
 * This transformation can be applied to any Maven project affected by package renaming.
 */
public class Main {
    
    /**
     * Configuration for the transformation.
     * These parameters should be adjusted based on the specific breaking change.
     */
    private static class TransformationConfig {
        // Old package prefix to be replaced
        final String oldPackagePrefix;
        // New package prefix to replace with
        final String newPackagePrefix;
        // List of specific class migrations (optional)
        final List<ClassMigration> classMigrations;
        
        TransformationConfig(String oldPackagePrefix, String newPackagePrefix, List<ClassMigration> classMigrations) {
            this.oldPackagePrefix = oldPackagePrefix;
            this.newPackagePrefix = newPackagePrefix;
            this.classMigrations = classMigrations;
        }
    }
    
    /**
     * Represents migration of a specific class with potential method signature changes.
     */
    private static class ClassMigration {
        final String oldClassName;
        final String newClassName;
        final List<MethodMigration> methodMigrations;
        
        ClassMigration(String oldClassName, String newClassName, List<MethodMigration> methodMigrations) {
            this.oldClassName = oldClassName;
            this.newClassName = newClassName;
            this.methodMigrations = methodMigrations;
        }
    }
    
    /**
     * Represents migration of a method signature.
     */
    private static class MethodMigration {
        final String oldMethodSignature;
        final String newMethodSignature;
        
        MethodMigration(String oldMethodSignature, String newMethodSignature) {
            this.oldMethodSignature = oldMethodSignature;
            this.newMethodSignature = newMethodSignature;
        }
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <config-file>");
            System.err.println("  source-directory: Root directory of Java source files to transform");
            System.err.println("  config-file: JSON configuration file for the transformation");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String configFile = args[1];
        
        // Load configuration (in a real scenario, this would be from JSON)
        TransformationConfig config = loadDefaultConfig();
        
        System.out.println("Starting transformation...");
        System.out.println("Old package: " + config.oldPackagePrefix);
        System.out.println("New package: " + config.newPackagePrefix);
        
        // Find all Java files
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        // Configure JavaParser
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        
        ParserConfiguration parserConfig = new ParserConfiguration();
        parserConfig.setSymbolResolver(symbolSolver);
        parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);
        JavaParser javaParser = new JavaParser(parserConfig);
        
        int transformedFiles = 0;
        int totalChanges = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                // Enable lexical preservation to maintain formatting
                LexicalPreservingPrinter.setup(cu);
                
                int changesInFile = transformCompilationUnit(cu, config);
                
                if (changesInFile > 0) {
                    // Write the transformed file
                    String transformedCode = LexicalPreservingPrinter.print(cu);
                    Files.write(javaFile, transformedCode.getBytes());
                    transformedFiles++;
                    totalChanges += changesInFile;
                    System.out.println("Transformed " + javaFile + " (" + changesInFile + " changes)");
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Transformed " + transformedFiles + " files with " + totalChanges + " total changes");
    }
    
    /**
     * Transform a single compilation unit based on the configuration.
     */
    private static int transformCompilationUnit(CompilationUnit cu, TransformationConfig config) {
        int changes = 0;
        
        // Transform imports
        if (cu.getImports() != null) {
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                
                // Check if this import needs transformation
                if (importName.startsWith(config.oldPackagePrefix)) {
                    String newImportName = importName.replace(config.oldPackagePrefix, config.newPackagePrefix);
                    importDecl.setName(newImportName);
                    changes++;
                    
                    System.out.println("  Updated import: " + importName + " -> " + newImportName);
                }
                
                // Check for specific class migrations
                for (ClassMigration migration : config.classMigrations) {
                    if (importName.equals(config.oldPackagePrefix + "." + migration.oldClassName) ||
                        importName.endsWith("." + migration.oldClassName)) {
                        
                        String newImportName;
                        if (importName.equals(config.oldPackagePrefix + "." + migration.oldClassName)) {
                            // Full qualified name
                            newImportName = config.newPackagePrefix + "." + migration.newClassName;
                        } else {
                            // Partial import or wildcard
                            newImportName = importName.replace(
                                "." + migration.oldClassName, 
                                "." + migration.newClassName
                            );
                        }
                        importDecl.setName(newImportName);
                        changes++;
                        System.out.println("  Migrated class import: " + importName + " -> " + newImportName);
                    }
                }
            }
        }
        
        // Also need to handle fully qualified names in the code itself
        // For example: develop.p2p.lib.WaveCreator wave = new develop.p2p.lib.WaveCreator(...);
        // This requires a more sophisticated AST traversal
        
        return changes;
    }
    
    /**
     * Recursively find all Java files in a directory.
     */
    private static List<Path> findJavaFiles(String directory) throws IOException {
        return Files.walk(Paths.get(directory))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
    }
    
    /**
     * Load default configuration for the PeyangSuperLibrary breaking change.
     * In a real scenario, this would load from a JSON config file.
     */
    private static TransformationConfig loadDefaultConfig() {
        String oldPackagePrefix = "develop.p2p.lib";
        String newPackagePrefix = "tokyo.peya.lib";
        
        List<ClassMigration> classMigrations = new ArrayList<>();
        
        // Add specific class migrations if needed
        // For this breaking change, classes have the same names, just different packages
        classMigrations.add(new ClassMigration("WaveCreator", "WaveCreator", new ArrayList<>()));
        classMigrations.add(new ClassMigration("LearnMath", "LearnMath", new ArrayList<>()));
        classMigrations.add(new ClassMigration("LeetConverter", "LeetConverter", new ArrayList<>()));
        
        // If there were method signature changes, we would add them here
        // Example: classMigrations.add(new ClassMigration("OldClass", "NewClass", 
        //     Arrays.asList(new MethodMigration("oldMethod()", "newMethod(int)"))));
        
        return new TransformationConfig(oldPackagePrefix, newPackagePrefix, classMigrations);
    }
}
