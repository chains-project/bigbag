package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/bval/bval-jsr/src/test/java/org/apache/bval/jsr/DefaultMessageInterpolatorTest.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        CtModel model = launcher.getModel();

        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if ("getArgumentAt".equals(inv.getExecutable().getSimpleName())) {
                inv.getExecutable().setSimpleName("getArgument");
            }
        }

        launcher.setSourceOutputDirectory("/workspace/bval/bval-jsr/src/test/java");
        launcher.prettyprint();
    }
}