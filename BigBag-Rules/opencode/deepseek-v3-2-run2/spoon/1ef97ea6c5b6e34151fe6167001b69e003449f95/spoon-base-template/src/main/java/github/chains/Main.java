package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying DateAndTime API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find all methods in the model
            List<CtMethod<?>> methods = model.getElements(new TypeFilter<>(CtMethod.class));
            
            int fixedMethods = 0;
            for (CtMethod<?> method : methods) {
                if (fixDateAndTimeMethods(method)) {
                    fixedMethods++;
                }
            }
            
            // Apply the transformations
            launcher.prettyprint();
            System.out.println("Transformation completed. Fixed " + fixedMethods + " method(s).");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean fixDateAndTimeMethods(CtMethod<?> method) {
        // Check if method has @Override annotation
        boolean hasOverride = method.getAnnotations().stream()
            .anyMatch(anno -> anno.getAnnotationType().getSimpleName().equals("Override"));
        
        if (!hasOverride) {
            return false;
        }
        
        String methodName = method.getSimpleName();
        
        // Only fix methods that override DateAndTime methods
        if (!methodName.equals("between") && !methodName.equals("birthday") && 
            !methodName.equals("future") && !methodName.equals("past")) {
            return false;
        }
        
        // Check return type - should be Timestamp in new API
        CtTypeReference<?> returnType = method.getType();
        boolean returnsTimestamp = isTimestampType(returnType);
        
        if (!returnsTimestamp) {
            return false;
        }
        
        boolean changed = false;
        
        // Special handling for between() method - parameters changed from Date to Timestamp
        if (methodName.equals("between") && method.getParameters().size() == 2) {
            for (CtParameter<?> param : method.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (isDateType(paramType)) {
                    // Change Date parameter to Timestamp for between() method
                    try {
                        CtTypeReference<?> timestampType = param.getFactory().createReference("java.sql.Timestamp");
                        param.setType(timestampType);
                        changed = true;
                        System.out.println("  Changed between() parameter from Date to Timestamp: " + 
                                          method.getSignature());
                    } catch (Exception e) {
                        System.err.println("  Error changing parameter type: " + e.getMessage());
                    }
                }
            }
        }
        
        // For future() and past() with Date reference parameter, keep as Date
        // (parameters didn't change in new API, only return type changed)
        
        return changed;
    }
    
    private static boolean isDateType(CtTypeReference<?> typeRef) {
        if (typeRef == null) return false;
        try {
            String qualifiedName = typeRef.getQualifiedName();
            return "java.util.Date".equals(qualifiedName);
        } catch (Exception e) {
            return "Date".equals(typeRef.getSimpleName());
        }
    }
    
    private static boolean isTimestampType(CtTypeReference<?> typeRef) {
        if (typeRef == null) return false;
        try {
            String qualifiedName = typeRef.getQualifiedName();
            return "java.sql.Timestamp".equals(qualifiedName) || 
                   "Timestamp".equals(typeRef.getSimpleName());
        } catch (Exception e) {
            return "Timestamp".equals(typeRef.getSimpleName());
        }
    }
}