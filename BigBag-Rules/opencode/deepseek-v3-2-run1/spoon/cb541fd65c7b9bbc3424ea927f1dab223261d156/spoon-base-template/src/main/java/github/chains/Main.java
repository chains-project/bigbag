package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import java.util.List;
import java.util.Set;

public class Main {
    // Methods that exist in both Matchers and CoreMatchers
    private static final Set<String> COMMON_METHODS = Set.of(
        "containsString", "hasItem", "hasItems", "not", "notNullValue",
        "equalTo", "is", "allOf", "describedAs", "any", "startsWith",
        "endsWith"
    );
    
    // Methods that only exist in Matchers (not in CoreMatchers)
    private static final Set<String> MATCHERS_ONLY_METHODS = Set.of(
        "emptyIterableOf", "hasProperty", "hasSize", "hasToString",
        "hasEntry", "hasKey", "everyItem", "iterableWithSize", "empty"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hamcrest 2.2 compatibility transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Process all method invocations
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
            int changed = 0;
            for (CtInvocation<?> invoc : invocations) {
                if (processInvocation(invoc, factory)) {
                    changed++;
                }
            }
            
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete! Changed " + changed + " method calls.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static boolean processInvocation(CtInvocation<?> invoc, Factory factory) {
        // Check if this is a call to Matchers.someMethod()
        if (invoc.getTarget() instanceof CtTypeAccess) {
            CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) invoc.getTarget();
            CtTypeReference<?> typeRef = typeAccess.getAccessedType();
            
            if (typeRef != null && "org.hamcrest.Matchers".equals(typeRef.getQualifiedName())) {
                String methodName = invoc.getExecutable().getSimpleName();
                
                if (MATCHERS_ONLY_METHODS.contains(methodName)) {
                    // Method only exists in Matchers, keep it as is
                    System.out.println("  Keeping Matchers." + methodName + "() as Matchers-only method");
                    return false;
                } else if (COMMON_METHODS.contains(methodName)) {
                    // Method exists in both, change to CoreMatchers
                    CtTypeReference<?> coreMatchersRef = factory.Type().createReference("org.hamcrest.CoreMatchers");
                    typeAccess.setAccessedType((CtTypeReference) coreMatchersRef);
                    System.out.println("  Changing Matchers." + methodName + "() to CoreMatchers." + methodName + "()");
                    return true;
                } else {
                    // Unknown method - change to CoreMatchers (will fail at compile time if doesn't exist)
                    CtTypeReference<?> coreMatchersRef = factory.Type().createReference("org.hamcrest.CoreMatchers");
                    typeAccess.setAccessedType((CtTypeReference) coreMatchersRef);
                    System.out.println("  Changing Matchers." + methodName + "() to CoreMatchers." + methodName + "() (assuming it exists)");
                    return true;
                }
            }
        }
        return false;
    }
}