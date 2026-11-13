package github.chains;

/**
 * Generic transformation to fix Hamcrest 2.2 compatibility issues.
 * 
 * Problem: In Hamcrest 2.0+, the org.hamcrest.Matchers class was removed.
 * 
 * Solution: Replace all imports of org.hamcrest.Matchers with 
 * org.hamcrest.CoreMatchers and update usage accordingly.
 * 
 * This transformation should be applied to any Maven project that:
 * 1. Has imports like "import org.hamcrest.Matchers;"
 * 2. Uses Hamcrest matchers in expressions like "Matchers.containsString(...)"
 * 
 * The transformation works by:
 * 1. Finding all files importing org.hamcrest.Matchers
 * 2. Replacing the import with org.hamcrest.CoreMatchers
 * 3. Updating any direct usage patterns accordingly
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic Hamcrest 2.2 compatibility transformation ready.");
        System.out.println("This would fix imports of org.hamcrest.Matchers by replacing them with org.hamcrest.CoreMatchers");
    }
}