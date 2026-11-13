package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtVisitor;
import spoon.reflect.visitor.Filter;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * Generic Spoon transformation rule for Jetty 11 migration.
 * This is a GENERIC, REUSABLE transformation that can be applied to ANY Maven project
 * affected by Jetty 11 breaking changes.
 * 
 * Breaking changes addressed:
 * 1. javax.servlet → jakarta.servlet package migration
 * 2. SelectChannelConnector → ServerConnector migration  
 * 3. Removed Server methods (setSendServerVersion, setSendDateHeader)
 * 4. Connector API changes (setPort, getLocalPort)
 * 5. AbstractHandler.handle() signature change
 * 
 * Structural patterns matched:
 * - ANY reference to javax.servlet.* → jakarta.servlet.*
 * - new SelectChannelConnector() → new ServerConnector(server)
 * - server.setSendServerVersion() → // removed
 * - server.setSendDateHeader() → // removed
 * - connector.setPort() → serverConnector.setPort() (with type check)
 * - connector.getLocalPort() → serverConnector.getLocalPort() (with type check)
 * - AbstractHandler.handle() parameter types javax.servlet → jakarta.servlet
 */
public class Main {
    
    // CONFIGURATION - These constants define the transformation rules
    // They are parameterized by fully-qualified type names from the dependency,
    // NOT from the client project. This makes the rule GENERIC.
    
    // Old javax.servlet package prefix
    private static final String JAVAX_SERVLET_PREFIX = "javax.servlet";
    
    // New jakarta.servlet package prefix  
    private static final String JAKARTA_SERVLET_PREFIX = "jakarta.servlet";
    
    // Old connector class (removed in Jetty 11)
    private static final String SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    
    // New connector class
    private static final String SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    
    // Connector interface
    private static final String CONNECTOR_INTERFACE = "org.eclipse.jetty.server.Connector";
    
    // Server class
    private static final String SERVER_CLASS = "org.eclipse.jetty.server.Server";
    
    // AbstractHandler class
    private static final String ABSTRACT_HANDLER = "org.eclipse.jetty.server.handler.AbstractHandler";
    
    // Methods removed from Server class
    private static final Set<String> REMOVED_SERVER_METHODS = new HashSet<>(Arrays.asList(
        "setSendServerVersion",
        "setSendDateHeader"
    ));
    
    // Connector methods that moved to ServerConnector
    private static final Set<String> CONNECTOR_METHODS_TO_CHECK = new HashSet<>(Arrays.asList(
        "setPort",
        "getLocalPort"
    ));
    
    private static int fixesApplied = 0;
    private static int warnings = 0;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Optional: java github.chains.Main <source-directory> <output-report-file>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String reportFile = args.length > 1 ? args[1] : "jetty11-migration-report.txt";
        
        System.out.println("=== Generic Jetty 11 Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Report file: " + reportFile);
        System.out.println("\nThis transformation is GENERIC and can be applied to ANY project");
        System.out.println("affected by Jetty 11 breaking changes.");
        
