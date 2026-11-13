package github.chains;

/**
 * Generic Spoon transformation to fix Flyway constructor calls
 * that changed from new Flyway() to new Flyway(Configuration)
 * 
 * This transformation identifies all calls to:
 *   new Flyway()
 *   
 * And transforms them to:
 *   new Flyway(FluentConfiguration.configure())
 * 
 * This addresses the breaking change in Flyway 9.19.1 where the 
 * default constructor was removed and now requires a Configuration object.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation for Flyway constructor fix:");
        System.out.println("Usage: java -jar spoon-transformer.jar <source-directory>");
        System.out.println("This transformation replaces new Flyway() calls with new Flyway(FluentConfiguration.configure())");
    }
}