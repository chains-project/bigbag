package github.chains;

/**
 * Main entry point for Hazelcast API transformation tool.
 * 
 * This file serves as the main entry point that references the actual transformer
 * implementation in HazelcastApiTransformer.
 */
public class Main {
    public static void main(String[] args) {
        // Delegate to the actual transformer implementation
        HazelcastApiTransformer.main(args);
    }
}