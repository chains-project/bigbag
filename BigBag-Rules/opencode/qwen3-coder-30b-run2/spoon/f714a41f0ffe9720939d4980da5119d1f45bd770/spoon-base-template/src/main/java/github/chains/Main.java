package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Get the input directory from command line arguments
        String inputDir = args.length > 0 ? args[0] : "/workspace/XChange";
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Process the code
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Transformation rules
        fixPubNubConfiguration(model, factory);
        
        // Print statistics
        System.out.println("Transformation completed.");
    }
    
    /**
     * Fix PNConfiguration instantiation - replace no-arg constructor with UserId constructor
     */
    private static void fixPubNubConfiguration(CtModel model, Factory factory) {
        // Find all PNConfiguration constructor calls with no arguments
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return "com.pubnub.api.PNConfiguration".equals(element.getType().getSimpleName()) &&
                       element.getArguments().isEmpty();
            }
        });
        
        for (CtConstructorCall call : constructorCalls) {
            // Create new constructor call with UserId parameter
            CtConstructorCall newCall = factory.Core().createConstructorCall();
            newCall.setType(call.getType());
            newCall.setArguments(List.of(factory.Code().createLiteral("")));
            
            // Replace the old constructor call
            call.replace(newCall);
        }
    }
}
    
    /**
     * Fix PNConfiguration instantiation - replace no-arg constructor with UserId constructor
     */
    private static void fixPubNubConfiguration(CtModel model, Factory factory) {
        // Find all PNConfiguration constructor calls with no arguments
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return element.getType().getQualifiedName().equals("com.pubnub.api.PNConfiguration") &&
                       element.getArguments().isEmpty();
            }
        });
        
        for (CtConstructorCall<?> call : constructorCalls) {
            // Create new constructor call with UserId parameter
            // Use a default userId value for now (would need to be parameterized)
            CtConstructorCall<?> newCall = factory.Core().createConstructorCall();
            newCall.setType(call.getType());
            newCall.setArguments(List.of(factory.Code().createLiteral("")));
            
            // Replace the old constructor call
            call.replace(newCall);
        }
    }
    
    /**
     * Fix SubscribeCallback interface usage by removing references to missing classes
     */
    private static void fixSubscribeCallbackInterface(CtModel model, Factory factory) {
        // Find all imports related to the missing objects classes
        List<CtImport> importsToRemove = model.getElements(new TypeFilter<>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference().getQualifiedName().startsWith("com.pubnub.api.models.consumer.pubsub.objects.");
            }
        });
        
        for (CtImport importToRemove : importsToRemove) {
            importToRemove.delete();
        }
    }
}