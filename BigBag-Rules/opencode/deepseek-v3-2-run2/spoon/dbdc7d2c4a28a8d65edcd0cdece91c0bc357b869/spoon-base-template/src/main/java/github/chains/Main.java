package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <sourceDirectory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all method invocations of enableLogging on AbstractUnArchiver or subclasses
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check if the method name is enableLogging
                if (!"enableLogging".equals(invocation.getExecutable().getSimpleName())) {
                    return false;
                }
                
                // Get the target expression type
                try {
                    if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
                        String targetTypeName = invocation.getTarget().getType().getQualifiedName();
                        // Check if target is AbstractUnArchiver or a subclass
                        if (targetTypeName != null && (
                            targetTypeName.equals("org.codehaus.plexus.archiver.AbstractUnArchiver") ||
                            targetTypeName.startsWith("org.codehaus.plexus.archiver.") && 
                            (targetTypeName.endsWith("UnArchiver") || targetTypeName.contains(".zip.") || 
                             targetTypeName.contains(".tar.") || targetTypeName.contains(".jar."))
                        )) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // If we can't determine the type, check by method signature pattern
                    System.err.println("Warning: Could not determine type for invocation: " + invocation);
                }
                
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " enableLogging() calls to remove");
        
        // Remove each invocation
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("Removing enableLogging call at: " + invocation.getPosition());
            invocation.delete();
        }
        
        // Write the transformed code
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Removed " + invocations.size() + " enableLogging() calls.");
        System.out.println("================================================");
        System.out.println("Breaking Change Analysis:");
        System.out.println("  - Dependency: plexus-archiver 2.x → 4.4.0");
        System.out.println("  - Old API: AbstractUnArchiver.enableLogging(Logger logger)");
        System.out.println("  - New API: No direct replacement - uses SLF4J static binding");
        System.out.println("  - Transformation: Remove enableLogging() method calls");
        System.out.println("================================================");
        System.out.println("Note: After transformation, you may need to:");
        System.out.println("  1. Remove unused imports (Logger, ConsoleLogger)");
        System.out.println("  2. Configure SLF4J at application level");
        System.out.println("  3. Ensure proper SLF4J implementation in classpath");
    }
}