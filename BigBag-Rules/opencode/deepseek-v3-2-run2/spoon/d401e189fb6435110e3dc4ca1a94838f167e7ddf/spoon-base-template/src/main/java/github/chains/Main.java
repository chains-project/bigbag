package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.processing.AbstractProcessor;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false); // Need classpath for proper analysis
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        try {
            CtModel model = launcher.buildModel();
            
            // First pass: Add helper method to classes that need it
            AddHelperMethodProcessor helperProcessor = new AddHelperMethodProcessor();
            model.processWith(helperProcessor);
            
            // Second pass: Fix setLevel calls
            FixSetLevelProcessor fixProcessor = new FixSetLevelProcessor();
            model.processWith(fixProcessor);
            
            // Output transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed! Fixed " + fixProcessor.fixedCount + " setLevel calls.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor to add a helper method to classes that use Logger.setLevel.
     */
    static class AddHelperMethodProcessor extends AbstractProcessor<CtClass<?>> {
        private Set<String> classesNeedingHelper = new HashSet<>();
        
        @Override
        public boolean isToBeProcessed(CtClass<?> clazz) {
            // Check if this class contains Logger.setLevel calls
            // We'll collect class names in the fix processor
            return true;
        }
        
        @Override
        public void process(CtClass<?> clazz) {
            // This processor just marks classes that need the helper
            // The actual addition happens in FixSetLevelProcessor
        }
    }
    
    /**
     * Processor to fix Logger.setLevel calls by replacing them with
     * reflection-based calls that avoid the LoggingEventAware interface.
     */
    static class FixSetLevelProcessor extends AbstractProcessor<CtInvocation<?>> {
        int fixedCount = 0;
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            // Check for setLevel method calls
            CtExecutableReference<?> exec = invocation.getExecutable();
            if (exec == null || !"setLevel".equals(exec.getSimpleName())) {
                return false;
            }
            
            // Check if this is called on a Logger instance
            // by checking the declaring type
            CtTypeReference<?> declaringType = exec.getDeclaringType();
            if (declaringType == null) {
                return false;
            }
            
            String declaringTypeName = declaringType.getQualifiedName();
            return "ch.qos.logback.classic.Logger".equals(declaringTypeName);
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            System.out.println("Found Logger.setLevel at: " + invocation.getPosition());
            
            try {
                // Get the target expression and level argument
                CtExpression<?> target = invocation.getTarget();
                List<CtExpression<?>> args = invocation.getArguments();
                if (args.isEmpty()) {
                    return;
                }
                
                CtExpression<?> levelArg = args.get(0);
                
                // Create replacement using reflection
                // Pattern: setLogLevelViaReflection(loggerExpr, level)
                
                // First, ensure the containing class has the helper method
                CtClass<?> containingClass = invocation.getParent(CtClass.class);
                if (containingClass != null) {
                    addHelperMethodIfNeeded(containingClass);
                }
                
                // Create call to helper method
                String helperCall = createHelperCall(target, levelArg);
                CtStatement replacement = getFactory().createCodeSnippetStatement(helperCall);
                
                // Replace the invocation
                if (invocation.getParent() instanceof CtStatement) {
                    invocation.getParent().replace(replacement);
                } else if (invocation.getParent() instanceof CtBlock) {
                    // The invocation itself is a statement in a block
                    replacement.replace(invocation);
                }
                
                fixedCount++;
                System.out.println("Successfully fixed setLevel call");
                
            } catch (Exception e) {
                System.err.println("Error fixing setLevel call: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        private void addHelperMethodIfNeeded(CtClass<?> clazz) {
            // Check if helper method already exists
            String helperName = "setLogLevelViaReflection";
            boolean hasHelper = clazz.getMethods().stream()
                .anyMatch(m -> helperName.equals(m.getSimpleName()));
            
            if (!hasHelper) {
                // Add helper method
                CtMethod<?> helperMethod = getFactory().createMethod();
                helperMethod.addModifier(ModifierKind.PRIVATE);
                helperMethod.addModifier(ModifierKind.STATIC);
                helperMethod.setType(getFactory().Type().createReference(void.class));
                helperMethod.setSimpleName(helperName);
                
                // Add parameters
                helperMethod.addParameter(getFactory().createParameter(
                    helperMethod,
                    getFactory().Type().createReference(Object.class),
                    "logger"
                ));
                helperMethod.addParameter(getFactory().createParameter(
                    helperMethod,
                    getFactory().Type().createReference("ch.qos.logback.classic.Level"),
                    "level"
                ));
                
                // Create method body
                String body = 
                    "if (logger == null || level == null) return;\n" +
                    "try {\n" +
                    "    java.lang.reflect.Method setLevelMethod = logger.getClass().getMethod(\"setLevel\", ch.qos.logback.classic.Level.class);\n" +
                    "    setLevelMethod.invoke(logger, level);\n" +
                    "} catch (Exception e) {\n" +
                    "    throw new RuntimeException(\"Failed to set log level via reflection\", e);\n" +
                    "}";
                
                helperMethod.setBody(getFactory().createCodeSnippetStatement(body));
                clazz.addMethod(helperMethod);
                
                System.out.println("Added helper method to class: " + clazz.getQualifiedName());
            }
        }
        
        private String createHelperCall(CtExpression<?> loggerExpr, CtExpression<?> levelExpr) {
            return "setLogLevelViaReflection(" + loggerExpr.toString() + ", " + levelExpr.toString() + ")";
        }
    }
}