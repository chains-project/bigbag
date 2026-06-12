package github.chains;

import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.cu.CompilationUnit;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        MavenLauncher launcher = new MavenLauncher("/workspace/docker-adapter", MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        CtModel model = launcher.getModel();

        List<CtInvocation> invocations = model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (super.matches(element)) {
                    if ("asString".equals(element.getExecutable().getSimpleName())) {
                        CtExpression target = element.getTarget();
                        if (target instanceof CtConstructorCall) {
                            if (((CtConstructorCall) target).getType().getSimpleName().equals("HexOf")) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });

        for (CtInvocation inv : invocations) {
            CtConstructorCall hexOfCall = (CtConstructorCall) inv.getTarget();
            CtConstructorCall bytesOfCall = (CtConstructorCall) hexOfCall.getArguments().get(0);
            CtExpression shaDigestArg = (CtExpression) bytesOfCall.getArguments().get(0);
            
            CtExpression replacement = launcher.getFactory().Code().createCodeSnippetExpression(
                "org.apache.commons.codec.binary.Hex.encodeHexString(" + shaDigestArg.toString() + ")"
            );
            inv.replace(replacement);
        }

        for (CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            imports.removeIf(imp -> imp.toString().contains("cactoos"));
            cu.setImports(imports);
        }

        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java");
        launcher.prettyprint();
    }
}
