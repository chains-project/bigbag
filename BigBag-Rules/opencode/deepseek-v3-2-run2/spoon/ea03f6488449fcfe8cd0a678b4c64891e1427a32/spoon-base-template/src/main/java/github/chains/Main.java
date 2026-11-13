package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;
import java.util.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar transformation.jar <source-directory> <output-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Jedis 4.3.2 pipeline interface migration...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processor
        launcher.addProcessor(new JedisPipelineMigrationProcessor());
        
        // Build model and apply transformations
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
    
    static class JedisPipelineMigrationProcessor extends AbstractProcessor<CtType<?>> {
        // Map old interfaces to new interfaces
        private static final Map<String, String> OLD_TO_NEW_INTERFACES = new HashMap<>();
        static {
            // String-based pipeline interfaces
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.BasicRedisPipeline", 
                                     "redis.clients.jedis.commands.PipelineCommands");
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.MultiKeyCommandsPipeline", 
                                     "redis.clients.jedis.commands.PipelineCommands");
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.ScriptingCommandsPipeline", 
                                     "redis.clients.jedis.commands.PipelineCommands");
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.RedisPipeline", 
                                     "redis.clients.jedis.commands.PipelineCommands");
            
            // Binary-based pipeline interfaces
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.BinaryRedisPipeline", 
                                     "redis.clients.jedis.commands.PipelineBinaryCommands");
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline", 
                                     "redis.clients.jedis.commands.PipelineBinaryCommands");
            OLD_TO_NEW_INTERFACES.put("redis.clients.jedis.commands.BinaryScriptingCommandsPipeline", 
                                     "redis.clients.jedis.commands.PipelineBinaryCommands");
        }
        
        @Override
        public void process(CtType<?> type) {
            // Process type references in extends/implements clauses
            processSuperInterfaces(type);
        }
        
        private void processSuperInterfaces(CtType<?> type) {
            Set<CtTypeReference<?>> interfaces = type.getSuperInterfaces();
            Set<CtTypeReference<?>> newInterfaces = new HashSet<>();
            boolean changed = false;
            
            // Track which new interfaces we've added to avoid duplicates
            Set<String> addedInterfaces = new HashSet<>();
            
            for (CtTypeReference<?> interfaceRef : interfaces) {
                String interfaceName = interfaceRef.getQualifiedName();
                
                if (OLD_TO_NEW_INTERFACES.containsKey(interfaceName)) {
                    // Replace with new interface
                    String newInterfaceName = OLD_TO_NEW_INTERFACES.get(interfaceName);
                    if (!addedInterfaces.contains(newInterfaceName)) {
                        CtTypeReference<?> newInterfaceRef = getFactory().Type().createReference(newInterfaceName);
                        newInterfaces.add(newInterfaceRef);
                        addedInterfaces.add(newInterfaceName);
                    }
                    changed = true;
                } else if ("redis.clients.jedis.commands.ClusterPipeline".equals(interfaceName)) {
                    // Remove ClusterPipeline from implements clause - it's now a class
                    changed = true;
                    // Don't add it back
                } else {
                    // Keep the interface as-is
                    newInterfaces.add(interfaceRef);
                    addedInterfaces.add(interfaceName);
                }
            }
            
            // Also need to add RedisModulePipelineCommands for pipeline interfaces
            // that work with Redis modules (like RedisGraph)
            if (changed && (type.getSimpleName().contains("Pipeline") || type.getSimpleName().contains("Transaction"))) {
                // Check if this is a Redis module pipeline/transaction interface
                String redisModulePipeline = "redis.clients.jedis.commands.RedisModulePipelineCommands";
                if (!addedInterfaces.contains(redisModulePipeline)) {
                    CtTypeReference<?> redisModuleRef = getFactory().Type().createReference(redisModulePipeline);
                    newInterfaces.add(redisModuleRef);
                    addedInterfaces.add(redisModulePipeline);
                }
            }
            
            if (changed) {
                type.setSuperInterfaces(newInterfaces);
            }
        }
    }
}