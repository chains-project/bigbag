package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to Flyway
        List<CtConstructorCall> flywayCtors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                try {
                    return element.getType() != null && 
                           element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                } catch (Exception e) {
                    return false;
                }
            }
        });
        
        System.out.println("Found " + flywayCtors.size() + " Flyway constructor calls");
        
        for (CtConstructorCall ctorCall : flywayCtors) {
            transformFlywayInstantiation(ctorCall);
        }
        
        // Save transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFlywayInstantiation(CtConstructorCall flywayCtor) {
        System.out.println("Transforming Flyway instantiation at: " + 
                          flywayCtor.getPosition().getLine());
        
        // Get the parent statement (usually a variable assignment or declaration)
        CtStatement parentStatement = flywayCtor.getParent(CtStatement.class);
        if (parentStatement == null) {
            System.out.println("  Warning: Could not find parent statement");
            return;
        }
        
        // Check if this is part of a variable declaration
        CtVariable variable = flywayCtor.getParent(CtVariable.class);
        String varName = null;
        if (variable != null) {
            varName = variable.getSimpleName();
        }
        
        // Find all method calls on this Flyway instance that follow the constructor
        List<CtInvocation> methodCalls = findSetterCalls(flywayCtor, varName);
        
        if (methodCalls.isEmpty()) {
            System.out.println("  No setter calls found, using minimal transformation");
            // Simple case: just new Flyway() -> Flyway.configure().load()
            transformSimpleCase(flywayCtor);
        } else {
            System.out.println("  Found " + methodCalls.size() + " setter calls");
            transformWithSetters(flywayCtor, methodCalls);
        }
    }
    
    private static List<CtInvocation> findSetterCalls(CtConstructorCall ctorCall, String varName) {
        List<CtInvocation> setterCalls = new ArrayList<>();
        
        // Get the enclosing block
        CtBlock block = ctorCall.getParent(CtBlock.class);
        if (block == null || varName == null) {
            return setterCalls;
        }
        
        // Find the index of the constructor call
        int ctorIndex = -1;
        List<CtStatement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).equals(ctorCall.getParent(CtStatement.class))) {
                ctorIndex = i;
                break;
            }
        }
        
        if (ctorIndex == -1) {
            return setterCalls;
        }
        
        // Look for setter calls on the same variable in subsequent statements
        for (int i = ctorIndex + 1; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            
            // Check if this statement contains an invocation
            List<CtInvocation> invocations = stmt.getElements(new TypeFilter<CtInvocation>(CtInvocation.class));
            for (CtInvocation invoc : invocations) {
                // Check if it's a method call on our variable
                CtExpression target = invoc.getTarget();
                if (target instanceof CtVariableAccess) {
                    CtVariableAccess varAccess = (CtVariableAccess) target;
                    if (varName.equals(varAccess.getVariable().getSimpleName())) {
                        String methodName = invoc.getExecutable().getSimpleName();
                        if (methodName.startsWith("set")) {
                            setterCalls.add(invoc);
                        }
                    }
                }
            }
            
            // Stop if we find a statement that doesn't use our variable
            // (or if we find a return/break/continue)
            if (!stmt.toString().contains(varName)) {
                break;
            }
        }
        
        return setterCalls;
    }
    
    private static void transformSimpleCase(CtConstructorCall ctorCall) {
        // Replace: new Flyway() -> Flyway.configure().load()
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        
        // Create: Flyway.configure()
        CtInvocation configureCall = launcher.getFactory().createInvocation(
            launcher.getFactory().createTypeAccess(launcher.getFactory().Type().get("org.flywaydb.core.Flyway").getReference()),
            launcher.getFactory().createExecutableReference().setSimpleName("configure")
        );
        
        // Create: .load()
        CtInvocation loadCall = launcher.getFactory().createInvocation(
            configureCall,
            launcher.getFactory().createExecutableReference().setSimpleName("load")
        );
        
        // Replace the constructor call with the chain
        ctorCall.replace(loadCall);
    }
    
    private static void transformWithSetters(CtConstructorCall ctorCall, List<CtInvocation> setterCalls) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        
        // Start with: Flyway.configure()
        CtExpression currentExpr = launcher.getFactory().createInvocation(
            launcher.getFactory().createTypeAccess(launcher.getFactory().Type().get("org.flywaydb.core.Flyway").getReference()),
            launcher.getFactory().createExecutableReference().setSimpleName("configure")
        );
        
        // Process each setter call
        for (CtInvocation setterCall : setterCalls) {
            String setterName = setterCall.getExecutable().getSimpleName();
            List<CtExpression> args = setterCall.getArguments();
            
            // Map setter name to fluent method name
            String fluentMethodName = mapSetterToFluentMethod(setterName);
            
            if (fluentMethodName != null && !args.isEmpty()) {
                // Create the fluent method call
                CtInvocation fluentCall = launcher.getFactory().createInvocation(
                    currentExpr,
                    launcher.getFactory().createExecutableReference().setSimpleName(fluentMethodName),
                    args.get(0) // Use the first argument
                );
                
                currentExpr = fluentCall;
                
                // Remove the setter call statement
                CtStatement setterStatement = setterCall.getParent(CtStatement.class);
                if (setterStatement != null) {
                    setterStatement.delete();
                }
            }
        }
        
        // Add .load() at the end
        CtInvocation loadCall = launcher.getFactory().createInvocation(
            currentExpr,
            launcher.getFactory().createExecutableReference().setSimpleName("load")
        );
        
        // Replace the constructor call with the chain
        ctorCall.replace(loadCall);
    }
    
    private static String mapSetterToFluentMethod(String setterName) {
        switch (setterName) {
            case "setDataSource":
                return "dataSource";
            case "setClassLoader":
                return "classLoader";
            case "setLocations":
                return "locations";
            case "setValidateOnMigrate":
                return "validateOnMigrate";
            default:
                System.out.println("  Warning: Unknown setter method: " + setterName);
                return null;
        }
    }
}