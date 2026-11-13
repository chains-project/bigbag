package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation for migrating from com.gargoylesoftware.htmlunit.ScriptResult
 * to org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
 * 
 * This transformation handles:
 * 1. Changing imports from old to new package
 * 2. Updating constructor calls to use .toString() on arguments
 * 
 * The transformation is parameterized by:
 * - Old fully-qualified type name: "com.gargoylesoftware.htmlunit.ScriptResult"
 * - New fully-qualified type name: "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult"
 * - Constructor signature change: Object parameter -> String parameter
 */
public class GenericScriptResultTransformation {
    
    // Configuration parameters - these would ideally be passed as arguments
    private static final String OLD_TYPE_NAME = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_TYPE_NAME = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String TYPE_SIMPLE_NAME = "ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java GenericScriptResultTransformation <sourceDirectory> <outputDirectory>");
            System.err.println("Example: java GenericScriptResultTransformation /path/to/project /path/to/transformed");
            System.err.println();
            System.err.println("Transformation parameters (hardcoded):");
            System.err.println("  Old type: " + OLD_TYPE_NAME);
            System.err.println("  New type: " + NEW_TYPE_NAME);
            System.err.println("  Change: Constructor(Object) -> Constructor(String)");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args[1];
        
        System.out.println("Starting generic ScriptResult API migration transformation");
        System.out.println("Source directory: " + sourceDirectory);
        System.out.println("Output directory: " + outputDirectory);
        System.out.println("Transforming: " + OLD_TYPE_NAME + " -> " + NEW_TYPE_NAME);
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(outputDirectory);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Apply transformations
            transformCompilationUnits(model, factory);
            
            // Generate transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformCompilationUnits(CtModel model, Factory factory) {
        System.out.println("Scanning compilation units...");
        
        List<CtCompilationUnit> compilationUnits = model.getRootPackage().getElements(new TypeFilter<>(CtCompilationUnit.class));
        System.out.println("Found " + compilationUnits.size() + " compilation units");
        
        for (CtCompilationUnit cu : compilationUnits) {
            transformCompilationUnit(cu, factory);
        }
    }
    
    private static void transformCompilationUnit(CtCompilationUnit cu, Factory factory) {
        boolean transformed = false;
        
        // Check imports for old type
        for (CtImport imp : cu.getImports()) {
            if (imp.toString().contains(OLD_TYPE_NAME)) {
                System.out.println("Found " + OLD_TYPE_NAME + " import in " + cu.getFile().getName());
                transformed = true;
                break;
            }
        }
        
        // Also check if type is used without import (fully qualified)
        String cuContent = cu.toString();
        if (cuContent.contains(OLD_TYPE_NAME) || cuContent.contains(TYPE_SIMPLE_NAME + "(")) {
            transformed = true;
        }
        
        if (transformed) {
            System.out.println("Transforming compilation unit: " + cu.getFile().getName());
            updateImports(cu, factory);
            updateConstructorCalls(cu, factory);
        }
    }
    
    private static void updateImports(CtCompilationUnit cu, Factory factory) {
        List<CtImport> importsToRemove = new ArrayList<>();
        boolean needsNewImport = false;
        
        for (CtImport imp : cu.getImports()) {
            String importStr = imp.toString();
            if (importStr.contains(OLD_TYPE_NAME)) {
                System.out.println("  Removing import: " + importStr);
                importsToRemove.add(imp);
                needsNewImport = true;
            }
        }
        
        // Remove old imports
        for (CtImport imp : importsToRemove) {
            cu.getImports().remove(imp);
        }
        
        // Add new import
        if (needsNewImport) {
            CtTypeReference<?> newTypeRef = factory.createReference(NEW_TYPE_NAME);
            CtImport newImport = factory.createImport(newTypeRef);
            cu.getImports().add(newImport);
            System.out.println("  Adding import: " + newImport);
        }
    }
    
    private static void updateConstructorCalls(CtCompilationUnit cu, Factory factory) {
        // Find all constructor calls to ScriptResult
        List<CtConstructorCall<?>> constructorCalls = cu.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                try {
                    if (element.getType() != null) {
                        String typeName = element.getType().getQualifiedName();
                        return OLD_TYPE_NAME.equals(typeName) || 
                               NEW_TYPE_NAME.equals(typeName) ||
                               TYPE_SIMPLE_NAME.equals(element.getType().getSimpleName());
                    }
                } catch (Exception e) {
                    // Type reference might not be resolvable
                    String simpleName = element.getType() != null ? element.getType().getSimpleName() : "";
                    return TYPE_SIMPLE_NAME.equals(simpleName);
                }
                return false;
            }
        });
        
        System.out.println("  Found " + constructorCalls.size() + " ScriptResult constructor calls");
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            transformConstructorCall(constructorCall, factory);
        }
    }
    
    private static void transformConstructorCall(CtConstructorCall<?> constructorCall, Factory factory) {
        System.out.println("    Transforming constructor call at line ~" + constructorCall.getPosition().getLine());
        
        // Update the type reference to new type
        try {
            CtTypeReference<?> newTypeRef = factory.createReference(NEW_TYPE_NAME);
            constructorCall.setType(newTypeRef);
        } catch (Exception e) {
            System.err.println("    Could not update type reference: " + e.getMessage());
        }
        
        // Add .toString() to arguments if needed
        if (constructorCall.getArguments().size() == 1) {
            try {
                var argument = constructorCall.getArguments().get(0);
                
                // Check if argument is already a .toString() call
                if (!isToStringCall(argument)) {
                    // Create .toString() invocation
                    var toStringCall = factory.createInvocation(
                        argument.clone(),
                        factory.createExecutableReference()
                            .setDeclaringType(factory.createReference("java.lang.Object"))
                            .setSimpleName("toString")
                    );
                    
                    // Replace argument with .toString() call
                    constructorCall.getArguments().set(0, toStringCall);
                    System.out.println("    Added .toString() to argument");
                }
            } catch (Exception e) {
                System.err.println("    Error transforming argument: " + e.getMessage());
            }
        }
    }
    
    private static boolean isToStringCall(Object expression) {
        if (expression instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            return "toString".equals(invocation.getExecutable().getSimpleName());
        }
        return false;
    }
}