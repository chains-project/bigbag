package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        // Add only main source files, exclude test files
        launcher.addInputResource(sourceDir + "/src/main/java");
        
        try {
            CtModel model = launcher.buildModel();
            
            // Transformation 1: Replace PublishMetadata with MessageMetadata
            replacePublishMetadataWithMessageMetadata(model);
            
            // Transformation 2: Remove setContext calls
            removeSetContextCalls(model);
            
            // Apply transformations
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void replacePublishMetadataWithMessageMetadata(CtModel model) {
        List<CtTypeReference<?>> refsToReplace = new ArrayList<>();
        List<CtTypeAccess<?>> accessesToReplace = new ArrayList<>();
        
        model.getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> ref) {
                if (ref.getQualifiedName() != null && 
                    ref.getQualifiedName().equals("com.google.cloud.pubsublite.PublishMetadata")) {
                    refsToReplace.add(ref);
                }
                super.visitCtTypeReference(ref);
            }
            
            @Override
            public <T> void visitCtTypeAccess(CtTypeAccess<T> access) {
                CtTypeReference<T> ref = access.getAccessedType();
                if (ref != null && 
                    ref.getQualifiedName() != null && 
                    ref.getQualifiedName().equals("com.google.cloud.pubsublite.PublishMetadata")) {
                    accessesToReplace.add(access);
                }
                super.visitCtTypeAccess(access);
            }
        });
        
        System.out.println("Found " + refsToReplace.size() + " type references to PublishMetadata");
        System.out.println("Found " + accessesToReplace.size() + " type accesses to PublishMetadata");
        
        // Replace type references
        for (CtTypeReference<?> ref : refsToReplace) {
            try {
                CtTypeReference<?> newRef = ref.getFactory().createReference("com.google.cloud.pubsublite.MessageMetadata");
                ref.replace(newRef);
            } catch (Exception e) {
                System.err.println("Failed to replace type reference: " + e.getMessage());
            }
        }
        
        // Replace type accesses
        for (CtTypeAccess<?> access : accessesToReplace) {
            try {
                CtTypeReference<?> newRef = access.getFactory().createReference("com.google.cloud.pubsublite.MessageMetadata");
                CtTypeAccess<?> newAccess = access.getFactory().createTypeAccess(newRef);
                access.replace(newAccess);
            } catch (Exception e) {
                System.err.println("Failed to replace type access: " + e.getMessage());
            }
        }
    }
    
    private static void removeSetContextCalls(CtModel model) {
        List<CtInvocation<?>> callsToRemove = new ArrayList<>();
        
        model.getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                try {
                    String methodName = invocation.getExecutable().getSimpleName();
                    if ("setContext".equals(methodName)) {
                        // Check if target is a Builder type
                        if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
                            String targetType = invocation.getTarget().getType().getQualifiedName();
                            if (targetType != null && 
                                (targetType.equals("com.google.cloud.pubsublite.internal.wire.SinglePartitionPublisherBuilder.Builder") ||
                                 targetType.endsWith(".SinglePartitionPublisherBuilder$Builder"))) {
                                callsToRemove.add(invocation);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue scanning
                }
                super.visitCtInvocation(invocation);
            }
        });
        
        System.out.println("Found " + callsToRemove.size() + " setContext calls to remove");
        
        for (CtInvocation<?> invocation : callsToRemove) {
            try {
                invocation.delete();
            } catch (Exception e) {
                System.err.println("Failed to remove setContext call: " + e.getMessage());
            }
        }
    }
}