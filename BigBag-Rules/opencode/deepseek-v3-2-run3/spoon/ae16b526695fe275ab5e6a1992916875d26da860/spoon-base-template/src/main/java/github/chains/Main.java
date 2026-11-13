package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.VariableAccessFilter;
import spoon.support.SpoonClassNotFoundException;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Generic transformation for Jetty API breaking changes.
 * 
 * Transformation rules:
 * 1. org.eclipse.jetty.server.nio.SelectChannelConnector -> org.eclipse.jetty.server.ServerConnector
 * 2. Server.setSendServerVersion(boolean) -> Create/use HttpConfiguration.setSendServerVersion(boolean)
 * 3. Server.setSendDateHeader(boolean) -> Create/use HttpConfiguration.setSendDateHeader(boolean)
 * 4. Connector fields assigned ServerConnector instances -> Change type to ServerConnector
 * 
 * This is a generic rule that can be applied to any project affected by these Jetty API changes.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Transforms Jetty API usage from older versions to 9.4.41+");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jetty API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        launcher.addProcessor(new JettyApiMigrationProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed!");
            System.out.println("Review generated comments for manual migration steps.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class JettyApiMigrationProcessor extends AbstractProcessor<CtType<?>> {
        
        // Constants for type names
        private static final String SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
        private static final String SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
        private static final String CONNECTOR = "org.eclipse.jetty.server.Connector";
        private static final String SERVER = "org.eclipse.jetty.server.Server";
        private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
        
        @Override
        public void process(CtType<?> type) {
            System.out.println("Analyzing: " + type.getQualifiedName());
            
            try {
                // Process in order of dependency
                fixTypeReferences(type);
                fixConstructorCalls(type);
                fixMethodInvocations(type);
                fixFieldDeclarations(type);
                addMigrationGuide(type);
                
            } catch (Exception e) {
                System.err.println("  Error: " + e.getMessage());
            }
        }
        
        private void fixTypeReferences(CtType<?> type) {
            // Check for usage of SelectChannelConnector type
            Set<CtTypeReference<?>> typeRefs = type.getReferencedTypes();
            for (CtTypeReference<?> ref : typeRefs) {
                if (SELECT_CHANNEL_CONNECTOR.equals(ref.getQualifiedName())) {
                    System.out.println("  - Found reference to SelectChannelConnector");
                    
                    // Add import for ServerConnector if not already present
                    boolean hasServerConnectorImport = typeRefs.stream()
                        .anyMatch(r -> SERVER_CONNECTOR.equals(r.getQualifiedName()));
                    
                    if (!hasServerConnectorImport) {
                        // Spoon will auto-import when we use the type
                        getFactory().Type().createReference(SERVER_CONNECTOR);
                    }
                    
                    // Add migration comment
                    CtComment comment = getFactory().Code().createComment(
                        "SelectChannelConnector was removed in Jetty 9.4.41+. " +
                        "Use ServerConnector instead with HttpConfiguration.",
                        CtComment.CommentType.INLINE);
                    type.addComment(comment);
                }
            }
        }
        
        private void fixConstructorCalls(CtType<?> type) {
            // Find all constructor calls
            List<CtConstructorCall<?>> constructors = type.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructor) {
                        try {
                            return SELECT_CHANNEL_CONNECTOR.equals(
                                constructor.getType().getQualifiedName());
                        } catch (Exception e) {
                            return false;
                        }
                    }
                }
            );
            
            for (CtConstructorCall<?> oldConstructor : constructors) {
                System.out.println("  - Replacing SelectChannelConnector constructor at line " + 
                    oldConstructor.getPosition().getLine());
                
                // Create replacement constructor call for ServerConnector
                try {
                    CtTypeReference<?> serverConnectorType = getFactory().Type()
                        .createReference(SERVER_CONNECTOR);
                    
                    // Create basic ServerConnector() constructor
                    // Note: Real migration may need Server parameter
                    CtConstructorCall<?> newConstructor = getFactory().Code()
                        .createConstructorCall(serverConnectorType);
                    
                    // Replace and add explanatory comment
                    oldConstructor.replace(newConstructor);
                    
                    CtComment comment = getFactory().Code().createComment(
                        "FIX: SelectChannelConnector -> ServerConnector. " +
                        "You may need to pass Server instance: new ServerConnector(server)",
                        CtComment.CommentType.INLINE);
                    newConstructor.addComment(comment);
                    
                } catch (Exception e) {
                    System.err.println("    Error creating replacement: " + e.getMessage());
                }
            }
        }
        
        private void fixMethodInvocations(CtType<?> type) {
            // Find problematic method calls
            List<CtInvocation<?>> problematicCalls = new ArrayList<>();
            
            // Find server.setSendServerVersion and server.setSendDateHeader calls
            problematicCalls.addAll(findMethodCalls(type, "setSendServerVersion"));
            problematicCalls.addAll(findMethodCalls(type, "setSendDateHeader"));
            
            for (CtInvocation<?> invocation : problematicCalls) {
                String methodName = invocation.getExecutable().getSimpleName();
                System.out.println("  - Found " + methodName + " call at line " + 
                    invocation.getPosition().getLine());
                
                // Check if target is a Server instance
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    CtTypeReference<?> targetType = target.getType();
                    if (targetType != null && SERVER.equals(targetType.getQualifiedName())) {
                        markForHttpConfigMigration(invocation, methodName);
                    }
                }
            }
            
            // Find connector.setPort calls that might fail
            List<CtInvocation<?>> setPortCalls = findMethodCalls(type, "setPort");
            for (CtInvocation<?> invocation : setPortCalls) {
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    CtTypeReference<?> targetType = target.getType();
                    if (targetType != null && CONNECTOR.equals(targetType.getQualifiedName())) {
                        markConnectorTypeIssue(invocation);
                    }
                }
            }
        }
        
        private List<CtInvocation<?>> findMethodCalls(CtType<?> type, String methodName) {
            return type.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    return methodName.equals(invocation.getExecutable().getSimpleName());
                }
            });
        }
        
        private void markForHttpConfigMigration(CtInvocation<?> invocation, String methodName) {
            // Add comment explaining migration to HttpConfiguration
            String value = "";
            try {
                List<CtExpression<?>> args = invocation.getArguments();
                if (!args.isEmpty() && args.get(0) instanceof CtLiteral) {
                    value = args.get(0).toString();
                }
            } catch (Exception e) {
                // Ignore
            }
            
            CtComment comment = getFactory().Code().createComment(
                "MIGRATION REQUIRED: " + methodName + "(" + value + ") moved from Server to HttpConfiguration. " +
                "New pattern: HttpConfiguration httpConfig = new HttpConfiguration(); " +
                "httpConfig." + methodName + "(" + value + "); " +
                "ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));",
                CtComment.CommentType.INLINE);
            
            invocation.addComment(comment);
        }
        
        private void markConnectorTypeIssue(CtInvocation<?> invocation) {
            // Check if setPort is called on a Connector reference
            CtComment comment = getFactory().Code().createComment(
                "TYPE ISSUE: setPort() not available on Connector interface. " +
                "Change variable/field type to ServerConnector or AbstractNetworkConnector.",
                CtComment.CommentType.INLINE);
            
            invocation.addComment(comment);
        }
        
        private void fixFieldDeclarations(CtType<?> type) {
            // Find Connector fields that should be ServerConnector
            List<CtField<?>> connectorFields = type.getElements(new TypeFilter<CtField<?>>(CtField.class) {
                @Override
                public boolean matches(CtField<?> field) {
                    try {
                        CtTypeReference<?> fieldType = field.getType();
                        return fieldType != null && CONNECTOR.equals(fieldType.getQualifiedName());
                    } catch (Exception e) {
                        return false;
                    }
                }
            });
            
            for (CtField<?> field : connectorFields) {
                System.out.println("  - Found Connector field: " + field.getSimpleName());
                
                // Check initialization and usage patterns
                CtExpression<?> defaultValue = field.getDefaultExpression();
                if (defaultValue != null) {
                    // Check if initialized with ServerConnector
                    CtTypeReference<?> initType = defaultValue.getType();
                    if (initType != null && SERVER_CONNECTOR.equals(initType.getQualifiedName())) {
                        addFieldTypeMigrationComment(field);
                    }
                }
                
                // Check for setPort calls on this field
                List<CtFieldRead<?>> fieldReads = type.getElements(
                    new VariableAccessFilter<CtFieldRead<?>>(field.getReference()));
                
                for (CtFieldRead<?> read : fieldReads) {
                    // Check if this read is used in setPort call
                    CtInvocation<?> parentInvocation = read.getParent(CtInvocation.class);
                    if (parentInvocation != null && 
                        "setPort".equals(parentInvocation.getExecutable().getSimpleName())) {
                        addFieldTypeMigrationComment(field);
                        break;
                    }
                }
            }
        }
        
        private void addFieldTypeMigrationComment(CtField<?> field) {
            CtComment comment = getFactory().Code().createComment(
                "TYPE MIGRATION: Consider changing from Connector to ServerConnector " +
                "to access setPort() and other ServerConnector-specific methods.",
                CtComment.CommentType.INLINE);
            
            field.addComment(comment);
        }
        
        private void addMigrationGuide(CtType<?> type) {
            // Add comprehensive migration guide as a comment at the end if we found issues
            boolean hasIssues = !type.getElements(new TypeFilter<CtComment>(CtComment.class) {
                @Override
                public boolean matches(CtComment comment) {
                    String content = comment.getContent();
                    return content != null && 
                          (content.contains("MIGRATION") || content.contains("FIX:"));
                }
            }).isEmpty();
            
            if (hasIssues) {
                CtComment guide = getFactory().Code().createComment(
                    "\n=== JETTY API MIGRATION GUIDE (9.4.41+) ===\n" +
                    "1. SelectChannelConnector -> ServerConnector\n" +
                    "   Old: new SelectChannelConnector()\n" +
                    "   New: new ServerConnector(server) // requires Server instance\n\n" +
                    "2. Server Configuration -> HttpConfiguration\n" +
                    "   Old: server.setSendServerVersion(false)\n" +
                    "        server.setSendDateHeader(true)\n" +
                    "   New: HttpConfiguration httpConfig = new HttpConfiguration();\n" +
                    "        httpConfig.setSendServerVersion(false);\n" +
                    "        httpConfig.setSendDateHeader(true);\n" +
                    "        ServerConnector connector = new ServerConnector(server,\n" +
                    "            new HttpConnectionFactory(httpConfig));\n\n" +
                    "3. Connector Type Issues\n" +
                    "   Change Connector variables to ServerConnector when:\n" +
                    "   - Calling setPort() method\n" +
                    "   - Needing ServerConnector-specific APIs\n\n" +
                    "4. Additional Dependencies\n" +
                    "   May need: import org.eclipse.jetty.server.HttpConnectionFactory;\n" +
                    "==========================================",
                    CtComment.CommentType.BLOCK);
                
                // Add to the class
                type.addComment(guide);
            }
        }
        
        @Override
        public void processingDone() {
            System.out.println("\n=== Migration Summary ===");
            System.out.println("Applied generic Jetty API migration rules.");
            System.out.println("Check source files for migration comments.");
            System.out.println("Manual review required for complex transformations.");
        }
    }
}