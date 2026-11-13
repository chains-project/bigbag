package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.factory.Factory;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation rule for Flyway 9.x breaking API change.
 * 
 * Transforms:
 *   Old: new Flyway() + setter methods
 *   New: Flyway.configure() fluent API with .load()
 * 
 * Pattern:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(ds);
 *   flyway.setClassLoader(cl);
 *   →
 *   Flyway flyway = Flyway.configure(cl).dataSource(ds).load();
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to Flyway
        List<CtConstructorCall> flywayConstructorCalls = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                CtTypeReference typeRef = element.getType();
                if (typeRef == null) return false;
                
                String typeName = typeRef.getQualifiedName();
                return typeName != null && typeName.equals("org.flywaydb.core.Flyway");
            }
        });
        
        System.out.println("Found " + flywayConstructorCalls.size() + " Flyway constructor calls");
        
        // Transform each Flyway constructor call
        for (CtConstructorCall constructorCall : flywayConstructorCalls) {
            try {
                transformFlywayConstructorCall(constructorCall);
            } catch (Exception e) {
                System.err.println("Error transforming constructor call at " + constructorCall.getPosition() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory("./transformed-output");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to ./transformed-output");
    }
    
    private static void transformFlywayConstructorCall(CtConstructorCall constructorCall) {
        Factory factory = constructorCall.getFactory();
        
        // Create the new fluent API call: Flyway.configure()...
        // Check if we need classLoader parameter
        boolean hasClassLoaderSetter = false;
        CtExpression classLoaderExpr = null;
        
        // Find setter calls
        CtBlock block = constructorCall.getParent(CtBlock.class);
        if (block == null) {
            System.err.println("Warning: Flyway constructor call not in a block");
            return;
        }
        
        // Find the variable name
        String varName = findVariableName(constructorCall);
        if (varName == null) {
            System.err.println("Warning: Could not determine variable name for Flyway instance");
            return;
        }
        
        // Find the statement index
        CtStatement constructorStmt = constructorCall.getParent(CtStatement.class);
        int constructorIndex = block.getStatements().indexOf(constructorStmt);
        
        // Collect setter calls
        List<CtInvocation> setterCalls = new ArrayList<>();
        List<Integer> setterIndices = new ArrayList<>();
        List<String> setterNames = new ArrayList<>();
        List<List<CtExpression>> setterArgs = new ArrayList<>();
        
        for (int i = constructorIndex + 1; i < block.getStatements().size(); i++) {
            CtStatement stmt = block.getStatements().get(i);
            if (stmt instanceof CtInvocation) {
                CtInvocation invocation = (CtInvocation) stmt;
                CtExpression target = invocation.getTarget();
                
                if (target instanceof CtVariableRead) {
                    CtVariableRead targetVar = (CtVariableRead) target;
                    if (targetVar.getVariable().getSimpleName().equals(varName)) {
                        String methodName = invocation.getExecutable().getSimpleName();
                        if (methodName.startsWith("set") && methodName.length() > 3) {
                            setterCalls.add(invocation);
                            setterIndices.add(i);
                            setterNames.add(methodName);
                            setterArgs.add(new ArrayList<>(invocation.getArguments()));
                            
                            if (methodName.equals("setClassLoader")) {
                                hasClassLoaderSetter = true;
                                if (!invocation.getArguments().isEmpty()) {
                                    classLoaderExpr = invocation.getArguments().get(0);
                                }
                            }
                        }
                    }
                }
            } else {
                break;
            }
        }
        
        System.out.println("Found " + setterCalls.size() + " setter calls for variable " + varName);
        
        // Build fluent expression as code snippet
        StringBuilder fluentBuilder = new StringBuilder();
        
        // Get Flyway class name (qualified or simple)
        CtTypeReference flywayType = constructorCall.getType();
        String flywayClassName = flywayType.getSimpleName();
        
        // Start with Flyway.configure() or Flyway.configure(classLoader)
        fluentBuilder.append(flywayClassName).append(".configure(");
        if (hasClassLoaderSetter && classLoaderExpr != null) {
            fluentBuilder.append(classLoaderExpr.toString());
        }
        fluentBuilder.append(")");
        
        // Add fluent method calls for other setters
        for (int i = 0; i < setterCalls.size(); i++) {
            String setterName = setterNames.get(i);
            
            // Skip setClassLoader - handled as configure parameter
            if (setterName.equals("setClassLoader")) {
                continue;
            }
            
            // Convert setXxx to xxx (camelCase)
            String fluentName = Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
            List<CtExpression> args = setterArgs.get(i);
            
            fluentBuilder.append(".").append(fluentName).append("(");
            if (!args.isEmpty()) {
                for (int j = 0; j < args.size(); j++) {
                    if (j > 0) fluentBuilder.append(", ");
                    fluentBuilder.append(args.get(j).toString());
                }
            }
            fluentBuilder.append(")");
        }
        
        // Add .load() at the end
        fluentBuilder.append(".load()");
        
        // Parse the fluent expression
        try {
            CtExpression fluentExpr = factory.createCodeSnippetExpression(fluentBuilder.toString());
            constructorCall.replace(fluentExpr);
            
            // Remove setter calls
            for (int i = setterIndices.size() - 1; i >= 0; i--) {
                int index = setterIndices.get(i);
                if (index < block.getStatements().size()) {
                    block.getStatements().remove(index);
                }
            }
            
            System.out.println("Transformed: " + fluentBuilder.toString());
        } catch (Exception e) {
            System.err.println("Failed to parse fluent expression: " + e.getMessage());
        }
    }
    
    private static String findVariableName(CtConstructorCall constructorCall) {
        // Check parent assignment
        CtAssignment assignment = constructorCall.getParent(CtAssignment.class);
        if (assignment != null) {
            CtExpression assigned = assignment.getAssigned();
            if (assigned instanceof CtVariableRead) {
                return ((CtVariableRead) assigned).getVariable().getSimpleName();
            }
        }
        
        // Check if constructor call is directly in a statement
        CtStatement parentStmt = constructorCall.getParent(CtStatement.class);
        if (parentStmt != null) {
            String stmtStr = parentStmt.toString();
            // Simple pattern matching
            if (stmtStr.contains("Flyway") && stmtStr.contains("=")) {
                String[] parts = stmtStr.split("\\s+");
                for (int i = 0; i < parts.length; i++) {
                    if ((parts[i].equals("Flyway") || parts[i].endsWith(".Flyway")) && i + 1 < parts.length) {
                        String varPart = parts[i + 1];
                        // Extract variable name before = or ;
                        int eqIdx = varPart.indexOf('=');
                        int semiIdx = varPart.indexOf(';');
                        int endIdx = eqIdx != -1 ? eqIdx : (semiIdx != -1 ? semiIdx : varPart.length());
                        return varPart.substring(0, endIdx);
                    }
                }
            }
        }
        
        return "flyway"; // Default fallback
    }
}