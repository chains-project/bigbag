package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -cp . github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        int processedFiles = 0;
        int fixedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            processedFiles++;
            boolean fileModified = processFile(javaFile.toFile());
            if (fileModified) {
                fixedFiles++;
                System.out.println("Fixed: " + javaFile);
            }
        }
        
        System.out.println("Processed " + processedFiles + " files");
        System.out.println("Fixed " + fixedFiles + " files with Jasypt Spring Security issues");
    }
    
    private static boolean processFile(File file) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream(file)).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Check for and remove org.jasypt.spring.security imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        List<ImportDeclaration> importsToRemove = imports.stream()
            .filter(imp -> imp.getNameAsString().startsWith("org.jasypt.spring.security"))
            .collect(Collectors.toList());
            
        if (!importsToRemove.isEmpty()) {
            imports.removeAll(importsToRemove);
            modified = true;
            
            // Add required imports for adapters if not already present
            if (!cu.getImports().stream().anyMatch(imp -> imp.getNameAsString().equals("org.jasypt.util.password.PasswordEncryptor"))) {
                cu.addImport("org.jasypt.util.password.PasswordEncryptor");
            }
            if (!cu.getImports().stream().anyMatch(imp -> imp.getNameAsString().equals("org.jasypt.encryption.pbe.PBEStringEncryptor"))) {
                cu.addImport("org.jasypt.encryption.pbe.PBEStringEncryptor");
            }
            if (!cu.getImports().stream().anyMatch(imp -> imp.getNameAsString().equals("org.jasypt.encryption.pbe.PBEByteEncryptor"))) {
                cu.addImport("org.jasypt.encryption.pbe.PBEByteEncryptor");
            }
        }
        
        // Find and fix PasswordEncoder usage
        List<ObjectCreationExpr> passwordEncoderCreations = cu.findAll(ObjectCreationExpr.class, 
            expr -> expr.getType().toString().equals("PasswordEncoder") || 
                   expr.getType().toString().equals("PBEPasswordEncoder"));
        
        for (ObjectCreationExpr creation : passwordEncoderCreations) {
            if (creation.getType().toString().equals("PasswordEncoder")) {
                // Replace new PasswordEncoder() with inline adapter
                // org.jasypt.spring.security.PasswordEncoder was a class that implemented
                // org.acegisecurity.providers.encoding.PasswordEncoder and wrapped a PasswordEncryptor
                String adapterCode = "new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
                                   "    private PasswordEncryptor passwordEncryptor;\n" +
                                   "    \n" +
                                   "    public void setPasswordEncryptor(PasswordEncryptor passwordEncryptor) {\n" +
                                   "        this.passwordEncryptor = passwordEncryptor;\n" +
                                   "    }\n" +
                                   "    \n" +
                                   "    public String encodePassword(String rawPass, Object salt) {\n" +
                                   "        return passwordEncryptor.encryptPassword(rawPass);\n" +
                                   "    }\n" +
                                   "    \n" +
                                   "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
                                   "        return passwordEncryptor.checkPassword(rawPass, encPass);\n" +
                                   "    }\n" +
                                   "}";
                
                // Parse the adapter code and replace the creation expression
                try {
                    // Parse the adapter code which is an anonymous class creation
                    Expression adapterExpr = parser.parseExpression(adapterCode).getResult().orElse(null);
                    if (adapterExpr != null && adapterExpr instanceof ObjectCreationExpr) {
                        creation.replace(adapterExpr);
                        modified = true;
                    }
                } catch (Exception e) {
                    System.err.println("Error replacing PasswordEncoder in " + file + ": " + e.getMessage());
                }
            } else if (creation.getType().toString().equals("PBEPasswordEncoder")) {
                // Replace new PBEPasswordEncoder() with inline adapter
                // org.jasypt.spring.security.PBEPasswordEncoder was similar but for PBE encryption
                String adapterCode = "new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
                                   "    private PBEStringEncryptor pbeStringEncryptor;\n" +
                                   "    \n" +
                                   "    public void setPbeStringEncryptor(PBEStringEncryptor encryptor) {\n" +
                                   "        this.pbeStringEncryptor = encryptor;\n" +
                                   "    }\n" +
                                   "    \n" +
                                   "    public String encodePassword(String rawPass, Object salt) {\n" +
                                   "        return pbeStringEncryptor.encrypt(rawPass);\n" +
                                   "    }\n" +
                                   "    \n" +
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
                    // Parse the adapter code which is an anonymous class creation
                    Expression adapterExpr = parser.parseExpression(adapterCode).getResult().orElse(null);
                    if (adapterExpr != null && adapterExpr instanceof ObjectCreationExpr) {
                        creation.replace(adapterExpr);
                        modified = true;
                    }
                } catch (Exception e) {
                    System.err.println("Error replacing PBEPasswordEncoder in " + file + ": " + e.getMessage());
                }
            }
        }
        
        // Handle type references that might be ambiguous
        // We need to be careful not to change other PasswordEncoder types
        List<ClassOrInterfaceType> passwordEncoderTypes = cu.findAll(ClassOrInterfaceType.class,
            type -> (type.getNameAsString().equals("PasswordEncoder") || 
                    type.getNameAsString().equals("PBEPasswordEncoder")) &&
                    // Check if this type reference is in a context where we removed the import
                    cu.getImports().stream().noneMatch(imp -> 
                        imp.getNameAsString().equals("org.springframework.security.crypto.password.PasswordEncoder") ||
                        imp.getNameAsString().equals("org.acegisecurity.providers.encoding.PasswordEncoder")));
        
        // For these ambiguous types, we need to add import or fully qualify
        for (ClassOrInterfaceType type : passwordEncoderTypes) {
            // If it's PasswordEncoder and we don't have any PasswordEncoder import,
            // it's likely referring to the removed org.jasypt.spring.security.PasswordEncoder
            // We should change it to org.acegisecurity.providers.encoding.PasswordEncoder
            if (type.getNameAsString().equals("PasswordEncoder")) {
                type.setName("org.acegisecurity.providers.encoding.PasswordEncoder");
                modified = true;
            } else if (type.getNameAsString().equals("PBEPasswordEncoder")) {
                // PBEPasswordEncoder only existed in org.jasypt.spring.security
                // So we need to change it to the interface it implemented
                type.setName("org.acegisecurity.providers.encoding.PasswordEncoder");
                modified = true;
            }
        }
        
        if (modified) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(cu.toString().getBytes());
            }
        }
        
        return modified;
    }
}