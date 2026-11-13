package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import java.util.HashMap;
import java.util.Map;

public class SimpleMain {
    
    private static final Map<String, String> TV_CONSTANTS = new HashMap<>();
    
    static {
        TV_CONSTANTS.put("SEVEN", "7");
        TV_CONSTANTS.put("TEN", "10");
        TV_CONSTANTS.put("MILLION", "1000000");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java SimpleMain <source-dir> <output-dir>");
            System.exit(1);
        }
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        
        CtModel model = launcher.buildModel();
        
        // Remove Tv imports
        model.getElements(new TypeFilter<CtImport>(CtImport.class)).forEach(importElem -> {
            String importStr = importElem.toString();
            if (importStr.contains("com.jcabi.aspects.Tv")) {
                System.out.println("Removing import: " + importStr);
                importElem.delete();
            }
        });
        
        // Replace Tv field accesses
        model.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class)).forEach(fieldRead -> {
            try {
                CtFieldReference<?> fieldRef = fieldRead.getVariable();
                if (fieldRef != null) {
                    CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
                    if (declaringType != null && "com.jcabi.aspects.Tv".equals(declaringType.getQualifiedName())) {
                        String fieldName = fieldRef.getSimpleName();
                        String literalValue = TV_CONSTANTS.get(fieldName);
                        if (literalValue != null) {
                            System.out.println("Replacing Tv." + fieldName + " with " + literalValue);
                            Factory factory = fieldRead.getFactory();
                            long longValue = Long.parseLong(literalValue);
                            CtLiteral<Integer> literal = factory.createLiteral((int) longValue);
                            fieldRead.replace(literal);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing field read: " + e.getMessage());
            }
        });
        
        launcher.prettyprint();
        System.out.println("Transformation complete!");
    }
}