package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic Spoon transformation for fixing Jetty API breaking changes from Jetty 8/9 to Jetty 9.4+.
 * This transformation handles the main breaking API changes identified in the jadler project.
 * 
 * BREAKING CHANGES ADDRESSED:
 * 1. SelectChannelConnector -> ServerConnector migration
 *    - Old: new SelectChannelConnector()
 *    - New: new ServerConnector(server)  (requires Server parameter)
 *    
 * 2. Server configuration methods moved to HttpConfiguration
 *    - Old: server.setSendServerVersion(false)
 *    - New: httpConfig.setSendServerVersion(false)
 *    - Old: server.setSendDateHeader(true)  
 *    - New: httpConfig.setSendDateHeader(true)
 *    
 * 3. Connector methods (setPort, getLocalPort) - should still work with ServerConnector
 * 
 * TRANSFORMATION APPROACH:
 * - Generic pattern matching based on fully-qualified type names
 * - Parameterized by old/new class names for reusability
 * - Outputs transformed code to separate directory
 * - Reports transformations applied
 */
public class Main {
    
    // Configuration - parameterizable for different projects
    private static final String DEFAULT_SOURCE_DIR = "/workspace/jadler";
    private static final String DEFAULT_OUTPUT_DIR = "/workspace/jadler-transformed";
    
    // API MAPPING CONFIGURATION - make these parameters for maximum reusability
    private static final String OLD_CONNECTOR_CLASS = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_CLASS = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_CLASS = "org.eclipse.jetty.server.Server";
    private static final String HTTP_CONFIG_CLASS = "org.eclipse.jetty.server.HttpConfiguration";
    
    // Methods that moved from Server to HttpConfiguration
    private static final Map<String, String> METHOD_MIGRATIONS = new HashMap<>();
    static {
        METHOD_MIGRATIONS.put("setSendServerVersion", HTTP_CONFIG_CLASS);
        METHOD_MIGRATIONS.put("setSendDateHeader", HTTP_CONFIG_CLASS);
    }
    
    public static void main(String[] args) {
        String sourceDir = args.length > 0 ? args[0] : DEFAULT_SOURCE_DIR;
        String outputDir = args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR;
        
        System.out.println("=== GENERIC JETTY API MIGRATION TRANSFORMATION ===");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println();
        System.out.println("API MAPPINGS:");
        System.out.println("  " + OLD_CONNECTOR_CLASS + " -> " + NEW_CONNECTOR_CLASS);
        System.out.println("  Server.setSendServerVersion() -> HttpConfiguration.setSendServerVersion()");
        System.out.println("  Server.setSendDateHeader() -> HttpConfiguration.setSendDateHeader()");
        System.out.println("===================================================");
        
        try {
            // Create Spoon launcher with minimal configuration
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true); // Don't require full classpath
            launcher.getEnvironment().setAutoImports(true); // Handle imports automatically
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Add processors for different transformation types
            launcher.addProcessor(new ConnectorTypeProcessor());
            launcher.addProcessor(new ServerMethodProcessor());
            launcher.addProcessor(new ConstructorCallProcessor());
            
            System.out.println("Building AST model and applying transformations...");
            launcher.run();
            
            System.out.println("\n=== TRANSFORMATION COMPLETE ===");
            System.out.println("Transformed code saved to: " + outputDir);
            System.out.println();
            System.out.println("NEXT STEPS:");
            System.out.println("1. Review transformed files in output directory");
            System.out.println("2. Manually add HttpConfiguration setup if needed");
            System.out.println("3. Update project dependencies to Jetty 9.4.41+");
            System.out.println("4. Compile: mvn clean compile");
            System.out.println("5. Run tests: mvn test");
            
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    // =======================================================================
    // PROCESSOR 1: Update type references (fields, parameters, return types)
    // =======================================================================
    public static class ConnectorTypeProcessor extends AbstractProcessor<CtType<?>> {
        private int replacements = 0;
        
        @Override
        public void process(CtType<?> type) {
            System.out.println("Processing type: " + type.getQualifiedName());
            
            // Check all type references in this type
            java.util.Set<CtTypeReference<?>> refs = type.getReferencedTypes();
            for (CtTypeReference<?> ref : refs) {
                if (OLD_CONNECTOR_CLASS.equals(ref.getQualifiedName())) {
                    System.out.println("  Found reference to " + OLD_CONNECTOR_CLASS);
                    System.out.println("  Should be replaced with " + NEW_CONNECTOR_CLASS);
                    replacements++;
                }
            }
            
            // More specific field type replacements
            if (type instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) type;
                
                // Check fields
                for (CtField<?> field : clazz.getFields()) {
                    CtTypeReference<?> fieldType = field.getType();
                    if (fieldType != null && OLD_CONNECTOR_CLASS.equals(fieldType.getQualifiedName())) {
                        System.out.println("  [FIELD] " + field.getSimpleName() + " : " + OLD_CONNECTOR_CLASS + " -> " + NEW_CONNECTOR_CLASS);
                        CtTypeReference<?> newType = getFactory().Type().createReference(NEW_CONNECTOR_CLASS);
                        field.setType(newType);
                        replacements++;
                    }
                }
                
                // Check method parameters
                for (CtMethod<?> method : clazz.getMethods()) {
                    for (CtParameter<?> param : method.getParameters()) {
                        CtTypeReference<?> paramType = param.getType();
                        if (paramType != null && OLD_CONNECTOR_CLASS.equals(paramType.getQualifiedName())) {
                            System.out.println("  [PARAM] " + param.getSimpleName() + " in " + method.getSimpleName() + 
                                             " : " + OLD_CONNECTOR_CLASS + " -> " + NEW_CONNECTOR_CLASS);
                            CtTypeReference<?> newType = getFactory().Type().createReference(NEW_CONNECTOR_CLASS);
                            param.setType(newType);
                            replacements++;
                        }
                    }
                }
            }
        }
        
        @Override
        public void processingDone() {
            System.out.println("\nConnectorTypeProcessor: " + replacements + " type replacements identified");
        }
    }
    
