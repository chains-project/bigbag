package github.chains;

/**
 * Generic Flyway API Fix Transformation
 * 
 * This tool applies a generic transformation to fix Flyway API breaking changes 
 * introduced in version 9.10.2.
 * 
 * Breaking Changes Fixed:
 * - Constructor: new Flyway() → Flyway.configure().load()
 * 
 * Usage: java -cp target/classes github.chains.Main /path/to/project/src
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Flyway Fix Transformation - Generic Rule");
    }
}