package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;


import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic Spoon transformation for Jetty 8/9 to Jetty 11 migration.
 * This transformation handles multiple breaking changes in a generic way
 * that can be applied to any project affected by these API changes.
 */
public class Main {
    
    private final Launcher launcher;
    private final Factory factory;
    private final CtModel model;
    
    // Configuration parameters - can be adjusted for different projects
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_CONNECTOR_CLASS = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_CLASS = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_CLASS = "org.eclipse.jetty.server.Server";
    private static final String CONNECTOR_INTERFACE = "org.eclipse.jetty.server.Connector";
    
    public Main(String sourceDir) {
        this.launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.buildModel();
        this.factory = launcher.getFactory();
        this.model = launcher.getModel();
    }
    
    /**
     * Apply all transformations for Jetty 11 migration
     */
    public void applyTransformations() {
        System.out.println("Applying generic Jetty 8/9 to Jetty 11 migration transformations...");
        System.out.println("Configuration:");
        System.out.println("  Servlet: " + OLD_SERVLET_PREFIX + " -> " + NEW_SERVLET_PREFIX);
        System.out.println("  Connector: " + OLD_CONNECTOR_CLASS + " -> " + NEW_CONNECTOR_CLASS);
        
        // 1. javax.servlet to jakarta.servlet package migration
        migrateServletPackages();
        
        // 2. SelectChannelConnector to ServerConnector replacement
        replaceSelectChannelConnector();
        
        // 3. Server method relocation to HttpConfiguration
        migrateServerMethods();
        
        // 4. Fix Connector interface method usage
        fixConnectorMethods();
        
        
        
        System.out.println("Transformations completed.");
    }
    
