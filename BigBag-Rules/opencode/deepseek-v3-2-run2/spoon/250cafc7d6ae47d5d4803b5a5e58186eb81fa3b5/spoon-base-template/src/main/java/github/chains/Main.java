package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtReturn;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.processing.AbstractProcessor;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for migrating Flyway API from old pattern:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(...);
 *   flyway.setClassLoader(...);
 *   flyway.setLocations(...);
 *   flyway.setValidateOnMigrate(...);
 *   return flyway;
 * 
 * To new fluent API pattern:
 *   Flyway flyway = Flyway.configure()
 *       .dataSource(...)
 *       .locations(...)
 *       .validateOnMigrate(...)
 *       .load();
 *   return flyway;
 * 
 * This transformation is generic and can be applied to any project.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add processor
        launcher.addProcessor(new FlywayMigrationProcessor());
        
        try {
            CtModel model = launcher.buildModel();
            launcher.process();
            launcher.prettyprint();
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class FlywayMigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
            // Match: new Flyway()
            CtTypeReference<?> typeRef = constructorCall.getType();
            if (typeRef == null) return false;
            
            String typeName = typeRef.getQualifiedName();
            return "org.flywaydb.core.Flyway".equals(typeName);
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            System.out.println("Found Flyway constructor call at: " + constructorCall.getPosition());
            
            try {
                // Get the parent statement containing this constructor
                CtStatement parentStmt = constructorCall.getParent(CtStatement.class);
                if (parentStmt == null) {
                    System.out.println("Warning: Constructor not in a statement");
                    return;
                }
                
                // Get the containing block
                CtBlock<?> block = parentStmt.getParent(CtBlock.class);
                if (block == null) {
                    System.out.println("Warning: Cannot find containing block");
                    return;
                }
                
                // Get all statements in the block
                List<CtStatement> statements = new ArrayList<>(block.getStatements());
                int startIndex = statements.indexOf(parentStmt);
                if (startIndex == -1) {
                    System.out.println("Warning: Cannot find statement in block");
                    return;
                }
                
                // Collect the setter calls that follow
                List<CtInvocation<?>> setterCalls = new ArrayList<>();
                List<CtStatement> statementsToRemove = new ArrayList<>();
                
                for (int i = startIndex + 1; i < statements.size(); i++) {
                    CtStatement stmt = statements.get(i);
                    
                    // Check if this is a setter invocation on a Flyway variable
                    List<CtInvocation<?>> invocations = stmt.getElements(new TypeFilter<>(CtInvocation.class));
                    boolean foundSetter = false;
                    
                    for (CtInvocation<?> inv : invocations) {
                        String methodName = inv.getExecutable().getSimpleName();
                        if (methodName.startsWith("set") && 
                            (methodName.equals("setDataSource") || 
                             methodName.equals("setClassLoader") || 
                             methodName.equals("setLocations") || 
                             methodName.equals("setValidateOnMigrate"))) {
                            
                            setterCalls.add(inv);
                            foundSetter = true;
                        }
                    }
                    
                    if (foundSetter) {
                        statementsToRemove.add(stmt);
                    } else {
                        // Stop when we find a non-setter statement
                        break;
                    }
                }
                
                // Build the new fluent API call
                StringBuilder newCode = new StringBuilder();
                
                // Start with Flyway.configure()
                newCode.append("Flyway.configure()");
                
                // Process each setter call in order
                for (CtInvocation<?> setter : setterCalls) {
                    String methodName = setter.getExecutable().getSimpleName();
                    List<CtExpression<?>> args = setter.getArguments();
                    
                    if (args.isEmpty()) continue;
                    
                    String argStr = args.get(0).toString();
                    
                    if ("setDataSource".equals(methodName)) {
                        newCode.append("\n\t\t.dataSource(").append(argStr).append(")");
                    } else if ("setLocations".equals(methodName)) {
                        newCode.append("\n\t\t.locations(").append(argStr).append(")");
                    } else if ("setValidateOnMigrate".equals(methodName)) {
                        newCode.append("\n\t\t.validateOnMigrate(").append(argStr).append(")");
                    }
                    // Note: setClassLoader is skipped as configure() uses thread context classloader
                }
                
                // Add .load() at the end
                newCode.append("\n\t\t.load()");
                
                // Replace the constructor call with the new fluent API
                if (parentStmt instanceof CtLocalVariable) {
                    CtLocalVariable<?> localVar = (CtLocalVariable<?>) parentStmt;
                    String varName = localVar.getSimpleName();
                    String varType = localVar.getType().getSimpleName();
                    String replacement = varType + " " + varName + " = " + newCode.toString() + ";";
                    
                    CtStatement newStmt = getFactory().Code().createCodeSnippetStatement(replacement);
                    parentStmt.replace(newStmt);
                    
                    // Remove the old setter statements
                    for (CtStatement toRemove : statementsToRemove) {
                        toRemove.delete();
                    }
                    
                    System.out.println("Successfully transformed Flyway initialization at line: " + constructorCall.getPosition().getLine());
                }
                
            } catch (Exception e) {
                System.err.println("Error processing Flyway constructor: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}