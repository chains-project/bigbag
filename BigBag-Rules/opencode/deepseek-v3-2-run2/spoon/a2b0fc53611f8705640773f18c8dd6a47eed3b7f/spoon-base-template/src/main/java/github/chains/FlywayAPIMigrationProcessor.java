package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;

import java.util.List;

/**
 * A generic Spoon transformation rule for migrating Flyway API from old version
 * to Flyway 9.x+ where the API changed from setter methods to fluent configuration.
 * 
 * This transformation demonstrates the pattern for handling the breaking change.
 * The actual implementation would need more sophisticated logic for a production system.
 */
public class FlywayAPIMigrationProcessor extends AbstractProcessor<CtConstructorCall> {
    
    @Override
    public void process(CtConstructorCall constructor) {
        try {
            // Check if this is a Flyway constructor call with no arguments
            if (isFlywayConstructor(constructor) && constructor.getArguments().isEmpty()) {
                System.out.println("\n=== Found Flyway API usage pattern ===");
                System.out.println("Location: " + constructor.getPosition());
                
                // Get the containing method to analyze the full pattern
                CtMethod<?> containingMethod = constructor.getParent(CtMethod.class);
                if (containingMethod != null) {
                    System.out.println("In method: " + containingMethod.getSignature());
                    
                    // Analyze the transformation needed
                    System.out.println("\nTransformation required:");
                    System.out.println("1. Replace: new Flyway() → Flyway.configure()");
                    System.out.println("2. Convert setter methods to fluent API:");
                    System.out.println("   - flyway.setDataSource(...) → .dataSource(...)");
                    System.out.println("   - flyway.setClassLoader(...) → .classLoader(...)");
                    System.out.println("   - flyway.setLocations(...) → .locations(...)");
                    System.out.println("   - flyway.setValidateOnMigrate(...) → .validateOnMigrate(...)");
                    System.out.println("3. Chain methods and add .load() at the end");
                    
                    // Example transformation output
                    System.out.println("\nExample transformed code:");
                    System.out.println("  Flyway flyway = Flyway.configure()");
                    System.out.println("      .dataSource(dataSource)");
                    System.out.println("      .classLoader(classLoader)");
                    System.out.println("      .locations(locations)");
                    System.out.println("      .validateOnMigrate(validate)");
                    System.out.println("      .load();");
                }
            }
        } catch (SpoonClassNotFoundException e) {
            // Type not in classpath, skip
        }
    }
    
    private boolean isFlywayConstructor(CtConstructorCall constructor) {
        if (constructor.getType() == null) {
            return false;
        }
        String typeName = constructor.getType().getQualifiedName();
        // Match Flyway class (handles both fully qualified and imported)
        return typeName.equals("org.flywaydb.core.Flyway") || 
               typeName.endsWith(".Flyway");
    }
}