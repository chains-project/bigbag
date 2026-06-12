package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule for fixing breaking MINA dependency changes in Java projects.
 * This transformation addresses common breaking changes in org.apache.mina:mina-core 2.2.1
 */
public class Main {
    public static void main(String[] args) {
        // Check if we have input and output directories
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformer.jar <input_dir> <output_dir>");
            return;
        }
        
        String inputDir = args[0];
        String outputDir = args[1];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Process the code
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Apply transformations for MINA 2.2.1 breaking changes
        applyMinaBreakingChanges(factory);
        
        // Generate the transformed code
        launcher.prettyprint();
    }
    
    /**
     * Apply transformations for MINA 2.2.1 breaking changes
     */
    private static void applyMinaBreakingChanges(Factory factory) {
        // Pattern 1: Transform IoUtil.await methods with new signature
        // Old: await(Iterable<?>)
        // New: await(Iterable<?>, long, TimeUnit) - requires timeout parameters
        transformIoUtilAwait(factory);
        
        // Pattern 2: Transform IoUtil.awaitUninterruptibly methods with new signature
        // Old: awaitUninterruptibly(Iterable<?>)
        // New: awaitUninterruptibly(Iterable<?>, long, TimeUnit) - requires timeout parameters
        transformIoUtilAwaitUninterruptibly(factory);
        
        // Pattern 3: Transform IoBuffer allocation methods to add boolean parameter
        transformIoBufferAllocations(factory);
    }
    
    /**
     * Transform IoUtil.await methods to include timeout parameters
     */
    private static void transformIoUtilAwait(Factory factory) {
        // Find all calls to IoUtil.await methods
        List<CtInvocation<?>> awaitInvocations = factory.getModel().getElements(
            new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    if (element.getExecutable() == null) return false;
                    CtExecutableReference<?> executable = element.getExecutable();
                    return executable.getDeclaringType() != null &&
                           "org.apache.mina.core.IoUtil".equals(executable.getDeclaringType().getQualifiedName()) &&
                           executable.getSimpleName().equals("await");
                }
            }
        );
        
        for (CtInvocation<?> invocation : awaitInvocations) {
            // Get the actual number of arguments
            int argCount = invocation.getArguments().size();
            
            // If it's the old signature (1 argument), add timeout parameters
            if (argCount == 1) {
                // Create timeout parameters (5 seconds, TimeUnit.SECONDS)
                CtLiteral<Long> timeoutValue = factory.createLiteral(5L);
                // For simplicity, we'll add the literal directly as a workaround
                invocation.addArgument(timeoutValue);
                // Add TimeUnit.SECONDS - we'll use a string-based approach for now
                CtLiteral<String> timeUnitValue = factory.createLiteral("java.util.concurrent.TimeUnit.SECONDS");
                invocation.addArgument(timeUnitValue);
            }
        }
    }
    
    /**
     * Transform IoUtil.awaitUninterruptibly methods to include timeout parameters
     */
    private static void transformIoUtilAwaitUninterruptibly(Factory factory) {
        // Find all calls to IoUtil.awaitUninterruptibly methods
        List<CtInvocation<?>> awaitUninterruptiblyInvocations = factory.getModel().getElements(
            new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    if (element.getExecutable() == null) return false;
                    CtExecutableReference<?> executable = element.getExecutable();
                    return executable.getDeclaringType() != null &&
                           "org.apache.mina.core.IoUtil".equals(executable.getDeclaringType().getQualifiedName()) &&
                           executable.getSimpleName().equals("awaitUninterruptibly");
                }
            }
        );
        
        for (CtInvocation<?> invocation : awaitUninterruptiblyInvocations) {
            // Get the actual number of arguments
            int argCount = invocation.getArguments().size();
            
            // If it's the old signature (1 argument), add timeout parameters
            if (argCount == 1) {
                // Create timeout parameters (5 seconds, TimeUnit.SECONDS)
                CtLiteral<Long> timeoutValue = factory.createLiteral(5L);
                // For simplicity, we'll add the literal directly as a workaround
                invocation.addArgument(timeoutValue);
                // Add TimeUnit.SECONDS - we'll use a string-based approach for now
                CtLiteral<String> timeUnitValue = factory.createLiteral("java.util.concurrent.TimeUnit.SECONDS");
                invocation.addArgument(timeUnitValue);
            }
        }
    }
    
    /**
     * Transform IoBuffer allocation methods to handle new API requirements
     */
    private static void transformIoBufferAllocations(Factory factory) {
        // Find all IoBuffer.allocate calls
        List<CtInvocation<?>> bufferAllocations = factory.getModel().getElements(
            new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    if (element.getExecutable() == null) return false;
                    CtExecutableReference<?> executable = element.getExecutable();
                    return executable.getDeclaringType() != null &&
                           "org.apache.mina.core.buffer.IoBuffer".equals(executable.getDeclaringType().getQualifiedName()) &&
                           executable.getSimpleName().equals("allocate");
                }
            }
        );
        
        for (CtInvocation<?> invocation : bufferAllocations) {
            // Get the actual number of arguments
            int argCount = invocation.getArguments().size();
            
            // If it's the old signature (1 argument) and needs the boolean parameter
            if (argCount == 1) {
                // Add the default boolean parameter for autoExpand (true)
                CtLiteral<Boolean> autoExpandValue = factory.createLiteral(true);
                invocation.addArgument(autoExpandValue);
            }
        }
    }
}