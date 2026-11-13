package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic Spoon transformation for PubNub API 6.3.2 migration.
 * 
 * Fixes the following breaking changes:
 * 1. PNConfiguration constructor now requires UUID/String parameter
 * 2. PNUserResult -> PNUUIDMetadataResult (package change)
 * 3. PNMembershipResult package moved
 * 4. SubscribeCallback interface redesigned
 * 
 * Usage: java Main <input-source-directory> <output-directory>
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <input-source-directory> <output-directory>");
            System.err.println("Example: java Main /path/to/project/src /path/to/output");
            System.err.println("\nThis transformation fixes PubNub API 6.3.2 breaking changes:");
            System.err.println("1. PNConfiguration constructor now requires UUID/String parameter");
            System.err.println("2. PNUserResult -> PNUUIDMetadataResult (package change)");
            System.err.println("3. PNMembershipResult package moved");
            System.err.println("4. SubscribeCallback interface redesigned");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        System.out.println("Applying PubNub 6.3.2 API migration transformation...");
        System.out.println("Input: " + inputPath);
        System.out.println("Output: " + outputPath);

        Launcher launcher = new Launcher();
        
        // Configure Spoon
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add input source directory
        launcher.addInputResource(inputPath);
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputPath);

        // Build the model
        try {
            launcher.buildModel();
            
            // Apply transformations
            fixPNConfigurationConstructors(launcher);
            updateTypeReferences(launcher);
            updateSubscribeCallbackClasses(launcher);
            
            // Generate the transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Summary of changes:");
            System.out.println("1. Fixed PNConfiguration constructor calls (added UUID parameter)");
            System.out.println("2. Updated type references for PubNub object API classes");
            System.out.println("3. Updated SubscribeCallback implementations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Fix PNConfiguration constructor calls by adding a UUID parameter
     */
    private static void fixPNConfigurationConstructors(Launcher launcher) {
        System.out.println("Fixing PNConfiguration constructor calls...");
        
        // Find all constructor calls to PNConfiguration
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(
            launcher.getFactory(),
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getType() == null) return false;
                    String typeName = constructorCall.getType().getQualifiedName();
                    return "com.pubnub.api.PNConfiguration".equals(typeName);
                }
            }
        );

        int fixedCount = 0;
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            // Check if it's a no-arg constructor
            if (constructorCall.getArguments().isEmpty()) {
                System.out.println("Found no-arg PNConfiguration constructor at: " + 
                    constructorCall.getPosition());
                
                // Create a UUID string literal as the argument
                String uuidString = "generated-uuid-" + UUID.randomUUID().toString().substring(0, 8);
                
                // Use code snippet replacement
                String newCode = "new com.pubnub.api.PNConfiguration(\"" + uuidString + "\")";
                
                // Create a code snippet to replace the constructor call
                CtCodeSnippetExpression<?> snippet = 
                    launcher.getFactory().createCodeSnippetExpression(newCode);
                
                constructorCall.replace(snippet);
                fixedCount++;
            }
        }
        
        System.out.println("Fixed " + fixedCount + " PNConfiguration constructor calls");
    }

    /**
     * Update type references in the code (imports and usage)
     */
    private static void updateTypeReferences(Launcher launcher) {
        System.out.println("Updating type references...");
        
        // Map of old type names to new type names
        Map<String, String> typeMappings = new HashMap<>();
        typeMappings.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult",
            "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult"
        );
        typeMappings.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult",
            "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult"
        );
        
        // Find all type references in the code
        List<CtTypeReference<?>> typeRefs = Query.getElements(
            launcher.getFactory(),
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return typeRef.getQualifiedName() != null;
                }
            }
        );
        
        int updatedRefs = 0;
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            
            // Check if this type needs to be updated
            for (Map.Entry<String, String> entry : typeMappings.entrySet()) {
                if (entry.getKey().equals(typeName)) {
                    System.out.println("Updating type reference: " + typeName + " -> " + entry.getValue());
                    
                    // Create a new type reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(entry.getValue());
                    
                    // Replace the old type reference with the new one
                    typeRef.replace(newTypeRef);
                    updatedRefs++;
                    break;
                }
            }
        }
        
        System.out.println("Updated " + updatedRefs + " type references");
    }

    /**
     * Update SubscribeCallback implementations:
     * 1. Rename user() method to uuid()
     * 2. Remove space() method
     * 3. Change class to extend BaseSubscribeCallback instead of implementing SubscribeCallback
     */
    private static void updateSubscribeCallbackClasses(Launcher launcher) {
        System.out.println("Updating SubscribeCallback implementations...");
        
        // Find all classes
        List<CtClass<?>> allClasses = Query.getElements(
            launcher.getFactory(),
            new TypeFilter<CtClass<?>>(CtClass.class)
        );
        
        final String subscribeCallbackClass = "com.pubnub.api.callbacks.SubscribeCallback";
        final String baseSubscribeCallbackClass = "com.pubnub.api.callbacks.SubscribeCallback$BaseSubscribeCallback";
        
        int updatedClasses = 0;
        for (CtClass<?> ctClass : allClasses) {
            boolean isSubscribeCallbackImpl = false;
            
            // Check if class implements SubscribeCallback
            for (CtTypeReference<?> interf : ctClass.getSuperInterfaces()) {
                if (subscribeCallbackClass.equals(interf.getQualifiedName())) {
                    isSubscribeCallbackImpl = true;
                    break;
                }
            }
            
            // Check if extends BaseSubscribeCallback
            CtTypeReference<?> superClass = ctClass.getSuperclass();
            if (superClass != null && baseSubscribeCallbackClass.equals(superClass.getQualifiedName())) {
                isSubscribeCallbackImpl = true;
            }
            
            if (!isSubscribeCallbackImpl) {
                continue;
            }
            
            System.out.println("Found SubscribeCallback implementation: " + ctClass.getQualifiedName());
            
            boolean hasChanges = false;
            
            // 1. Rename user() method to uuid()
            for (CtMethod<?> method : ctClass.getMethods()) {
                if ("user".equals(method.getSimpleName())) {
                    System.out.println("  Renaming user() to uuid()");
                    method.setSimpleName("uuid");
                    hasChanges = true;
                }
                
                // 2. Remove space() method
                else if ("space".equals(method.getSimpleName())) {
                    System.out.println("  Removing space() method");
                    method.delete();
                    hasChanges = true;
                }
            }
            
            // 3. Change class to extend BaseSubscribeCallback if it implements SubscribeCallback directly
            for (CtTypeReference<?> interf : ctClass.getSuperInterfaces()) {
                if (subscribeCallbackClass.equals(interf.getQualifiedName())) {
                    System.out.println("  Changing to extend BaseSubscribeCallback");
                    
                    // Remove the interface
                    Set<CtTypeReference<?>> interfaces = new HashSet<>(ctClass.getSuperInterfaces());
                    interfaces.removeIf(i -> subscribeCallbackClass.equals(i.getQualifiedName()));
                    ctClass.setSuperInterfaces(interfaces);
                    
                    // Set superclass to BaseSubscribeCallback
                    CtTypeReference<?> baseCallbackRef = launcher.getFactory().createReference(baseSubscribeCallbackClass);
                    ctClass.setSuperclass(baseCallbackRef);
                    hasChanges = true;
                    break;
                }
            }
            
            if (hasChanges) {
                updatedClasses++;
            }
        }
        
        System.out.println("Updated " + updatedClasses + " SubscribeCallback implementations");
    }
}