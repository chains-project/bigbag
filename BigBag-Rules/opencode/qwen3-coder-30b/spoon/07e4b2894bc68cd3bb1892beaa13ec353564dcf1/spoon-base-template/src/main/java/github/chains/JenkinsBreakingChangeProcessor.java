package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.CtScanner;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 * Spoon processor to detect and fix breaking changes in Jenkins acceptance test harness
 */
public class JenkinsBreakingChangeProcessor extends AbstractProcessor<CtElement> {
    
    private static final Set<String> OLD_TEST_BASE_CLASSES = Set.of(
        "org.jenkinsci.test.acceptance.junit.AbstractJUnitTest",
        "org.jenkinsci.test.acceptance.AbstractPipelineTest",
        "org.jenkinsci.test.acceptance.junit.WithPlugins"
    );
    
    private static final Set<String> NEW_TEST_BASE_CLASSES = Set.of(
        "org.jenkinsci.test.acceptance.junit.AbstractJUnitTest",
        "org.jenkinsci.test.acceptance.AbstractPipelineTest",
        "org.jenkinsci.test.acceptance.junit.WithPlugins"
    );
    
    private static final Map<String, String> METHOD_SIGNATURE_CHANGES = new HashMap<>();
    static {
        // Add common method signature changes
        METHOD_SIGNATURE_CHANGES.put("org.jenkinsci.test.acceptance.junit.AbstractJUnitTest.findAvailablePort", 
            "org.jenkinsci.test.acceptance.junit.AbstractJUnitTest.findAvailablePort()");
        METHOD_SIGNATURE_CHANGES.put("org.jenkinsci.test.acceptance.junit.AbstractJUnitTest.injectSpec", 
            "org.jenkinsci.test.acceptance.junit.AbstractJUnitTest.injectSpec()");
    }
    
    @Override
    public void process(CtElement element) {
        // Process class declarations
        if (element instanceof CtClass) {
            processClass((CtClass) element);
        }
        
        // Process method declarations
        if (element instanceof CtMethod) {
            processMethod((CtMethod) element);
        }
        
        // Process method calls
        if (element instanceof CtInvocation) {
            processInvocation((CtInvocation) element);
        }
    }
    
    private void processClass(CtClass<?> ctClass) {
        // Check for inheritance from old test base classes
        CtTypeReference<?> superClass = ctClass.getSuperclass();
        if (superClass != null && OLD_TEST_BASE_CLASSES.contains(superClass.getQualifiedName())) {
            // Change to new base class
            String newBaseClass = getNewBaseClass(superClass.getQualifiedName());
            if (newBaseClass != null) {
                ctClass.setSuperclass(getFactory().Type().createReference(newBaseClass));
            }
        }
        
        // Check for annotations that might need updating
        List<CtAnnotation<?>> annotations = ctClass.getAnnotations();
        for (CtAnnotation<?> annotation : annotations) {
            // Update any deprecated or changed annotations
            updateAnnotation(annotation);
        }
    }
    
    private void processMethod(CtMethod<?> ctMethod) {
        // Check for deprecated or changed method signatures
        String methodSignature = ctMethod.getDeclaringType().getQualifiedName() + "." + ctMethod.getSimpleName() + 
            "(" + getParameterTypes(ctMethod) + ")";
            
        if (METHOD_SIGNATURE_CHANGES.containsKey(methodSignature)) {
            // Handle method signature changes
            handleMethodSignatureChange(ctMethod);
        }
    }
    
    private void processInvocation(CtInvocation<?> invocation) {
        // Check for method calls that may have changed signatures
        CtExecutableReference<?> executableRef = invocation.getExecutable();
        if (executableRef != null) {
            String signature = executableRef.getDeclaringType().getQualifiedName() + "." + executableRef.getSimpleName();
            
            // Look for common breaking changes
            if (signature.contains("findAvailablePort") || 
                signature.contains("injectSpec")) {
                // Handle any signature changes
                handleMethodCallChange(invocation, signature);
            }
        }
    }
    
    private void updateAnnotation(CtAnnotation<?> annotation) {
        // This would handle annotation changes if needed
        // For now, we'll leave it as a placeholder
    }
    
    private void handleMethodSignatureChange(CtMethod<?> method) {
        // This would handle method signature changes
        // For now, we'll leave it as a placeholder
    }
    
    private void handleMethodCallChange(CtInvocation<?> invocation, String signature) {
        // This would handle specific method call changes
        // For now, we'll leave it as a placeholder
    }
    
    private String getNewBaseClass(String oldBaseClass) {
        // Return the new base class name if there's a mapping
        if (oldBaseClass.equals("org.jenkinsci.test.acceptance.junit.AbstractJUnitTest")) {
            return "org.jenkinsci.test.acceptance.junit.AbstractJUnitTest";
        }
        // Add more mappings as needed
        return oldBaseClass;
    }
    
    private String getParameterTypes(CtMethod<?> method) {
        List<CtTypeReference<?>> params = method.getParameters();
        if (params.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(params.get(i).getQualifiedName());
        }
        return sb.toString();
    }
}