package github.chains;

/**
 * Generic transformation rule for fixing breaking changes in CloudResourceManager.Builder constructor.
 * 
 * This template shows how to identify and fix breaking changes in Google Cloud APIs where:
 * 1. A constructor signature changes (e.g., from 3 args to 4 args)
 * 2. The change affects multiple projects using the same library
 * 
 * Pattern:
 * - Match the old API pattern: new CloudResourceManager.Builder(transport, JSON_FACTORY, initializer)
 * - Transform to the new API pattern: new CloudResourceManager.Builder(..., newParam)
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic CloudResourceManager transformation template");
        System.out.println("Usage: java Main <source_directory>");
    }
}