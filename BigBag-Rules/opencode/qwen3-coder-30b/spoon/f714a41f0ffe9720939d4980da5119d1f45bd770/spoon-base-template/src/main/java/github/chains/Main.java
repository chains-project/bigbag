package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtNewClassImpl;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process
        launcher.addInputResource("/workspace/XChange");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/XChange-output");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Fix 1: Remove imports for missing classes
        List<CtImport> importsToRemove = new ArrayList<>();
        model.getElements(new TypeFilter<CtImport>(CtImport.class)).forEach(importDecl -> {
            String importedType = importDecl.getReference().toString();
            if (importedType.contains("PNMembershipResult") ||
                importedType.contains("PNSpaceResult") ||
                importedType.contains("PNUserResult")) {
                importsToRemove.add(importDecl);
            }
        });
        
        // Remove the imports
        importsToRemove.forEach(importDecl -> {
            importDecl.delete();
        });
        
        // Fix 2: Remove method overrides for missing callback methods
        // Find all SubscribeCallback implementations
        model.getElements(new TypeFilter<CtClass<?>>(CtClass.class)).forEach(clazz -> {
            if (clazz.getSuperclass() != null && 
                clazz.getSuperclass().toString().contains("SubscribeCallback")) {
                
                // Remove the user, space, and membership method overrides
                clazz.getMethods().removeIf(method -> {
                    return (method.getSimpleName().equals("user") ||
                           method.getSimpleName().equals("space") ||
                           method.getSimpleName().equals("membership"));
                });
            }
        });
        
        // Generate the modified code
        launcher.setSourceOutputDirectory("/workspace/XChange-output");
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
    }
}