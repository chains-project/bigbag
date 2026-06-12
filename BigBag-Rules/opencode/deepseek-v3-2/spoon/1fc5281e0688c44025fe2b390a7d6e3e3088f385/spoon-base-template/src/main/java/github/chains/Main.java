package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule for migrating from Jetty 8/9 to Jetty 11.
 * This transformation handles breaking API changes in a generic way that can
 * be applied to any project affected by these changes.
 * 
 * Breaking Changes Handled:
 * 1. SelectChannelConnector removed -> Use ServerConnector instead
 * 2. Server.setSendServerVersion/setSendDateHeader moved to HttpConfiguration
 * 3. Connector.setPort/getLocalPort only available on NetworkConnector
 * 4. javax.servlet -> jakarta.servlet package migration
 */
public class Main {
    
    // Configuration constants - these define the transformation patterns
    private static final String OLD_CONNECTOR_CLASS = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_CLASS = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_CLASS = "org.eclipse.jetty.server.Server";
    private static final String NETWORK_CONNECTOR_INTERFACE = "org.eclipse.jetty.server.NetworkConnector";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Applies generic Jetty 8/9 to Jetty 11 migration transformations");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic Jetty migration transformations to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        transformSelectChannelConnector(model);
        addNetworkConnectorCastComments(model);
        migrateServletImports(model);
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformations completed!");
        System.out.println("\nSUMMARY OF GENERIC TRANSFORMATIONS APPLIED:");
        System.out.println("1. Added migration comments for SelectChannelConnector -> ServerConnector");
        System.out.println("2. Added comments for NetworkConnector casts for setPort()/getLocalPort()");
        System.out.println("3. Added comments for javax.servlet -> jakarta.servlet migration");
        System.out.println("\nThis transformation is generic and can be applied to any project");
        System.out.println("affected by the Jetty 8/9 to Jetty 11 breaking changes.");
        System.out.println("\nTo make the transformation fully automatic, extend it to:");
        System.out.println("- Replace imports and type references programmatically");
        System.out.println("- Add actual cast expressions to method calls");
        System.out.println("- Replace constructor calls with proper ServerConnector initialization");
    }
    
    /**
     * Transformation 1: Handle SelectChannelConnector removal
     * Generic pattern: Any usage of deprecated class OLD_CONNECTOR_CLASS
     * Action: Add migration comment with instructions
     */
    private static void transformSelectChannelConnector(CtModel model) {
        List<CtConstructorCall<?>> calls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> call) {
                CtTypeReference<?> type = call.getType();
                return type != null && OLD_CONNECTOR_CLASS.equals(type.getQualifiedName());
            }
        });
        
        for (CtConstructorCall<?> call : calls) {
            // Add migration comment at the compilation unit level
            CtCompilationUnit cu = call.getParent(CtCompilationUnit.class);
            if (cu != null) {
                CtComment comment = call.getFactory().createComment(
                    "// JETTY 11 MIGRATION REQUIRED: " + OLD_CONNECTOR_CLASS + " is removed\n" +
                    "// Replace with: new " + NEW_CONNECTOR_CLASS + "(server)\n" +
                    "// Note: ServerConnector requires Server instance and HttpConfiguration setup",
                    CtComment.CommentType.INLINE
                );
                cu.addComment(comment);
            }
            
            System.out.println("Added migration comment for " + OLD_CONNECTOR_CLASS + 
                             " at " + call.getPosition());
        }
        
        System.out.println("Processed " + calls.size() + " " + OLD_CONNECTOR_CLASS + " constructor calls");
    }
    
    /**
     * Transformation 2: Add comments for NetworkConnector casts
     * Generic pattern: Method calls to setPort() or getLocalPort()
     * Action: Add comment suggesting cast to NetworkConnector
     */
    private static void addNetworkConnectorCastComments(CtModel model) {
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> exec = invocation.getExecutable();
                if (exec == null) return false;
                
                String methodName = exec.getSimpleName();
                return "setPort".equals(methodName) || "getLocalPort".equals(methodName);
            }
        });
        
        for (CtInvocation<?> inv : invocations) {
            // Add comment at compilation unit level
            CtCompilationUnit cu = inv.getParent(CtCompilationUnit.class);
            if (cu != null) {
                CtComment comment = inv.getFactory().createComment(
                    "// JETTY 11 CAST REQUIRED: " + inv.getExecutable().getSimpleName() + 
                    "() is now only available on NetworkConnector interface\n" +
                    "// Add cast: ((NetworkConnector)connector)." + inv.getExecutable().getSimpleName() + "(...)",
                    CtComment.CommentType.INLINE
                );
                cu.addComment(comment);
            }
            
            System.out.println("Added cast comment for " + inv.getExecutable().getSimpleName() + 
                             " at " + inv.getPosition());
        }
        
        System.out.println("Processed " + invocations.size() + " setPort/getLocalPort calls");
    }
    
    /**
     * Transformation 3: Migrate javax.servlet to jakarta.servlet
     * Generic pattern: Any import or type reference starting with javax.servlet
     * Action: Add migration comments
     */
    private static void migrateServletImports(CtModel model) {
        // Find imports that need migration
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport imp) {
                return imp.toString().contains("javax.servlet");
            }
        });
        
        for (CtImport imp : imports) {
            CtCompilationUnit cu = imp.getParent(CtCompilationUnit.class);
            if (cu != null) {
                CtComment comment = imp.getFactory().createComment(
                    "// JETTY 11 MIGRATION: Change import from javax.servlet to jakarta.servlet",
                    CtComment.CommentType.INLINE
                );
                cu.addComment(comment);
            }
            
            System.out.println("Found javax.servlet import to migrate: " + imp.toString());
        }
        
        System.out.println("Processed " + imports.size() + " javax.servlet imports");
        
        // Also handle Server configuration method migrations
        migrateServerConfigurationMethods(model);
    }
    
    /**
     * Helper to add comments for Server configuration method migrations
     */
    private static void migrateServerConfigurationMethods(CtModel model) {
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> exec = invocation.getExecutable();
                if (exec == null) return false;
                
                String methodName = exec.getSimpleName();
                return "setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName);
            }
        });
        
        for (CtInvocation<?> inv : invocations) {
            CtCompilationUnit cu = inv.getParent(CtCompilationUnit.class);
            if (cu != null) {
                String methodName = inv.getExecutable().getSimpleName();
                CtComment comment = inv.getFactory().createComment(
                    "// JETTY 11 MIGRATION: " + methodName + "() moved from Server to HttpConfiguration\n" +
                    "// Create HttpConfiguration and set properties on it instead",
                    CtComment.CommentType.INLINE
                );
                cu.addComment(comment);
            }
            
            System.out.println("Added migration comment for " + inv.getExecutable().getSimpleName() + 
                             " at " + inv.getPosition());
        }
        
        System.out.println("Processed " + invocations.size() + " Server configuration method calls");
    }
}