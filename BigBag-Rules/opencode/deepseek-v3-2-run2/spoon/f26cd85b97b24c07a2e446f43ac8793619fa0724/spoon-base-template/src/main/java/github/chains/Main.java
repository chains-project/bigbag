package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Filter;
import java.util.*;

/**
 * Generic Spoon transformation for migrating from Jetty 8/9 to Jetty 11.
 * This transformation detects and marks breaking API changes for manual fixing.
 * 
 * Breaking Change Patterns:
 * 1. Old: org.eclipse.jetty.server.nio.SelectChannelConnector
 *    New: org.eclipse.jetty.server.ServerConnector
 *    
 * 2. Old: Server.setSendServerVersion(boolean)
 *    New: HttpConfiguration.setSendServerVersion(boolean)
 *    
 * 3. Old: Server.setSendDateHeader(boolean)  
 *    New: HttpConfiguration.setSendDateHeader(boolean)
 *    
 * 4. Old: Connector.setPort(int)
 *    New: Set port in ServerConnector constructor or via NetworkConnector
 */
public class Main {
    
    // Configuration - these define the breaking change patterns
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    private static final String CONNECTOR_TYPE = "org.eclipse.jetty.server.Connector";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("This transformation identifies Jetty 8/9 -> Jetty 11 breaking changes.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Analyzing Jetty API usage in: " + sourceDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            spoon.reflect.factory.Factory factory = launcher.getFactory();
            
            // Analyze and mark breaking changes
            int issuesFound = analyzeJettyBreakingChanges(model, factory);
            
            if (issuesFound > 0) {
                // Output the annotated code with TODO comments
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                
                System.out.println("\nAnalysis completed!");
                System.out.println("Found " + issuesFound + " Jetty 11 compatibility issue(s)");
                System.out.println("\nCode has been annotated with TODO comments.");
                System.out.println("\nKey Migration Steps:");
                System.out.println("1. Replace SelectChannelConnector with ServerConnector");
                System.out.println("2. Move server config methods to HttpConfiguration");
                System.out.println("3. Set port in ServerConnector constructor");
            } else {
                System.out.println("\nNo Jetty compatibility issues found!");
            }
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int analyzeJettyBreakingChanges(CtModel model, spoon.reflect.factory.Factory factory) {
        int issueCount = 0;
        
        // Analyze each type in the model
        for (CtType<?> type : model.getAllTypes()) {
            // Check for old connector usage
            issueCount += checkForOldConnector(type, factory);
            
            // Check for deprecated Server methods
            issueCount += checkForDeprecatedServerMethods(type, factory);
            
            // Check for Connector.setPort() calls
            issueCount += checkForConnectorSetPortCalls(type, factory);
        }
        
        return issueCount;
    }
    
    private static int checkForOldConnector(CtType<?> type, spoon.reflect.factory.Factory factory) {
        int count = 0;
        
        // Check for imports of old connector
        CtCompilationUnit cu = type.getPosition().getCompilationUnit();
        if (cu != null) {
            for (CtImport imp : cu.getImports()) {
                String importStr = imp.toString();
                if (importStr.contains(OLD_CONNECTOR)) {
                    System.out.println("Found import of deprecated class: " + importStr + " in " + type.getQualifiedName());
                    
                    // Add file-level comment about the migration
                    CtComment comment = factory.createComment(
                        "TODO: Jetty 11 Migration Required\n" +
                        "Replace: import " + OLD_CONNECTOR + ";\n" +
                        "With:    import " + NEW_CONNECTOR + ";\n" +
                        "And update constructor calls accordingly.",
                        CtComment.CommentType.FILE
                    );
                    cu.addComment(comment);
                    count++;
                }
            }
        }
        
        // Check for constructor calls
        List<CtConstructorCall<?>> oldConstructorCalls = type.getElements(
            new Filter<CtConstructorCall<?>>() {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    CtTypeReference<?> typeRef = element.getType();
                    return typeRef != null && OLD_CONNECTOR.equals(typeRef.getQualifiedName());
                }
            });
        
        for (CtConstructorCall<?> call : oldConstructorCalls) {
            System.out.println("Found deprecated constructor: new " + OLD_CONNECTOR + "() at " + call.getPosition());
            
            // Add inline comment
            CtComment comment = factory.createInlineComment(
                "TODO: Jetty 11 - Replace with new " + NEW_CONNECTOR + "(server, port)\n" +
                "Port must be specified in constructor or via NetworkConnector.setPort()"
            );
            
            CtStatement parent = call.getParent(CtStatement.class);
            if (parent != null) {
                parent.addComment(comment);
            }
            count++;
        }
        
        return count;
    }
    