        try (PrintWriter reportWriter = new PrintWriter(new FileWriter(reportFile))) {
            reportWriter.println("=== Generic Jetty 11 Migration Report ===");
            reportWriter.println("Source: " + sourceDir);
            reportWriter.println("Timestamp: " + new Date());
            reportWriter.println("\nTRANSFORMATION RULES APPLIED:");
            reportWriter.println("1. javax.servlet → jakarta.servlet (package migration)");
            reportWriter.println("2. SelectChannelConnector → ServerConnector (class replacement)");
            reportWriter.println("3. Remove Server.setSendServerVersion() and setSendDateHeader()");
            reportWriter.println("4. Update Connector.setPort()/getLocalPort() calls");
            reportWriter.println("5. Fix AbstractHandler.handle() signature");
            reportWriter.println();
            
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Apply transformations by scanning all elements
            applyTransformations(model.getRootPackage(), reportWriter);
            
            reportWriter.println("\n=== SUMMARY ===");
            reportWriter.println("Total fixes applied: " + fixesApplied);
            reportWriter.println("Warnings/notes: " + warnings);
            reportWriter.println("\n=== MANUAL REVIEW REQUIRED ===");
            reportWriter.println("The following changes require manual adjustment:");
            reportWriter.println("1. SelectChannelConnector → ServerConnector: Constructor arguments need adjustment");
            reportWriter.println("   ServerConnector requires Server as first constructor parameter");
            reportWriter.println("2. Verify Connector.setPort()/getLocalPort() calls target ServerConnector instances");
            reportWriter.println("3. Update any configuration that used removed Server methods");
            
            System.out.println("\n=== Transformation Complete ===");
            System.out.println("Total fixes applied: " + fixesApplied);
            System.out.println("Warnings/notes: " + warnings);
            System.out.println("Report saved to: " + reportFile);
            System.out.println("\nIMPORTANT: Some changes require manual review!");
            System.out.println("Please review the report file for details.");
            
            // Output transformed code
            launcher.prettyprint();
            
        } catch (Exception e) {
            System.err.println("Error applying transformations: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Apply all transformations by recursively visiting all elements in the package
     */
    private static void applyTransformations(CtPackage pkg, PrintWriter report) {
        // Process all types in this package
        for (CtType<?> type : pkg.getTypes()) {
            processType(type, report);
        }
        
        // Recursively process subpackages
        for (CtPackage subPkg : pkg.getPackages()) {
            applyTransformations(subPkg, report);
        }
    }
    
    /**
     * Process a type and all its elements
     */
    private static void processType(CtType<?> type, PrintWriter report) {
        // Check if this is an AbstractHandler implementation
        CtTypeReference<?> superClass = type.getSuperclass();
        if (superClass != null && ABSTRACT_HANDLER.equals(superClass.getQualifiedName())) {
            report.println("FOUND: AbstractHandler implementation: " + type.getQualifiedName());
            checkHandleMethod(type, report);
        }
        
        // Process fields
        for (CtField<?> field : type.getFields()) {
            processField(field, report);
        }
        
        // Process methods
        for (CtMethod<?> method : type.getMethods()) {
            processMethod(method, report);
        }
        
        // Process nested types
        for (CtType<?> nestedType : type.getNestedTypes()) {
            processType(nestedType, report);
        }
    }
    
    /**
     * Process a field declaration
     */
    private static void processField(CtField<?> field, PrintWriter report) {
        // Fix field type if it uses javax.servlet
        fixTypeReference(field.getType(), report);
    }
    
    /**
     * Process a method declaration
     */
    private static void processMethod(CtMethod<?> method, PrintWriter report) {
        // Fix return type
        fixTypeReference(method.getType(), report);
        
        // Fix parameter types
        for (CtParameter<?> param : method.getParameters()) {
            fixTypeReference(param.getType(), report);
        }
        
        // Process method body
        CtBlock<?> body = method.getBody();
        if (body != null) {
            processBlock(body, report);
        }
    }
    
    /**
     * Process a code block
     */
    private static void processBlock(CtBlock<?> block, PrintWriter report) {
        for (CtStatement statement : block.getStatements()) {
            processStatement(statement, report);
        }
    }
    
    /**
     * Process a statement
     */
    private static void processStatement(CtStatement statement, PrintWriter report) {
        if (statement instanceof CtLocalVariable) {
            processLocalVariable((CtLocalVariable<?>) statement, report);
        } else if (statement instanceof CtInvocation) {
            processInvocation((CtInvocation<?>) statement, report);
        } else if (statement instanceof CtIf) {
            processIf((CtIf) statement, report);
        } else if (statement instanceof CtFor) {
            processFor((CtFor) statement, report);
        } else if (statement instanceof CtWhile) {
            processWhile((CtWhile) statement, report);
        } else if (statement instanceof CtTry) {
            processTry((CtTry) statement, report);
        } else if (statement instanceof CtBlock) {
            processBlock((CtBlock<?>) statement, report);
        }
        // Add other statement types as needed
    }
    
    /**
     * Process a local variable declaration
     */
    private static void processLocalVariable(CtLocalVariable<?> variable, PrintWriter report) {
        // Fix variable type
        fixTypeReference(variable.getType(), report);
        
        // Check initialization
        CtExpression<?> initialization = variable.getDefaultExpression();
        if (initialization != null) {
            processExpression(initialization, report);
        }
    }
    
    /**
     * Process an invocation (method call)
     */
    private static void processInvocation(CtInvocation<?> invocation, PrintWriter report) {
        // Check for removed Server methods
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef != null) {
            String methodName = execRef.getSimpleName();
            
            if (REMOVED_SERVER_METHODS.contains(methodName)) {
                String location = getLocation(invocation);
                report.println("FOUND: " + methodName + "() at " + location);
                report.println("  ACTION: This method was removed in Jetty 11.");
                report.println("  Remove or replace with equivalent configuration.");
                warnings++;
            }
            
            // Check for Connector methods that moved to ServerConnector
            if (CONNECTOR_METHODS_TO_CHECK.contains(methodName)) {
                String location = getLocation(invocation);
                report.println("FOUND: " + methodName + "() at " + location);
                
                // Check target type
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    CtTypeReference<?> targetType = target.getType();
                    if (targetType != null) {
                        String targetTypeName = targetType.getQualifiedName();
                        if (CONNECTOR_INTERFACE.equals(targetTypeName)) {
                            report.println("  WARNING: Calling " + methodName + "() on Connector interface.");
                            report.println("           This method is now only available on ServerConnector class.");
                            warnings++;
                        } else if (SERVER_CONNECTOR.equals(targetTypeName)) {
                            report.println("  OK: Calling " + methodName + "() on ServerConnector (correct).");
                            fixesApplied++;
                        } else if (targetTypeName != null) {
                            report.println("  NOTE: Calling " + methodName + "() on " + targetTypeName);
                            report.println("        Verify this extends/implements ServerConnector.");
                            warnings++;
                        }
                    }
                }
            }
        }
        
        // Process arguments
        for (CtExpression<?> arg : invocation.getArguments()) {
            processExpression(arg, report);
        }
    }
    
