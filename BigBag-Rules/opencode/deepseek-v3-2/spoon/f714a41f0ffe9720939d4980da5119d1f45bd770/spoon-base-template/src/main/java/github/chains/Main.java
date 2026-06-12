package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Main {
    // Configuration for the transformation
    private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();
    
    static {
        // Type mappings for relocated classes
        TYPE_MAPPINGS.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult",
            "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult"
        );
        TYPE_MAPPINGS.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult", 
            "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult"
        );
        TYPE_MAPPINGS.put(
            "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult",
            "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult"
        );
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <source-directory> [subscribe-key]");
            System.err.println("  source-directory: Path to Java source code to transform");
            System.err.println("  subscribe-key: (Optional) Subscribe key for PNConfiguration constructor");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String subscribeKey = args.length > 1 ? args[1] : "YOUR_SUBSCRIBE_KEY_HERE";
        
        System.out.println("Applying PubNub 6.3.2 migration transformations to: " + sourceDir);
        System.out.println("Using subscribe key: " + subscribeKey);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        fixPNConfigurationConstructor(model, subscribeKey);
        fixTypeReferences(model);
        
        // Write changes back to source files
        launcher.prettyprint();
        
        System.out.println("Transformation completed!");
        System.out.println("Manual steps required:");
        System.out.println("1. Update SubscribeCallback implementations:");
        System.out.println("   - Add 'file' method: public void file(PubNub pubnub, PNFileEventResult result)");
        System.out.println("   - Rename 'user' method to 'uuid'");
        System.out.println("   - Rename 'space' method to 'channel'");
        System.out.println("   - Update parameter types in 'uuid', 'channel', 'membership' methods");
        System.out.println("2. Review PNConfiguration constructor calls");
        System.out.println("3. Compile and fix any remaining issues");
    }
    
    private static void fixPNConfigurationConstructor(CtModel model, String subscribeKey) {
        System.out.println("\nFixing PNConfiguration constructor calls...");
        
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference<?> typeRef = element.getType();
                return typeRef != null && 
                       "com.pubnub.api.PNConfiguration".equals(typeRef.getQualifiedName()) &&
                       element.getArguments().isEmpty();
            }
        });
        
        for (CtConstructorCall<?> call : constructorCalls) {
            System.out.println("  Found PNConfiguration() at: " + call.getPosition());
            
            // Create string literal for subscribe key
            CtLiteral<String> keyLiteral = call.getFactory().createLiteral(subscribeKey);
            
            // Create new constructor call with subscribe key parameter
            CtConstructorCall<?> newCall = call.getFactory().createConstructorCall(
                call.getFactory().createReference("com.pubnub.api.PNConfiguration"),
                keyLiteral
            );
            
            call.replace(newCall);
            System.out.println("  -> Replaced with PNConfiguration(\"" + subscribeKey + "\")");
        }
    }
    
    private static void fixTypeReferences(CtModel model) {
        System.out.println("\nFixing type references for relocated classes...");
        
        for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
            String oldType = entry.getKey();
            String newType = entry.getValue();
            
            System.out.println("  Processing " + oldType + " -> " + newType);
            
            // Find all type references
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    return oldType.equals(element.getQualifiedName());
                }
            });
            
            for (CtTypeReference<?> typeRef : typeRefs) {
                System.out.println("    Found at: " + typeRef.getPosition());
                // Create new type reference with updated qualified name
                CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newType);
                typeRef.replace(newTypeRef);
            }
        }
    }
}