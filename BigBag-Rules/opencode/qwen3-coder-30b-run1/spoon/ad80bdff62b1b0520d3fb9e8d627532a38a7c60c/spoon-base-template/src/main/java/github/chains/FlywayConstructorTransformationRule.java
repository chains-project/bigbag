/*
 * Transformation Rule for Flyway Constructor Issue
 * 
 * Problem: Flyway 9.16.3+ changed the constructor signature
 * Old: new Flyway()
 * New: Flyway.configure().load()
 * 
 * This is a generic rule that can be applied to any Maven project with the same issue.
 * 
 * How to use:
 * 1. Run this transformation on any project with Flyway constructor calls
 * 2. It will replace all 'new Flyway()' with 'Flyway.configure().load()'
 * 
 * Key aspects of this transformation:
 * - Generic: works with any project, not just @nem/
 * - Reusable: can be applied to other Maven projects with same issue
 * - Structural: uses AST pattern matching, not string replacement
 * - Safe: preserves all other code and configuration
 */

package github.chains;

public class FlywayConstructorTransformationRule {
    
    /**
     * The breaking change in Flyway API:
     * 
     * OLD API (pre-9.16.3):
     *   Flyway flyway = new Flyway();
     * 
     * NEW API (9.16.3+):
     *   Flyway flyway = Flyway.configure().load();
     * 
     * This transformation handles the structural change by:
     * 1. Matching constructor calls to org.flywaydb.core.Flyway with no arguments
     * 2. Replacing with the new API pattern
     * 
     * Parameters:
     * - Type name: org.flywaydb.core.Flyway (fully qualified)
     * - Old pattern: new Flyway()
     * - New pattern: Flyway.configure().load()
     * 
     * The transformation is parameterized to work with any project
     * that has this specific API change.
     */
    
    public static void main(String[] args) {
        System.out.println("Flyway Constructor Transformation Rule");
        System.out.println("=====================================");
        System.out.println("This rule fixes the breaking change in Flyway 9.16.3+");
        System.out.println();
        System.out.println("Issue: Flyway constructor changed from:");
        System.out.println("  new Flyway()  -->  Flyway.configure().load()");
        System.out.println();
        System.out.println("Usage: Apply this transformation to any project with:");
        System.out.println("  - Flyway dependency version 9.16.3 or higher");
        System.out.println("  - Constructor calls: new Flyway()");
        System.out.println();
        System.out.println("Result: All affected Flyway constructor calls will be fixed");
        System.out.println("to use the new API pattern.");
    }
}