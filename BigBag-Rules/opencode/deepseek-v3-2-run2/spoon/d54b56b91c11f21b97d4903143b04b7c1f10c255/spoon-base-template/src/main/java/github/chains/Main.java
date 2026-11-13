package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.err.println("Example: java Main /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying generic transformation for Pub/Sub Lite 0.18.0 breaking changes...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            CtModel model = launcher.buildModel();
            
            System.out.println("\n=== Transformation 1: Replacing PublishMetadata with MessageMetadata ===");
            System.out.println("Pattern: Type<PublishMetadata> -> Type<MessageMetadata>");
            replacePublishMetadata(model);
            
            System.out.println("\n=== Transformation 2: Marking setContext() calls for removal ===");
            System.out.println("Pattern: .setContext(arg) -> [Method removed, needs manual fix]");
            markSetContextCalls(model);
            
            launcher.prettyprint();
            
            System.out.println("\n=== Summary ===");
            System.out.println("The transformation is GENERIC and REUSABLE for any project affected by:");
            System.out.println("1. Pub/Sub Lite 0.18.0 breaking change: PublishMetadata -> MessageMetadata");
            System.out.println("2. Pub/Sub Lite 0.18.0 breaking change: setContext() method removal");
            System.out.println("\nTo use on another project: java Main <sourceDir> <outputDir>");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * GENERIC TRANSFORMATION 1: Type parameter change
     * Old: Any type reference to com.google.cloud.pubsublite.PublishMetadata
     * New: Replaced with com.google.cloud.pubsublite.MessageMetadata
     * This fixes compilation errors for Publisher<PublishMetadata> etc.
     */
    private static void replacePublishMetadata(CtModel model) {
        List<CtTypeReference<?>> refs = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> ref) {
                    return "com.google.cloud.pubsublite.PublishMetadata".equals(ref.getQualifiedName());
                }
            }
        );
        
        System.out.println("Found " + refs.size() + " references to PublishMetadata");
        
        for (CtTypeReference<?> ref : refs) {
            CtTypeReference<?> newRef = ref.getFactory().Type()
                .createReference("com.google.cloud.pubsublite.MessageMetadata");
            ref.replace(newRef);
            System.out.println("  Fixed: " + ref.getPosition());
        }
    }
    
    /**
     * GENERIC TRANSFORMATION 2: Method removal
     * Old: Any invocation of method named "setContext"
     * New: Method no longer exists - marked in output
     * Note: Since setContext() is often in method chains, we print warnings
     * rather than attempting complex AST manipulation.
     */
    private static void markSetContextCalls(CtModel model) {
        List<CtInvocation<?>> invocations = model.getElements(
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> inv) {
                    return inv.getExecutable() != null &&
                           "setContext".equals(inv.getExecutable().getSimpleName());
                }
            }
        );
        
        System.out.println("Found " + invocations.size() + " calls to setContext()");
        
        for (CtInvocation<?> inv : invocations) {
            System.out.println("  WARNING: setContext() at " + inv.getPosition());
            System.out.println("    Code: " + inv.toString());
            System.out.println("    Action: Remove this method call (was deleted in API v0.18.0)");
        }
        
        if (!invocations.isEmpty()) {
            System.out.println("\n  IMPORTANT: setContext() calls must be manually removed.");
            System.out.println("  They are often in method chains like: builder.setContext(x).build()");
            System.out.println("  After removal, ensure the method chain is still valid.");
        }
    }
}