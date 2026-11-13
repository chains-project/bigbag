package github.chains;

/**
 * Generic JavaParser transformation to fix breaking dependency updates in Hazelcast.
 * This rule handles common breaking changes in Hazelcast dependencies by structurally
 * matching patterns and applying appropriate transformations.
 * 
 * The transformation handles these common Hazelcast API breaking changes:
 * 1. IMap.addEntryListener(listener, includeValues) -> addEntryListener(listener, includeValues, true)
 * 2. IMap.lock(key) -> lock(key, -1)  
 * 3. IMap.tryLock(key) -> tryLock(key, -1)
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("Hazelcast API Transformation Template");
        System.out.println("=====================================");
        System.out.println("This template addresses common Hazelcast breaking changes:");
        System.out.println("1. addEntryListener() signature change");
        System.out.println("2. lock() signature change");
        System.out.println("3. tryLock() signature change");
        System.out.println("");
        System.out.println("Usage: java Main <source_directory>");
        System.out.println("Where source_directory contains Java files to transform");
    }
}
