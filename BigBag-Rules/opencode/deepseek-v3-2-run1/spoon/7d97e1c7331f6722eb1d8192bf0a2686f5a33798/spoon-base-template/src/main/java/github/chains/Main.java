package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying transformation for removed Tv class...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all field accesses to Tv constants
        model.getElements(new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class) {
            @Override
            public boolean matches(CtFieldAccess<?> fieldAccess) {
                // Check if this is a field access to com.jcabi.aspects.Tv
                try {
                    String targetType = fieldAccess.getTarget().toString();
                    String fieldName = fieldAccess.getVariable().getSimpleName();
                    
                    // Check if this is Tv.CONSTANT access
                    // Could be "com.jcabi.aspects.Tv" or just "Tv" depending on imports
                    if (targetType.equals("com.jcabi.aspects.Tv") || 
                        targetType.equals("Tv")) {
                        
                        System.out.println("Found Tv." + fieldName + " at " + 
                                          fieldAccess.getPosition().getFile().getName() + ":" +
                                          fieldAccess.getPosition().getLine());
                        
                        // Map constant names to integer values
                        Integer intValue = mapConstantToInt(fieldName);
                        if (intValue != null) {
                            // Create integer literal to replace the field access
                            CtLiteral<Integer> literal = fieldAccess.getFactory().createLiteral(intValue);
                            fieldAccess.replace(literal);
                            System.out.println("  -> Replaced with: " + intValue);
                        } else {
                            System.err.println("  Warning: Unknown constant name: " + fieldName + ", replacing with 0");
                            CtLiteral<Integer> literal = fieldAccess.getFactory().createLiteral(0);
                            fieldAccess.replace(literal);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    // Ignore errors, continue processing
                }
                return false;
            }
        });
        
        // Also remove imports of com.jcabi.aspects.Tv
        model.getElements(new TypeFilter<spoon.reflect.declaration.CtImport>(spoon.reflect.declaration.CtImport.class) {
            @Override
            public boolean matches(spoon.reflect.declaration.CtImport importDecl) {
                String importStr = importDecl.toString();
                if (importStr.contains("com.jcabi.aspects.Tv")) {
                    System.out.println("Removing import: " + importStr);
                    importDecl.delete();
                    return true;
                }
                return false;
            }
        });
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static Integer mapConstantToInt(String constantName) {
        // Map common constant names to their integer values
        // This is a simplified mapping - in practice, you'd want a more complete mapping
        Map<String, Integer> constantMap = new HashMap<>();
        
        // Basic numbers
        constantMap.put("ONE", 1);
        constantMap.put("TWO", 2);
        constantMap.put("THREE", 3);
        constantMap.put("FOUR", 4);
        constantMap.put("FIVE", 5);
        constantMap.put("SIX", 6);
        constantMap.put("SEVEN", 7);
        constantMap.put("EIGHT", 8);
        constantMap.put("NINE", 9);
        constantMap.put("TEN", 10);
        
        // Other common constants
        constantMap.put("TWENTY", 20);
        constantMap.put("THIRTY", 30);
        constantMap.put("FORTY", 40);
        constantMap.put("FIFTY", 50);
        constantMap.put("SIXTY", 60);
        constantMap.put("SEVENTY", 70);
        constantMap.put("EIGHTY", 80);
        constantMap.put("NINETY", 90);
        constantMap.put("HUNDRED", 100);
        constantMap.put("THOUSAND", 1000);
        
        // Return mapped value or null if not found
        return constantMap.get(constantName);
    }
}