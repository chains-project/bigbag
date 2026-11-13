package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile.toFile())) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            boolean modified = false;
            
            // Check if file contains Jasypt Spring Security imports or references
            boolean hasJasyptSpringSecurity = false;
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration imp : imports) {
                String importName = imp.getNameAsString();
                if (importName.equals("org.jasypt.spring.security.PasswordEncoder") ||
                    importName.equals("org.jasypt.spring.security.PBEPasswordEncoder")) {
                    hasJasyptSpringSecurity = true;
                    break;
                }
            }
            
            if (!hasJasyptSpringSecurity) {
                // Also check for usage in code
                String fileContent = Files.readString(file.toPath());
                if (fileContent.contains("org.jasypt.spring.security")) {
                    hasJasyptSpringSecurity = true;
                }
            }
            
            if (!hasJasyptSpringSecurity) {
                return false;
            }
            
            System.out.println("Processing file with Jasypt Spring Security: " + file.getName());
            
            // Transform imports
            List<ImportDeclaration> toRemove = new ArrayList<>();
            for (ImportDeclaration imp : imports) {
                String importName = imp.getNameAsString();
                
                // Remove Jasypt Spring Security imports
                if (importName.equals("org.jasypt.spring.security.PasswordEncoder") ||
                    importName.equals("org.jasypt.spring.security.PBEPasswordEncoder")) {
                    toRemove.add(imp);
                    modified = true;
                    System.out.println("  Removing import: " + importName);
                }
            }
            // Remove imports after iteration
            for (ImportDeclaration imp : toRemove) {
                imp.remove();
            }
            
            // Find and transform PasswordEncoder constructor calls
            List<ObjectCreationExpr> toTransform = new ArrayList<>();
            cu.walk(ObjectCreationExpr.class, objectCreation -> {
                String typeName = objectCreation.getTypeAsString();
                if (typeName.equals("PasswordEncoder") || typeName.equals("PBEPasswordEncoder")) {
                    toTransform.add(objectCreation);
                }
            });
            
            for (ObjectCreationExpr objectCreation : toTransform) {
                String typeName = objectCreation.getTypeAsString();
                if (typeName.equals("PasswordEncoder")) {
                    transformPasswordEncoderCreation(objectCreation, cu);
                    modified = true;
                    System.out.println("  Transformed PasswordEncoder creation");
                } else if (typeName.equals("PBEPasswordEncoder")) {
                    transformPBEPasswordEncoderCreation(objectCreation, cu);
                    modified = true;
                    System.out.println("  Transformed PBEPasswordEncoder creation");
                }
            }
            
            // Handle setPasswordEncryptor method calls that might need adjustment
            cu.walk(MethodCallExpr.class, methodCall -> {
                String methodName = methodCall.getNameAsString();
                if (methodName.equals("setPasswordEncryptor") || methodName.equals("setPbeStringEncryptor")) {
                    // These method calls are now on anonymous inner classes that don't have these methods
                    // We need to check if they're being called on a newly created object
                    // If so, we should integrate the logic into the anonymous class
                    handleMethodCallOnNewObject(methodCall, cu);
                }
            });
            
            if (modified) {
                // Write the transformed file
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(cu.toString());
                }
                System.out.println("  Successfully transformed: " + file.getName());
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static void transformPasswordEncoderCreation(ObjectCreationExpr objectCreation, CompilationUnit cu) {
        // Replace: new PasswordEncoder()
        // With anonymous inner class that implements Acegi PasswordEncoder
        // and can accept setPasswordEncryptor() calls
        
        String replacementCode = 
            "new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
            "    private org.jasypt.util.password.PasswordEncryptor passwordEncryptor;\n" +
            "    \n" +
            "    public void setPasswordEncryptor(org.jasypt.util.password.PasswordEncryptor passwordEncryptor) {\n" +
            "        this.passwordEncryptor = passwordEncryptor;\n" +
            "    }\n" +
            "    \n" +
            "    @Override\n" +
            "    public String encodePassword(String rawPass, Object salt) {\n" +
            "        return passwordEncryptor.encryptPassword(rawPass);\n" +
            "    }\n" +
            "    \n" +
            "    @Override\n" +
            "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
            "        return passwordEncryptor.checkPassword(rawPass, encPass);\n" +
            "    }\n" +
            "}";
        
        try {
            JavaParser parser = new JavaParser();
            Expression replacementExpr = parser.parseExpression(replacementCode).getResult().orElse(null);
            if (replacementExpr instanceof ObjectCreationExpr) {
                ObjectCreationExpr replacement = (ObjectCreationExpr) replacementExpr;
                objectCreation.replace(replacement);
            }
        } catch (Exception e) {
            System.err.println("Error transforming PasswordEncoder: " + e.getMessage());
        }
    }
    
    private static void transformPBEPasswordEncoderCreation(ObjectCreationExpr objectCreation, CompilationUnit cu) {
        // Replace: new PBEPasswordEncoder()
        // With anonymous inner class that implements Acegi PasswordEncoder
        
        String replacementCode = 
            "new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
            "    private org.jasypt.encryption.pbe.PBEStringEncryptor pbeStringEncryptor;\n" +
            "    \n" +
            "    public void setPbeStringEncryptor(org.jasypt.encryption.pbe.PBEStringEncryptor pbeStringEncryptor) {\n" +
            "        this.pbeStringEncryptor = pbeStringEncryptor;\n" +
            "    }\n" +
            "    \n" +
            "    @Override\n" +
            "    public String encodePassword(String rawPass, Object salt) {\n" +
            "        return pbeStringEncryptor.encrypt(rawPass);\n" +
            "    }\n" +
            "    \n" +
            "    @Override\n" +
            "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
            "        try {\n" +
            "            String decrypted = pbeStringEncryptor.decrypt(encPass);\n" +
            "            return rawPass.equals(decrypted);\n" +
            "        } catch (Exception e) {\n" +
            "            return false;\n" +
            "        }\n" +
            "    }\n" +
            "}";
        
        try {
            JavaParser parser = new JavaParser();
            Expression replacementExpr = parser.parseExpression(replacementCode).getResult().orElse(null);
            if (replacementExpr instanceof ObjectCreationExpr) {
                ObjectCreationExpr replacement = (ObjectCreationExpr) replacementExpr;
                objectCreation.replace(replacement);
            }
        } catch (Exception e) {
            System.err.println("Error transforming PBEPasswordEncoder: " + e.getMessage());
        }
    }
    
    private static void handleMethodCallOnNewObject(MethodCallExpr methodCall, CompilationUnit cu) {
        // Check if this method call is on a newly created object that we transformed
        // If it's setPasswordEncryptor or setPbeStringEncryptor called on a variable
        // that was assigned from new PasswordEncoder() or new PBEPasswordEncoder(),
        // we might need to handle it differently
        
        // For now, we'll leave these as-is since our anonymous classes have these methods
        // But they might need to be integrated into the constructor
        
        // Example pattern:
        // PasswordEncoder encoder = new PasswordEncoder();
        // encoder.setPasswordEncryptor(new StrongPasswordEncryptor());
        // This should become:
        // PasswordEncoder encoder = new PasswordEncoder() {
        //     private PasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
        //     ...
        // };
        
        // This is complex to handle generically, so we'll leave it for now
        // and rely on the manual fixes for specific cases
    }
}