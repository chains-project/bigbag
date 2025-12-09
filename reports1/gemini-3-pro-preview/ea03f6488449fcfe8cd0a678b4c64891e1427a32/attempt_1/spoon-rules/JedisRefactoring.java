package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Spoon Refactoring Script for Jedis Library Changes.
 * 
 * CHANGES ADDRESSED:
 * 1. Removal of `redis.clients.jedis.Client` class -> Replaced usages with `redis.clients.jedis.Connection`.
 * 2. Removal of `getClient()` methods in `BinaryJedis`, `Pipeline`, `Transaction` etc. -> Renamed to `getConnection()`.
 */
public class JedisRefactoring {

    public static class JedisMigrationProcessor extends AbstractProcessor<CtElement> {
        
        private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
            "Jedis", "BinaryJedis", "Pipeline", "PipelineBase", 
            "ShardedJedisPipeline", "Transaction"
        ));

        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            // Process both Invocations (method calls) and TypeReferences (class usages)
            if (candidate instanceof CtInvocation) {
                return true;
            }
            if (candidate instanceof CtTypeReference) {
                return true;
            }
            return false;
        }

        @Override
        public void process(CtElement element) {
            if (element instanceof CtInvocation) {
                processInvocation((CtInvocation<?>) element);
            } else if (element instanceof CtTypeReference) {
                processTypeReference((CtTypeReference<?>) element);
            }
        }

        private void processInvocation(CtInvocation<?> invocation) {
            // 1. Check Method Name
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"getClient".equals(methodName)) {
                return;
            }

            // 2. Check Owner Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            boolean isJedisType = false;
            
            if (declaringType != null) {
                String typeName = declaringType.getSimpleName();
                // Check if the owner is one of the affected Jedis classes
                // We use relaxed checking (contains) to handle subclasses or full qualified names
                for (String target : TARGET_CLASSES) {
                    if (typeName.contains(target)) {
                        isJedisType = true;
                        break;
                    }
                }
            } else {
                // In NoClasspath, declaring type might be null. 
                // Heuristic: If method is getClient() and no args, or string args, it's likely Jedis.
                // To be safe, we might skip or assume based on context. 
                // Here we assume true if unknown to catch all potential breakages, 
                // relies on user review.
                isJedisType = true; 
            }

            if (!isJedisType) {
                return;
            }

            // 3. Refactoring Action: Rename getClient -> getConnection
            // Note: This applies to both 0-arg and 1-arg versions. 
            // Although 1-arg versions (e.g. getClient(byte[])) are removed and might not have a direct 
            // 1-to-1 replacement in getConnection(), renaming them alerts the user to the API shift 
            // and fixes the 0-arg common case.
            invocation.getExecutable().setSimpleName("getConnection");
            
            System.out.println("Refactored: getClient() -> getConnection() at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "?"));
        }

        private void processTypeReference(CtTypeReference<?> typeRef) {
            // 1. Check Qualified Name for the removed Client class
            if (typeRef.getQualifiedName().equals("redis.clients.jedis.Client")) {
                // 2. Refactoring Action: Replace with Connection
                typeRef.setSimpleName("Connection");
                typeRef.setPackage(typeRef.getFactory().Package().getOrCreate("redis.clients.jedis"));
                
                System.out.println("Refactored Type: Client -> Connection at line " + 
                    (typeRef.getPosition().isValidPosition() ? typeRef.getPosition().getLine() : "?"));
            }
        }
    }

    public static void main(String[] args) {
        // Configuration: Input/Output paths
        String inputPath = "/home/kth/Documents/last_transformer/output/ea03f6488449fcfe8cd0a678b4c64891e1427a32/JRedisGraph/src/main/java/com/redislabs/redisgraph/impl/api/ContextedRedisGraph.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ea03f6488449fcfe8cd0a678b4c64891e1427a32/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ea03f6488449fcfe8cd0a678b4c64891e1427a32/JRedisGraph/src/main/java/com/redislabs/redisgraph/impl/api/ContextedRedisGraph.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ea03f6488449fcfe8cd0a678b4c64891e1427a32/attempt_1/transformed");

        // CRITICAL: Configure Environment for Code Preservation (Sniper Mode)
        // 1. Preserve Comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Use Sniper Printer to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add Processor
        launcher.addProcessor(new JedisMigrationProcessor());

        System.out.println("Starting Jedis Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}