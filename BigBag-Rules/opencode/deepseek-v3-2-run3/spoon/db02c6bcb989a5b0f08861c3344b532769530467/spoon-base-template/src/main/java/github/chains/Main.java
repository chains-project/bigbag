package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar spoon-transformation.jar <input-source-dir> <output-dir> <direction>");
            System.err.println("  direction: 'addBoolean' to add false boolean parameter (for upgrading to new API)");
            System.err.println("             'removeBoolean' to remove boolean parameter (for downgrading to old API)");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/output addBoolean");
            System.exit(1);
        }

        String inputSourceDir = args[0];
        String outputDir = args[1];
        String direction = args[2];

        if (!direction.equals("addBoolean") && !direction.equals("removeBoolean")) {
            System.err.println("Error: direction must be either 'addBoolean' or 'removeBoolean'");
            System.exit(1);
        }

        System.out.println("Applying Hamcrest API migration transformation (" + direction + ")...");
        System.out.println("Input directory: " + inputSourceDir);
        System.out.println("Output directory: " + outputDir);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath to avoid dependency resolution issues
        launcher.addInputResource(inputSourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformation for Hamcrest StringContains, StringStartsWith, StringEndsWith, StringMatches
        model.getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> constructorCall) {
                super.visitCtConstructorCall(constructorCall);
                
                String typeName = constructorCall.getType().getQualifiedName();
                
                // Check if this is a Hamcrest string matcher constructor call
                if (typeName != null && (
                    typeName.equals("org.hamcrest.core.StringContains") ||
                    typeName.equals("org.hamcrest.core.StringStartsWith") ||
                    typeName.equals("org.hamcrest.core.StringEndsWith") ||
                    typeName.equals("org.hamcrest.core.StringMatches"))) {
                    
                    if (direction.equals("addBoolean")) {
                        // Convert single argument to two arguments with false (case-sensitive)
                        if (constructorCall.getArguments().size() == 1) {
                            System.out.println("Transforming single arg to two args: " + typeName);
                            CtConstructorCall<T> newCall = constructorCall.getFactory().createConstructorCall(
                                constructorCall.getType(),
                                constructorCall.getFactory().createLiteral(false),
                                constructorCall.getArguments().get(0)
                            );
                            constructorCall.replace(newCall);
                        }
                        // If already two args, leave as is (might need to validate boolean)
                    } else if (direction.equals("removeBoolean")) {
                        // Convert two arguments to single argument (losing ignoreCase info)
                        if (constructorCall.getArguments().size() == 2) {
                            System.out.println("Transforming two args to single arg: " + typeName);
                            CtConstructorCall<T> newCall = constructorCall.getFactory().createConstructorCall(
                                constructorCall.getType(),
                                constructorCall.getArguments().get(1)
                            );
                            constructorCall.replace(newCall);
                        }
                        // If already single arg, leave as is
                    }
                }
            }
        });
        
        // Output transformed code
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to: " + outputDir);
        System.out.println("Note: When using 'removeBoolean', case-sensitivity information is lost.");
        System.out.println("      Calls with 'true' (ignoreCase) will become case-sensitive.");
    }
}