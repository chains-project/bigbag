package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation rule for migrating from Jedis < 4.3.2 to Jedis 4.3.2+
 * This transformation handles the breaking changes in the Pipeline/Transaction API.
 * 
 * This is a SIMPLIFIED version focusing on pattern matching and replacement.
 * A full implementation would need more sophisticated AST analysis.
 */
public class Main {
    
    // Old pipeline interfaces that no longer exist in Jedis 4.3.2
    private static final List<String> OLD_PIPELINE_INTERFACES = Arrays.asList(
        "redis.clients.jedis.commands.BasicRedisPipeline",
        "redis.clients.jedis.commands.BinaryRedisPipeline", 
        "redis.clients.jedis.commands.BinaryScriptingCommandsPipeline",
        "redis.clients.jedis.commands.ClusterPipeline",
        "redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline",
        "redis.clients.jedis.commands.MultiKeyCommandsPipeline",
        "redis.clients.jedis.commands.RedisPipeline",
        "redis.clients.jedis.commands.ScriptingCommandsPipeline"
    );
    
    // New consolidated interfaces in Jedis 4.3.2
    private static final String NEW_PIPELINE_COMMANDS = "redis.clients.jedis.commands.PipelineCommands";
    private static final String NEW_PIPELINE_BINARY_COMMANDS = "redis.clients.jedis.commands.PipelineBinaryCommands";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Jedis 4.3.2 Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce verbose output
        
