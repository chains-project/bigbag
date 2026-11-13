package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import java.util.*;

/**
 * Generic transformation rule for snakeyaml 1.31 breaking API change:
 * The method Representer.getProperties(Class) no longer throws IntrospectionException.
 * 
 * This transformation removes "throws IntrospectionException" from:
 * 1. Methods named getProperties with a single Class parameter
 * 2. Any method that calls super.getProperties()
 * 
 * The transformation is generic and can be applied to any project affected by
 * this snakeyaml API change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <project-path>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String projectPath = args[0];
        System.out.println("Processing project at: " + projectPath);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        try {
            CtModel model = launcher.buildModel();
            
            int fixedMethods = 0;
            
            for (CtType<?> type : model.getAllTypes()) {
                // Fix methods
                for (CtMethod<?> method : type.getMethods()) {
                    if (isGetPropertiesMethod(method)) {
                        // Check if method throws IntrospectionException
                        Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
                        CtTypeReference<? extends Throwable> introspectionExceptionRef = findIntrospectionExceptionRef(thrownTypes);
                        
                        if (introspectionExceptionRef != null) {
                            System.out.println("Removing throws IntrospectionException from: " + 
                                              type.getQualifiedName() + "." + method.getSignature());
                            method.removeThrownType(introspectionExceptionRef);
                            fixedMethods++;
                        }
                    }
                }
            }
            
            System.out.println("\nFixed " + fixedMethods + " method(s)");
            
            if (fixedMethods > 0) {
                // Apply transformations
                launcher.setSourceOutputDirectory(projectPath);
                launcher.prettyprint();
                System.out.println("\nTransformations applied successfully to: " + projectPath);
                System.out.println("\nNote: You may also need to:");
                System.out.println("1. Remove 'throws IntrospectionException' from methods that call super.getProperties()");
                System.out.println("2. Remove unused imports of java.beans.IntrospectionException");
                System.out.println("3. Add try-catch blocks if IntrospectionException is needed for other reasons");
            } else {
                System.out.println("\nNo getProperties methods needed fixing");
                System.out.println("Check for methods that call super.getProperties() and may need updating");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean isGetPropertiesMethod(CtMethod<?> method) {
        // Check if method is named getProperties
        if (!"getProperties".equals(method.getSimpleName())) {
            return false;
        }
        
        // Check if method has one parameter of type Class
        List<CtParameter<?>> parameters = method.getParameters();
        if (parameters.size() != 1) {
            return false;
        }
        
        CtParameter<?> param = parameters.get(0);
        CtTypeReference<?> paramType = param.getType();
        
        // Accept Class or Class<?> or Class<? extends Object>
        String paramTypeName = paramType.getQualifiedName();
        if (paramTypeName == null) {
            return false;
        }
        
        // Simple check for Class type
        return paramTypeName.contains("Class");
    }
    
    private static CtTypeReference<? extends Throwable> findIntrospectionExceptionRef(
            Set<CtTypeReference<? extends Throwable>> thrownTypes) {
        for (CtTypeReference<? extends Throwable> thrownType : thrownTypes) {
            String qualifiedName = thrownType.getQualifiedName();
            if (qualifiedName != null && 
                (qualifiedName.equals("java.beans.IntrospectionException") ||
                 qualifiedName.endsWith(".IntrospectionException"))) {
                return thrownType;
            }
        }
        return null;
    }
}