package github.chains;

import spoon.reflect.factory.Factory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.Launcher;

/**
 * Generic Spoon transformation to fix jasypt breaking changes in Spring Security integration.
 * This fixes the removal of org.jasypt.spring.security.PasswordEncoder and PBEPasswordEncoder classes.
 */
public class Main {
    public static void main(String[] args) {
        // Default to workspace directory as source
        String sourceDirectory = "/workspace/geostore";
        
        // If argument provided, use that as source directory
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed-output");
        launcher.getEnvironment().setComplianceLevel(8);
        
        CtModel model = launcher.buildModel();
        
        // Process the model with our transformation
        JasyptSpringSecurityFixProcessor processor = new JasyptSpringSecurityFixProcessor(launcher.getFactory());
        model.processWith(processor);
        
        System.out.println("Transformation processing complete");
    }
}

class JasyptSpringSecurityFixProcessor extends AbstractProcessor<CtElement> {
    private Factory factory;
    
    public JasyptSpringSecurityFixProcessor(Factory factory) {
        this.factory = factory;
    }
    
    @Override
    public void process(CtElement element) {
        // Remove imports of deprecated jasypt spring security classes
        if (element instanceof CtImport) {
            processImport((CtImport) element);
        }
        // Handle constructor calls to deprecated classes
        else if (element instanceof CtNewClass) {
            processNewClass((CtNewClass) element);
        }
        // Handle method calls that reference deprecated classes
        else if (element instanceof CtConstructorCall) {
            processConstructorCall((CtConstructorCall) element);
        }
    }
    
    private void processImport(CtImport ctImport) {
        CtReference importedRef = ctImport.getReference();
        if (importedRef instanceof CtTypeReference) {
            CtTypeReference<?> typeRef = (CtTypeReference<?>) importedRef;
            String qualifiedName = typeRef.getQualifiedName();
            
            // Remove imports for deprecated jasypt spring security classes
            if (qualifiedName.equals("org.jasypt.spring.security.PasswordEncoder") ||
                qualifiedName.equals("org.jasypt.spring.security.PBEPasswordEncoder")) {
                ctImport.delete();
            }
        }
    }
    
    private void processNewClass(CtNewClass newClass) {
        CtTypeReference<?> typeRef = newClass.getType();
        if (typeRef != null) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Handle direct instantiation of deprecated PasswordEncoder
            if (qualifiedName.equals("org.jasypt.spring.security.PasswordEncoder")) {
                // Transform to instantiate StrongPasswordEncryptor directly
                // This creates a snippet that represents a proper replacement
                CtCodeSnippetExpression replacement = factory.createCodeSnippetExpression(
                    "new org.jasypt.util.password.StrongPasswordEncryptor()");
                newClass.replace(replacement);
            }
            // Handle direct instantiation of deprecated PBEPasswordEncoder
            else if (qualifiedName.equals("org.jasypt.spring.security.PBEPasswordEncoder")) {
                // Transform to instantiate StandardPBEStringEncryptor directly
                CtCodeSnippetExpression replacement = factory.createCodeSnippetExpression(
                    "new org.jasypt.encryption.pbe.StandardPBEStringEncryptor()");
                newClass.replace(replacement);
            }
        }
    }
    
    private void processConstructorCall(CtConstructorCall constructorCall) {
        CtTypeReference<?> typeRef = constructorCall.getType();
        if (typeRef != null) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Handle constructor calls to deprecated PasswordEncoder
            if (qualifiedName.equals("org.jasypt.spring.security.PasswordEncoder")) {
                // Transform to StrongPasswordEncryptor
                CtCodeSnippetExpression replacement = factory.createCodeSnippetExpression(
                    "new org.jasypt.util.password.StrongPasswordEncryptor()");
                constructorCall.replace(replacement);
            }
            // Handle constructor calls to deprecated PBEPasswordEncoder
            else if (qualifiedName.equals("org.jasypt.spring.security.PBEPasswordEncoder")) {
                // Transform to StandardPBEStringEncryptor
                CtCodeSnippetExpression replacement = factory.createCodeSnippetExpression(
                    "new org.jasypt.encryption.pbe.StandardPBEStringEncryptor()");
                constructorCall.replace(replacement);
            }
        }
    }
}