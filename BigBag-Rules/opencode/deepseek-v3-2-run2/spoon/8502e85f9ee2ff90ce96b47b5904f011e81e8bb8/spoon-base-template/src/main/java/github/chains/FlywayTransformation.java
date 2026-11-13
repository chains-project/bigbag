package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.*;

/**
 * Generic Spoon transformation for Flyway API migration from version <9.x to 9.20.1+
 * 
 * Transformation pattern:
 * Old: new Flyway() -> setters -> return flyway
 * New: Flyway.configure() -> fluent configuration -> .load()
 * 
 * This transformation handles:
 * 1. Direct constructor calls: new Flyway()
 * 2. Method chains: new Flyway().setX().setY()
 * 3. Variable assignment with separate setters
 */
public class FlywayTransformation {
    
    // Map old setter methods to new fluent API methods
    // Note: setClassLoader is special - it becomes an argument to configure()
    private static final Map<String, String> METHOD_MAPPINGS = createMethodMappings();
    
    private static Map<String, String> createMethodMappings() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("setDataSource", "dataSource");
        mappings.put("setLocations", "locations");
        mappings.put("setValidateOnMigrate", "validateOnMigrate");
        mappings.put("setBaselineOnMigrate", "baselineOnMigrate");
        mappings.put("setBaselineVersion", "baselineVersion");
        mappings.put("setEncoding", "encoding");
        mappings.put("setSchemas", "schemas");
        mappings.put("setTable", "table");
        mappings.put("setTarget", "target");
        mappings.put("setPlaceholders", "placeholders");
        mappings.put("setOutOfOrder", "outOfOrder");
        mappings.put("setCleanOnValidationError", "cleanOnValidationError");
        mappings.put("setCleanDisabled", "cleanDisabled");
        return mappings;
    }
    
    public static void transformProject(String sourceDir) {
        System.out.println("Transforming Flyway API in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        launcher.addProcessor(new FlywayProcessor());
        
        try {
            launcher.run();
            System.out.println("Flyway API transformation completed!");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class FlywayProcessor extends AbstractProcessor<CtNewClass<?>> {
        
        @Override
        public void process(CtNewClass<?> constructor) {
            if (!isFlywayConstructor(constructor)) {
                return;
            }
            
            System.out.println("Processing Flyway constructor at: " + constructor.getPosition());
            
            try {
                // Try to transform this constructor call
                transformConstructor(constructor);
            } catch (Exception e) {
                System.err.println("Failed to transform constructor at " + constructor.getPosition() + ": " + e.getMessage());
            }
        }
        
        private boolean isFlywayConstructor(CtNewClass<?> constructor) {
            CtTypeReference<?> typeRef = constructor.getType();
            return typeRef != null && "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName());
        }
        
        private void transformConstructor(CtNewClass<?> constructor) {
            // Find all related method calls
            List<CtInvocation<?>> methodCalls = findRelatedMethodCalls(constructor);
            
            // Build replacement code
            String replacementCode = buildReplacementCode(methodCalls);
            
            // Apply replacement
            applyReplacement(constructor, methodCalls, replacementCode);
        }
        
        private List<CtInvocation<?>> findRelatedMethodCalls(CtNewClass<?> constructor) {
            List<CtInvocation<?>> methodCalls = new ArrayList<>();
            
            // Check for method chain starting from constructor
            CtElement parent = constructor.getParent();
            CtInvocation<?> currentInvocation = null;
            
            if (parent instanceof CtInvocation) {
                currentInvocation = (CtInvocation<?>) parent;
                while (currentInvocation != null) {
                    CtExpression<?> target = currentInvocation.getTarget();
                    if (target == constructor || 
                        (methodCalls.size() > 0 && target == methodCalls.get(methodCalls.size() - 1))) {
                        methodCalls.add(currentInvocation);
                        CtElement nextParent = currentInvocation.getParent();
                        currentInvocation = (nextParent instanceof CtInvocation) ? (CtInvocation<?>) nextParent : null;
                    } else {
                        break;
                    }
                }
            }
            
            // If no chain, look for variable declaration with subsequent setters
            if (methodCalls.isEmpty()) {
                methodCalls.addAll(findSetterCallsOnVariable(constructor));
            }
            
            return methodCalls;
        }
        
        private List<CtInvocation<?>> findSetterCallsOnVariable(CtNewClass<?> constructor) {
            List<CtInvocation<?>> methodCalls = new ArrayList<>();
            
            CtVariable<?> variable = constructor.getParent(CtVariable.class);
            if (variable == null) {
                return methodCalls;
            }
            
            CtVariableReference<?> varRef = variable.getReference();
            CtBlock<?> block = constructor.getParent(CtBlock.class);
            if (block == null) {
                return methodCalls;
            }
            
            boolean foundConstructor = false;
            for (CtStatement stmt : block.getStatements()) {
                if (stmt.equals(constructor.getParent(CtStatement.class))) {
                    foundConstructor = true;
                    continue;
                }
                
                if (!foundConstructor) {
                    continue;
                }
                
                // Look for method calls on our variable
                List<CtInvocation<?>> invocations = stmt.getElements(new TypeFilter<>(CtInvocation.class));
                for (CtInvocation<?> invocation : invocations) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target instanceof CtVariableRead) {
                        CtVariableRead<?> varRead = (CtVariableRead<?>) target;
                        if (varRef.equals(varRead.getVariable())) {
                            methodCalls.add(invocation);
                        }
                    }
                }
                
                // Stop at return statement
                if (stmt instanceof CtReturn) {
                    break;
                }
            }
            
            return methodCalls;
        }
        
        private String buildReplacementCode(List<CtInvocation<?>> methodCalls) {
            // Handle setClassLoader specially - it becomes an argument to configure()
            String classLoaderArg = null;
            List<CtInvocation<?>> otherMethodCalls = new ArrayList<>();
            
            for (CtInvocation<?> methodCall : methodCalls) {
                String oldMethodName = methodCall.getExecutable().getSimpleName();
                if ("setClassLoader".equals(oldMethodName) && !methodCall.getArguments().isEmpty()) {
                    classLoaderArg = methodCall.getArguments().get(0).toString();
                } else {
                    otherMethodCalls.add(methodCall);
                }
            }
            
            StringBuilder sb = new StringBuilder("Flyway.configure(");
            if (classLoaderArg != null) {
                sb.append(classLoaderArg);
            }
            sb.append(")");
            
            for (CtInvocation<?> methodCall : otherMethodCalls) {
                String oldMethodName = methodCall.getExecutable().getSimpleName();
                String newMethodName = METHOD_MAPPINGS.get(oldMethodName);
                
                if (newMethodName != null) {
                    sb.append(".").append(newMethodName).append("(");
                    List<CtExpression<?>> args = methodCall.getArguments();
                    for (int i = 0; i < args.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(args.get(i).toString());
                    }
                    sb.append(")");
                }
            }
            
            sb.append(".load()");
            return sb.toString();
        }
        
        private void applyReplacement(CtNewClass<?> constructor, List<CtInvocation<?>> methodCalls, String replacementCode) {
            // Create replacement expression
            CtCodeSnippetExpression replacement = getFactory().Code().createCodeSnippetExpression(replacementCode);
            
            // Replace the constructor with our new expression
            CtElement parent = constructor.getParent();
            
            if (parent instanceof CtVariable) {
                ((CtVariable<?>) parent).setDefaultExpression(replacement);
            } else if (parent instanceof CtReturn) {
                ((CtReturn<?>) parent).setReturnedExpression(replacement);
            } else if (parent instanceof CtAssignment) {
                ((CtAssignment<?, ?>) parent).setAssignment(replacement);
            } else if (parent instanceof CtInvocation && !methodCalls.isEmpty()) {
                // Replace the last invocation in the chain
                CtInvocation<?> lastInvocation = methodCalls.get(methodCalls.size() - 1);
                CtElement lastParent = lastInvocation.getParent();
                
                if (lastParent instanceof CtVariable) {
                    ((CtVariable<?>) lastParent).setDefaultExpression(replacement);
                } else if (lastParent instanceof CtReturn) {
                    ((CtReturn<?>) lastParent).setReturnedExpression(replacement);
                } else if (lastParent instanceof CtInvocation) {
                    // This shouldn't happen in a well-formed chain
                    System.err.println("Unexpected invocation parent structure");
                }
            }
            
            // Delete old method calls
            for (CtInvocation<?> methodCall : methodCalls) {
                if (methodCall.getParent() != null) {
                    methodCall.delete();
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtNewClass<?> candidate) {
            return isFlywayConstructor(candidate);
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java FlywayTransformation <source-directory>");
            System.err.println("Example: java FlywayTransformation /workspace/nem/nis/src/main/java");
            System.exit(1);
        }
        
        transformProject(args[0]);
    }
}