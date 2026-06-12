package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        launcher.setSourceOutputDirectory("/workspace/sorald/sorald/src/main/java");
        launcher.buildModel();

        CtClass<?> ctClass = launcher.getFactory().Class().get("sorald.sonar.SonarLintEngine");
        for (CtMethod<?> method : ctClass.getMethods()) {
            if (method.getSimpleName().equals("buildAnalysisEngineConfiguration")) {
                method.getElements(new TypeFilter<>(CtInvocation.class)).forEach(invocation -> {
                    if (invocation.getExecutable().getSimpleName().equals("addEnabledLanguages")) {
                        invocation.replace(invocation.getTarget());
                    }
                });
            }
        }
        
        launcher.prettyprint();
    }
}
