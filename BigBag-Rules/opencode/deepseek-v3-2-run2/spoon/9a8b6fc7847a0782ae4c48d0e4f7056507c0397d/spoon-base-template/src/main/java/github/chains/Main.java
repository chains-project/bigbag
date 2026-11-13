package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic transformation rule for fixing breaking changes in jasypt 1.9.2.
 * Specifically addresses the removal of org.jasypt.spring.security.PasswordEncoder
 * and org.jasypt.spring.security.PBEPasswordEncoder classes.
 * 
 * This transformation:
 * 1. Finds all imports of missing jasypt-spring classes
 * 2. Creates replacement adapter implementations with same FQN
 * 3. Handles naming conflicts by using fully qualified names
 * 
 * The transformation is generic and can be applied to any Maven project
 * affected by this breaking change.
 */
public class Main {
    
    private static final String JASYPT_SPRING_SECURITY_PACKAGE = "org.jasypt.spring.security";
    private static final String PASSWORD_ENCODER_CLASS = "PasswordEncoder";
    private static final String PBEPASSWORD_ENCODER_CLASS = "PBEPasswordEncoder";
    private static final String FULL_PASSWORD_ENCODER = JASYPT_SPRING_SECURITY_PACKAGE + "." + PASSWORD_ENCODER_CLASS;
    private static final String FULL_PBEPASSWORD_ENCODER = JASYPT_SPRING_SECURITY_PACKAGE + "." + PBEPASSWORD_ENCODER_CLASS;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.err.println("Note: This transformation fixes the breaking change in jasypt 1.9.2");
            System.err.println("      where org.jasypt.spring.security.* classes were removed.");
            return;
        }
        
        String sourceDir = args[0];
        
        try {
            System.out.println("Applying jasypt 1.9.2 breaking change fix to: " + sourceDir);
            applyTransformation(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error applying transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void applyTransformation(String sourceDir) throws Exception {
        // Simple file scanning approach - look for import statements in Java files
        boolean foundPasswordEncoderImport = false;
        boolean foundPBEPasswordEncoderImport = false;
        
        File sourceDirFile = new File(sourceDir);
        if (!sourceDirFile.exists()) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Walk through Java files and check for imports
        List<File> javaFiles = findJavaFiles(sourceDirFile);
        
        for (File javaFile : javaFiles) {
            String content = new String(Files.readAllBytes(javaFile.toPath()));
            if (content.contains("import " + FULL_PASSWORD_ENCODER + ";")) {
                foundPasswordEncoderImport = true;
                System.out.println("Found import of missing class " + FULL_PASSWORD_ENCODER + " in: " + javaFile.getPath());
            }
            if (content.contains("import " + FULL_PBEPASSWORD_ENCODER + ";")) {
                foundPBEPasswordEncoderImport = true;
                System.out.println("Found import of missing class " + FULL_PBEPASSWORD_ENCODER + " in: " + javaFile.getPath());
            }
        }
        
        // Create adapter implementation files if needed
        if (foundPasswordEncoderImport || foundPBEPasswordEncoderImport) {
            createAdapterFiles(sourceDir);
        } else {
            System.out.println("No missing jasypt-spring imports found. Nothing to fix.");
        }
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new java.util.ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }
    
    private static void createAdapterFiles(String sourceDir) throws Exception {
        // Calculate the base directory for the package
        String baseDir = sourceDir;
        if (!baseDir.endsWith("/")) {
            baseDir += "/";
        }
        
        String packageDir = baseDir + "org/jasypt/spring/security";
        Files.createDirectories(Paths.get(packageDir));
        
        // Create PasswordEncoder adapter if needed
        File passwordEncoderFile = new File(packageDir + "/PasswordEncoder.java");
        if (!passwordEncoderFile.exists()) {
            try (PrintWriter out = new PrintWriter(passwordEncoderFile)) {
                out.println("package org.jasypt.spring.security;");
                out.println();
                out.println("import org.jasypt.encryption.StringEncryptor;");
                out.println("import org.jasypt.encryption.pbe.PBEStringEncryptor;");
                out.println("import org.jasypt.util.password.PasswordEncryptor;");
                out.println();
                out.println("/**");
                out.println(" * Replacement for missing PasswordEncoder class in jasypt 1.9.2.");
                out.println(" * Implements Acegi Security PasswordEncoder interface using jasypt's PasswordEncryptor.");
                out.println(" */");
                out.println("public class PasswordEncoder implements org.acegisecurity.providers.encoding.PasswordEncoder {");
                out.println("    ");
                out.println("    private PasswordEncryptor passwordEncryptor;");
                out.println("    private StringEncryptor stringEncryptor;");
                out.println("    private PBEStringEncryptor pbeStringEncryptor;");
                out.println("    ");
                out.println("    public PasswordEncoder() {");
                out.println("        // Default constructor");
                out.println("    }");
                out.println("    ");
                out.println("    public void setPasswordEncryptor(PasswordEncryptor passwordEncryptor) {");
                out.println("        this.passwordEncryptor = passwordEncryptor;");
                out.println("    }");
                out.println("    ");
                out.println("    public void setStringEncryptor(StringEncryptor stringEncryptor) {");
                out.println("        this.stringEncryptor = stringEncryptor;");
                out.println("    }");
                out.println("    ");
                out.println("    public void setPbeStringEncryptor(PBEStringEncryptor pbeStringEncryptor) {");
                out.println("        this.pbeStringEncryptor = pbeStringEncryptor;");
                out.println("    }");
                out.println("    ");
                out.println("    @Override");
                out.println("    public String encodePassword(String rawPass, Object salt) {");
                out.println("        if (passwordEncryptor != null) {");
                out.println("            return passwordEncryptor.encryptPassword(rawPass);");
                out.println("        } else if (stringEncryptor != null) {");
                out.println("            return stringEncryptor.encrypt(rawPass);");
                out.println("        } else if (pbeStringEncryptor != null) {");
                out.println("            return pbeStringEncryptor.encrypt(rawPass);");
                out.println("        }");
                out.println("        throw new IllegalStateException(\"No encryptor configured\");");
                out.println("    }");
                out.println("    ");
                out.println("    @Override");
                out.println("    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {");
                out.println("        if (passwordEncryptor != null) {");
                out.println("            return passwordEncryptor.checkPassword(rawPass, encPass);");
                out.println("        } else if (stringEncryptor != null) {");
                out.println("            return stringEncryptor.decrypt(encPass).equals(rawPass);");
                out.println("        } else if (pbeStringEncryptor != null) {");
                out.println("            return pbeStringEncryptor.decrypt(encPass).equals(rawPass);");
                out.println("        }");
                out.println("        throw new IllegalStateException(\"No encryptor configured\");");
                out.println("    }");
                out.println("}");
                System.out.println("Created PasswordEncoder adapter at: " + passwordEncoderFile.getAbsolutePath());
            }
        }
        
        // Create PBEPasswordEncoder adapter if needed
        File pbePasswordEncoderFile = new File(packageDir + "/PBEPasswordEncoder.java");
        if (!pbePasswordEncoderFile.exists()) {
            try (PrintWriter out = new PrintWriter(pbePasswordEncoderFile)) {
                out.println("package org.jasypt.spring.security;");
                out.println();
                out.println("import org.jasypt.encryption.pbe.PBEStringEncryptor;");
                out.println();
                out.println("/**");
                out.println(" * Replacement for missing PBEPasswordEncoder class in jasypt 1.9.2.");
                out.println(" * Implements Acegi Security PasswordEncoder interface using jasypt's PBEStringEncryptor.");
                out.println(" */");
                out.println("public class PBEPasswordEncoder implements org.acegisecurity.providers.encoding.PasswordEncoder {");
                out.println("    ");
                out.println("    private PBEStringEncryptor pbeStringEncryptor;");
                out.println("    ");
                out.println("    public PBEPasswordEncoder() {");
                out.println("        // Default constructor");
                out.println("    }");
                out.println("    ");
                out.println("    public void setPbeStringEncryptor(PBEStringEncryptor pbeStringEncryptor) {");
                out.println("        this.pbeStringEncryptor = pbeStringEncryptor;");
                out.println("    }");
                out.println("    ");
                out.println("    @Override");
                out.println("    public String encodePassword(String rawPass, Object salt) {");
                out.println("        if (pbeStringEncryptor != null) {");
                out.println("            return pbeStringEncryptor.encrypt(rawPass);");
                out.println("        }");
                out.println("        throw new IllegalStateException(\"No PBE string encryptor configured\");");
                out.println("    }");
                out.println("    ");
                out.println("    @Override");
                out.println("    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {");
                out.println("        if (pbeStringEncryptor != null) {");
                out.println("            return pbeStringEncryptor.decrypt(encPass).equals(rawPass);");
                out.println("        }");
                out.println("        throw new IllegalStateException(\"No PBE string encryptor configured\");");
                out.println("    }");
                out.println("}");
                System.out.println("Created PBEPasswordEncoder adapter at: " + pbePasswordEncoderFile.getAbsolutePath());
            }
        }
        
        System.out.println("Adapter implementation files created in: " + packageDir);
        System.out.println("Note: The adapters implement the same interfaces with the same FQN,");
        System.out.println("      so existing imports and constructor calls will work without changes.");
    }
}