    private static int checkForDeprecatedServerMethods(CtType<?> type, spoon.reflect.factory.Factory factory) {
        int count = 0;
        
        // Methods that moved from Server to HttpConfiguration
        String[] deprecatedMethods = {"setSendServerVersion", "setSendDateHeader"};
        
        for (String methodName : deprecatedMethods) {
            List<CtInvocation<?>> calls = type.getElements(
                new Filter<CtInvocation<?>>() {
                    @Override
                    public boolean matches(CtInvocation<?> element) {
                        CtExecutableReference<?> execRef = element.getExecutable();
                        if (execRef == null) return false;
                        
                        if (!methodName.equals(execRef.getSimpleName())) {
                            return false;
                        }
                        
                        // Check if target might be a Server instance
                        CtExpression<?> target = element.getTarget();
                        if (target == null) return false;
                        
                        CtTypeReference<?> targetType = target.getType();
                        return targetType != null && 
                               (SERVER_TYPE.equals(targetType.getQualifiedName()) ||
                                targetType.isSubtypeOf(factory.Type().createReference(SERVER_TYPE)));
                    }
                });
            
            for (CtInvocation<?> call : calls) {
                System.out.println("Found deprecated Server." + methodName + "() at " + call.getPosition());
                
                CtComment comment = factory.createInlineComment(
                    "TODO: Jetty 11 - Server." + methodName + "() moved to HttpConfiguration\n" +
                    "Create HttpConfiguration instance and call " + methodName + "() on it\n" +
                    "Then pass to ServerConnector via HttpConnectionFactory"
                );
                
                CtStatement parent = call.getParent(CtStatement.class);
                if (parent != null) {
                    parent.addComment(comment);
                }
                count++;
            }
        }
        
        return count;
    }
    
    private static int checkForConnectorSetPortCalls(CtType<?> type, spoon.reflect.factory.Factory factory) {
        int count = 0;
        
        List<CtInvocation<?>> setPortCalls = type.getElements(
            new Filter<CtInvocation<?>>() {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    CtExecutableReference<?> execRef = element.getExecutable();
                    if (execRef == null) return false;
                    
                    if (!"setPort".equals(execRef.getSimpleName())) {
                        return false;
                    }
                    
                    // Check if target might be a Connector
                    CtExpression<?> target = element.getTarget();
                    if (target == null) return false;
                    
                    CtTypeReference<?> targetType = target.getType();
                    if (targetType == null) return false;
                    
                    // Check if it's a Connector or unknown type (could be Connector due to type erasure)
                    return targetType.getQualifiedName() != null && 
                           (targetType.getQualifiedName().contains("Connector") ||
                            CONNECTOR_TYPE.equals(targetType.getQualifiedName()) ||
                            targetType.isSubtypeOf(factory.Type().createReference(CONNECTOR_TYPE)));
                }
            });
        
        for (CtInvocation<?> call : setPortCalls) {
            System.out.println("Found Connector.setPort() call at " + call.getPosition());
            
            CtComment comment = factory.createInlineComment(
                "TODO: Jetty 11 - Connector.setPort() removed\n" +
                "Set port in ServerConnector constructor: new ServerConnector(server, port)\n" +
                "Or cast to NetworkConnector and call setPort()"
            );
            
            CtStatement parent = call.getParent(CtStatement.class);
            if (parent != null) {
                parent.addComment(comment);
            }
            count++;
        }
        
        return count;
    }
}