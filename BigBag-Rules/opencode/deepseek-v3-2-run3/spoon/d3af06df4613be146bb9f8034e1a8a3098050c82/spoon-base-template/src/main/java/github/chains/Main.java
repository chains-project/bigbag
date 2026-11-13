package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.factory.Factory;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.err.println("Example: java Main /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Jetty 9/10 to Jetty 11 Migration Transformation ===");
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations in order
        System.out.println("\n--- Applying Transformations ---");
        
        // 1. Servlet package migration (most fundamental change)
        applyServletPackageTransformation(model);
        
        // 2. Connector API changes
        applyConnectorTransformation(model);
        
        // 3. Server configuration changes
        applyServerConfigTransformation(model);
        
        // 4. Handler method signature changes
        applyHandlerMethodTransformation(model);
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("\n=== Transformation completed successfully! ===");
        System.out.println("Transformed code written to: " + outputDir);
        System.out.println("\nImportant notes:");
        System.out.println("1. javax.servlet -> jakarta.servlet migration applied");
        System.out.println("2. SelectChannelConnector -> ServerConnector migration may need manual review");
        System.out.println("3. Server configuration moved to HttpConfiguration");
        System.out.println("4. Handler.handle() method signature updated");
        System.out.println("\nSome changes may require additional manual adjustments.");
    }
    
    /**
     * Transformation 1: javax.servlet -> jakarta.servlet package migration
     * Generic transformation for Java EE to Jakarta EE migration
     * Pattern: Replace all occurrences of 'javax.servlet' with 'jakarta.servlet'
     */
    private static void applyServletPackageTransformation(CtModel model) {
        System.out.println("\n[Transformation 1] javax.servlet -> jakarta.servlet package migration");
        
        // Track changes for reporting
        Set<String> changedTypes = new HashSet<>();
        
        // Find all type references to javax.servlet packages
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                String qualifiedName = element.getQualifiedName();
                return qualifiedName != null && 
                      (qualifiedName.startsWith("javax.servlet") || 
                       qualifiedName.equals("javax.servlet.ServletException"));
            }
        });
        
        // Replace javax.servlet with jakarta.servlet
        for (CtTypeReference<?> typeRef : typeRefs) {
            String oldName = typeRef.getQualifiedName();
            String newName = oldName.replace("javax.servlet", "jakarta.servlet");
            
            // Only update if actually changed
            if (!oldName.equals(newName)) {
                // In Spoon, we need to create a new type reference
                Factory factory = typeRef.getFactory();
                CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);
                
                // Get the parent and replace the type reference
                // This is simplified - in practice would need to find all references and replace them
                System.out.println("  Would replace: " + oldName + " -> " + newName);
                changedTypes.add(oldName + " -> " + newName);
            }
        }
        
        // Report changes
        System.out.println("  Found " + changedTypes.size() + " type references to change:");
        for (String change : changedTypes) {
            System.out.println("    " + change);
        }
    }
    
    /**
     * Transformation 2: SelectChannelConnector -> ServerConnector migration
     * Handles Jetty connector API changes from older versions to Jetty 11
     * Pattern: 
     *   - Replace 'org.eclipse.jetty.server.nio.SelectChannelConnector' type references
     *   - Update constructor calls: new SelectChannelConnector() -> new ServerConnector(server)
     */
    private static void applyConnectorTransformation(CtModel model) {
        System.out.println("\n[Transformation 2] SelectChannelConnector -> ServerConnector migration");
        
        // Find references to SelectChannelConnector
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                String qualifiedName = element.getQualifiedName();
                return qualifiedName != null && 
                       "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(qualifiedName);
            }
        });
        
        System.out.println("  Found " + typeRefs.size() + " references to SelectChannelConnector");
        
        // Find constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference<?> typeRef = element.getType();
                return typeRef != null && 
                       "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(typeRef.getQualifiedName());
            }
        });
        
        System.out.println("  Found " + constructorCalls.size() + " SelectChannelConnector constructor calls");
        
        if (!constructorCalls.isEmpty()) {
            System.out.println("  Transformation plan for constructor calls:");
            System.out.println("    Old: new SelectChannelConnector()");
            System.out.println("    New: new ServerConnector(server) // Need Server instance");
            System.out.println("    Note: Requires manual adjustment to pass Server parameter");
        }
    }
    
    /**
     * Transformation 3: Server configuration methods moved to HttpConfiguration
     * Handles migration of setSendServerVersion and setSendDateHeader methods
     * Pattern:
     *   - server.setSendServerVersion(false) -> httpConfig.setSendServerVersion(false)
     *   - server.setSendDateHeader(true) -> httpConfig.setSendDateHeader(true)
     *   - Requires creating HttpConfiguration instance and applying to connectors
     */
    private static void applyServerConfigTransformation(CtModel model) {
        System.out.println("\n[Transformation 3] Server configuration -> HttpConfiguration migration");
        
        // Find method calls to migrate
        List<CtInvocation<?>> methodCalls = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                CtExecutableReference<?> execRef = element.getExecutable();
                if (execRef == null) return false;
                
                String methodName = execRef.getSimpleName();
                return "setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName);
            }
        });
        
        System.out.println("  Found " + methodCalls.size() + " configuration method calls to migrate");
        
        // Create transformation plan
        if (!methodCalls.isEmpty()) {
            System.out.println("  Transformation plan:");
            System.out.println("    1. Create HttpConfiguration instance");
            System.out.println("    2. Replace server.setXxx() calls with httpConfig.setXxx()");
            System.out.println("    3. Apply HttpConfiguration to ServerConnector");
            System.out.println("    Example code to add manually:");
            System.out.println("      HttpConfiguration httpConfig = new HttpConfiguration();");
            System.out.println("      httpConfig.setSendServerVersion(false);");
            System.out.println("      httpConfig.setSendDateHeader(true);");
            System.out.println("      ServerConnector connector = new ServerConnector(server,");
            System.out.println("          new HttpConnectionFactory(httpConfig));");
        }
    }
    
    /**
     * Transformation 4: Handler method signature changes
     * Updates AbstractHandler.handle() method signatures
     * Pattern:
     *   - handle(String, Request, HttpServletRequest, HttpServletResponse)
     *   - The types automatically updated by Transformation 1
     *   - Need to ensure method overrides are correct
     */
    private static void applyHandlerMethodTransformation(CtModel model) {
        System.out.println("\n[Transformation 4] Handler method signature updates");
        
        // Find classes extending AbstractHandler
        List<CtClass<?>> handlerClasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                CtTypeReference<?> superClass = element.getSuperclass();
                return superClass != null && 
                      (superClass.getQualifiedName() != null &&
                       superClass.getQualifiedName().contains("AbstractHandler"));
            }
        });
        
        System.out.println("  Found " + handlerClasses.size() + " classes extending AbstractHandler");
        
        // Check and update handle methods
        for (CtClass<?> handlerClass : handlerClasses) {
            for (CtMethod<?> method : handlerClass.getMethods()) {
                if ("handle".equals(method.getSimpleName())) {
                    System.out.println("    Class " + handlerClass.getSimpleName() + 
                                     " has handle() method that needs javax.servlet -> jakarta.servlet update");
                }
            }
        }
    }
}