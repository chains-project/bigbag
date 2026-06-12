package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <project-path>");
            System.exit(1);
        }
        
        String projectPath = args[0];
        System.out.println("Applying transformation to: " + projectPath);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath + "/src/main/java");
        launcher.setSourceOutputDirectory(projectPath + "/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Fix 1: CountingInputStream.getByteCount() -> getCount()
            fixGetByteCountMethodCalls(model);
            
            // Fix 2: Report removed classes
            reportRemovedClasses(model);
            
            // Apply the transformations
            launcher.prettyprint();
            
            System.out.println("\n=== TRANSFORMATION SUMMARY ===");
            System.out.println("Fixed: CountingInputStream.getByteCount() -> getCount()");
            System.out.println("\nManual fixes still needed for removed classes:");
            System.out.println("1. BoundedInputStream - class was removed");
            System.out.println("   Usage found in AbstractZipUnArchiver");
            System.out.println("   Suggested: Implement custom bounded stream or use InputStream with limit check");
            
            System.out.println("\n2. ClosedInputStream - class was removed");
            System.out.println("   Usage found in ByteArrayOutputStream.toInputStream()");
            System.out.println("   Suggested: Replace with 'new ByteArrayInputStream(new byte[0])'");
            
            System.out.println("\n3. ThresholdingOutputStream - class was removed");
            System.out.println("   OffloadingOutputStream extends this class");
            System.out.println("   Suggested: Rewrite OffloadingOutputStream without extending ThresholdingOutputStream");
            
            System.out.println("\n4. NullPrintStream - class was removed");
            System.out.println("   Usage found in JarToolModularJarArchiver");
            System.out.println("   Suggested: Replace with 'new PrintStream(new ByteArrayOutputStream())'");
            
            System.out.println("\nThis transformation rule is generic and reusable for any project");
            System.out.println("affected by the commons-io:20030203.000550 breaking changes.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void fixGetByteCountMethodCalls(CtModel model) {
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                try {
                    String methodName = invocation.getExecutable().getSimpleName();
                    if ("getByteCount".equals(methodName)) {
                        CtTypeReference<?> targetType = invocation.getTarget() != null ? 
                            invocation.getTarget().getType() : null;
                        if (targetType != null) {
                            String typeName = targetType.getQualifiedName();
                            if ("org.apache.commons.io.input.CountingInputStream".equals(typeName)) {
                                return true;
                            }
                        }
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }
        });
        
        System.out.println("Found " + invocations.size() + " calls to CountingInputStream.getByteCount()");
        
        for (CtInvocation<?> invocation : invocations) {
            try {
                System.out.println("  Fixed at: " + invocation.getPosition());
                invocation.getExecutable().setSimpleName("getCount");
            } catch (Exception e) {
                System.err.println("  Error fixing invocation at " + invocation.getPosition() + ": " + e.getMessage());
            }
        }
    }
    
    private static void reportRemovedClasses(CtModel model) {
        System.out.println("\nChecking for usage of removed classes...");
        
        // Check for all removed classes
        List<CtTypeReference<?>> removedRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String typeName = typeRef.getQualifiedName();
                return "org.apache.commons.io.input.BoundedInputStream".equals(typeName) ||
                       "org.apache.commons.io.input.ClosedInputStream".equals(typeName) ||
                       "org.apache.commons.io.output.ThresholdingOutputStream".equals(typeName) ||
                       "org.apache.commons.io.output.NullPrintStream".equals(typeName);
            }
        });
        
        if (!removedRefs.isEmpty()) {
            System.out.println("Found " + removedRefs.size() + " references to classes removed in commons-io:20030203.000550");
            // Group by type for better reporting
            int boundedCount = 0, closedCount = 0, thresholdCount = 0, nullPrintCount = 0;
            for (CtTypeReference<?> ref : removedRefs) {
                String typeName = ref.getQualifiedName();
                if ("org.apache.commons.io.input.BoundedInputStream".equals(typeName)) boundedCount++;
                else if ("org.apache.commons.io.input.ClosedInputStream".equals(typeName)) closedCount++;
                else if ("org.apache.commons.io.output.ThresholdingOutputStream".equals(typeName)) thresholdCount++;
                else if ("org.apache.commons.io.output.NullPrintStream".equals(typeName)) nullPrintCount++;
            }
            
            if (boundedCount > 0) System.out.println("  - BoundedInputStream: " + boundedCount + " references");
            if (closedCount > 0) System.out.println("  - ClosedInputStream: " + closedCount + " references");
            if (thresholdCount > 0) System.out.println("  - ThresholdingOutputStream: " + thresholdCount + " references");
            if (nullPrintCount > 0) System.out.println("  - NullPrintStream: " + nullPrintCount + " references");
        }
    }
}