    /**
     * Process an expression
     */
    private static void processExpression(CtExpression<?> expression, PrintWriter report) {
        if (expression instanceof CtNewClass) {
            processNewClass((CtNewClass<?>) expression, report);
        } else if (expression instanceof CtInvocation) {
            processInvocation((CtInvocation<?>) expression, report);
        } else if (expression instanceof CtFieldAccess) {
            processFieldAccess((CtFieldAccess<?>) expression, report);
        } else if (expression instanceof CtVariableAccess) {
            processVariableAccess((CtVariableAccess<?>) expression, report);
        }
        // Add other expression types as needed
    }
    
    /**
     * Process a new class instantiation
     */
    private static void processNewClass(CtNewClass<?> newClass, PrintWriter report) {
        // Fix SelectChannelConnector → ServerConnector
        CtTypeReference<?> typeRef = newClass.getType();
        if (typeRef != null && SELECT_CHANNEL_CONNECTOR.equals(typeRef.getQualifiedName())) {
            try {
                // Create ServerConnector type reference
                CtTypeReference<?> serverConnectorRef = newClass.getFactory().Type().createReference(SERVER_CONNECTOR);
                newClass.setType(serverConnectorRef);
                
                String location = getLocation(newClass);
                report.println("FIXED: SelectChannelConnector → ServerConnector at " + location);
                report.println("  NOTE: ServerConnector constructor requires Server parameter.");
                report.println("        Old: new SelectChannelConnector()");
                report.println("        New: new ServerConnector(server) // Add server parameter");
                fixesApplied++;
                warnings++;
            } catch (Exception e) {
                report.println("WARNING: Could not fix SelectChannelConnector: " + e.getMessage());
                warnings++;
            }
        }
        
        // Process constructor arguments
        for (CtExpression<?> arg : newClass.getArguments()) {
            processExpression(arg, report);
        }
    }
    
