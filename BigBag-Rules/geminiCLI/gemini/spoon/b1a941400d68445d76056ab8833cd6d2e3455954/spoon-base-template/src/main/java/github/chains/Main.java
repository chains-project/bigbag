package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/fluxtion/compiler/src/test/java/com/fluxtion/compiler/builder/factory/GraphOfInstancesTest.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory("/workspace/fluxtion/compiler/src/test/java");

        CtModel model = launcher.buildModel();

        for (CtConstructorCall<?> c : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (c.getType().getQualifiedName().equals("org.yaml.snakeyaml.constructor.Constructor")) {
                if (c.getArguments().isEmpty()) {
                    CtExpression<?> arg = launcher.getFactory().createCodeSnippetExpression("new org.yaml.snakeyaml.LoaderOptions()");
                    ((CtConstructorCall) c).addArgument(arg);
                }
            }
        }

        launcher.prettyprint();
    }
}
