import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class RunTransformation {
    public static void main(String[] args) {
        String sourceDir = "/workspace/polyglot-maven/polyglot-yaml/src/main/java";
        System.out.println("Testing transformation on: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(11);
        
        try {
            CtModel model = launcher.buildModel();
            System.out.println("Model built successfully");
            
            // Check for Yaml constructor calls
            List<CtConstructorCall<?>> constructorCalls = Query.getElements(launcher.getFactory(), 
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class));
            
            System.out.println("Total constructor calls found: " + constructorCalls.size());
            
            for (CtConstructorCall<?> call : constructorCalls) {
                String typeName = call.getType().getQualifiedName();
                if (typeName != null && typeName.contains("Yaml")) {
                    System.out.println("Found Yaml constructor call with " + call.getArguments().size() + " parameters at: " + 
                        call.getPosition());
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
