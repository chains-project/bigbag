package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying SnakeYAML 1.32 API fix to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all classes in the model
        List<CtClass<?>> classes = model.getElements(new TypeFilter<>(CtClass.class));
        
        int fixCount = 0;
        
        for (CtClass<?> clazz : classes) {
            // Get all methods in the class
            for (CtMethod<?> method : clazz.getMethods()) {
                // Check if method is named getProperties with Class parameter
                if (isGetPropertiesMethod(method)) {
                    // Remove IntrospectionException from throws clause if present
                    if (removeIntrospectionException(method)) {
                        System.out.println("Fixed method: " + clazz.getQualifiedName() + "." + method.getSignature());
                        fixCount++;
                    }
                }
                
                // Also check for any method that throws IntrospectionException
                // that might be calling super.getProperties
                if (method.getThrownTypes().stream()
                    .anyMatch(t -> t.getQualifiedName().equals("java.beans.IntrospectionException"))) {
                    // Check if this method calls super.getProperties
                    // This is a more complex analysis that would require scanning method body
                    System.out.println("Method throws IntrospectionException: " + 
                                      clazz.getQualifiedName() + "." + method.getSignature());
                }
            }
        }
        
        System.out.println("Applied " + fixCount + " fixes.");
        
        // Output transformed code
        launcher.setSourceOutputDirectory("./transformed-output");
        launcher.prettyprint();
    }
    
    /**
     * Check if a method is getProperties with Class parameter
     * Pattern: protected Set<Property> getProperties(Class<?> type)
     */
    private static boolean isGetPropertiesMethod(CtMethod<?> method) {
        // Check method name
        if (!"getProperties".equals(method.getSimpleName())) {
            return false;
        }
        
        // Check parameters (should have exactly one Class parameter)
        if (method.getParameters().size() != 1) {
            return false;
        }
        
        CtTypeReference<?> paramType = method.getParameters().get(0).getType();
        if (paramType == null || !paramType.getQualifiedName().equals("java.lang.Class")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Remove IntrospectionException from method's throws clause
     */
    private static boolean removeIntrospectionException(CtMethod<?> method) {
        boolean modified = false;
        
        // Check if method throws IntrospectionException
        List<CtTypeReference<?>> thrownTypes = method.getThrownTypes();
        for (int i = 0; i < thrownTypes.size(); i++) {
            CtTypeReference<?> exceptionType = thrownTypes.get(i);
            if (exceptionType.getQualifiedName().equals("java.beans.IntrospectionException")) {
                // Remove this exception from throws clause
                thrownTypes.remove(i);
                modified = true;
                break;
            }
        }
        
        return modified;
    }
}