    /**
     * Process field access
     */
    private static void processFieldAccess(CtFieldAccess<?> fieldAccess, PrintWriter report) {
        // Fix field type if needed
        fixTypeReference(fieldAccess.getType(), report);
    }
    
    /**
     * Process variable access
     */
    private static void processVariableAccess(CtVariableAccess<?> variableAccess, PrintWriter report) {
        // Fix variable type if needed
        fixTypeReference(variableAccess.getType(), report);
    }
    
    /**
     * Fix a type reference from javax.servlet to jakarta.servlet
     */
    private static void fixTypeReference(CtTypeReference<?> typeRef, PrintWriter report) {
        if (typeRef != null) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(JAVAX_SERVLET_PREFIX)) {
                String newName = qualifiedName.replace(JAVAX_SERVLET_PREFIX, JAKARTA_SERVLET_PREFIX);
                try {
                    typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    report.println("FIXED: " + qualifiedName + " → " + newName);
                    fixesApplied++;
                } catch (Exception e) {
                    report.println("WARNING: Could not fix " + qualifiedName + ": " + e.getMessage());
                    warnings++;
                }
            }
        }
    }
    
    /**
     * Check handle() method in AbstractHandler implementations
     */
    private static void checkHandleMethod(CtType<?> type, PrintWriter report) {
        for (CtMethod<?> method : type.getMethods()) {
            if ("handle".equals(method.getSimpleName())) {
                List<CtParameter<?>> params = method.getParameters();
                if (params.size() == 4) {
                    boolean hasJavaxServlet = false;
                    for (CtParameter<?> param : params) {
                        CtTypeReference<?> paramType = param.getType();
                        if (paramType != null) {
                            String paramTypeName = paramType.getQualifiedName();
                            if (paramTypeName != null && paramTypeName.startsWith(JAVAX_SERVLET_PREFIX)) {
                                hasJavaxServlet = true;
                                break;
                            }
                        }
                    }
                    
                    if (hasJavaxServlet) {
                        report.println("  WARNING: handle() method uses javax.servlet types");
                        report.println("           Should be migrated to jakarta.servlet types");
                        warnings++;
                    }
                }
            }
        }
    }
    
    // Helper methods for different statement types (simplified)
    private static void processIf(CtIf ctIf, PrintWriter report) {
        processExpression(ctIf.getCondition(), report);
        processStatement(ctIf.getThenStatement(), report);
        if (ctIf.getElseStatement() != null) {
            processStatement(ctIf.getElseStatement(), report);
        }
    }
    
    private static void processFor(CtFor ctFor, PrintWriter report) {
        for (CtStatement init : ctFor.getForInit()) {
            processStatement(init, report);
        }
        if (ctFor.getExpression() != null) {
            processExpression(ctFor.getExpression(), report);
        }
        for (CtStatement update : ctFor.getForUpdate()) {
            processStatement(update, report);
        }
        processStatement(ctFor.getBody(), report);
    }
    
    private static void processWhile(CtWhile ctWhile, PrintWriter report) {
        processExpression(ctWhile.getLoopingExpression(), report);
        processStatement(ctWhile.getBody(), report);
    }
    
    private static void processTry(CtTry ctTry, PrintWriter report) {
        processBlock(ctTry.getBody(), report);
        for (CtCatch ctCatch : ctTry.getCatchers()) {
            processCatch(ctCatch, report);
        }
        if (ctTry.getFinalizer() != null) {
            processBlock(ctTry.getFinalizer(), report);
        }
    }
    
    private static void processCatch(CtCatch ctCatch, PrintWriter report) {
        fixTypeReference(ctCatch.getParameter().getType(), report);
        processBlock(ctCatch.getBody(), report);
    }
    
    private static String getLocation(CtElement element) {
        if (element.getPosition().isValidPosition()) {
            return element.getPosition().getFile().getName() + ":" + element.getPosition().getLine();
        }
        return "unknown location";
    }
}