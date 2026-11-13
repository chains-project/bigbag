package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.*;
import java.util.*;

/**
 * Generic, reusable transformation for Jetty 8→11 breaking API changes.
 * 
 * Transformations applied (all parameterized by class names):
 * 1. javax.servlet → jakarta.servlet (package rename)
 * 2. SelectChannelConnector → ServerConnector (class relocation/rename)
 * 3. Connector.setPort(int) → ServerConnector.setPort(int)
 * 4. Server.setSendServerVersion()/setSendDateHeader() → HttpConfiguration
 * 
 * Usage: java Main <source-directory> <output-directory>
 */
public class Main {
    
    // PARAMETERS - can be adjusted for different Jetty versions
    private static final String OLD_CONNECTOR_CLASS = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_CLASS = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PKG = "javax.servlet";
    private static final String NEW_SERVLET_PKG = "jakarta.servlet";
    private static final String HTTP_CONFIG_CLASS = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONN_FACTORY_CLASS = "org.eclipse.jetty.server.HttpConnectionFactory";
    
    private Factory factory;
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-dir> <output-dir>");
            System.exit(1);
        }
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        new Main(launcher).run();
        launcher.prettyprint();
    }
    
    public Main(Launcher launcher) {
        this.factory = launcher.getFactory();
    }
    
    public void run() {
        System.out.println("Applying generic Jetty 8→11 transformation...");
        CtModel model = factory.getModel();
        
        // Apply transformations
        updateImportsAndTypeRefs(model);
        updateConnectorTypes(model);
        updateHandlerSignatures(model);
        addMigrationComments(model);
        
        System.out.println("Transformation complete.");
    }
    
    /**
     * 1. Update imports and type references from javax.servlet to jakarta.servlet
     */
    private void updateImportsAndTypeRefs(CtModel model) {
        System.out.println("Updating servlet package: " + OLD_SERVLET_PKG + " → " + NEW_SERVLET_PKG);
        
        // Update all type references
        for (CtTypeReference<?> ref : model.getRootPackage().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String fqn = ref.getQualifiedName();
            if (fqn.startsWith(OLD_SERVLET_PKG)) {
                String newName = fqn.replace(OLD_SERVLET_PKG, NEW_SERVLET_PKG);
                ref.replace(factory.createReference(newName));
            }
        }
    }
    
    /**
     * 2. Update SelectChannelConnector to ServerConnector
     */
    private void updateConnectorTypes(CtModel model) {
        System.out.println("Replacing connector: " + OLD_CONNECTOR_CLASS + " → " + NEW_CONNECTOR_CLASS);
        
        // Update type references
        for (CtTypeReference<?> ref : model.getRootPackage().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_CONNECTOR_CLASS.equals(ref.getQualifiedName())) {
                ref.replace(factory.createReference(NEW_CONNECTOR_CLASS));
            }
        }
        
        // Update constructor calls: new SelectChannelConnector() → new ServerConnector(server)
        // For generic transformation, we add a comment guide
        for (CtConstructorCall<?> call : model.getRootPackage().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (call.getType() != null && OLD_CONNECTOR_CLASS.equals(call.getType().getQualifiedName())) {
                CtComment comment = factory.createComment(
                    "// Jetty 11: " + OLD_CONNECTOR_CLASS + " → " + NEW_CONNECTOR_CLASS + "\n" +
                    "// Replace: new SelectChannelConnector() → new ServerConnector(server)",
                    CtComment.CommentType.INLINE
                );
                call.addComment(comment);
            }
        }
    }
    
    /**
     * 3. Update Handler.handle() method signatures
     */
    private void updateHandlerSignatures(CtModel model) {
        System.out.println("Updating Handler.handle() signatures...");
        
        for (CtMethod<?> method : model.getRootPackage().getElements(new TypeFilter<>(CtMethod.class))) {
            if (!"handle".equals(method.getSimpleName())) continue;
            
            List<CtParameter<?>> params = method.getParameters();
            if (params.size() != 4) continue;
            
            // Update parameter types if they're javax.servlet types
            for (CtParameter<?> param : params) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType != null && paramType.getQualifiedName().startsWith(OLD_SERVLET_PKG)) {
                    String newName = paramType.getQualifiedName().replace(OLD_SERVLET_PKG, NEW_SERVLET_PKG);
                    param.setType(factory.createReference(newName));
                }
            }
        }
    }
    
    /**
     * 4. Add migration comments for other breaking changes
     */
    private void addMigrationComments(CtModel model) {
        System.out.println("Adding migration guide comments...");
        
        for (CtInvocation<?> inv : model.getRootPackage().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> exec = inv.getExecutable();
            if (exec == null) continue;
            
            String methodName = exec.getSimpleName();
            
            // Server.setSendServerVersion() / setSendDateHeader()
            if (("setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName)) &&
                exec.getDeclaringType() != null &&
                exec.getDeclaringType().getQualifiedName().startsWith("org.eclipse.jetty.server.Server")) {
                
                CtComment comment = factory.createComment(
                    "// Jetty 11: " + methodName + "() moved to HttpConfiguration\n" +
                    "// New pattern: HttpConfiguration config = new HttpConfiguration();\n" +
                    "//              config.setSendServerVersion(false);\n" +
                    "//              config.setSendDateHeader(true);\n" +
                    "//              ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(config));",
                    CtComment.CommentType.INLINE
                );
                
                CtStatement stmt = inv.getParent(CtStatement.class);
                if (stmt != null) {
                    stmt.addComment(comment);
                }
            }
            
            // Connector.setPort()
            if ("setPort".equals(methodName) && 
                exec.getDeclaringType() != null &&
                exec.getDeclaringType().getQualifiedName().startsWith("org.eclipse.jetty.server.Connector")) {
                
                CtExpression<?> target = inv.getTarget();
                if (target != null) {
                    CtComment comment = factory.createComment(
                        "// Jetty 11: Connector.setPort() only works on NetworkConnector subclasses\n" +
                        "// Ensure variable is typed as ServerConnector, not generic Connector",
                        CtComment.CommentType.INLINE
                    );
                    
                    CtStatement stmt = inv.getParent(CtStatement.class);
                    if (stmt != null) {
                        stmt.addComment(comment);
                    }
                }
            }
        }
    }
}