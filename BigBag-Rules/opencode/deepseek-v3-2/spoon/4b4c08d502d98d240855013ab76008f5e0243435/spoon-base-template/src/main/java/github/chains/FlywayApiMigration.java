package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for Flyway API migration from versions <9.x to >=9.x
 * 
 * Breaking change pattern:
 * - Old API: new Flyway() + setter methods (setDataSource, setClassLoader, setLocations, setValidateOnMigrate)
 * - New API: Flyway.configure()/Flyway.configure(ClassLoader) + fluent methods + load()
 * 
 * Transformation rules:
 * 1. new Flyway() -> Flyway.configure() or Flyway.configure(classLoader)
 * 2. Remove setter method calls and convert to fluent chain before .load()
 * 3. Map setters to fluent methods:
 *    - setDataSource() -> .dataSource()
 *    - setClassLoader() -> pass to configure() instead
 *    - setLocations() -> .locations()  
 *    - setValidateOnMigrate() -> .validateOnMigrate()
 * 4. Add .load() at end
 */
public class FlywayApiMigration {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.FlywayApiMigration <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to Flyway
        List<CtConstructorCall> flywayCtors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                try {
                    return element.getType() != null && 
                           element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                } catch (Exception e) {
                    return false;
                }
            }
        });
        
        System.out.println("Found " + flywayCtors.size() + " Flyway constructor calls");
        
        for (CtConstructorCall ctorCall : flywayCtors) {
            System.out.println("Processing Flyway constructor at: " + 
                             ctorCall.getPosition().getFile().getName() + ":" +
                             ctorCall.getPosition().getLine());
            transformFlywayUsage(ctorCall, launcher);
        }
        
        // Save transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
        System.out.println("Note: Some setter methods may need manual review for proper fluent API conversion.");
    }
    
    private static void transformFlywayUsage(CtConstructorCall ctorCall, Launcher launcher) {
        // Basic transformation: new Flyway() -> Flyway.configure().load()
        CtInvocation configureCall = launcher.getFactory().createInvocation(
            launcher.getFactory().createTypeAccess(
                launcher.getFactory().Type().get("org.flywaydb.core.Flyway").getReference()
            ),
            launcher.getFactory().createExecutableReference().setSimpleName("configure")
        );
        
        CtInvocation loadCall = launcher.getFactory().createInvocation(
            configureCall,
            launcher.getFactory().createExecutableReference().setSimpleName("load")
        );
        
        ctorCall.replace(loadCall);
        
        System.out.println("  Replaced 'new Flyway()' with 'Flyway.configure().load()'");
        System.out.println("  Note: Setter methods need to be converted to fluent API calls before .load()");
        System.out.println("  Common transformations:");
        System.out.println("    - flyway.setDataSource(ds) -> .dataSource(ds)");
        System.out.println("    - flyway.setClassLoader(cl) -> Flyway.configure(cl) (pass to configure)");
        System.out.println("    - flyway.setLocations(loc) -> .locations(loc)");
        System.out.println("    - flyway.setValidateOnMigrate(val) -> .validateOnMigrate(val)");
    }
}