package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming Hamcrest StringContains/StringStartsWith constructors...");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to StringContains with boolean first parameter
        List<CtConstructorCall> stringContainsCalls = model.getElements(
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    if (element.getType() == null || element.getExecutable() == null) {
                        return false;
                    }
                    
                    // Check if it's StringContains constructor
                    String typeName = element.getType().getQualifiedName();
                    if (!"org.hamcrest.core.StringContains".equals(typeName)) {
                        return false;
                    }
                    
                    // Check if it has exactly 2 arguments (boolean, String)
                    List<?> arguments = element.getArguments();
                    if (arguments.size() != 2) {
                        return false;
                    }
                    
                    // First argument should be boolean (or Boolean)
                    return true;
                }
            }
        );
        
        // Transform StringContains calls
        for (CtConstructorCall call : stringContainsCalls) {
            System.out.println("Transforming StringContains at: " + call.getPosition());
            
            // Keep only the second argument (string)
            List<CtExpression<?>> arguments = call.getArguments();
            if (arguments.size() == 2) {
                call.getArguments().clear();
                call.addArgument(arguments.get(1));
            }
        }
        
        // Find all constructor calls to StringStartsWith with boolean first parameter
        List<CtConstructorCall> stringStartsWithCalls = model.getElements(
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    if (element.getType() == null || element.getExecutable() == null) {
                        return false;
                    }
                    
                    // Check if it's StringStartsWith constructor
                    String typeName = element.getType().getQualifiedName();
                    if (!"org.hamcrest.core.StringStartsWith".equals(typeName)) {
                        return false;
                    }
                    
                    // Check if it has exactly 2 arguments (boolean, String)
                    List<?> arguments = element.getArguments();
                    if (arguments.size() != 2) {
                        return false;
                    }
                    
                    // First argument should be boolean (or Boolean)
                    return true;
                }
            }
        );
        
        // Transform StringStartsWith calls
        for (CtConstructorCall call : stringStartsWithCalls) {
            System.out.println("Transforming StringStartsWith at: " + call.getPosition());
            
            // Keep only the second argument (string)
            List<CtExpression<?>> arguments = call.getArguments();
            if (arguments.size() == 2) {
                call.getArguments().clear();
                call.addArgument(arguments.get(1));
            }
        }
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Processed " + 
            (stringContainsCalls.size() + stringStartsWithCalls.size()) + " constructor calls.");
    }
}