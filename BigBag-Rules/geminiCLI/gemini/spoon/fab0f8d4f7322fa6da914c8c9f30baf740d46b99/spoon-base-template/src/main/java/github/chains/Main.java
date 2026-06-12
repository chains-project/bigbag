package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtCompilationUnit;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java");
        launcher.addInputResource("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java");
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java/");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation inv : invocations) {
            if (inv.getExecutable().getSimpleName().equals("asString")) {
                CtExpression target = inv.getTarget();
                if (target instanceof CtConstructorCall) {
                    CtConstructorCall hexOfCall = (CtConstructorCall) target;
                    if (hexOfCall.getType().getSimpleName().equals("HexOf")) {
                        CtExpression hexOfArg = (CtExpression) hexOfCall.getArguments().get(0);
                        if (hexOfArg instanceof CtConstructorCall) {
                            CtConstructorCall bytesOfCall = (CtConstructorCall) hexOfArg;
                            if (bytesOfCall.getType().getSimpleName().equals("BytesOf")) {
                                CtExpression bytesOfArg = (CtExpression) bytesOfCall.getArguments().get(0);
                                
                                CtExpression replacement = launcher.getFactory().Code().createCodeSnippetExpression(
                                    "org.apache.commons.codec.digest.DigestUtils.sha256Hex(" + bytesOfArg.toString() + ")"
                                );
                                inv.replace(replacement);
                            }
                        }
                    }
                }
            }
        }
        
        launcher.getModel().getAllTypes().forEach(type -> {
            if (type.getPosition().isValidPosition()) {
                CtCompilationUnit cu = launcher.getFactory().CompilationUnit().getOrCreate(type);
                cu.getImports().removeIf(anImport -> {
                    String importStr = anImport.toString();
                    return importStr.contains("org.cactoos.io.BytesOf") || importStr.contains("org.cactoos.text.HexOf");
                });
            }
        });
        
        launcher.prettyprint();
    }
}
