package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtReturn;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation for Flyway API breaking change (9.x+).
 * 
 * Transforms:
 *   Old: Flyway flyway = new Flyway();
 *        flyway.setDataSource(dataSource);
 *        flyway.setClassLoader(classLoader); // Optional
 *        flyway.setLocations(locations);
 *        flyway.setValidateOnMigrate(validate);
 *        return flyway;
 *        
 *   New: return Flyway.configure()
 *              .dataSource(dataSource)
 *              .locations(locations)
 *              .validateOnMigrate(validate)
 *              .load();
 *              
 * Note: setClassLoader() is handled by passing to Flyway.configure(classLoader)
 * or is often optional in new API.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <sourceDirectory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find Flyway constructor calls
            List<CtConstructorCall> constructors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    if (element.getType() == null) return false;
                    String typeName = element.getType().getQualifiedName();
                    return "org.flywaydb.core.Flyway".equals(typeName);
                }
            });
            
            System.out.println("Found " + constructors.size() + " Flyway constructors");
            
            for (CtConstructorCall constructor : constructors) {
                transformConstructor(factory, constructor);
            }
            
            System.out.println("Transformation complete.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformConstructor(Factory factory, CtConstructorCall constructor) {
        try {
            // Get method containing the constructor
            CtStatement stmt = constructor.getParent(CtStatement.class);
            if (stmt == null) return;
            
            CtBlock<?> block = stmt.getParent(CtBlock.class);
            if (block == null) return;
            
            // Find variable name if it's a declaration
            String varName = null;
            if (stmt instanceof CtLocalVariable) {
                CtLocalVariable var = (CtLocalVariable) stmt;
                varName = var.getSimpleName();
            }
            
            if (varName == null) {
                System.out.println("Warning: Could not identify Flyway variable name");
                return;
            }
            
            // Collect setter invocations on this variable
            List<CtInvocation> setters = new ArrayList<>();
            List<CtStatement> statements = new ArrayList<>(block.getStatements());
            int startIdx = statements.indexOf(stmt);
            
            for (int i = startIdx + 1; i < statements.size(); i++) {
                CtStatement s = statements.get(i);
                if (s instanceof CtInvocation) {
                    CtInvocation inv = (CtInvocation) s;
                    if (inv.getTarget() != null && inv.getTarget().toString().startsWith(varName + ".")) {
                        String methodName = inv.getExecutable().getSimpleName();
                        if (methodName.startsWith("set")) {
                            setters.add(inv);
                        }
                    }
                } else if (s instanceof CtReturn) {
                    // Check if returning our variable
                    CtReturn ret = (CtReturn) s;
                    if (ret.getReturnedExpression() != null && 
                        ret.getReturnedExpression().toString().equals(varName)) {
                        // Transform entire pattern
                        transformPattern(factory, block, stmt, setters, ret);
                        return;
                    }
                    break;
                } else {
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Transform error: " + e.getMessage());
        }
    }
    
    private static void transformPattern(Factory factory, CtBlock<?> block, 
                                         CtStatement declStmt, List<CtInvocation> setters,
                                         CtReturn returnStmt) {
        try {
            // Build fluent chain
            StringBuilder chain = new StringBuilder("org.flywaydb.core.Flyway.configure()");
            
            boolean hasClassLoader = false;
            String classLoaderArg = null;
            
            for (CtInvocation setter : setters) {
                String methodName = setter.getExecutable().getSimpleName();
                List<?> args = setter.getArguments();
                
                if (args.isEmpty()) continue;
                
                String arg = args.get(0).toString();
                
                switch (methodName) {
                    case "setDataSource":
                        chain.append(".dataSource(").append(arg).append(")");
                        break;
                    case "setClassLoader":
                        hasClassLoader = true;
                        classLoaderArg = arg;
                        break;
                    case "setLocations":
                        chain.append(".locations(").append(arg).append(")");
                        break;
                    case "setValidateOnMigrate":
                        chain.append(".validateOnMigrate(").append(arg).append(")");
                        break;
                }
            }
            
            chain.append(".load()");
            
            // Handle classLoader specially
            if (hasClassLoader && classLoaderArg != null) {
                // Insert at beginning of chain
                String oldChain = chain.toString();
                chain = new StringBuilder("org.flywaydb.core.Flyway.configure(" + classLoaderArg + ")");
                chain.append(oldChain.substring(oldChain.indexOf('.') + 1));
            }
            
            // Create new return statement
            CtCodeSnippetExpression newReturnExpr = factory.Code().createCodeSnippetExpression(chain.toString());
            returnStmt.setReturnedExpression(newReturnExpr);
            
            // Remove declaration and setters
            block.removeStatement(declStmt);
            for (CtInvocation setter : setters) {
                CtStatement s = setter.getParent(CtStatement.class);
                if (s != null) block.removeStatement(s);
            }
            
            System.out.println("Transformed Flyway pattern at line " + declStmt.getPosition().getLine());
            
        } catch (Exception e) {
            System.err.println("Pattern transform error: " + e.getMessage());
        }
    }
}