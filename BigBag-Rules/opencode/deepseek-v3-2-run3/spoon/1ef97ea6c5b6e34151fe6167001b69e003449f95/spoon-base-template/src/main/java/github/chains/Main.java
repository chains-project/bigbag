package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;

/**
 * Generic transformation rule for datafaker 1.4.0 breaking change:
 * 
 * Breaking Change: DateAndTime.between(Date, Date) -> DateAndTime.between(Timestamp, Timestamp)
 * 
 * Transformation:
 * 1. Find all classes extending net.datafaker.DateAndTime
 * 2. For each between(Date, Date) method:
 *    - Change parameter types from Date to Timestamp
 *    - Method body may need manual adjustment if converting Date to Timestamp
 *    
 * This transformation is generic and can be applied to any project affected by
 * the datafaker 1.4.0 API change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-dir> <output-dir>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/output");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming DateAndTime breaking change...");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setNoClasspath(true); // Simpler for generic transformation
        
        try {
            // Build model
            CtModel model = launcher.buildModel();
            
            // Find all classes that extend net.datafaker.DateAndTime
            int transformationCount = 0;
            for (CtClass clazz : model.getElements(new TypeFilter<CtClass>(CtClass.class) {
                @Override
                public boolean matches(CtClass element) {
                    CtTypeReference superClass = element.getSuperclass();
                    if (superClass == null) return false;
                    return "net.datafaker.DateAndTime".equals(superClass.getQualifiedName());
                }
            })) {
                System.out.println("Found class extending DateAndTime: " + clazz.getQualifiedName());
                transformationCount += transformDateAndTimeClass(clazz);
            }
            
            if (transformationCount > 0) {
                // Apply transformations
                launcher.prettyprint();
                System.out.println("Applied " + transformationCount + " transformation(s)");
                System.out.println("Transformation complete! Output written to: " + outputDir);
            } else {
                System.out.println("No DateAndTime extensions found to transform.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transform a class extending DateAndTime
     * @return number of transformations applied
     */
    private static int transformDateAndTimeClass(CtClass clazz) {
        int transformations = 0;
        
        for (CtMethod method : clazz.getMethods()) {
            // Look for between methods with Date parameters
            if ("between".equals(method.getSimpleName())) {
                // Check if all parameters are Date
                boolean allDateParams = true;
                for (CtParameter param : method.getParameters()) {
                    CtTypeReference paramType = param.getType();
                    if (!"java.util.Date".equals(paramType.getQualifiedName())) {
                        allDateParams = false;
                        break;
                    }
                }
                
                if (allDateParams && method.getParameters().size() == 2) {
                    // Transform: between(Date, Date) -> between(Timestamp, Timestamp)
                    for (CtParameter param : method.getParameters()) {
                        param.setType(param.getType().getFactory().createReference("java.sql.Timestamp"));
                    }
                    System.out.println("  Transformed: " + method.getSignature());
                    transformations++;
                    
                    // Note: Method body may need manual adjustment
                    // e.g., super.between(from, to) may need conversion if from/to are Date
                    System.out.println("    Note: Check method body for Date->Timestamp conversion");
                }
            }
        }
        
        return transformations;
    }
}