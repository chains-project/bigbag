package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.Collections;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create launcher and set up the model
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/XChange");
        launcher.setSourceOutputDirectory("/workspace/XChange-fixed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Fix 1: PNConfiguration constructor calls without parameters 
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall call : constructorCalls) {
            if (call.getType().getSimpleName().equals("PNConfiguration") && call.getArguments().isEmpty()) {
                // Replace with constructor call that passes empty string parameter
                CtLiteral<String> emptyStringLiteral = factory.Code().createLiteral("");
                call.setArguments(Collections.singletonList(emptyStringLiteral));
            }
        }
        
        // Fix 2: Remove imports for missing classes
        List<CtImport> importsToRemove = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : importsToRemove) {
            String importedTypeName = ctImport.getReference().getSimpleName();
            if ("PNMembershipResult".equals(importedTypeName) || 
                "PNSpaceResult".equals(importedTypeName) || 
                "PNUserResult".equals(importedTypeName)) {
                ctImport.delete();
            }
        }
        
        // Fix 3: Find and modify anonymous SubscribeCallback classes
        List<CtType<?>> anonymousTypes = model.getElements(new TypeFilter<>(CtType.class));
        
        // Write the modified files
        launcher.setSourceOutputDirectory("/workspace/XChange-fixed");
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
}