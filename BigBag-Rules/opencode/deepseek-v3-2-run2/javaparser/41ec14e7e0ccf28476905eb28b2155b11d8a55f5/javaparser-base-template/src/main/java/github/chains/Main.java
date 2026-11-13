package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking dependency updates in pm-wicket-utils 5.0
 * 
 * This transformation handles two breaking changes:
 * 1. HibernateValidatorProperty.validatorFactory field removal
 * 2. javax.validation package migration to jakarta.validation
 * 
 * The rule is parameterized and can be applied to any project affected by these changes.
 */
public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_VALIDATOR_FACTORY_CLASS = "com.premiumminds.webapp.wicket.validators.HibernateValidatorProperty";
    private static final String OLD_VALIDATOR_FACTORY_FIELD = "validatorFactory";
    private static final String NEW_VALIDATOR_FACTORY_CALL = "Validation.buildDefaultValidatorFactory().getValidator()";
    private static final String OLD_VALIDATION_PACKAGE_PREFIX = "javax.validation";
    private static final String NEW_VALIDATION_PACKAGE_PREFIX = "jakarta.validation";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            }
            
            System.out.println("Transformation completed. Modified " + transformedFiles + " files.");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
            
            // Enable lexical preservation to maintain formatting
            LexicalPreservingPrinter.setup(cu);
            
            boolean modified = false;
            
            // Transform 1: Replace javax.validation imports with jakarta.validation
            modified |= transformValidationImports(cu);
            
            // Transform 2: Replace HibernateValidatorProperty.validatorFactory.getValidator() calls
            modified |= transformValidatorFactoryCalls(cu);
            
            if (modified) {
                String transformedContent = LexicalPreservingPrinter.print(cu);
                Files.writeString(filePath, transformedContent);
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Transform javax.validation imports to jakarta.validation
     */
    private static boolean transformValidationImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith(OLD_VALIDATION_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(
                    OLD_VALIDATION_PACKAGE_PREFIX, 
                    NEW_VALIDATION_PACKAGE_PREFIX
                );
                importDecl.setName(newImportName);
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * Transform HibernateValidatorProperty.validatorFactory.getValidator() calls
     * to Validation.buildDefaultValidatorFactory().getValidator()
     */
    private static boolean transformValidatorFactoryCalls(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all field access expressions matching the pattern
        List<FieldAccessExpr> fieldAccesses = cu.findAll(FieldAccessExpr.class, 
            fieldAccess -> isValidatorFactoryAccess(fieldAccess));
        
        for (FieldAccessExpr fieldAccess : fieldAccesses) {
            // Replace the entire field access with the new factory method call
            MethodCallExpr newCall = createNewValidatorFactoryCall();
            
            // Replace the field access in its parent
            Node parent = fieldAccess.getParentNode().orElse(null);
            if (parent != null) {
                parent.replace(fieldAccess, newCall);
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * Check if a field access expression matches the old validator factory pattern
     */
    private static boolean isValidatorFactoryAccess(FieldAccessExpr fieldAccess) {
        try {
            // Check if it's a field access on HibernateValidatorProperty
            if (fieldAccess.getScope().isNameExpr()) {
                NameExpr scope = fieldAccess.getScope().asNameExpr();
                String scopeName = scope.getNameAsString();
                
                // Match the class name (could be simple name or fully qualified)
                String simpleClassName = OLD_VALIDATOR_FACTORY_CLASS.substring(
                    OLD_VALIDATOR_FACTORY_CLASS.lastIndexOf('.') + 1
                );
                
                if (scopeName.equals(simpleClassName) || scopeName.equals(OLD_VALIDATOR_FACTORY_CLASS)) {
                    // Check if it's accessing the validatorFactory field
                    return fieldAccess.getNameAsString().equals(OLD_VALIDATOR_FACTORY_FIELD);
                }
            }
        } catch (Exception e) {
            // If parsing fails, skip this node
        }
        
        return false;
    }
    
    /**
     * Create the new validator factory method call expression
     */
    private static MethodCallExpr createNewValidatorFactoryCall() {
        // Create: Validation.buildDefaultValidatorFactory().getValidator()
        MethodCallExpr buildCall = new MethodCallExpr(
            new NameExpr("Validation"), 
            "buildDefaultValidatorFactory"
        );
        
        MethodCallExpr getValidatorCall = new MethodCallExpr(buildCall, "getValidator");
        return getValidatorCall;
    }
}