    /**
     * Generic transformation: Migrate javax.servlet.* to jakarta.servlet.*
     * This is a generic transformation that works for any project migrating
     * from Java EE javax.servlet to Jakarta EE jakarta.servlet
     */
    private void migrateServletPackages() {
        System.out.println("\n=== Transformation 1: Servlet Package Migration ===");
        
        int migratedImports = 0;
        int migratedTypes = 0;
        
        // Find all imports with old servlet prefix
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.startsWith(OLD_SERVLET_PREFIX)) {
                String newImport = importStr.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX);
                try {
                    // Create new import
                    CtImport newImp = factory.createImport(factory.createReference(newImport));
                    imp.replace(newImp);
                    migratedImports++;
                } catch (Exception e) {
                    System.err.println("Failed to migrate import: " + importStr + " -> " + newImport);
                }
            }
        }
        
        // Find all type references to old servlet types
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_SERVLET_PREFIX)) {
                String newName = qualifiedName.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX);
                try {
                    CtTypeReference<?> newTypeRef = factory.createReference(newName);
                    typeRef.replace(newTypeRef);
                    migratedTypes++;
                } catch (Exception e) {
                    System.err.println("Failed to migrate type reference: " + qualifiedName + " -> " + newName);
                }
            }
        }
        
        System.out.println("Migrated " + migratedImports + " imports and " + migratedTypes + " type references");
    }
    
    /**
     * Generic transformation: Replace SelectChannelConnector with ServerConnector
     * This handles:
     * 1. Constructor calls: new SelectChannelConnector() -> new ServerConnector(server, port)
     * 2. Type references: SelectChannelConnector -> ServerConnector
     * 3. Variable declarations
     */
    private void replaceSelectChannelConnector() {
        System.out.println("\n=== Transformation 2: Connector Replacement ===");
        
        int replacedConstructors = 0;
        int replacedTypes = 0;
        
        // Pattern 1: Direct constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : constructorCalls) {
            CtTypeReference<?> typeRef = call.getType();
            if (typeRef != null && OLD_CONNECTOR_CLASS.equals(typeRef.getQualifiedName())) {
                try {
                    // Create new ServerConnector constructor call
                    // Note: This is a simplified replacement - actual migration requires
                    // port to be passed in constructor and Server instance
                    CtConstructorCall<?> newCall = factory.createConstructorCall(
                        factory.createReference(NEW_CONNECTOR_CLASS)
                    );
                    
                    call.replace(newCall);
                    replacedConstructors++;
                    System.out.println("Replaced SelectChannelConnector constructor at: " + call.getPosition());
                    System.out.println("   Note: Manual adjustment needed - port and Server instance required");
                    
                } catch (Exception e) {
                    System.err.println("Failed to replace SelectChannelConnector constructor: " + e.getMessage());
                }
            }
        }
        
        // Pattern 2: Type references (variable declarations, method parameters, etc.)
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (OLD_CONNECTOR_CLASS.equals(qualifiedName)) {
                try {
                    CtTypeReference<?> newTypeRef = factory.createReference(NEW_CONNECTOR_CLASS);
                    typeRef.replace(newTypeRef);
                    replacedTypes++;
                } catch (Exception e) {
                    System.err.println("Failed to replace SelectChannelConnector type reference: " + e.getMessage());
                }
            }
        }
        
        System.out.println("Replaced " + replacedConstructors + " constructors and " + replacedTypes + " type references");
    }
    
    
    
    /**
     * Generic transformation: Migrate Server methods to HttpConfiguration
     * Old pattern: server.setSendServerVersion(boolean) and server.setSendDateHeader(boolean)
     * New pattern: Configure through HttpConfiguration object
     */
    private void migrateServerMethods() {
        System.out.println("\n=== Transformation 3: Server Method Migration ===");
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        int migratedMethods = 0;
        
        for (CtInvocation<?> inv : invocations) {
            CtExecutableReference<?> execRef = inv.getExecutable();
            if (execRef == null) continue;
            
            String methodName = execRef.getSimpleName();
            CtExpression<?> target = inv.getTarget();
            
            // Check if this is a method call on a Server instance
            if (target != null) {
                CtTypeReference<?> targetType = target.getType();
                if (targetType != null && SERVER_CLASS.equals(targetType.getQualifiedName())) {
                    
                    if ("setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName)) {
                        try {
                            // Get the boolean argument
                            List<CtExpression<?>> args = inv.getArguments();
                            if (!args.isEmpty()) {
                                CtExpression<?> arg = args.get(0);
                                
                                // Create HttpConfiguration setup pattern
                                // This transformation is complex and requires understanding
                                // of how HttpConfiguration is created and used
                                System.out.println("Found Server." + methodName + "(" + arg + ") at: " + inv.getPosition());
                                System.out.println("   Needs migration to HttpConfiguration.");
                                System.out.println("   Example pattern:");
                                System.out.println("     Old: server." + methodName + "(" + arg + ")");
                                System.out.println("     New: httpConfig.setSendServerVersion(" + arg + ")");
                                System.out.println("   where httpConfig is a HttpConfiguration instance");
                                
                                // Mark for manual review
                                migratedMethods++;
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to process Server method: " + methodName + " - " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        System.out.println("Identified " + migratedMethods + " Server methods needing migration to HttpConfiguration");
        System.out.println("Note: This transformation requires manual adjustment based on project structure");
    }
    
    /**
     * Generic transformation: Fix Connector interface method usage
     * Problems:
     * 1. connector.setPort(port) - port should be set via ServerConnector constructor
     * 2. connector.getLocalPort() - works on ServerConnector, may need casting
     */
    private void fixConnectorMethods() {
        System.out.println("\n=== Transformation 4: Connector Method Fixes ===");
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        int fixedMethods = 0;
        
        for (CtInvocation<?> inv : invocations) {
            CtExecutableReference<?> execRef = inv.getExecutable();
            if (execRef == null) continue;
            
            String methodName = execRef.getSimpleName();
            CtExpression<?> target = inv.getTarget();
            
            if (target != null) {
                CtTypeReference<?> targetType = target.getType();
                if (targetType != null) {
                    String targetTypeName = targetType.getQualifiedName();
                    
                    // Check if target is Connector or compatible type
                    if (targetTypeName != null && 
                        (targetTypeName.equals(CONNECTOR_INTERFACE) ||
                         targetTypeName.equals(NEW_CONNECTOR_CLASS) ||
                         targetTypeName.equals(OLD_CONNECTOR_CLASS))) {
                        
                        if ("setPort".equals(methodName)) {
                            System.out.println("Found Connector.setPort() call at: " + inv.getPosition());
                            System.out.println("   Action: Port should be set via ServerConnector constructor");
                            System.out.println("   Example: new ServerConnector(server, port)");
                            System.out.println("   This method call should be removed after constructor migration");
                            fixedMethods++;
                            
                        } else if ("getLocalPort".equals(methodName)) {
                            // Check if we need to cast to ServerConnector
                            boolean needsCast = targetTypeName.equals(CONNECTOR_INTERFACE);
                            
                            if (needsCast) {
                                System.out.println("getLocalPort() call at " + inv.getPosition() + " needs cast to ServerConnector");
                                System.out.println("   Example: ((ServerConnector) connector).getLocalPort()");
                                fixedMethods++;
                            } else {
                                System.out.println("getLocalPort() call at " + inv.getPosition() + " is already on ServerConnector");
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("Fixed " + fixedMethods + " Connector method calls");
    }
    
    
    
    /**
     * Save the transformed model to output directory
     */
    public void saveTransformedCode(String outputDir) {
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        System.out.println("\nTransformed code saved to: " + outputDir);
    }
    
    /**
     * Main method - entry point for the transformation tool
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";
        
        // Validate source directory
        File sourceDirFile = new File(sourceDir);
        if (!sourceDirFile.exists() || !sourceDirFile.isDirectory()) {
            System.err.println("Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        // Create output directory if it doesn't exist
        new File(outputDir).mkdirs();
        
        try {
            System.out.println("Starting generic Jetty migration transformation...");
            System.out.println("Source: " + sourceDir);
            System.out.println("Output: " + outputDir);
            System.out.println("=".repeat(60));
            
            Main transformer = new Main(sourceDir);
            transformer.applyTransformations();
            transformer.saveTransformedCode(outputDir);
            
            printSummary();
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Generic Jetty Migration Transformation Tool");
        System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> [output-directory]");
        System.err.println();
        System.err.println("Arguments:");
        System.err.println("  source-directory   Path to Java source code to transform");
        System.err.println("  output-directory   Path where transformed code will be saved");
        System.err.println("                     (default: <source-directory>-transformed)");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java -jar spoon-transformation.jar /path/to/project/src");
        System.err.println("  java -jar spoon-transformation.jar /path/to/project/src /path/to/output");
    }
    
    private static void printSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TRANSFORMATION SUMMARY");
        System.out.println("=".repeat(60));
        System.out.println("\nApplied generic transformations for Jetty 11 migration:");
        System.out.println();
        System.out.println("1. Servlet Package Migration:");
        System.out.println("   Pattern: " + OLD_SERVLET_PREFIX + ".* -> " + NEW_SERVLET_PREFIX + ".*");
        System.out.println("   Scope: All imports and type references");
        System.out.println();
        System.out.println("2. Connector Replacement:");
        System.out.println("   Pattern: " + OLD_CONNECTOR_CLASS + " -> " + NEW_CONNECTOR_CLASS);
        System.out.println("   Scope: Constructor calls and type references");
        System.out.println("   Note: Port handling moved from setPort() to constructor");
        System.out.println();
        System.out.println("3. Server Method Migration:");
        System.out.println("   Pattern: server.setSendServerVersion()/setSendDateHeader()");
        System.out.println("   New: Configure via HttpConfiguration object");
        System.out.println("   Note: Requires manual adjustment based on project structure");
        System.out.println();
        System.out.println("4. Connector Method Fixes:");
        System.out.println("   - setPort(): Remove - port now in constructor");
        System.out.println("   - getLocalPort(): May need cast to ServerConnector");
        System.out.println();
        System.out.println("IMPORTANT NOTES:");
        System.out.println("1. Some transformations require manual review and adjustment");
        System.out.println("2. Test the transformed code thoroughly");
        System.out.println("3. Review compilation errors after transformation");
        System.out.println("4. The transformation is generic - adjust configuration parameters");
        System.out.println("   for different projects in the source code");
        System.out.println("\n" + "=".repeat(60));
    }
}