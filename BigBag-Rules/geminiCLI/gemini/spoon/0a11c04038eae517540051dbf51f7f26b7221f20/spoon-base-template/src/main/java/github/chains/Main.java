package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/simplelocalize-cli/src/main/java/io/simplelocalize/cli/configuration/ConfigurationLoader.java");
        launcher.setSourceOutputDirectory("/workspace/simplelocalize-cli/src/main/java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        
        CtModel model = launcher.buildModel();
        
        CtClass<?> configLoaderClass = model.getElements(new TypeFilter<>(CtClass.class))
                .stream()
                .filter(c -> c.getSimpleName().equals("ConfigurationLoader"))
                .findFirst()
                .get();

        List<CtConstructorCall> constructorCalls = configLoaderClass.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall constructorCall : constructorCalls) {
            if (constructorCall.getType() != null && constructorCall.getType().getSimpleName().equals("Constructor") && constructorCall.getArguments().size() == 1) {
                CtExpression classArg = (CtExpression) constructorCall.getArguments().get(0);
                if (classArg.toString().contains("Configuration.class")) {
                    CtExpression newLoaderOptions = launcher.getFactory().Code().createCodeSnippetExpression("new org.yaml.snakeyaml.LoaderOptions()");
                    constructorCall.addArgument(newLoaderOptions);
                    System.out.println("Transformed: " + constructorCall);
                }
            }
        }
        
        launcher.prettyprint();
    }
}
