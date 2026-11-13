package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.Query;
import java.util.List;
import java.util.ArrayList;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation rule for fixing Jasypt 1.9.2 breaking changes.
 * 
 * Breaking Change Pattern:
 * - Old API: org.jasypt.spring.security.PasswordEncoder and org.jasypt.spring.security.PBEPasswordEncoder
 * - New API: These classes were removed in jasypt 1.9.2
 * - Transformation: Replace with anonymous inner class implementing org.acegisecurity.providers.encoding.PasswordEncoder
 * 
 * This transformation is generic and can be applied to any Maven project affected by this breaking change.
 * Simply run: java github.chains.Main /path/to/source/code
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.err.println("\nThis transformation fixes the jasypt 1.9.2 breaking change:");
            System.err.println("  - Removed: org.jasypt.spring.security.PasswordEncoder");
            System.err.println("  - Removed: org.jasypt.spring.security.PBEPasswordEncoder");
            System.err.println("  - Replaces with anonymous adapters implementing org.acegisecurity.providers.encoding.PasswordEncoder");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir + "/src/main/java"); // Only process main source files
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            // Create model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Create a processor for the transformation
            JasyptSpringSecurityProcessor processor = new JasyptSpringSecurityProcessor();
            processor.setFactory(factory);
            
            // Process the model
            model.processWith(processor);
            
            // Write transformed code to a temp directory
            String tempDir = sourceDir + "-transformed";
            launcher.setSourceOutputDirectory(tempDir);
            launcher.prettyprint();
            
            System.out.println("\n=== TRANSFORMATION COMPLETED ===");
            System.out.println("Summary:");
            System.out.println("  Files processed: " + processor.filesProcessed);
            System.out.println("  Removed imports: " + processor.removedImports);
            System.out.println("  Replaced PasswordEncoder instantiations: " + processor.replacedPasswordEncoders);
            System.out.println("  Replaced PBEPasswordEncoder instantiations: " + processor.replacedPBEPasswordEncoders);
            System.out.println("\nThe transformed code has been written to: " + tempDir);
            System.out.println("\n=== BREAKING CHANGE FIXED ===");
            System.out.println("Pattern: Classes removed in jasypt 1.9.2:");
            System.out.println("  - org.jasypt.spring.security.PasswordEncoder");
            System.out.println("  - org.jasypt.spring.security.PBEPasswordEncoder");
            System.out.println("\nReplacement: Anonymous inner class adapters that:");
            System.out.println("  1. Implement org.acegisecurity.providers.encoding.PasswordEncoder");
            System.out.println("  2. Wrap jasypt encryptors (StrongPasswordEncryptor, PBEStringEncryptor)");
            System.out.println("  3. Maintain same API surface (setPasswordEncryptor, setPbeStringEncryptor)");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            System.err.println("\nTo apply this fix manually:");
            System.err.println("1. Remove imports of org.jasypt.spring.security.*");
            System.err.println("2. Replace 'new PasswordEncoder()' with anonymous adapter");
            System.err.println("3. Replace 'new PBEPasswordEncoder()' with anonymous adapter");
            System.exit(1);
        }
    }
    
    public static class JasyptSpringSecurityProcessor extends AbstractProcessor<CtType<?>> {
        public int filesProcessed = 0;
        public int removedImports = 0;
        public int replacedPasswordEncoders = 0;
        public int replacedPBEPasswordEncoders = 0;
        
        @Override
        public void process(CtType<?> type) {
            filesProcessed++;
            
            // Process imports in this type
            processImports(type);
            
            // Process all constructor calls and new class expressions
            processConstructorCalls(type);
        }
        
        private void processImports(CtType<?> type) {
            // Get compilation unit to access imports
            if (type.getPosition().getCompilationUnit() != null) {
                List<CtImport> imports = new ArrayList<>(type.getPosition().getCompilationUnit().getImports());
                
                for (CtImport imp : imports) {
                    String importStr = imp.toString();
                    
                    // Remove imports of removed jasypt.spring.security classes
                    if (importStr.contains("org.jasypt.spring.security.PasswordEncoder") ||
                        importStr.contains("org.jasypt.spring.security.PBEPasswordEncoder")) {
                        
                        imp.delete();
                        removedImports++;
                    }
                }
            }
        }
        
        private void processConstructorCalls(CtType<?> type) {
            // Find all constructor calls (CtConstructorCall)
            List<CtConstructorCall<?>> constructorCalls = Query.getElements(type, 
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class));
            
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef != null) {
                    String typeName = typeRef.getQualifiedName();
                    
                    if ("org.jasypt.spring.security.PasswordEncoder".equals(typeName)) {
                        replacePasswordEncoderCall(constructorCall);
                        replacedPasswordEncoders++;
                    } else if ("org.jasypt.spring.security.PBEPasswordEncoder".equals(typeName)) {
                        replacePBEPasswordEncoderCall(constructorCall);
                        replacedPBEPasswordEncoders++;
                    }
                }
            }
            
            // Also check CtNewClass expressions
            List<CtNewClass<?>> newClasses = Query.getElements(type,
                new TypeFilter<CtNewClass<?>>(CtNewClass.class));
            
            for (CtNewClass<?> newClass : newClasses) {
                CtTypeReference<?> typeRef = newClass.getType();
                if (typeRef != null) {
                    String typeName = typeRef.getQualifiedName();
                    
                    if ("org.jasypt.spring.security.PasswordEncoder".equals(typeName)) {
                        replacePasswordEncoderCall(newClass);
                        replacedPasswordEncoders++;
                    } else if ("org.jasypt.spring.security.PBEPasswordEncoder".equals(typeName)) {
                        replacePBEPasswordEncoderCall(newClass);
                        replacedPBEPasswordEncoders++;
                    }
                }
            }
        }
        
        private void replacePasswordEncoderCall(CtElement element) {
            // Create adapter for PasswordEncoder
            String adapterCode = 
                "new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
                "    private org.jasypt.util.password.PasswordEncryptor passwordEncryptor;\n" +
                "    \n" +
                "    public void setPasswordEncryptor(org.jasypt.util.password.PasswordEncryptor passwordEncryptor) {\n" +
                "        this.passwordEncryptor = passwordEncryptor;\n" +
                "    }\n" +
                "    \n" +
                "    @Override\n" +
                "    public String encodePassword(String rawPass, Object salt) {\n" +
                "        if (passwordEncryptor == null) {\n" +
                "            throw new IllegalStateException(\"PasswordEncryptor not set\");\n" +
                "        }\n" +
                "        return passwordEncryptor.encryptPassword(rawPass);\n" +
                "    }\n" +
                "    \n" +
                "    @Override\n" +
                "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
                "        if (passwordEncryptor == null) {\n" +
                "            throw new IllegalStateException(\"PasswordEncryptor not set\");\n" +
                "        }\n" +
                "        return passwordEncryptor.checkPassword(rawPass, encPass);\n" +
                "    }\n" +
                "}";
            
            CtCodeSnippetExpression<?> adapterExpr = getFactory().createCodeSnippetExpression(adapterCode);
            
            if (element instanceof CtConstructorCall) {
                ((CtConstructorCall<?>) element).replace(adapterExpr);
            } else if (element instanceof CtNewClass) {
                ((CtNewClass<?>) element).replace(adapterExpr);
            }
        }
        
        private void replacePBEPasswordEncoderCall(CtElement element) {
            // Create adapter for PBEPasswordEncoder
            String adapterCode = 
                "new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
                "    private org.jasypt.encryption.pbe.PBEStringEncryptor stringEncryptor;\n" +
                "    \n" +
                "    public void setPbeStringEncryptor(org.jasypt.encryption.pbe.PBEStringEncryptor stringEncryptor) {\n" +
                "        this.stringEncryptor = stringEncryptor;\n" +
                "    }\n" +
                "    \n" +
                "    @Override\n" +
                "    public String encodePassword(String rawPass, Object salt) {\n" +
                "        if (stringEncryptor == null) {\n" +
                "            throw new IllegalStateException(\"PBEStringEncryptor not set\");\n" +
                "        }\n" +
                "        return stringEncryptor.encrypt(rawPass);\n" +
                "    }\n" +
                "    \n" +
                "    @Override\n" +
                "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
                "        if (stringEncryptor == null) {\n" +
                "            throw new IllegalStateException(\"PBEStringEncryptor not set\");\n" +
                "        }\n" +
                "        return rawPass.equals(stringEncryptor.decrypt(encPass));\n" +
                "    }\n" +
                "}";
            
            CtCodeSnippetExpression<?> adapterExpr = getFactory().createCodeSnippetExpression(adapterCode);
            
            if (element instanceof CtConstructorCall) {
                ((CtConstructorCall<?>) element).replace(adapterExpr);
            } else if (element instanceof CtNewClass) {
                ((CtNewClass<?>) element).replace(adapterExpr);
            }
        }
    }
}