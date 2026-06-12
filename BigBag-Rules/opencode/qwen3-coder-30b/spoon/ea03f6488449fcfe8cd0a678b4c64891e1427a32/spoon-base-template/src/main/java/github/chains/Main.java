package github.chains;

import spoon.Launcher;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher for processing Java files
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Add the source directory to process
        launcher.addInputResource("/workspace/JRedisGraph/src/main/java");
        launcher.addInputResource("/workspace/JRedisGraph/src/test/java");
        
        // Process the files
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Execute the transformation
        fixJedisPipelineCompatibility(factory);
        
        System.out.println("Jedis 4.3.2 compatibility transformation completed");
    }
    
    private static void fixJedisPipelineCompatibility(Factory factory) {
        // 1. Remove imports for the removed pipeline interfaces from redis.clients.jedis.commands
        System.out.println("Removing old pipeline imports...");
        String[] removedInterfaces = {
            "redis.clients.jedis.commands.BasicRedisPipeline",
            "redis.clients.jedis.commands.BinaryRedisPipeline",
            "redis.clients.jedis.commands.BinaryScriptingCommandsPipeline",
            "redis.clients.jedis.commands.ClusterPipeline",
            "redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline",
            "redis.clients.jedis.commands.MultiKeyCommandsPipeline",
            "redis.clients.jedis.commands.RedisPipeline",
            "redis.clients.jedis.commands.ScriptingCommandsPipeline"
        };
        
        // Remove the imports
        for (CtImport imp : factory.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            String importedType = imp.getReference().toString();
            for (String removed : removedInterfaces) {
                if (removed.equals(importedType)) {
                    System.out.println("Removing import: " + importedType);
                    imp.delete();
                    break;
                }
            }
        }
        
        System.out.println("Import removal completed");
    }
}