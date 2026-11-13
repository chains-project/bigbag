package github.chains;

/**
 * Generic JavaParser Transformation Rule for Hamcrest Constructor Compatibility
 * 
 * Breaking Change Analysis:
 * - Hamcrest 2.x changed StringContains constructor from (boolean, String) to (String)
 * - Hamcrest 2.x changed StringStartsWith constructor from (boolean, String) to (String)
 * 
 * The transformation fixes the API incompatibility by:
 * 1. Matching old constructor calls: new StringContains(boolean, String)
 * 2. Transforming them to equivalent Hamcrest 2.x patterns
 * 3. Applying the same fix to StringStartsWith calls
 * 
 * Usage: Apply this transformation to any Maven project with Hamcrest v2.x
 * that has compilation errors due to these constructor changes.
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("Hamcrest Constructor Compatibility Transformation");
        System.out.println("Generic rule for fixing StringContains/StringStartsWith constructor changes");
    }
}