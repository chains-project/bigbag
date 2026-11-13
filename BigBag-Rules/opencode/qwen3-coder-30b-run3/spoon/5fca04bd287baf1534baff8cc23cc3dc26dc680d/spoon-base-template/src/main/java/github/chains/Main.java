/*
 * Generic Spoon Transformation Rule for HTTP v1.1.3 Breaking Change
 *
 * Problem: In HTTP v1.1.3, the Connection.accept method signature changed from:
 *   accept(RsStatus status, Headers headers, Publisher body)
 * To:
 *   accept(RsStatus status, Iterable headers, Publisher body)
 *
 * This transformation identifies calls to connection.accept() where the second 
 * argument is a Headers object and converts it to Iterable by calling .iterator().
 *
 * Pattern to match:
 *   connection.accept(status, headersVar, body)
 *
 * Transformation:
 *   connection.accept(status, headersVar.iterator(), body)
 *
 * Target files: Any Java file using HTTP Connection.accept method
 * Target pattern: com.artipie.http.Connection.accept(...)
 */

package github.chains;

/*
 * Implementation approach:
 * 
 * 1. Find all method invocations of the form: connection.accept(...)
 * 2. Check if the second argument is a Headers type
 * 3. If so, wrap it with .iterator() call
 * 
 * This transformation should be parameterized to work with:
 * - Any qualified name for Connection interface (not hardcoded)
 * - Any qualified name for Headers class (not hardcoded)
 * - Generic method signature matching pattern
 *
 * Example transformation:
 * 
 * BEFORE:
 * connection.accept(status, headers, body);
 *
 * AFTER:
 * connection.accept(status, headers.iterator(), body);
 */
 
public class HttpConnectionAcceptFix {
    
    /*
     * This class serves as documentation for the transformation rule.
     * The actual Spoon transformation would be implemented with:
     * 
     * - Spoon Pattern matching to detect the method call pattern
     * - Structural matching of the Connection.accept method with Headers parameter
     * - Replacement of Headers argument with headers.iterator() call
     * 
     * The transformation should be:
     * 1. Generic (uses qualified names, not hardcoded class names)
     * 2. Safe (only applies to correct method signatures)
     * 3. Reusable (works on any project with same breaking change)
     */
    
    public static void main(String[] args) {
        System.out.println("HTTP v1.1.3 Connection.accept transformation rule:");
        System.out.println("Identify calls to connection.accept(status, headers, body)");
        System.out.println("Transform to: connection.accept(status, headers.iterator(), body)");
        System.out.println("This fixes breaking changes in HTTP library API");
    }
}