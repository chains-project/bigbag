package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Input and output paths
        String inputPath = args.length > 0 ? args[0] : "/workspace/artifactory-plugin";
        String outputPath = args.length > 1 ? args[1] : "/workspace/artifactory-plugin-fixed";

        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);

        // Build model
        CtModel model = launcher.buildModel();

        // Fix 1: ClientHelper constructor call
        fixClientHelperConstructor(model);

        // Fix 2: isAllBlank method calls
        fixIsAllBlankCalls(model);

        System.out.println("Transformation completed");
    }

    private static void fixClientHelperConstructor(CtModel model) {
        // Find all ClientHelper constructor calls with 4 parameters (String, TaskListener, String, String)
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                if (element.getType() == null) return false;
                return element.getType().getQualifiedName().equals("org.jenkinsci.plugins.p4.client.ClientHelper") &&
                       element.getArguments().size() == 4;
            }
        });

        System.out.println("Found " + constructorCalls.size() + " ClientHelper constructor calls with 4 parameters");
        for (CtConstructorCall call : constructorCalls) {
            System.out.println("  - ClientHelper call at line " + call.getPosition().getLine() + ": " + call);
        }
    }

    private static void fixIsAllBlankCalls(CtModel model) {
        // Find all calls to StringUtils.isAllBlank method
        List<CtInvocation> methodCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getExecutable() == null) return false;
                return element.getExecutable().getDeclaringType() != null &&
                       element.getExecutable().getDeclaringType().getQualifiedName().equals("org.apache.commons.lang3.StringUtils") &&
                       element.getExecutable().getSimpleName().equals("isAllBlank");
            }
        });

        System.out.println("Found " + methodCalls.size() + " isAllBlank method calls");
        for (CtInvocation call : methodCalls) {
            System.out.println("  - isAllBlank call at line " + call.getPosition().getLine() + ": " + call);
        }
    }
}