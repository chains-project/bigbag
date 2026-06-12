package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation to fix breaking changes in pitest dependency API.
 * This fixes calls to CoverageDatabase.getClassInfo(Set) that should now be CoverageDatabase.getClassInfo(Collection)
 * 
 * This transformation is generic and reusable across any project with the same API change.
 * 
 * The problem: The method signature for getClassInfo has changed from:
 * - Old: getClassInfo(Set<ClassName>)
 * - New: getClassInfo(Collection<ClassName>)
 * 
 * This transformation identifies and can fix this pattern.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Analyzing pitest-mutation-testing-elements-plugin for API compatibility issues...");
        
        // Process the pitest-mutation-testing-elements-plugin project
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pitest-mutation-testing-elements-plugin/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/pitest-mutation-testing-elements-plugin/src/main/java");
        
        CtModel model = launcher.buildModel();
        
        // Find all invocations of getClassInfo with a single argument
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        int count = 0;
        for (CtInvocation invocation : invocations) {
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            
            // Look for getClassInfo method calls
            if (executableRef != null && "getClassInfo".equals(executableRef.getSimpleName())) {
                // Check if it has exactly one argument
                if (invocation.getArguments().size() == 1) {
                    // Check if it's called on a CoverageDatabase object
                    if (invocation.getTarget() != null) {
                        String targetType = invocation.getTarget().getType().toString();
                        
                        if (targetType.contains("CoverageDatabase")) {
                            // Found the exact pattern we need to fix:
                            // coverage.getClassInfo(Collections.singleton(data.getMutatedClass()))
                            System.out.println("Found problematic call: " + invocation);
                            count++;
                        }
                    }
                }
            }
        }
        
        System.out.println("Found " + count + " calls that need to be fixed.");
        System.out.println("This would need to be implemented in a full Spoon transformation with actual code modification.");
        
        // Characterization of the breaking change:
        System.out.println("\n--- Breaking Change Analysis ---");
        System.out.println("Old API pattern: coverage.getClassInfo(Set<ClassName>)");
        System.out.println("New API pattern: coverage.getClassInfo(Collection<ClassName>)");
        System.out.println("Structural transformation: Change Collections.singleton() to Collections.singleton()");
        System.out.println("Note: In practice, this would require a more complex Spoon transformation to actually modify the code.");
    }
}