        try {
            CtModel model = launcher.buildModel();
            System.out.println("Model built successfully.");
            
            // Apply transformations
            System.out.println("\n1. Migrating interface declarations...");
            migrateInterfaces(model);
            
            System.out.println("\n2. Migrating type references...");
            migrateTypeReferences(model);
            
            System.out.println("\n3. Generating migration report...");
            generateReport(model);
            
            // Output transformed code
            System.out.println("\n4. Writing transformed code...");
            launcher.setSourceOutputDirectory("./transformed-output");
            launcher.prettyprint();
            
            System.out.println("\n=== Transformation Complete ===");
            System.out.println("Transformed code written to: ./transformed-output");
            System.out.println("\nNEXT STEPS:");
            System.out.println("1. Review transformed code in ./transformed-output");
            System.out.println("2. Manually update method calls from getResponse() to appendCommand()");
            System.out.println("3. Update constructors to use Connection instead of Client");
            System.out.println("4. Update CommandObject creation for graph operations");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Migrate interface declarations in type definitions
     */
    private static void migrateInterfaces(CtModel model) {
        // Find all types (classes, interfaces)
        List<CtType<?>> allTypes = model.getElements(new TypeFilter<CtType<?>>(CtType.class) {
            @Override
            public boolean matches(CtType<?> element) {
                return true;
            }
        });
        
        int migratedTypes = 0;
        
        for (CtType<?> type : allTypes) {
            boolean needsPipelineCommands = false;
            boolean needsBinaryCommands = false;
            List<CtTypeReference<?>> interfacesToRemove = new ArrayList<>();
            
            // Check super interfaces
            for (CtTypeReference<?> interfaceRef : type.getSuperInterfaces()) {
                String interfaceName = interfaceRef.getQualifiedName();
                if (OLD_PIPELINE_INTERFACES.contains(interfaceName)) {
                    interfacesToRemove.add(interfaceRef);
                    needsPipelineCommands = true;
                    
                    // Check if binary interface
                    if (interfaceName.contains("Binary")) {
                        needsBinaryCommands = true;
                    }
                }
            }
            
            // Apply changes if needed
            if (!interfacesToRemove.isEmpty()) {
                for (CtTypeReference<?> toRemove : interfacesToRemove) {
                    type.removeSuperInterface(toRemove);
                }
                
                // Add new consolidated interfaces
                if (needsPipelineCommands) {
                    CtTypeReference<?> pipelineCommands = type.getFactory().createReference(NEW_PIPELINE_COMMANDS);
                    if (!hasInterface(type, NEW_PIPELINE_COMMANDS)) {
                        type.addSuperInterface(pipelineCommands);
                    }
                }
                
                if (needsBinaryCommands) {
                    CtTypeReference<?> binaryCommands = type.getFactory().createReference(NEW_PIPELINE_BINARY_COMMANDS);
                    if (!hasInterface(type, NEW_PIPELINE_BINARY_COMMANDS)) {
                        type.addSuperInterface(binaryCommands);
                    }
                }
                
                migratedTypes++;
                System.out.println("  ✓ " + type.getQualifiedName());
            }
        }
        
        System.out.println("  Migrated " + migratedTypes + " type(s)");
    }
    
    /**
     * Migrate type references (Client -> Connection, etc.)
     */
    private static void migrateTypeReferences(CtModel model) {
        // Find all type references
        List<CtTypeReference<?>> allTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return true;
            }
        });
        
        int migratedRefs = 0;
        
        for (CtTypeReference<?> typeRef : allTypeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // Replace Client with Connection
            if ("redis.clients.jedis.Client".equals(qualifiedName)) {
                CtTypeReference<?> connectionRef = typeRef.getFactory().createReference("redis.clients.jedis.Connection");
                typeRef.replace(connectionRef);
                migratedRefs++;
            }
            // Replace old pipeline interface references
            else if (OLD_PIPELINE_INTERFACES.contains(qualifiedName)) {
                // Determine replacement
                String replacement = qualifiedName.contains("Binary") ? 
                    NEW_PIPELINE_BINARY_COMMANDS : NEW_PIPELINE_COMMANDS;
                
                CtTypeReference<?> newRef = typeRef.getFactory().createReference(replacement);
                typeRef.replace(newRef);
                migratedRefs++;
            }
        }
        
        System.out.println("  Migrated " + migratedRefs + " type reference(s)");
    }
    
    /**
     * Generate a migration report
     */
    private static void generateReport(CtModel model) {
        System.out.println("\n=== MIGRATION REPORT ===");
        
        // Find method calls that need manual migration
        List<CtInvocation<?>> getResponseCalls = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return "getResponse".equals(element.getExecutable().getSimpleName());
            }
        });
        
        if (!getResponseCalls.isEmpty()) {
            System.out.println("\nMANUAL MIGRATION REQUIRED:");
            System.out.println("Found " + getResponseCalls.size() + " getResponse() calls that need manual update:");
            System.out.println("  Old: getResponse(Builder)");
            System.out.println("  New: appendCommand(new CommandObject(commandArguments, builder))");
            
            // Show sample locations
            int count = 0;
            for (CtInvocation<?> call : getResponseCalls) {
                if (count < 5) { // Show first 5
                    System.out.println("  - " + call.getPosition().getCompilationUnit().getFile().getName() + 
                                      " line " + call.getPosition().getLine());
                }
                count++;
            }
            if (count >= 5) {
                System.out.println("  ... and " + (count - 5) + " more");
            }
        }
        
        // Find constructor calls that might need updates
        List<CtConstructorCall> pipelineConstrCalls = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                CtTypeReference<?> type = element.getType();
                return type != null && (
                    "redis.clients.jedis.Pipeline".equals(type.getQualifiedName()) ||
                    "redis.clients.jedis.Transaction".equals(type.getQualifiedName())
                );
            }
        });
        
        if (!pipelineConstrCalls.isEmpty()) {
            System.out.println("\nCONSTRUCTOR UPDATES:");
            System.out.println("Found " + pipelineConstrCalls.size() + " Pipeline/Transaction constructors");
            System.out.println("Verify they use Connection/Jedis instead of Client");
        }
    }
    
    /**
     * Check if a type already has a specific interface
     */
    private static boolean hasInterface(CtType<?> type, String interfaceName) {
        for (CtTypeReference<?> interfaceRef : type.getSuperInterfaces()) {
            if (interfaceName.equals(interfaceRef.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}