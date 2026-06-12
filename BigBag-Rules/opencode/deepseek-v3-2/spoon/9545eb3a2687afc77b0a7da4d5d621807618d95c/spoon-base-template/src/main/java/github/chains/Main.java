package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.reflect.visitor.CtScanner;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Processing source directory: " + sourcePath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourcePath);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        applyHazelcastTransformations(model, launcher.getFactory());
        
        // Write transformed code back
        launcher.setSourceOutputDirectory(sourcePath);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void applyHazelcastTransformations(CtModel model, spoon.reflect.factory.Factory factory) {
        System.out.println("Applying Hazelcast 5.1 migration transformations...");
        
        // Create a scanner to visit all elements
        model.getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> typeRef) {
                super.visitCtTypeReference(typeRef);
                
                String typeName = typeRef.getQualifiedName();
                
                // Update type references
                if ("com.hazelcast.core.Cluster".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.core.Cluster -> com.hazelcast.cluster.Cluster");
                    // Create new type reference
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.cluster.Cluster");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.core.Member".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.core.Member -> com.hazelcast.cluster.Member");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.cluster.Member");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.core.MembershipEvent".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.core.MembershipEvent -> com.hazelcast.cluster.MembershipEvent");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.cluster.MembershipEvent");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.core.MembershipListener".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.core.MembershipListener -> com.hazelcast.cluster.MembershipListener");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.cluster.MembershipListener");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.core.IMap".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.core.IMap -> com.hazelcast.map.IMap");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.map.IMap");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.core.MapEvent".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.core.MapEvent -> com.hazelcast.map.MapEvent");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.map.MapEvent");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.monitor.LocalMapStats".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.monitor.LocalMapStats -> com.hazelcast.map.LocalMapStats");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.map.LocalMapStats");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.config.MaxSizeConfig".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.config.MaxSizeConfig -> com.hazelcast.config.EvictionConfig");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.config.EvictionConfig");
                    typeRef.replace(newRef);
                } else if ("com.hazelcast.config.MaxSizeConfig.MaxSizePolicy".equals(typeName)) {
                    System.out.println("  Updating: com.hazelcast.config.MaxSizeConfig.MaxSizePolicy -> com.hazelcast.config.EvictionPolicy");
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.config.EvictionPolicy");
                    typeRef.replace(newRef);
                }
            }
            
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> constCall) {
                super.visitCtConstructorCall(constCall);
                
                // Check for MaxSizeConfig constructor calls
                if (constCall.getType() != null && 
                    "com.hazelcast.config.MaxSizeConfig".equals(constCall.getType().getQualifiedName())) {
                    
                    System.out.println("  Found MaxSizeConfig constructor - updating to EvictionConfig");
                    System.out.println("  Note: Manual fix may be needed for MaxSizePolicy conversion");
                    
                    // Update the type to EvictionConfig
                    CtTypeReference<?> newRef = factory.createReference("com.hazelcast.config.EvictionConfig");
                    constCall.getType().replace(newRef);
                }
            }
        });
    }
}