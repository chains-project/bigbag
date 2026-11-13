package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            SourceRoot sourceRoot = new SourceRoot(sourceDir);
            sourceRoot.parse("", (localPath, absolutePath, result) -> {
                result.ifSuccessful(cu -> transformCompilationUnit(cu, absolutePath));
                return SourceRoot.Callback.Result.SAVE;
            });
            
            sourceRoot.saveAll();
            System.out.println("Transformation completed.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void transformCompilationUnit(CompilationUnit cu, Path filePath) {
        System.out.println("Processing: " + filePath);
        
        // Track changes for reporting
        final boolean[] importsChanged = new boolean[]{false};
        
        // 1. Fix imports from persistence to core.persistence
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.matches("com\\.premiumminds\\.billy\\.(?!core\\.)persistence\\.entities\\.jpa\\.QJPA.*")) {
                // Convert: com.premiumminds.billy.persistence.entities.jpa.QJPABusinessEntity
                // To:      com.premiumminds.billy.core.persistence.entities.jpa.QJPABusinessEntity
                String newImportName = importName.replace(
                    "com.premiumminds.billy.persistence.entities.jpa.",
                    "com.premiumminds.billy.core.persistence.entities.jpa.");
                importDecl.setName(newImportName);
                importsChanged[0] = true;
                System.out.println("  Updated import: " + importName + " -> " + newImportName);
            }
        }
        
        // 2. Find references to QJPA*Entity classes and ensure they're imported
        // This visitor will find Type references like "QJPAGenericInvoiceEntity"
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType type, Void arg) {
                String typeName = type.getNameAsString();
                
                // Check if it's a QJPA*Entity type
                if (typeName.matches("QJPA[A-Za-z]*Entity")) {
                    // Check if there's already an import for this class
                    boolean hasImport = cu.getImports().stream()
                        .anyMatch(imp -> {
                            String impName = imp.getNameAsString();
                            return impName.endsWith("." + typeName) || 
                                   impName.equals(typeName);
                        });
                    
                    // Check if we're in the same package (no import needed)
                    Optional<Name> packageName = cu.getPackageDeclaration().map(pd -> pd.getName());
                    boolean inSamePackage = packageName.isPresent() && 
                        packageName.get().asString().equals("com.premiumminds.billy.core.persistence.entities.jpa");
                    
                    if (!hasImport && !inSamePackage) {
                        // Add import for core.persistence package
                        String importToAdd = "com.premiumminds.billy.core.persistence.entities.jpa." + typeName;
                        
                        // Check if we're trying to import from wrong package
                        boolean hasWrongImport = cu.getImports().stream()
                            .anyMatch(imp -> {
                                String impName = imp.getNameAsString();
                                return impName.endsWith("." + typeName) && 
                                       impName.startsWith("com.premiumminds.billy.persistence.entities.jpa.");
                            });
                        
                        if (!hasWrongImport) {
                            cu.addImport(importToAdd);
                            importsChanged[0] = true;
                            System.out.println("  Added import: " + importToAdd);
                        }
                    }
                }
                return super.visit(type, arg);
            }
        }, null);
        
        if (importsChanged[0]) {
            System.out.println("  File modified: " + filePath);
        }
    }
}