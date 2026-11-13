package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.support.visitor.ProcessingVisitor;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Hamcrest StringContains/StringStartsWith API fix to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir + "/src/test/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        
        CtModel model = launcher.buildModel();
        
        // Transform StringContains constructor calls - remove boolean ignoreCase parameter
        List<CtConstructorCall<?>> stringContainsCalls = model
            .getElements((Filter<CtConstructorCall<?>>) element -> {
                if (element.getType() == null || element.getType().getQualifiedName() == null) {
                    return false;
                }
                return element.getType().getQualifiedName().equals("org.hamcrest.core.StringContains")
                    && element.getArguments().size() == 2
                    && element.getArguments().get(0).getType() != null
                    && element.getArguments().get(0).getType().getQualifiedName().equals("boolean")
                    && element.getArguments().get(1).getType() != null
                    && element.getArguments().get(1).getType().getQualifiedName().equals("java.lang.String");
            });
        
        for (CtConstructorCall<?> call : stringContainsCalls) {
            System.out.println("Transforming StringContains at: " + call.getPosition());
            // Create new constructor call without boolean ignoreCase parameter
            CtConstructorCall<?> newCall = launcher.getFactory().createConstructorCall(
                launcher.getFactory().Type().createReference("org.hamcrest.core.StringContains"),
                call.getArguments().get(1)  // Keep only the string argument
            );
            call.replace(newCall);
        }
        
        // Transform StringStartsWith constructor calls - remove boolean ignoreCase parameter
        List<CtConstructorCall<?>> stringStartsWithCalls = model
            .getElements((Filter<CtConstructorCall<?>>) element -> {
                if (element.getType() == null || element.getType().getQualifiedName() == null) {
                    return false;
                }
                return element.getType().getQualifiedName().equals("org.hamcrest.core.StringStartsWith")
                    && element.getArguments().size() == 2
                    && element.getArguments().get(0).getType() != null
                    && element.getArguments().get(0).getType().getQualifiedName().equals("boolean")
                    && element.getArguments().get(1).getType() != null
                    && element.getArguments().get(1).getType().getQualifiedName().equals("java.lang.String");
            });
        
        for (CtConstructorCall<?> call : stringStartsWithCalls) {
            System.out.println("Transforming StringStartsWith at: " + call.getPosition());
            // Create new constructor call without boolean ignoreCase parameter
            CtConstructorCall<?> newCall = launcher.getFactory().createConstructorCall(
                launcher.getFactory().Type().createReference("org.hamcrest.core.StringStartsWith"),
                call.getArguments().get(1)  // Keep only the string argument
            );
            call.replace(newCall);
        }
        
        // Apply the transformations
        launcher.setSourceOutputDirectory(sourceDir + "/src/test/java");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Modified " + 
            (stringContainsCalls.size() + stringStartsWithCalls.size()) + " constructor calls.");
    }
}