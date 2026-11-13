package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.QueueProcessingManager;
import spoon.reflect.factory.Factory;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtExpression;

import java.io.File;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Add required dependencies for compilation
        launcher.getEnvironment().setSourceClasspath(new String[] {
            "/root/.m2/repository/org/apache/maven/surefire/surefire-api/3.0.0-M7/surefire-api-3.0.0-M7.jar",
            "/root/.m2/repository/org/apache/maven/surefire/surefire-shared-utils/3.0.0-M7/surefire-shared-utils-3.0.0-M7.jar"
        });
        
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        for (CtInvocation<?> invocation : Query.getElements(model.getRootPackage(), new TypeFilter<>(CtInvocation.class))) {
            processInvocation(invocation);
        }
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void processInvocation(CtInvocation<?> invocation) {
        // Check if this is a static method call to TestListResolver.getWildcard()
        if (isTestListResolverGetWildcardCall(invocation)) {
            try {
                // Create a new constructor call: new TestListResolver("*.class")
                Factory factory = invocation.getFactory();
                
                // Create the string literal "*.class"
                CtLiteral<String> wildcardLiteral = factory.createLiteral("*.class");
                
                // Get type reference to TestListResolver
                CtTypeReference<?> testListResolverType = null;
                if (invocation.getTarget() instanceof CtTypeAccess) {
                    testListResolverType = ((CtTypeAccess<?>) invocation.getTarget()).getAccessedType();
                } else {
                    // Try to get it from the method's declaring type
                    testListResolverType = invocation.getExecutable().getDeclaringType();
                }
                
                if (testListResolverType != null) {
                    // Create constructor call
                    CtConstructorCall<?> constructorCall = factory.Core().createConstructorCall();
                    constructorCall.setType(testListResolverType);
                    constructorCall.setArguments(Arrays.asList(wildcardLiteral));
                    
                    // Replace the method invocation with constructor call
                    invocation.replace(constructorCall);
                    
                    System.out.println("Fixed getWildcard() call at " + invocation.getPosition());
                }
            } catch (Exception e) {
                System.err.println("Error fixing getWildcard() call at " + invocation.getPosition() + ": " + e.getMessage());
            }
        }
    }
    
    private static boolean isTestListResolverGetWildcardCall(CtInvocation<?> invocation) {
        // Check if the method being called is named "getWildcard"
        if (!"getWildcard".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        
        // Check if it's a static method call
        if (invocation.getTarget() == null) {
            // No target means it's a static method call in the current class
            // We need to check the import or fully qualified name
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType != null) {
                return "org.apache.maven.surefire.api.testset.TestListResolver".equals(
                    declaringType.getQualifiedName());
            }
            return false;
        } else if (invocation.getTarget() instanceof CtTypeAccess) {
            // Static method call with class name prefix
            CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) invocation.getTarget();
            return "org.apache.maven.surefire.api.testset.TestListResolver".equals(
                typeAccess.getAccessedType().getQualifiedName());
        }
        
        return false;
    }
}