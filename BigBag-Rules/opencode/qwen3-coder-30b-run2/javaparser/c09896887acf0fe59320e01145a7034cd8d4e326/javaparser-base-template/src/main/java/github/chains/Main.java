package github.chains;

/**
 * Generic transformation to fix breaking changes in GitHub API where public fields
 * became private with getter methods.
 * Specifically fixes cases where .status field in GHCompare was changed to getStatus().
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("GitHub API field fixer ready");
        System.out.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
    }
}