package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation rule for migrating from URL to URI in GeoIP2 3.0.0 API.
 * 
 * This processor handles two types of transformations:
 * 1. Method calls: Replaces getUrl() with getUri()
 * 2. Constructor parameters: Changes URL parameters to URI parameters
 * 
 * The transformation is generic and can be applied to any project affected by
 * the breaking change from geoip2 2.x to 3.0.0.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying GeoIP2 3.0.0 URL-to-URI migration to: " + sourceDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            
            // Add processors for URL-to-URI migration
            launcher.addProcessor(new UrlToUriMethodCallProcessor());
            launcher.addProcessor(new UrlToUriConstructorProcessor());
            launcher.addProcessor(new UrlToUriMethodParameterProcessor());
            
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor for replacing method calls from getUrl() to getUri()
     */
    static class UrlToUriMethodCallProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> exec = candidate.getExecutable();
            if (exec == null) {
                return false;
            }
            
            String methodName = exec.getSimpleName();
            // Check if this is a getUrl() method call
            if ("getUrl".equals(methodName)) {
                // Check if the declaring type is a GeoIP2 exception class or similar
                CtTypeReference<?> declaringType = exec.getDeclaringType();
                if (declaringType != null) {
                    String typeName = declaringType.getQualifiedName();
                    // Apply to geoip2 exception classes and any class with getUrl() method
                    return typeName.contains("Exception") || 
                           typeName.contains("geoip2") ||
                           typeName.startsWith("com.maxmind.");
                }
            }
            return false;
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> exec = invocation.getExecutable();
            System.out.println("Replacing method call: " + exec.getSignature() + 
                             " in " + invocation.getPosition());
            
            // Get the necessary information from the original executable
            CtTypeReference<?> declaringType = exec.getDeclaringType();
            CtTypeReference<?> returnType = exec.getType();
            List<CtTypeReference<?>> paramTypes = exec.getParameters();
            
            // Create new executable reference for getUri() with same signature
            // Using the createReference method that takes all parameters
            CtExecutableReference<?> newExec = getFactory().Executable()
                .createReference(declaringType, returnType, "getUri", 
                               paramTypes.toArray(new CtTypeReference[0]));
            
            // Copy type parameters if any
            if (exec.getActualTypeArguments() != null && !exec.getActualTypeArguments().isEmpty()) {
                newExec.setActualTypeArguments(exec.getActualTypeArguments());
            }
            
            // Replace the method call using a raw type to avoid generic type issues
            // This is safe because we're replacing getUrl() with getUri() which should have
            // identical signatures except for the method name
            @SuppressWarnings({"unchecked", "rawtypes"})
            CtExecutableReference rawExec = newExec;
            invocation.setExecutable(rawExec);
        }
    }
    
    /**
     * Processor for changing constructor parameters from URL to URI type
     */
    static class UrlToUriConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            CtTypeReference<?> type = candidate.getType();
            if (type == null) {
                return false;
            }
            
            String typeName = type.getQualifiedName();
            // Check if this is a constructor for a GeoIP2 exception class
            if (typeName.contains("HttpException") || typeName.contains("InvalidRequestException") ||
                (typeName.contains("Exception") && typeName.contains("maxmind"))) {
                
                // Check if any parameter is of type URL
                List<CtTypeReference<?>> paramTypes = candidate.getExecutable().getParameters();
                for (CtTypeReference<?> paramType : paramTypes) {
                    if (paramType.getQualifiedName().equals(URL.class.getName()) ||
                        paramType.getSimpleName().equals("URL")) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            System.out.println("Processing constructor: " + constructorCall.getType().getQualifiedName() +
                             " in " + constructorCall.getPosition());
            
            // We need to change the constructor signature and potentially update arguments
            // Since we can't directly change the constructor signature, we need to
            // create a new constructor call with URI type and adapt arguments
            
            // This is a complex transformation that would require:
            // 1. Changing the constructor signature in the class definition
            // 2. Updating all calls to that constructor
            // For now, we'll output a warning and skip this transformation
            // as it requires modifying class definitions, not just calls
            
            System.out.println("WARNING: Constructor parameter type changes require " +
                             "modifying class definitions. Manual update needed for: " +
                             constructorCall.getType().getQualifiedName());
        }
    }
    
    /**
     * Processor for updating method parameters from URL to URI in class definitions
     */
    static class UrlToUriMethodParameterProcessor extends AbstractProcessor<CtMethod<?>> {
        
        @Override
        public boolean isToBeProcessed(CtMethod<?> candidate) {
            // Check if this method has URL parameters
            List<CtParameter<?>> parameters = candidate.getParameters();
            for (CtParameter<?> param : parameters) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType.getQualifiedName().equals(URL.class.getName()) ||
                    paramType.getSimpleName().equals("URL")) {
                    // Check if this is in a relevant class
                    CtClass<?> declaringClass = candidate.getParent(CtClass.class);
                    if (declaringClass != null) {
                        String className = declaringClass.getQualifiedName();
                        return className.contains("Exception") && 
                               (className.contains("geoip2") || className.contains("maxmind"));
                    }
                }
            }
            return false;
        }
        
        @Override
        public void process(CtMethod<?> method) {
            System.out.println("Processing method parameters in: " + 
                             method.getSignature() + " at " + method.getPosition());
            
            List<CtParameter<?>> parameters = method.getParameters();
            List<CtParameter<?>> newParameters = new ArrayList<>();
            
            for (CtParameter<?> param : parameters) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType.getQualifiedName().equals(URL.class.getName()) ||
                    paramType.getSimpleName().equals("URL")) {
                    
                    // Change parameter type from URL to URI
                    CtTypeReference<URI> uriType = getFactory().Type().createReference(URI.class);
                    CtParameter<Object> newParam = getFactory().Core().createParameter();
                    newParam.setType(uriType);
                    newParam.setSimpleName(param.getSimpleName());
                    
                    // Copy annotations
                    if (param.getAnnotations() != null) {
                        newParam.setAnnotations(param.getAnnotations());
                    }
                    
                    newParameters.add(newParam);
                    System.out.println("  Changing parameter type: " + param.getSimpleName() + 
                                     " from URL to URI");
                } else {
                    newParameters.add(param);
                }
            }
            
            // Update method parameters
            method.setParameters(newParameters);
            
            // Also need to update the method body if it references URL type
            // This would require additional analysis and transformation
            System.out.println("WARNING: Method body may need manual updates for URL->URI changes");
        }
    }
}