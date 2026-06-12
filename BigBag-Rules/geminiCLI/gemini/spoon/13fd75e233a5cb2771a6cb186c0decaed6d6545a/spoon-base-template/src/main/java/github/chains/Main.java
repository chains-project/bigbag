package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Launcher launcher = new Launcher();
        // Input: docker-adapter test sources only
        launcher.addInputResource("/workspace/docker-adapter/src/test/java");
        
        // Output: a temporary folder
        String outputDir = "/workspace/docker-adapter/spooned";
        launcher.setSourceOutputDirectory(outputDir);
        
        // Configure environment
        launcher.getEnvironment().setAutoImports(true); 
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(false);
        
        launcher.buildModel();
        
        List<CtConstructorCall> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall call : calls) {
            String typeName = call.getType().getQualifiedName();
            if ("org.hamcrest.core.StringContains".equals(typeName) || "org.hamcrest.core.StringStartsWith".equals(typeName)) {
                List<CtExpression> arguments = call.getArguments();
                if (arguments.size() == 2) {
                     CtExpression firstArg = arguments.get(0);
                     String argContent = firstArg.toString();
                     if (argContent.equals("true") || argContent.equals("false")) {
                         System.out.println("Fixing usage in " + call.getPosition().getFile().getName() + " at line " + call.getPosition().getLine());
                         call.removeArgument(firstArg);
                     }
                }
            }
        }
        
        launcher.prettyprint();
        System.out.println("Transformation complete. Output in " + outputDir);
    }
}