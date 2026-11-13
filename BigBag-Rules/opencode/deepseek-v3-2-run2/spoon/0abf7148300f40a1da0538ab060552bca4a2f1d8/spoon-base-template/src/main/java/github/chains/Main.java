package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;

import java.io.File;

/**
 * Generic Spoon transformation for JasperReports 6.19.1 breaking API change.
 * 
 * Breaking Change: JRPen.setLineWidth() changed from float to Float parameter
 * Old API: setLineWidth(float lineWidth)
 * New API: setLineWidth(Float lineWidth)
 * 
 * Transformation: Wraps primitive int arguments with Float.valueOf()
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source-directory>");
            System.err.println("Example: java -jar spoon-base.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        File sourceDir = new File(sourcePath);
        
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("Error: Source directory does not exist: " + sourcePath);
            System.exit(1);
        }
        
        System.out.println("Applying JasperReports 6.19.1 API transformation...");
        System.out.println("Source directory: " + sourcePath);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourcePath);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.buildModel();
            
            // Apply transformation
            applyJasperReportsTransformation(launcher);
            
            // Generate transformed code
            launcher.setSourceOutputDirectory(sourcePath);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Applies the JasperReports API transformation:
     * Converts primitive int arguments to Float.valueOf() for setLineWidth calls
     */
    private static void applyJasperReportsTransformation(Launcher launcher) {
        // Find all method invocations
        launcher.getModel()
            .getElements(new TypeFilter<>(CtInvocation.class))
            .forEach(invocation -> {
                // Check if this is a setLineWidth method call
                if (isSetLineWidthCall(invocation)) {
                    // Get the first argument (line width parameter)
                    if (invocation.getArguments().size() == 1) {
                        var argument = invocation.getArguments().get(0);
                        
                        // Check if argument is a primitive int (literal or method call)
                        if (isPrimitiveIntExpression(argument)) {
                            
                            // Wrap primitive int with Float.valueOf()
                            wrapPrimitiveWithFloatValueOf(invocation, argument);
                            
                            System.out.println("Transformed setLineWidth call at: " + 
                                invocation.getPosition().getFile().getName() + 
                                ":" + invocation.getPosition().getLine());
                        }
                    }
                }
            });
    }
    
    /**
     * Checks if the invocation is a call to setLineWidth on a JRPen object
     */
    private static boolean isSetLineWidthCall(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        
        // Match setLineWidth method name
        if (!"setLineWidth".equals(methodName)) {
            return false;
        }
        
        // Check if target type is JRPen or compatible
        // We use a broader pattern to catch all potential setLineWidth calls
        // since the exact type might not be available without classpath
        return true;
    }
    
    /**
     * Checks if the expression returns a primitive int
     */
    private static boolean isPrimitiveIntExpression(Object expression) {
        // Check if it's a literal with int type
        if (expression instanceof CtLiteral && 
            ((CtLiteral<?>) expression).getType() != null &&
            ((CtLiteral<?>) expression).getType().getSimpleName().equals("int")) {
            return true;
        }
        
        // Check if it's a method call that returns int
        if (expression instanceof CtInvocation) {
            CtInvocation<?> methodCall = (CtInvocation<?>) expression;
            String returnType = methodCall.getType().getSimpleName();
            return "int".equals(returnType);
        }
        
        return false;
    }
    
    /**
     * Wraps a primitive int expression with Float.valueOf() call
     */
    private static void wrapPrimitiveWithFloatValueOf(CtInvocation<?> invocation, Object primitiveExpression) {
        try {
            // Create Float.valueOf() invocation
            var factory = invocation.getFactory();
            
            // Create Float type access
            CtTypeAccess<Float> floatTypeAccess = factory.createTypeAccess();
            floatTypeAccess.setAccessedType(factory.Type().createReference(Float.class));
            
            // Create Float.valueOf() invocation
            CtInvocation<Float> floatValueOf = factory.createInvocation();
            floatValueOf.setTarget(floatTypeAccess);
            floatValueOf.setExecutable(
                factory.createExecutableReference()
                    .setDeclaringType(factory.Type().createReference(Float.class))
                    .setSimpleName("valueOf")
            );
            floatValueOf.addArgument(((spoon.reflect.code.CtExpression<?>) primitiveExpression).clone());
            
            // Replace the primitive argument with Float.valueOf() call
            invocation.getArguments().set(0, floatValueOf);
            
        } catch (Exception e) {
            System.err.println("Error wrapping primitive with Float.valueOf: " + e.getMessage());
        }
    }
}