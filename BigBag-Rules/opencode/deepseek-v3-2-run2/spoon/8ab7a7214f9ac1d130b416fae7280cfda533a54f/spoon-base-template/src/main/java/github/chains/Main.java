package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;

import java.util.*;

/**
 * Generic Spoon transformation for the breaking change:
 * com.gargoylesoftware.htmlunit.ScriptResult class removal in acceptance-test-harness.
 * 
 * Old API: new ScriptResult(Object).getJavaScriptResult() -> Object
 * New API: executeScript returns Object directly
 * 
 * This transformation:
 * 1. Removes ScriptResult imports
 * 2. Replaces ScriptResult constructor calls with their argument
 * 3. Removes getJavaScriptResult() method calls
 * 4. Updates ScriptResult type references to Object
 */
public class Main {
    private static final String SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-directory>");
            System.err.println("Applies transformation for ScriptResult removal to all Java files in directory");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("=== ScriptResult Removal Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Breaking change: " + SCRIPT_RESULT_TYPE + " class removed");
        System.out.println("Transformation: new ScriptResult(x).getJavaScriptResult() -> x");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setCopyResources(false);
        
        CtModel model = launcher.buildModel();
        spoon.reflect.factory.Factory factory = launcher.getFactory();
        
        int importCount = removeScriptResultImports(factory);
        int ctorCount = replaceScriptResultConstructors(factory);
        int methodCount = removeGetJavaScriptResultCalls(factory);
        int typeCount = updateScriptResultTypeReferences(factory);
        
        System.out.println("\n=== Transformation Complete ===");
        System.out.println("Transformations applied:");
        System.out.println("  - Removed imports: " + importCount);
        System.out.println("  - Replaced constructors: " + ctorCount);
        System.out.println("  - Removed getJavaScriptResult() calls: " + methodCount);
        System.out.println("  - Updated type references: " + typeCount);
        
        if (importCount + ctorCount + methodCount + typeCount > 0) {
            System.out.println("\nWriting transformed code...");
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Code written successfully.");
        } else {
            System.out.println("\nNo transformations needed - code is already compatible.");
        }
    }
    
    private static int removeScriptResultImports(spoon.reflect.factory.Factory factory) {
        List<CtCompilationUnit> compilationUnits = Query.getElements(factory, 
            new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        
        int count = 0;
        for (CtCompilationUnit cu : compilationUnits) {
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            List<CtImport> filteredImports = new ArrayList<>();
            
            for (CtImport imp : imports) {
                if (!isScriptResultImport(imp)) {
                    filteredImports.add(imp);
                } else {
                    count++;
                }
            }
            
            if (filteredImports.size() != imports.size()) {
                cu.setImports(filteredImports);
            }
        }
        return count;
    }
    
    private static boolean isScriptResultImport(CtImport imp) {
        if (imp.toString().startsWith("import static")) {
            return false;
        }
        CtReference ref = imp.getReference();
        return ref != null && ref.toString().contains(SCRIPT_RESULT_TYPE);
    }
    
    private static int replaceScriptResultConstructors(spoon.reflect.factory.Factory factory) {
        List<CtConstructorCall> constructors = Query.getElements(factory,
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    CtTypeReference<?> typeRef = element.getType();
                    return typeRef != null && SCRIPT_RESULT_TYPE.equals(typeRef.getQualifiedName());
                }
            });
        
        for (CtConstructorCall ctor : constructors) {
            List<CtExpression<?>> args = ctor.getArguments();
            if (!args.isEmpty()) {
                CtExpression<?> arg = args.get(0);
                ctor.replace(arg);
            }
        }
        return constructors.size();
    }
    
    private static int removeGetJavaScriptResultCalls(spoon.reflect.factory.Factory factory) {
        List<CtInvocation<?>> invocations = Query.getElements(factory,
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    CtExecutableReference<?> execRef = element.getExecutable();
                    return execRef != null && "getJavaScriptResult".equals(execRef.getSimpleName());
                }
            });
        
        for (CtInvocation<?> inv : invocations) {
            CtExpression<?> target = inv.getTarget();
            if (target != null) {
                inv.replace(target);
            }
        }
        return invocations.size();
    }
    
    private static int updateScriptResultTypeReferences(spoon.reflect.factory.Factory factory) {
        int count = 0;
        
        // Update variable declarations
        List<CtVariable<?>> variables = Query.getElements(factory,
            new TypeFilter<CtVariable<?>>(CtVariable.class) {
                @Override
                public boolean matches(CtVariable<?> element) {
                    return isScriptResultType(element.getType());
                }
            });
        
        for (CtVariable<?> var : variables) {
            var.setType(factory.Type().createReference(Object.class));
            count++;
        }
        
        // Update method return types
        List<CtMethod<?>> methods = Query.getElements(factory,
            new TypeFilter<CtMethod<?>>(CtMethod.class) {
                @Override
                public boolean matches(CtMethod<?> element) {
                    return isScriptResultType(element.getType());
                }
            });
        
        for (CtMethod<?> method : methods) {
            method.setType(factory.Type().createReference(Object.class));
            count++;
        }
        
        // Update parameters
        List<CtParameter<?>> params = Query.getElements(factory,
            new TypeFilter<CtParameter<?>>(CtParameter.class) {
                @Override
                public boolean matches(CtParameter<?> element) {
                    return isScriptResultType(element.getType());
                }
            });
        
        for (CtParameter<?> param : params) {
            param.setType(factory.Type().createReference(Object.class));
            count++;
        }
        
        // Update fields
        List<CtField<?>> fields = Query.getElements(factory,
            new TypeFilter<CtField<?>>(CtField.class) {
                @Override
                public boolean matches(CtField<?> element) {
                    return isScriptResultType(element.getType());
                }
            });
        
        for (CtField<?> field : fields) {
            field.setType(factory.Type().createReference(Object.class));
            count++;
        }
        
        return count;
    }
    
    private static boolean isScriptResultType(CtTypeReference<?> typeRef) {
        return typeRef != null && SCRIPT_RESULT_TYPE.equals(typeRef.getQualifiedName());
    }
}