package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.setSourceOutputDirectory("/workspace/sorald/sorald/src/main/java");
        launcher.buildModel();

        CtModel model = launcher.getModel();

        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation inv : invocations) {
            if ("addEnabledLanguages".equals(inv.getExecutable().getSimpleName())) {
                inv.replace(inv.getTarget());
            }
        }

        launcher.prettyprint();
    }
}