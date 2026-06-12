package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.CtModel;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add processor to fix logback Logger issue
        launcher.addProcessor(new LogbackLoggerFixProcessor());
        
        launcher.run();
        
        System.out.println("Transformation complete!");
    }
    
    static class LogbackLoggerFixProcessor extends AbstractProcessor<CtType<?>> {
        @Override
        public boolean isToBeProcessed(CtType<?> type) {
            // Process all classes
            return true;
        }
        
        @Override
        public void process(CtType<?> type) {
            // Remove imports of ch.qos.logback.classic.Logger
            removeLoggerImports(type);
            
            // Find and fix setLevel calls
            fixSetLevelCalls(type);
        }
        
        private void removeLoggerImports(CtType<?> type) {
            List<CtImport> imports = type.getImports();
            for (int i = imports.size() - 1; i >= 0; i--) {
                CtImport imp = imports.get(i);
                if (imp.toString().contains("ch.qos.logback.classic.Logger")) {
                    imports.remove(i);
                    System.out.println("Removed Logger import from: " + type.getQualifiedName());
                }
            }
        }
        
        private void fixSetLevelCalls(CtType<?> type) {
            // Find all method invocations in the type
            List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
            
            for (CtInvocation<?> invocation : invocations) {
                // Check if this is a setLevel method call
                if ("setLevel".equals(invocation.getExecutable().getSimpleName())) {
                    // Check if target is a cast to Logger
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        String targetStr = target.toString();
                        if (targetStr.contains("(Logger)") && targetStr.contains("LoggerFactory.getLogger")) {
                            System.out.println("Found logback Logger cast at: " + invocation.getPosition());
                            
                            // Get the level argument
                            List<CtExpression<?>> args = invocation.getArguments();
                            if (args.size() == 1) {
                                CtExpression<?> levelArg = args.get(0);
                                
                                // Create replacement code
                                Factory factory = getFactory();
                                
                                // Extract the getLogger call - look for LoggerFactory.getLogger
                                // This is a simplified approach
                                String targetCode = target.toString();
                                String getLoggerCall = extractGetLoggerCall(targetCode);
                                
                                // Replace Logger.ROOT_LOGGER_NAME with "ROOT"
                                if (getLoggerCall.contains("Logger.ROOT_LOGGER_NAME")) {
                                    getLoggerCall = getLoggerCall.replace("Logger.ROOT_LOGGER_NAME", "\"ROOT\"");
                                }
                                
                                // Build replacement code
                                String replacementCode = buildReplacementCode(getLoggerCall, levelArg.toString());
                                
                                // Replace the statement
                                CtStatement statement = invocation.getParent(CtStatement.class);
                                if (statement != null) {
                                    CtCodeSnippetStatement replacement = factory.createCodeSnippetStatement(replacementCode);
                                    statement.replace(replacement);
                                    System.out.println("Replaced setLevel call with reflection");
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private String extractGetLoggerCall(String targetCode) {
            // Simplified extraction - in real implementation would use AST traversal
            // Remove the cast part: ((Logger) LoggerFactory.getLogger(...)) -> LoggerFactory.getLogger(...)
            return targetCode.replaceFirst("^\\(\\s*\\(\\s*Logger\\s*\\)\\s*", "").replaceFirst("\\s*\\)$", "");
        }
        
        private String buildReplacementCode(String getLoggerCall, String levelArg) {
            return "{\n" +
                   "  Object logger = " + getLoggerCall + ";\n" +
                   "  try {\n" +
                   "    java.lang.reflect.Method setLevelMethod = logger.getClass().getMethod(\"setLevel\", ch.qos.logback.classic.Level.class);\n" +
                   "    setLevelMethod.invoke(logger, " + levelArg + ");\n" +
                   "  } catch (Exception e) {\n" +
                   "    // Ignore reflection failure\n" +
                   "  }\n" +
                   "}";
        }
    }
}