package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Flyway breaking changes.
 * This transformation updates code that uses the deprecated Flyway constructor
 * to use the new Configuration-based API.
 */
public class Main {
    public static void main(String[] args) {
        try {
            // Parse the source code
            Launcher launcher = new Launcher();
            launcher.addInputResource("/workspace/nem");
            launcher.setSourceOutputDirectory("/workspace/nem-transformed");
            
            // Set up the environment  
            launcher.getEnvironment().setComplianceLevel(8);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(false);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Apply transformations
            transformFlywayCode(model);
            
            // Process and generate the transformed code
            launcher.process();
            
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Transforms Flyway instantiation code to use the new Configuration API.
     */
    private static void transformFlywayCode(CtModel model) {
        // Find all new Flyway() constructor calls that have no arguments 
        // and replace them with new Flyway(FluentConfiguration.configure())
        model.getElements(new TypeFilter<>(CtNewClass.class))
              .stream()
              .filter(nc -> nc.getType() != null)
              .filter(nc -> nc.getType().getQualifiedName().equals("org.flywaydb.core.Flyway"))
              .filter(nc -> nc.getArguments().isEmpty()) // Constructors with no arguments
              .forEach(flywayNewClass -> {
                  // Clone the original and update it to use new API
                  try {
                      CtNewClass newFlywayNewClass = flywayNewClass.clone();
                      newFlywayNewClass.setType(flywayNewClass.getType());
                      
                      // Create the FluentConfiguration.configure() call as the argument
                      CtInvocation configureCall = flywayNewClass.getFactory().createInvocation(
                          flywayNewClass.getFactory().createTypeAccess(
                              flywayNewClass.getFactory().createCtTypeReference("org.flywaydb.core.api.configuration.FluentConfiguration")),
                          flywayNewClass.getFactory().createExecutableReference().setSimpleName("configure")
                      );
                      
                      newFlywayNewClass.setArguments(List.of(configureCall));
                      
                      // Replace the original Flyway instantiation with the new one
                      flywayNewClass.replace(newFlywayNewClass);
                  } catch (Exception e) {
                      System.err.println("Error transforming Flyway constructor: " + e.getMessage());
                  }
              });
    }
}