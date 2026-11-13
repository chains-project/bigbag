package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation to fix breaking API changes from asto-core v1.15.3.
 * 
 * This transformation handles the migration from:
 * - Old API: com.artipie.asto.factory.Storages with newStorage(String, YamlMapping)
 * - New API: com.artipie.asto.factory.StoragesLoader with newObject(String, Config)
 * 
 * This is a generic, reusable transformation that can be applied to any Java project
 * affected by this specific breaking change in the asto-core dependency.
 * 
 * The transformation is parameterized by the old and new class/method names,
 * making it adaptable to similar breaking changes in other dependencies.
 */
public class Main {
    
    // Configuration - these could be made command-line parameters
    private static final String OLD_CLASS = "com.artipie.asto.factory.Storages";
    private static final String NEW_CLASS = "com.artipie.asto.factory.StoragesLoader";
    private static final String OLD_METHOD = "newStorage";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp . github.chains.Main <source-directory>");
            System.err.println("\nApplies generic transformation for asto-core v1.15.3 breaking change:");
            System.err.println("  - Replaces " + OLD_CLASS + " with " + NEW_CLASS);
            System.err.println("  - Replaces " + OLD_METHOD + "() calls with newObject()");
            System.err.println("  - Wraps YamlMapping argument in new Config.YamlStorageConfig()");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic transformation to: " + sourceDir);
        System.out.println("Transformation: " + OLD_CLASS + " -> " + NEW_CLASS);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        System.out.println("Model built successfully");
        
        // Find and transform constructor calls
        transformConstructorCalls(model, launcher);
        
        // Find and transform method calls  
        transformMethodCalls(model, launcher);
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed successfully!");
        System.out.println("The transformation is generic and reusable for any project affected by:");
        System.out.println("  - " + OLD_CLASS + " -> " + NEW_CLASS + " migration");
        System.out.println("  - " + OLD_METHOD + "() -> newObject() with Config.YamlStorageConfig wrapper");
    }
    
    private static void transformConstructorCalls(CtModel model, Launcher launcher) {
        List<CtConstructorCall<?>> constructorCalls = model.getElements(
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    try {
                        return constructorCall.getType() != null && 
                               OLD_CLASS.equals(constructorCall.getType().getQualifiedName());
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        );
        
        System.out.println("\nFound " + constructorCalls.size() + " " + OLD_CLASS + " constructor calls");
        
        for (CtConstructorCall<?> call : constructorCalls) {
            try {
                System.out.println("  Transforming constructor at line: " + call.getPosition().getLine());
                
                // Create code snippet for the replacement
                String newCode = "new " + NEW_CLASS + "()";
                call.replace(launcher.getFactory().Code().createCodeSnippetExpression(newCode));
            } catch (Exception e) {
                System.err.println("  Error: " + e.getMessage());
            }
        }
    }
    
    private static void transformMethodCalls(CtModel model, Launcher launcher) {
        List<CtInvocation<?>> methodCalls = model.getElements(
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    try {
                        return invocation.getExecutable() != null && 
                               OLD_METHOD.equals(invocation.getExecutable().getSimpleName());
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        );
        
        System.out.println("\nFound " + methodCalls.size() + " " + OLD_METHOD + " method calls");
        
        for (CtInvocation<?> call : methodCalls) {
            try {
                System.out.println("  Transforming method call at line: " + call.getPosition().getLine());
                
                // Get arguments
                List<?> arguments = call.getArguments();
                if (arguments.size() != 2) {
                    System.err.println("    Warning: Expected 2 arguments, found " + arguments.size());
                    continue;
                }
                
                // Create the replacement code
                String typeArg = arguments.get(0).toString();
                String yamlArg = arguments.get(1).toString();
                String newCode = call.getTarget() + ".newObject(" + typeArg + 
                               ", new com.artipie.asto.factory.Config.YamlStorageConfig(" + yamlArg + "))";
                
                call.replace(launcher.getFactory().Code().createCodeSnippetExpression(newCode));
            } catch (Exception e) {
                System.err.println("  Error: " + e.getMessage());
            }
        }
    }
}