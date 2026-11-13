package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic transformation rule for package relocation breaking changes.
 * This rule updates import statements when a class has been moved from one package to another.
 * 
 * Configuration parameters:
 * - OLD_PACKAGE: The original package where the class was located (e.g., "eu.europa.esig.dss.pades")
 * - NEW_PACKAGE: The new package where the class is located (e.g., "eu.europa.esig.dss.enumerations")
 * - CLASS_NAME: The name of the class that was moved (e.g., "CertificationPermission")
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java github.chains.Main <source-directory> <old-package> <new-package> <class-name>");
            System.err.println("Example: java github.chains.Main /path/to/project/src eu.europa.esig.dss.pades eu.europa.esig.dss.enumerations CertificationPermission");
            System.err.println("");
            System.err.println("For package relocation breaking changes where a class has been moved from one package to another.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackage = args[1];
        String newPackage = args[2];
        String className = args[3];
        
        String oldImport = oldPackage + "." + className;
        String newImport = newPackage + "." + className;
        
        System.out.println("Applying package relocation transformation:");
        System.out.println("  Old import: " + oldImport);
        System.out.println("  New import: " + newImport);
        System.out.println("  Source directory: " + sourceDir);
        
        try {
            transformDirectory(sourceDir, oldImport, newImport);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir, String oldImport, String newImport) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(filePath -> transformFile(filePath, oldImport, newImport));
        }
    }
    
    private static void transformFile(Path filePath, String oldImport, String newImport) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new IOException("Failed to parse " + filePath)
            );
            
            PackageRelocationVisitor visitor = new PackageRelocationVisitor(oldImport, newImport);
            CompilationUnit transformedCu = (CompilationUnit) cu.accept(visitor, null);
            
            if (visitor.wasModified()) {
                System.out.println("  Modified: " + filePath);
                
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                String transformedCode = printer.print(transformedCu);
                
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(transformedCode);
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class PackageRelocationVisitor extends ModifierVisitor<Void> {
        private final String oldImport;
        private final String newImport;
        private boolean modified = false;
        
        public PackageRelocationVisitor(String oldImport, String newImport) {
            this.oldImport = oldImport;
            this.newImport = newImport;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches the old package.class pattern
            if (importName.equals(oldImport)) {
                System.out.println("    Found import to update: " + importName);
                modified = true;
                
                // Create new import declaration with same static/asterisk settings
                ImportDeclaration newImportDecl = new ImportDeclaration(
                    newImport,
                    importDecl.isStatic(),
                    importDecl.isAsterisk()
                );
                return newImportDecl;
            }
            
            return super.visit(importDecl, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}