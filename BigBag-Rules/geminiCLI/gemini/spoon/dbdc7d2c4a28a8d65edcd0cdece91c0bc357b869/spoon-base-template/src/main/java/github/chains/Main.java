package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set the input directory to the ci-sauce source code
        launcher.addInputResource("/workspace/ci-sauce/src/main/java");
        
        // Set output directory to the same source directory to overwrite
        launcher.setSourceOutputDirectory("/workspace/ci-sauce/src/main/java");
        
        // Configure environment
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // Perform transformation
        for (CtType<?> type : model.getAllTypes()) {
            if (type.getQualifiedName().equals("com.saucelabs.ci.sauceconnect.SauceConnectFourManager")) {
                for (CtMethod<?> method : type.getMethods()) {
                    if (method.getSimpleName().equals("extractArchive")) {
                        List<CtInvocation> invocations = method.getElements(new TypeFilter<>(CtInvocation.class));
                        for (CtInvocation invocation : invocations) {
                             if (invocation.getExecutable().getSimpleName().equals("enableLogging")) {
                                 invocation.delete();
                                 System.out.println("Removed enableLogging invocation in " + type.getQualifiedName());
                             }
                        }
                    }
                }
            }
        }

        // Apply changes
        launcher.prettyprint();
    }
}
