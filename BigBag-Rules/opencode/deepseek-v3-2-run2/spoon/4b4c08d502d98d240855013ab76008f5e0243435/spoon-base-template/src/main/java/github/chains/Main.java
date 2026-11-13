package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.*;
import spoon.reflect.reference.*;

import java.util.*;

/**
 * Generic Spoon transformation to fix Flyway API breaking changes.
 * 
 * Transforms:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(ds);
 *   flyway.setClassLoader(cl);
 *   flyway.setLocations(locs);
 *   flyway.setValidateOnMigrate(bool);
 *   return flyway;
 * 
 * To:
 *   return Flyway.configure()
 *       .dataSource(ds)
 *       .classLoader(cl)
 *       .locations(locs)
 *       .validateOnMigrate(bool)
 *       .load();
 * 
 * This transformation is generic and can be applied to any project
 * affected by the Flyway 9.x API breaking changes.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <sourceDirectory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        // Critical: Don't use autoimports to avoid corrupting imports
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        // Add processor
        launcher.addProcessor(new FlywayApiFixProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed!");
            System.out.println("Modified files are in the spooned/ directory");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class FlywayApiFixProcessor extends AbstractProcessor<CtMethod<?>> {
        
        @Override
        public void process(CtMethod<?> method) {
            // Check if method returns Flyway
            CtTypeReference<?> returnType = method.getType();
            if (returnType == null || !"org.flywaydb.core.Flyway".equals(returnType.getQualifiedName())) {
                return;
            }
            
            CtBlock<?> body = method.getBody();
            if (body == null) {
                return;
            }
            
            System.out.println("Processing Flyway-returning method: " + 
                method.getDeclaringType().getQualifiedName() + "#" + method.getSimpleName());
            
            // Look for the pattern
            List<CtStatement> statements = new ArrayList<>(body.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                CtStatement stmt = statements.get(i);
                
                if (stmt instanceof CtLocalVariable) {
                    CtLocalVariable<?> varDecl = (CtLocalVariable<?>) stmt;
                    
                    // Check if it's a Flyway variable initialized with new Flyway()
                    if (isFlywayVariable(varDecl) && isNoArgFlywayConstructor(varDecl.getDefaultExpression())) {
                        String varName = varDecl.getSimpleName();
                        
                        // Collect setter calls on this variable
                        List<SetterInfo> setters = new ArrayList<>();
                        int returnIdx = -1;
                        
                        // Scan forward
                        for (int j = i + 1; j < statements.size(); j++) {
                            CtStatement nextStmt = statements.get(j);
                            
                            if (nextStmt instanceof CtReturn) {
                                CtReturn<?> returnStmt = (CtReturn<?>) nextStmt;
                                if (returnsVariable(returnStmt, varName)) {
                                    returnIdx = j;
                                    break;
                                }
                            } else if (nextStmt instanceof CtInvocation) {
                                CtInvocation<?> invoc = (CtInvocation<?>) nextStmt;
                                SetterInfo setter = extractSetterInfo(invoc, varName);
                                if (setter != null) {
                                    setters.add(setter);
                                }
                            }
                        }
                        
                        if (returnIdx != -1 && !setters.isEmpty()) {
                            // Found the pattern!
                            System.out.println("  Found Flyway creation pattern with " + setters.size() + " setters");
                            
                            // Transform it
                            if (transformPattern(method, varDecl, setters, (CtReturn<?>) statements.get(returnIdx))) {
                                System.out.println("  Successfully transformed");
                                return; // Done with this method
                            }
                        }
                    }
                }
            }
        }
        
        private boolean isFlywayVariable(CtLocalVariable<?> varDecl) {
            CtTypeReference<?> type = varDecl.getType();
            return type != null && "org.flywaydb.core.Flyway".equals(type.getQualifiedName());
        }
        
        private boolean isNoArgFlywayConstructor(CtExpression<?> expr) {
            if (!(expr instanceof CtConstructorCall)) {
                return false;
            }
            CtConstructorCall<?> constr = (CtConstructorCall<?>) expr;
            CtTypeReference<?> type = constr.getType();
            return type != null && 
                   "org.flywaydb.core.Flyway".equals(type.getQualifiedName()) &&
                   constr.getArguments().isEmpty();
        }
        
        private boolean returnsVariable(CtReturn<?> returnStmt, String varName) {
            CtExpression<?> expr = returnStmt.getReturnedExpression();
            if (expr instanceof CtVariableAccess) {
                return varName.equals(((CtVariableAccess<?>) expr).getVariable().getSimpleName());
            }
            return false;
        }
        
        private SetterInfo extractSetterInfo(CtInvocation<?> invoc, String varName) {
            CtExpression<?> target = invoc.getTarget();
            if (!(target instanceof CtVariableAccess)) {
                return null;
            }
            
            CtVariableAccess<?> varAccess = (CtVariableAccess<?>) target;
            if (!varName.equals(varAccess.getVariable().getSimpleName())) {
                return null;
            }
            
            String methodName = invoc.getExecutable().getSimpleName();
            if (!methodName.startsWith("set") || invoc.getArguments().size() != 1) {
                return null;
            }
            
            return new SetterInfo(methodName, invoc.getArguments().get(0), invoc);
        }
        
        private boolean transformPattern(CtMethod<?> method, CtLocalVariable<?> varDecl,
                                        List<SetterInfo> setters, CtReturn<?> returnStmt) {
            try {
                Factory factory = getFactory();
                
                // Start building: Flyway.configure()
                CtTypeReference<?> flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
                CtTypeAccess<?> flywayTypeAccess = factory.createTypeAccess(flywayType);
                
                // Check for classLoader setter (special case)
                CtExpression<?> classLoaderArg = null;
                Iterator<SetterInfo> iter = setters.iterator();
                while (iter.hasNext()) {
                    SetterInfo setter = iter.next();
                    if ("setClassLoader".equals(setter.methodName)) {
                        classLoaderArg = setter.argument;
                        iter.remove();
                        break;
                    }
                }
                
                // Create configure() call
                CtExecutableReference<?> configureRef = factory.createExecutableReference();
                configureRef.setDeclaringType(flywayType);
                configureRef.setSimpleName("configure");
                
                CtInvocation<?> configCall;
                if (classLoaderArg != null) {
                    // Flyway.configure(classLoader)
                    configCall = factory.createInvocation(flywayTypeAccess, configureRef, classLoaderArg);
                } else {
                    // Flyway.configure()
                    configCall = factory.createInvocation(flywayTypeAccess, configureRef);
                }
                
                CtExpression<?> currentExpr = configCall;
                
                // Add other setters as fluent calls
                for (SetterInfo setter : setters) {
                    String fluentName = setter.methodName.substring(3); // Remove "set"
                    fluentName = Character.toLowerCase(fluentName.charAt(0)) + fluentName.substring(1);
                    
                    CtExecutableReference<?> methodRef = factory.createExecutableReference();
                    methodRef.setSimpleName(fluentName);
                    
                    currentExpr = factory.createInvocation(currentExpr, methodRef, setter.argument);
                }
                
                // Add .load()
                CtExecutableReference<?> loadRef = factory.createExecutableReference();
                loadRef.setSimpleName("load");
                CtInvocation<?> loadCall = factory.createInvocation(currentExpr, loadRef);
                
                // Replace return statement
                returnStmt.setReturnedExpression(loadCall);
                
                // Remove variable declaration
                varDecl.delete();
                
                // Remove setter calls
                for (SetterInfo setter : setters) {
                    setter.invocation.delete();
                }
                if (classLoaderArg != null) {
                    // Find and remove the setClassLoader invocation
                    // (We already removed it from the list)
                }
                
                return true;
            } catch (Exception e) {
                System.err.println("  Error transforming: " + e.getMessage());
                return false;
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtMethod<?> candidate) {
            // Only process methods returning Flyway
            CtTypeReference<?> returnType = candidate.getType();
            return returnType != null && "org.flywaydb.core.Flyway".equals(returnType.getQualifiedName());
        }
        
        static class SetterInfo {
            final String methodName;
            final CtExpression<?> argument;
            final CtInvocation<?> invocation;
            
            SetterInfo(String methodName, CtExpression<?> argument, CtInvocation<?> invocation) {
                this.methodName = methodName;
                this.argument = argument;
                this.invocation = invocation;
            }
        }
    }
}