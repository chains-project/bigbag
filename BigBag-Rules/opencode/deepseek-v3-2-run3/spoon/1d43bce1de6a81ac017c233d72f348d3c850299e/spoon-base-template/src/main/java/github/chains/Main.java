package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtReturn;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.declaration.CtCodeSnippet;
import spoon.reflect.cu.SourcePosition;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all Flyway constructor calls
        List<CtConstructorCall> flywayConstructors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                CtTypeReference typeRef = element.getType();
                return typeRef != null && 
                       "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName());
            }
        });
        
        System.out.println("Found " + flywayConstructors.size() + " Flyway constructor calls");
        
        for (CtConstructorCall constructorCall : flywayConstructors) {
            transformFlywayInstantiation(constructorCall, factory);
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory("./transformed-output");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to ./transformed-output");
    }
    
    private static void transformFlywayInstantiation(CtConstructorCall constructorCall, Factory factory) {
        // Get the parent statement (could be variable declaration, assignment, or return)
        CtStatement parentStatement = constructorCall.getParent(CtStatement.class);
        if (parentStatement == null) return;
        
        // Get the block containing this statement
        CtBlock<?> parentBlock = parentStatement.getParent(CtBlock.class);
        if (parentBlock == null) return;
        
        // Find the variable name if this is a variable declaration or assignment
        String variableName = findVariableName(parentStatement);
        
        // Create the new fluent API expression: Flyway.configure()
        CtCodeSnippetExpression configureSnippet = factory.createCodeSnippetExpression("Flyway.configure()");
        configureSnippet.setType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
        
        // We'll need to collect all setter calls that follow this constructor
        // and convert them to fluent method calls
        List<CtInvocation> setterCalls = findSubsequentSetterCalls(parentStatement, parentBlock, variableName);
        
        // Build the fluent chain starting with configure()
        CtExpression fluentChain = configureSnippet;
        
        for (CtInvocation setterCall : setterCalls) {
            String methodName = setterCall.getExecutable().getSimpleName();
            List<CtExpression> arguments = setterCall.getArguments();
            
            // Convert setter name from setXyz to xyz (camelCase)
            String fluentMethodName = convertSetterToFluentMethod(methodName);
            
            // Create the fluent method call
            CtInvocation fluentCall = factory.createInvocation();
            fluentCall.setTarget(fluentChain);
            
            CtExecutableReference methodRef = factory.createExecutableReference();
            methodRef.setSimpleName(fluentMethodName);
            // We don't know the exact parameter types, but Spoon will handle it
            fluentCall.setExecutable(methodRef);
            
            // Copy arguments
            if (arguments != null && !arguments.isEmpty()) {
                fluentCall.setArguments(new ArrayList<>(arguments));
            }
            
            fluentChain = fluentCall;
        }
        
        // Add .load() at the end
        CtInvocation loadCall = factory.createInvocation();
        loadCall.setTarget(fluentChain);
        
        CtExecutableReference loadRef = factory.createExecutableReference();
        loadRef.setSimpleName("load");
        loadCall.setExecutable(loadRef);
        
        // Replace the original constructor call with the new fluent chain
        if (parentStatement instanceof CtLocalVariable) {
            CtLocalVariable localVar = (CtLocalVariable) parentStatement;
            localVar.setAssignment(loadCall);
        } else if (parentStatement instanceof CtAssignment) {
            CtAssignment assignment = (CtAssignment) parentStatement;
            assignment.setAssignment(loadCall);
        } else if (parentStatement instanceof CtReturn) {
            CtReturn returnStmt = (CtReturn) parentStatement;
            returnStmt.setReturnedExpression(loadCall);
        } else {
            // Direct expression statement
            parentStatement.replace(loadCall);
        }
        
        // Now remove all the old setter call statements
        // We need to do this after building the chain because removing
        // statements changes indices
        if (variableName != null) {
            List<CtStatement> statements = parentBlock.getStatements();
            List<CtStatement> toRemove = new ArrayList<>();
            
            // Find statements to remove (setter calls on our variable)
            for (CtStatement stmt : statements) {
                // Check if this statement is an invocation on our variable
                List<CtInvocation> invocations = stmt.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation element) {
                        CtExpression target = element.getTarget();
                        if (target instanceof CtVariableRead) {
                            CtVariableRead varRead = (CtVariableRead) target;
                            return variableName.equals(varRead.getVariable().getSimpleName()) &&
                                   element.getExecutable().getSimpleName().startsWith("set");
                        }
                        return false;
                    }
                });
                
                if (!invocations.isEmpty()) {
                    toRemove.add(stmt);
                }
            }
            
            // Remove the statements
            for (CtStatement stmt : toRemove) {
                parentBlock.removeStatement(stmt);
            }
        }
    }
    
    private static String findVariableName(CtStatement statement) {
        if (statement instanceof CtLocalVariable) {
            return ((CtLocalVariable) statement).getSimpleName();
        } else if (statement instanceof CtAssignment) {
            CtAssignment assignment = (CtAssignment) statement;
            CtExpression assigned = assignment.getAssigned();
            if (assigned instanceof CtVariableWrite) {
                return ((CtVariableWrite) assigned).getVariable().getSimpleName();
            }
        }
        return null;
    }
    
    private static List<CtInvocation> findSubsequentSetterCalls(CtStatement startStatement, CtBlock<?> block, String variableName) {
        List<CtInvocation> setters = new ArrayList<>();
        
        if (variableName == null) return setters;
        
        List<CtStatement> statements = block.getStatements();
        int startIndex = statements.indexOf(startStatement);
        if (startIndex == -1) return setters;
        
        // Look for setter calls on the same variable in subsequent statements
        for (int i = startIndex + 1; i < statements.size(); i++) {
            CtStatement stmt = statements.get(i);
            
            // Check if this statement is an invocation
            List<CtInvocation> invocations = stmt.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    // Check if it's a method call on our variable
                    CtExpression target = element.getTarget();
                    if (target instanceof CtVariableRead) {
                        CtVariableRead varRead = (CtVariableRead) target;
                        return variableName.equals(varRead.getVariable().getSimpleName());
                    }
                    return false;
                }
            });
            
            for (CtInvocation invocation : invocations) {
                String methodName = invocation.getExecutable().getSimpleName();
                if (methodName.startsWith("set")) {
                    setters.add(invocation);
                }
            }
        }
        
        return setters;
    }
    
    private static String convertSetterToFluentMethod(String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return setterName;
        }
        
        // Convert setDataSource -> dataSource
        // Convert setValidateOnMigrate -> validateOnMigrate
        String baseName = setterName.substring(3);
        return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
    }
}