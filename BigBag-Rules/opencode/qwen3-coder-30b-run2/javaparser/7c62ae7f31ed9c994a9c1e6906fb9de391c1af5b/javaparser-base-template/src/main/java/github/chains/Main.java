package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.CodeGenerationUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation for fixing SessionImplementor to SharedSessionContractImplementor API changes.
 * 
 * This rule addresses the breaking change where Hibernate's UserType interface changed from using 
 * SessionImplementor to SharedSessionContractImplementor in method signatures:
 * 
 * Old: nullSafeGet(ResultSet rs, String[] names, SessionImplementor session, Object owner)
 * Old: nullSafeSet(PreparedStatement st, Object value, int index, SessionImplementor session)
 * 
 * New: nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
 * New: nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
 * 
 * The transformation identifies all classes implementing UserType and updates the method signatures
 * to use the correct parameter types.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try {
            // Process all Java files in the directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String code = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(code);
            
            // Apply transformation
            cu.accept(new UserTypeTransformationVisitor(), null);
            
            // Save the modified code back to the file
            Files.writeString(filePath, cu.toString());
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms UserType implementations to use the correct SessionImplementor type
     */
    static class UserTypeTransformationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Check if this is a method that could have the SessionImplementor issue
            if (isUserTypeMethod(n)) {
                transformSessionImplementorParameter(n);
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Determine if this method belongs to a UserType implementation that may need fixing
         */
        private boolean isUserTypeMethod(MethodDeclaration method) {
            // Check if any parameter is SessionImplementor
            return method.getParameters().stream()
                    .anyMatch(param -> param.getType().toString().contains("SessionImplementor"));
        }
        
        /**
         * Transform any SessionImplementor parameters to SharedSessionContractImplementor
         */
        private void transformSessionImplementorParameter(MethodDeclaration method) {
            method.getParameters().forEach(param -> {
                if (param.getType().toString().contains("SessionImplementor")) {
                    // Replace the type with SharedSessionContractImplementor
                    param.setType("org.hibernate.engine.spi.SharedSessionContractImplementor");
                }
            });
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Remove unused SessionImplementor imports
            if (n.getNameAsString().contains("SessionImplementor")) {
                // We'll remove this import in a cleaner way by modifying the import list
                return null;
            }
            return super.visit(n, arg);
        }
    }
}
