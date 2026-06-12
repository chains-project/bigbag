package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Repair {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Read from http project
        launcher.addInputResource("../http/src/main/java");
        
        // Setup environment
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8); 

        // Build model
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // 1. Locate YamlPolicyFactory
        for (CtType<?> type : model.getAllTypes()) {
            if (type.getQualifiedName().equals("com.artipie.security.policy.YamlPolicyFactory")) {
                System.out.println("Found YamlPolicyFactory, applying fix...");
                fixYamlPolicyFactory((CtClass<?>) type);
                
                // Save only this file
                saveType(type);
            }
        }
        System.out.println("Transformation complete.");
    }

    private static void saveType(CtType<?> type) {
        spoon.reflect.cu.CompilationUnit cu = type.getFactory().CompilationUnit().getMap().get(type.getPosition().getFile().getAbsolutePath());
        if (cu == null) {
            // Fallback strategy if map lookup fails by path
            for (spoon.reflect.cu.CompilationUnit unit : type.getFactory().CompilationUnit().getMap().values()) {
                 if (unit.getDeclaredTypes().contains(type)) {
                     cu = unit;
                     break;
                 }
            }
        }
        
        if (cu != null) {
            java.io.File file = cu.getFile();
            System.out.println("Writing to " + file.getAbsolutePath());
            try (java.io.PrintWriter out = new java.io.PrintWriter(file)) {
                out.print(cu.prettyprint());
            } catch (java.io.FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Could not find compilation unit for " + type.getQualifiedName());
        }
    }

    private static void fixYamlPolicyFactory(CtClass<?> clazz) {
         // Replace usage of Storages
         List<CtConstructorCall> constructorCalls = clazz.getElements(new TypeFilter<>(CtConstructorCall.class));
         
         for (CtConstructorCall call : constructorCalls) {
             if (call.getType().getSimpleName().equals("Storages")) {
                 if (call.getParent() instanceof CtInvocation) {
                     CtInvocation invocation = (CtInvocation) call.getParent();
                     if (invocation.getExecutable().getSimpleName().equals("newStorage")) {
                         
                         System.out.println("Refactoring new Storages().newStorage(...)");

                         // 1. Change target from new Storages() to new StoragesLoader()
                         CtTypeReference<?> storagesLoaderRef = clazz.getFactory().Type().createReference("com.artipie.asto.factory.StoragesLoader");
                         CtConstructorCall<?> newStoragesLoader = clazz.getFactory().Code().createConstructorCall(storagesLoaderRef);
                         invocation.setTarget(newStoragesLoader);
                         
                         // 2. Change method name to newObject
                         invocation.getExecutable().setSimpleName("newObject");
                         
                         // 3. Wrap the second argument
                         List<CtExpression<?>> args = invocation.getArguments();
                         if (args.size() == 2) {
                             CtExpression<?> arg1 = args.get(1);
                             invocation.removeArgument(arg1);
                             
                             CtTypeReference<?> configRef = clazz.getFactory().Type().createReference("com.artipie.asto.factory.Config.YamlStorageConfig");
                             CtConstructorCall<?> newConfig = clazz.getFactory().Code().createConstructorCall(configRef, arg1);
                             
                             invocation.addArgument(newConfig);
                         }
                     }
                 }
             }
         }
    }
}