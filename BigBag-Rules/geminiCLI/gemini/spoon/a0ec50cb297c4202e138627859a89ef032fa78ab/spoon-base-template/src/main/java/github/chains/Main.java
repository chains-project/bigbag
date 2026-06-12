package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java");
        launcher.addInputResource("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java");
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();

        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("asString") && inv.getTarget() != null) {
                String targetStr = inv.getTarget().toString();
                if (targetStr.contains("HexOf") && targetStr.contains("BytesOf")) {
                    CtExpression<?> newExpr = launcher.getFactory().Code().createCodeSnippetExpression(
                            "org.apache.commons.codec.binary.Hex.encodeHexString(sha.digest())"
                    );
                    inv.replace(newExpr);
                }
            }
        }
        
        for (spoon.reflect.cu.CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            java.util.List<CtImport> toRemove = new java.util.ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains("org.cactoos")) {
                    toRemove.add(imp);
                }
            }
            for (CtImport imp : toRemove) {
                cu.getImports().remove(imp);
            }
        }
        
        launcher.prettyprint();
    }
}
