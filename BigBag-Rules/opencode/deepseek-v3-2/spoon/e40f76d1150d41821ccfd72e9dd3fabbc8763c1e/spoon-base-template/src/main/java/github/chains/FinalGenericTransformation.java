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
import java.util.List;
import java.util.ArrayList;

/**
 * FINAL GENERIC TRANSFORMATION for ScriptResult API migration
 * 
 * This transformation handles the breaking change from:
 *   Old: com.gargoylesoftware.htmlunit.ScriptResult with constructor ScriptResult(Object)
 *   New: org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult with constructor ScriptResult(String)
 * 
 * Transformation rules:
 * 1. Change imports from old to new package
 * 2. Update constructor calls: new ScriptResult(arg) -> new ScriptResult(arg.toString())
 * 
 * The transformation is GENERIC and can be applied to ANY project by:
 * 1. Changing the OLD_TYPE_NAME and NEW_TYPE_NAME constants
 * 2. Adjusting the constructor transformation logic if needed
 * 
 * Note: Additional API differences (like missing getJavaScriptResult() method)
 * may require additional transformations not included here.
 */
public class FinalGenericTransformation {
    
    // CONFIGURATION - Parameterize these for different breaking changes
    private static final String OLD_TYPE_NAME = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_TYPE_NAME = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String TYPE_SIMPLE_NAME = "ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Generic API Migration Transformation ===");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println("Migration: " + OLD_TYPE_NAME + " -> " + NEW_TYPE_NAME);
        System.out.println("Change: Constructor(Object) -> Constructor(String)");
        System.out.println("=============================================");
        
        try {
            // Configure Spoon
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(false);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Build model and apply transformations
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            int transformedFiles = applyTransformations(model, factory);
            
            // Generate output
            launcher.prettyprint();
            
            System.out.println("✅ Transformation complete!");
            System.out.println("📁 Transformed " + transformedFiles + " file(s)");
            System.out.println("💾 Output written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("❌ Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: java FinalGenericTransformation <sourceDirectory> <outputDirectory>");
        System.err.println();
        System.err.println("This transformation fixes breaking API changes for:");
        System.err.println("  " + OLD_TYPE_NAME + " -> " + NEW_TYPE_NAME);
        System.err.println();
        System.err.println("Transformations applied:");
        System.err.println("  1. Update imports from old to new package");
        System.err.println("  2. Convert constructor arguments: arg -> arg.toString()");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java FinalGenericTransformation /path/to/src /path/to/transformed");
    }
    
    private static int applyTransformations(CtModel model, Factory factory) {
        int transformedCount = 0;
        List<CtCompilationUnit> allUnits = model.getRootPackage().getElements(new TypeFilter<>(CtCompilationUnit.class));
        
        System.out.println("🔍 Scanning " + allUnits.size() + " compilation unit(s)...");
        
        for (CtCompilationUnit cu : allUnits) {
            if (transformCompilationUnit(cu, factory)) {
                transformedCount++;
            }
        }
        
        return transformedCount;
    }
    
    private static boolean transformCompilationUnit(CtCompilationUnit cu, Factory factory) {
        boolean hasOldImport = false;
        boolean hasConstructorCalls = false;
        
        // Check for old imports
        for (CtImport imp : cu.getImports()) {
            if (imp.toString().contains(OLD_TYPE_NAME)) {
                hasOldImport = true;
                break;
            }
        }
        
        // Check for constructor calls (even without imports - could be fully qualified)
        List<CtConstructorCall<?>> constructorCalls = findScriptResultConstructorCalls(cu);
        hasConstructorCalls = !constructorCalls.isEmpty();
        
        if (!hasOldImport && !hasConstructorCalls) {
            return false; // Nothing to transform
        }
        
        String filename = cu.getFile() != null ? cu.getFile().getName() : "unknown";
        System.out.println("🔄 Transforming: " + filename);
        
        if (hasOldImport) {
            updateImports(cu, factory);
        }
        
        if (hasConstructorCalls) {
            System.out.println("   Found " + constructorCalls.size() + " constructor call(s) to transform");
            transformConstructorCalls(constructorCalls, factory);
        }
        
        return true;
    }
    
    private static List<CtConstructorCall<?>> findScriptResultConstructorCalls(CtCompilationUnit cu) {
        return cu.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                // Match by simple name since type resolution might fail
                String simpleName = element.getType() != null ? element.getType().getSimpleName() : "";
                return TYPE_SIMPLE_NAME.equals(simpleName);
            }
        });
    }
    
    private static void updateImports(CtCompilationUnit cu, Factory factory) {
        List<CtImport> toRemove = new ArrayList<>();
        
        // Remove old imports
        for (CtImport imp : cu.getImports()) {
            if (imp.toString().contains(OLD_TYPE_NAME)) {
                toRemove.add(imp);
                System.out.println("   📤 Removing import: " + imp);
            }
        }
        
        for (CtImport imp : toRemove) {
            cu.getImports().remove(imp);
        }
        
        // Add new import
        if (!toRemove.isEmpty()) {
            CtTypeReference<?> newTypeRef = factory.createReference(NEW_TYPE_NAME);
            CtImport newImport = factory.createImport(newTypeRef);
            cu.getImports().add(newImport);
            System.out.println("   📥 Adding import: " + newImport);
        }
    }
    
    private static void transformConstructorCalls(List<CtConstructorCall<?>> constructorCalls, Factory factory) {
        for (CtConstructorCall<?> call : constructorCalls) {
            transformConstructorCall(call, factory);
        }
    }
    
    private static void transformConstructorCall(CtConstructorCall<?> call, Factory factory) {
        // Update type reference to new type
        try {
            CtTypeReference<?> newTypeRef = factory.createReference(NEW_TYPE_NAME);
            call.setType(newTypeRef);
        } catch (Exception e) {
            System.err.println("   ⚠️ Could not update type reference: " + e.getMessage());
        }
        
        // Transform arguments: add .toString() if not already present
        if (call.getArguments().size() == 1) {
            try {
                var arg = call.getArguments().get(0);
                if (!isToStringCall(arg)) {
                    var toStringCall = createToStringInvocation(arg, factory);
                    call.getArguments().set(0, toStringCall);
                    System.out.println("   🔧 Added .toString() to constructor argument");
                }
            } catch (Exception e) {
                System.err.println("   ⚠️ Error transforming argument: " + e.getMessage());
            }
        }
    }
    
    private static CtInvocation<?> createToStringInvocation(Object expression, Factory factory) {
        return factory.createInvocation(
            factory.createCodeSnippetExpression(expression.toString()),
            factory.createExecutableReference()
                .setDeclaringType(factory.createReference("java.lang.Object"))
                .setSimpleName("toString")
        );
    }
    
    private static boolean isToStringCall(Object expression) {
        if (expression instanceof CtInvocation) {
            CtInvocation<?> inv = (CtInvocation<?>) expression;
            return "toString".equals(inv.getExecutable().getSimpleName());
        }
        return false;
    }
}