    // =======================================================================
    // PROCESSOR 2: Update Server method calls that moved to HttpConfiguration
    // =======================================================================
    public static class ServerMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        private int methodReplacements = 0;
        
        @Override
        public void process(CtInvocation<?> invocation) {
            if (invocation.getTarget() == null) return;
            
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Check if this is a method that needs migration
            if (METHOD_MIGRATIONS.containsKey(methodName)) {
                CtTypeReference<?> targetType = invocation.getTarget().getType();
                if (targetType != null && SERVER_CLASS.equals(targetType.getQualifiedName())) {
                    String newTargetClass = METHOD_MIGRATIONS.get(methodName);
                    System.out.println("  [METHOD] server." + methodName + "() -> " + newTargetClass + "." + methodName + "()");
                    System.out.println("    Location: " + invocation.getPosition());
                    methodReplacements++;
                    
                    // Note: Actual target replacement requires finding HttpConfiguration object
                    // This is complex and project-specific, so we just report it
                }
            }
        }
        
        @Override
        public void processingDone() {
            System.out.println("\nServerMethodProcessor: " + methodReplacements + " method calls need migration");
            if (methodReplacements > 0) {
                System.out.println("NOTE: Methods moved from Server to HttpConfiguration.");
                System.out.println("      Need to find/create HttpConfiguration object and update target.");
            }
        }
    }
    
    // =======================================================================
    // PROCESSOR 3: Update constructor calls
    // =======================================================================
    public static class ConstructorCallProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        private int constructorReplacements = 0;
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            if (constructorCall.getType() == null) return;
            
            String constructorType = constructorCall.getType().getQualifiedName();
            
            if (OLD_CONNECTOR_CLASS.equals(constructorType)) {
                System.out.println("  [CONSTRUCTOR] new " + OLD_CONNECTOR_CLASS + "() -> new " + NEW_CONNECTOR_CLASS + "(server)");
                System.out.println("    Location: " + constructorCall.getPosition());
                constructorReplacements++;
                
                // Note: ServerConnector requires Server parameter in constructor
                // In jadler pattern: "this.server" field should be passed
            }
        }
        
        @Override
        public void processingDone() {
            System.out.println("\nConstructorCallProcessor: " + constructorReplacements + " constructor calls need migration");
            if (constructorReplacements > 0) {
                System.out.println("NOTE: ServerConnector constructor requires Server parameter.");
                System.out.println("      Change: new SelectChannelConnector() -> new ServerConnector(server)");
            }
        }
    }
}