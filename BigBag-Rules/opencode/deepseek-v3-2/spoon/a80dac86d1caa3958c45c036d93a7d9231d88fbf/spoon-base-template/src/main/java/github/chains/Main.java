package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.Level;

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
        launcher.getEnvironment().setLevel("INFO");
        
        // Add processor to handle logback Logger casting issues
        launcher.addProcessor(new LogBackLoggerProcessor());
        
        launcher.run();
    }
    
    static class LogBackLoggerProcessor extends AbstractProcessor<CtLocalVariable<?>> {
        @Override
        public void process(CtLocalVariable<?> variable) {
            // Check if this is a variable declaration with cast to ch.qos.logback.classic.Logger
            CtTypeReference<?> variableType = variable.getType();
            
            if (variableType != null && 
                variableType.getQualifiedName() != null &&
                variableType.getQualifiedName().equals("ch.qos.logback.classic.Logger")) {
                
                // Check if this is a cast from LoggerFactory.getLogger()
                if (variable.getDefaultExpression() != null) {
                    String expressionStr = variable.getDefaultExpression().toString();
                    
                    // Pattern: (Logger) LoggerFactory.getLogger(...)
                    if (expressionStr.contains("LoggerFactory.getLogger")) {
                        System.out.println("Found logback Logger cast at: " + variable.getPosition());
                        
                        // Get the class containing this variable
                        CtClass<?> parentClass = variable.getParent(CtClass.class);
                        if (parentClass != null) {
                            System.out.println("  In class: " + parentClass.getQualifiedName());
                        }
                        
                        // Check what methods are called on this logger variable
                        checkLoggerUsage(variable);
                    }
                }
            }
        }
        
        private void checkLoggerUsage(CtLocalVariable<?> loggerVar) {
            // Get the method containing this variable
            CtMethod<?> parentMethod = loggerVar.getParent(CtMethod.class);
            if (parentMethod == null) return;
            
            // Find all invocations on this variable
            List<CtInvocation<?>> invocations = parentMethod.getElements(new TypeFilter<>(CtInvocation.class));
            
            for (CtInvocation<?> invocation : invocations) {
                if (invocation.getTarget() != null && 
                    invocation.getTarget().toString().equals(loggerVar.getSimpleName())) {
                    
                    String methodName = invocation.getExecutable().getSimpleName();
                    System.out.println("  Logger method called: " + methodName);
                    
                    // Handle specific methods that are logback-specific
                    if (methodName.equals("setLevel") || methodName.equals("addAppender") || 
                        methodName.equals("detachAppender") || methodName.equals("getAppender")) {
                        
                        System.out.println("  WARNING: This code uses logback-specific method: " + methodName);
                        System.out.println("  This may not work with slf4j-api 1.7.x when using logback-classic 1.4.3");
                        System.out.println("  Consider updating to slf4j-api 2.0.x or refactoring test code");
                        
                        // TODO: Add comment to code
                        // We could add a comment here, but that requires modifying the AST
                    }
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtLocalVariable<?> candidate) {
            // Only process if it's a Logger type
            if (candidate.getType() == null) return false;
            String typeName = candidate.getType().getQualifiedName();
            return typeName != null && typeName.equals("ch.qos.logback.classic.Logger");
        }
    }
}